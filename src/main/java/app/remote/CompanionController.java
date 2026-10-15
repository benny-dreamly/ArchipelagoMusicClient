/*
 * SPDX-License-Identifier: MPL-2.0
 */
package app.remote;

import app.AppContext;
import app.logic.QueueManager;
import app.player.Album;
import app.player.LocalPlayback;
import app.player.PhonePlayback;
import app.player.PlaybackController;
import app.player.PlaybackEngine;
import app.player.Song;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import javafx.application.Platform;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static app.util.ConfigManager.loadCompanionServer;

/**
 * Runs the LAN companion server the phone app connects to: starts/stops it,
 * publishes desktop playback state, swaps the audio sink between the desktop
 * and the phone, and applies remote commands. Extracted from
 * {@code MusicAppDemo}; the application-specific bits are reached through the
 * {@link AppContext}, and playback flow goes through {@link PlaybackController}.
 */
public class CompanionController {

    private static final Logger LOGGER = LoggerFactory.getLogger(CompanionController.class);

    private static final int COMPANION_HTTP_PORT = 8311;
    private static final int COMPANION_WS_PORT = 8312;

    private final AppContext host;
    private final PlaybackController playback;

    private CompanionServer server;
    private ScheduledExecutorService ticker;
    private ScheduledFuture<?> tickerTask;
    // Folder the companion server serves; remembered so toggling the setting
    // can start it without reloading the library.
    private List<File> musicRoot;

    public CompanionController(AppContext host, PlaybackController playback) {
        this.host = host;
        this.playback = playback;
    }

    public void start(List<File> musicRoots) {
        stop();
        // Remember the roots before the empty check so an empty library clears
        // any previously remembered roots instead of leaving restart() to serve
        // a stale folder.
        this.musicRoot = musicRoots;
        if (musicRoots == null || musicRoots.isEmpty()) return;
        if (!loadCompanionServer()) {
            LOGGER.info("Companion server disabled in settings; not starting");
            return;
        }
        server = new CompanionServer(musicRoots, COMPANION_HTTP_PORT, COMPANION_WS_PORT);
        server.setCommandHandler(this::handleCommand);
        server.setEventHandler(json -> Platform.runLater(() -> {
            if (playback.getEngine() instanceof PhonePlayback phone) {
                phone.handlePhoneEvent(json);
            }
        }));
        try {
            server.start();
        } catch (Exception e) {
            LOGGER.warn("Could not start companion server: {}", e.getMessage());
            server = null;
            return;
        }
        ticker = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "companion-state-ticker");
            t.setDaemon(true);
            return t;
        });
        tickerTask = ticker.scheduleAtFixedRate(
                () -> Platform.runLater(this::tick), 250, 500, TimeUnit.MILLISECONDS);
    }

    /** (Re)start on the last known music root, if any. */
    public void restart() {
        if (musicRoot != null) {
            start(musicRoot);
        }
    }

    public void stop() {
        switchPlaybackMode(false);
        if (tickerTask != null) {
            tickerTask.cancel(false);
            tickerTask = null;
        }
        if (ticker != null) {
            ticker.shutdownNow();
            ticker = null;
        }
        if (server != null) {
            server.stop();
            server = null;
        }
    }

    private void tick() {
        boolean phonePresent = server != null && server.hasActivePhone();
        if (phonePresent != (playback.getEngine() instanceof PhonePlayback)) {
            switchPlaybackMode(phonePresent);
        }
        publishState();
    }

    public void publishState() {
        CompanionServer running = server;
        if (running == null || !running.hasClients()) return;

        String title = null;
        String album = null;
        String stream = null;
        long durationMs = 0;
        long positionMs = 0;
        boolean playing = false;
        Song currentSong = playback.getCurrentSong();
        if (currentSong != null) {
            title = currentSong.getTitle();
            Album songAlbum = host.albumForSong(currentSong);
            if (songAlbum != null) album = songAlbum.getName();
            if (currentSong.getFilePath() != null) {
                stream = running.streamPath(new File(currentSong.getFilePath()));
            }
        }
        PlaybackEngine engine = playback.getEngine();
        if (engine != null) {
            playing = engine.isPlaying();
            positionMs = (long) engine.getCurrentTime().toMillis();
            Duration total = engine.getTotalDuration();
            if (total != null) durationMs = (long) total.toMillis();
        }
        QueueManager queueManager = host.queueManager();
        List<String> queue = queueManager != null
                ? queueManager.asList().stream().map(Song::getTitle).toList()
                : Collections.emptyList();
        int volume = (int) host.volume();
        String activeSource = engine instanceof PhonePlayback
                ? CompanionState.SOURCE_PHONE : CompanionState.SOURCE_DESKTOP;

        running.broadcast(new CompanionState(title, album, stream, durationMs, positionMs,
                playing, volume, queue, activeSource).toJson());
    }

    /**
     * Swaps the audio sink between the desktop speakers and the connected
     * phone. A phone-mode switch hands the current track over to the phone at
     * its current position; leaving phone mode resumes that position locally.
     * Only the active engine plays: no double audio.
     */
    private void switchPlaybackMode(boolean phoneMode) {
        CompanionServer running = server;
        if (running == null) return;
        if (phoneMode == (playback.getEngine() instanceof PhonePlayback)) return;

        PlaybackEngine oldEngine = playback.getEngine();
        Song song = playback.getCurrentSong();
        Duration resumeAt = oldEngine != null ? oldEngine.getCurrentTime() : Duration.ZERO;
        boolean wasLoaded = oldEngine != null && oldEngine.isLoaded();
        boolean wasPlaying = oldEngine != null && oldEngine.isPlaying();

        if (oldEngine != null) {
            oldEngine.stopAndRelease();
        }

        if (phoneMode) {
            PhonePlayback phone = new PhonePlayback(running::send,
                    s -> running.streamPath(new File(s.getFilePath())));
            playback.setEngine(phone);
            if (song != null && song.getFilePath() != null && wasLoaded) {
                playback.configureEngine(phone, song, host.albumForSong(song), null);
                phone.loadAndPlay(song);
                if (resumeAt != null && resumeAt.greaterThan(Duration.ZERO)) {
                    phone.seek(resumeAt);
                }
                if (!wasPlaying) {
                    phone.pause();
                }
            }
        } else {
            LocalPlayback local = new LocalPlayback();
            playback.setEngine(local);
            if (song != null && song.getFilePath() != null && wasLoaded) {
                playback.configureEngine(local, song, host.albumForSong(song), resumeAt);
                local.loadAndPlay(song);
                if (!wasPlaying) {
                    local.pause();
                }
            }
        }
        publishState();
    }

    private void handleCommand(JsonObject message) {
        // Validate off the JavaFX thread: a JSON null or wrong-typed field would
        // otherwise throw inside the runLater callback (past CompanionServer's
        // try/catch) and skip the publishState() below.
        if (message == null) {
            return;
        }
        if (message.has("cmd") && !isString(message.get("cmd"))) {
            LOGGER.info("Companion: ignoring command with a non-string 'cmd'");
            return;
        }
        if (message.has("value") && readNumber(message.get("value")) == null) {
            LOGGER.info("Companion: ignoring command with an invalid 'value'");
            return;
        }
        if (message.has("positionMs") && readNumber(message.get("positionMs")) == null) {
            LOGGER.info("Companion: ignoring command with an invalid 'positionMs'");
            return;
        }

        String cmd = message.has("cmd") ? message.get("cmd").getAsString() : "";
        Double volume = readNumber(message.get("value"));
        Double positionMs = readNumber(message.get("positionMs"));

        Platform.runLater(() -> {
            switch (cmd) {
                case "toggle" -> host.togglePlayPause();
                case "pause" -> {
                    PlaybackEngine engine = playback.getEngine();
                    if (engine != null && engine.isPlaying()) {
                        host.togglePlayPause();
                    }
                }
                case "play" -> {
                    PlaybackEngine engine = playback.getEngine();
                    QueueManager queueManager = host.queueManager();
                    if (engine != null && engine.isPaused()) {
                        host.togglePlayPause();
                    } else if ((engine == null || !engine.isLoaded())
                        && queueManager != null && !queueManager.isEmpty()) {
                        host.togglePlayPause();
                    }
                }
                case "next" -> playback.playNextInQueue();
                case "volume" -> {
                    if (volume != null) {
                        host.setVolume(volume);
                    }
                }
                case "seek" -> {
                    PlaybackEngine engine = playback.getEngine();
                    if (engine != null && engine.isLoaded() && positionMs != null) {
                        engine.seek(Duration.millis(positionMs));
                    }
                }
                default -> LOGGER.info("Companion: unknown command '{}'", cmd);
            }
            publishState();
        });
    }

    private static boolean isString(JsonElement element) {
        return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isString();
    }

    private static Double readNumber(JsonElement element) {
        if (element == null || !element.isJsonPrimitive()) {
            return null;
        }
        JsonPrimitive primitive = element.getAsJsonPrimitive();
        if (!primitive.isNumber()) {
            return null;
        }
        double value = primitive.getAsDouble();
        return Double.isFinite(value) ? value : null;
    }
}
