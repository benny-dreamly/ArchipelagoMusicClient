/*
 * SPDX-License-Identifier: MPL-2.0
 */
package app.e2e;

import app.remote.CompanionServer;
import app.remote.CompanionState;
import com.google.gson.JsonObject;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Stand-in for the real desktop app during end-to-end phone testing. Generates
 * a small WAV library in a temp directory, serves it over CompanionServer, and
 * mimics the desktop's command handling and state broadcasting. Watching the
 * "[event]" lines is how a live test is verified.
 */
public final class CompanionHarness {

    private static final int HTTP_PORT = 8311;
    private static final int WS_PORT = 8312;
    private static final int DURATION = 30; // seconds per track
    private static final int SAMPLE_RATE = 44100;
    private static final int TICK_MS = 1000;

    private static volatile java.util.concurrent.ScheduledFuture<?> tick;

    private CompanionHarness() {
    }

    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("archipelago-e2e");
        Path songA = root.resolve("Song A.wav");
        Path songB = root.resolve("Song B.wav");
        writeWav(songA, DURATION, 440);
        writeWav(songB, DURATION, 660);
        System.out.println("[harness] library at " + root);

        CompanionServer server = new CompanionServer(root.toFile(), HTTP_PORT, WS_PORT);
        server.start();
        System.out.println("[harness] reachable at " + server.reachableAddresses());

        AtomicReference<Path> current = new AtomicReference<>(songA);
        AtomicInteger volume = new AtomicInteger(50);
        AtomicInteger positionTicks = new AtomicInteger(0);
        AtomicBoolean playing = new AtomicBoolean(false);

        server.setCommandHandler(cmd -> {
            String name = cmd.has("cmd") ? cmd.get("cmd").getAsString() : "";
            System.out.println("[cmd] " + name);
            switch (name) {
                case "toggle" -> {
                    if (playing.getAndSet(!playing.get())) {
                        server.send(commandJson("pause").toString());
                    } else {
                        playing.set(true);
                        sendPlay(server, current.get());
                    }
                }
                case "pause" -> {
                    playing.set(false);
                    server.send(commandJson("pause").toString());
                }
                case "play" -> sendPlay(server, current.get());
                case "next" -> {
                    playing.set(true);
                    current.set(otherTrack(songA, songB, current.get()));
                    positionTicks.set(0);
                    sendPlay(server, current.get());
                }
                case "seek", "rate", "volume" -> {
                    if (cmd.has("value")) {
                        volume.set(cmd.get("value").getAsInt());
                    }
                    broadcastState(server, current.get(), playing.get(), volume.get(), positionTicks.get());
                }
                default -> {
                }
            }
        });

        server.setEventHandler(event -> {
            System.out.println("[event] " + event);
            if (!event.has("event")) return;
            if ("ended".equals(event.get("event").getAsString())) {
                Path next = otherTrack(songA, songB, current.get());
                current.set(next);
                positionTicks.set(0);
                playing.set(true);
                sendPlay(server, next);
            }
        });

        ScheduledExecutorService ticks = Executors.newSingleThreadScheduledExecutor();
        tick = ticks.scheduleAtFixedRate(() -> {
            int pos = positionTicks.getAndUpdate(p -> p >= DURATION * 1000 ? 0 : p + TICK_MS);
            broadcastState(server, current.get(), playing.get(), volume.get(), pos);
        }, 1, TICK_MS, TimeUnit.MILLISECONDS);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (tick != null) {
                tick.cancel(false);
            }
            ticks.shutdownNow();
            server.stop();
            System.out.println("[harness] stopped");
        }));

        System.out.println("[harness] running. Connect the phone to this host, then press Ctrl-C.");
        Thread.currentThread().join();
    }

    private static Path otherTrack(Path songA, Path songB, Path track) {
        return track.equals(songA) ? songB : songA;
    }

    private static void sendPlay(CompanionServer server, Path track) {
        JsonObject play = new JsonObject();
        play.addProperty("type", "command");
        play.addProperty("cmd", "play");
        play.addProperty("streamPath", server.streamPath(track.toFile()));
        play.addProperty("title", track.getFileName().toString().replace(".wav", ""));
        server.send(play.toString());
    }

    private static JsonObject commandJson(String cmd) {
        JsonObject json = new JsonObject();
        json.addProperty("type", "command");
        json.addProperty("cmd", cmd);
        return json;
    }

    private static void broadcastState(CompanionServer server, Path track, boolean playing,
                                       int volume, int positionMs) {
        File file = track.toFile();
        String title = file.getName().replace(".wav", "");
        CompanionState state = new CompanionState(
                title, null, server.streamPath(file),
                DURATION * 1000L, positionMs, playing, volume,
                List.of("Song A", "Song B"), CompanionState.SOURCE_PHONE);
        server.broadcast(state.toJson());
    }

    private static void writeWav(Path file, int seconds, int frequency) throws IOException {
        int dataSize = SAMPLE_RATE * seconds * 2;
        ByteBuffer bytes = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN);
        bytes.put("RIFF".getBytes(StandardCharsets.US_ASCII));
        bytes.putInt(36 + dataSize);
        bytes.put("WAVE".getBytes(StandardCharsets.US_ASCII));
        bytes.put("fmt ".getBytes(StandardCharsets.US_ASCII));
        bytes.putInt(16);
        bytes.putShort((short) 1);
        bytes.putShort((short) 1);
        bytes.putInt(SAMPLE_RATE);
        bytes.putInt(SAMPLE_RATE * 2);
        bytes.putShort((short) 2);
        bytes.putShort((short) 16);
        bytes.put("data".getBytes(StandardCharsets.US_ASCII));
        bytes.putInt(dataSize);
        double phase = 0;
        for (int i = 0; i < SAMPLE_RATE * seconds; i++) {
            short sample = (short) (Math.sin(phase) * Short.MAX_VALUE * 0.4);
            bytes.putShort(sample);
            phase += 2 * Math.PI * frequency / SAMPLE_RATE;
        }
        try (OutputStream out = Files.newOutputStream(file)) {
            out.write(bytes.array());
        }
    }
}