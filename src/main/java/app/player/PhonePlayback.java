/*
 * SPDX-License-Identifier: MPL-2.0
 */
package app.player;

import com.google.gson.JsonObject;
import javafx.scene.media.MediaPlayer;
import javafx.util.Duration;

import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Plays audio on a connected phone: the desktop never creates a MediaPlayer.
 * The desktop stays the session owner (checks, energy, queue) and sends
 * playback commands over a WebSocket; the phone reports progress/state back
 * as events via {@link #handlePhoneEvent(JsonObject)}. MusicAppDemo still
 * holds the Archipelago logic through the shared {@link PlaybackEngine}
 * contract (onEnded triggers checks/queue advance just like local playback).
 */
public final class PhonePlayback implements PlaybackEngine {

    private final Consumer<String> sink;
    private final Function<Song, String> streamPathResolver;

    private boolean loaded;
    private boolean playing;
    private long positionMs;
    private long durationMs = -1;

    private Runnable onReady;
    private Runnable onEnded;
    private Consumer<String> onError;
    private Consumer<Duration> onCurrentTime;

    public PhonePlayback(Consumer<String> sink, Function<Song, String> streamPathResolver) {
        this.sink = sink;
        this.streamPathResolver = streamPathResolver;
    }

    @Override
    public void loadAndPlay(Song song) {
        loaded = true;
        playing = false;
        positionMs = 0;
        durationMs = -1;
        if (song == null || song.getFilePath() == null) {
            return;
        }
        JsonObject cmd = command("play");
        cmd.addProperty("streamPath", streamPathResolver.apply(song));
        cmd.addProperty("title", song.getTitle());
        send(cmd);
    }

    @Override
    public void pause() {
        if (!loaded) return;
        playing = false;
        send(command("pause"));
    }

    @Override
    public void resume() {
        if (!loaded) return;
        playing = true;
        send(command("resume"));
    }

    @Override
    public void stopAndRelease() {
        loaded = false;
        playing = false;
        positionMs = 0;
        durationMs = -1;
        send(command("stop"));
    }

    @Override
    public MediaPlayer.Status getStatus() {
        if (!loaded) return MediaPlayer.Status.UNKNOWN;
        return playing ? MediaPlayer.Status.PLAYING : MediaPlayer.Status.PAUSED;
    }

    @Override
    public boolean isLoaded() {
        return loaded;
    }

    @Override
    public boolean isPlaying() {
        return playing;
    }

    @Override
    public boolean isPaused() {
        return loaded && !playing;
    }

    @Override
    public Duration getCurrentTime() {
        return Duration.millis((double) positionMs);
    }

    @Override
    public Duration getTotalDuration() {
        return durationMs < 0 ? null : Duration.millis((double) durationMs);
    }

    @Override
    public void seek(Duration time) {
        positionMs = (long) time.toMillis();
        JsonObject cmd = command("seek");
        cmd.addProperty("positionMs", positionMs);
        send(cmd);
    }

    @Override
    public void setRate(double rate) {
        JsonObject cmd = command("rate");
        cmd.addProperty("rate", rate);
        send(cmd);
    }

    @Override
    public void setVolume(double volume) {
        JsonObject cmd = command("volume");
        cmd.addProperty("value", Math.round(volume * 100));
        send(cmd);
    }

    @Override
    public void setOnReady(Runnable onReady) {
        this.onReady = onReady;
    }

    @Override
    public void setOnEnded(Runnable onEnded) {
        this.onEnded = onEnded;
    }

    @Override
    public void setOnError(Consumer<String> onError) {
        this.onError = onError;
    }

    @Override
    public void setOnCurrentTime(Consumer<Duration> onCurrentTime) {
        this.onCurrentTime = onCurrentTime;
    }

    /**
     * Ingests a phone event ({@code {type:"event", event:...}}) on the
     * websocket thread and updates playback state. Callers that touch the UI
     * must dispatch through Platform.runLater before mutating controls.
     */
    public void handlePhoneEvent(JsonObject event) {
        if (event == null) return;
        String name = event.has("event") ? event.get("event").getAsString() : "";
        if (event.has("positionMs")) {
            positionMs = event.get("positionMs").getAsLong();
        }
        try {
            switch (name) {
                case "started" -> {
                    loaded = true;
                    playing = true;
                    if (event.has("durationMs")) {
                        durationMs = event.get("durationMs").getAsLong();
                    }
                    if (onReady != null) onReady.run();
                }
                case "paused" -> playing = false;
                case "resumed" -> playing = true;
                case "ended" -> {
                    playing = false;
                    if (onEnded != null) onEnded.run();
                }
                case "error" -> {
                    if (onError != null) {
                        onError.accept(event.has("message")
                                ? event.get("message").getAsString() : "Unknown error");
                    }
                }
                default -> {
                }
            }
        } finally {
            if (!"ended".equals(name) && onCurrentTime != null) {
                onCurrentTime.accept(Duration.millis((double) positionMs));
            }
        }
    }

    private JsonObject command(String cmd) {
        JsonObject json = new JsonObject();
        json.addProperty("type", "command");
        json.addProperty("cmd", cmd);
        return json;
    }

    private void send(JsonObject json) {
        sink.accept(json.toString());
    }
}