/*
 * SPDX-License-Identifier: MPL-2.0
 */
package app.player;

import app.archipelago.EnergyLinkListener;
import app.logic.QueueManager;
import app.player.ui.AlbumArtPanel;
import app.player.ui.PlayerPanel;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.scene.image.Image;
import javafx.scene.media.MediaPlayer;
import javafx.util.Duration;
import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.tag.images.Artwork;
import org.jaudiotagger.tag.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.LinkedList;
import java.util.Locale;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static app.util.Dialogs.showError;
import static app.util.TimeUtils.formatTime;

/**
 * Owns the current playback engine, the track that is playing, the play history
 * and the rate/boost state. Extracted from {@code MusicAppDemo}; the
 * application-specific effects (queue display, remote state, play recording)
 * are reached through the {@link Host} callbacks.
 */
public class PlaybackController {

    private static final Logger LOGGER = LoggerFactory.getLogger(PlaybackController.class);

    private static final double BOOST_RATE_150 = 1.5;
    private static final double BOOST_RATE_200 = 2.0;
    private static final double BOOST_COST_150 = 1_500;
    private static final double BOOST_COST_200 = 2_250;
    private static final Duration BOOST_DURATION = Duration.seconds(30);
    private static final double SONG_ENERGY_CREDIT = 500;
    private static final Duration CLICK_WINDOW = Duration.millis(300);
    private static final int HISTORY_LIMIT = 100;

    /** Callbacks the controller needs from the owning application. */
    public interface Host {
        Album albumForSong(Song song);

        boolean canPlay(Song song, Album album);

        String lockedSongMessage(Song song, Album album);

        QueueManager queueManager();

        void publishRemoteState();

        void updateQueueDisplay();

        void highlightCurrentSong(Album album, String songTitle);

        void onSongFinished(Song song, Album album);

        EnergyLinkListener energyLink();

        boolean clientConnected();

        void appendOutput(String message);
    }

    private final Host host;
    private final PlayerPanel playerPanel;
    private final AlbumArtPanel albumArtPanel;
    private final ExecutorService artworkExecutor = new ThreadPoolExecutor(
            1, 1, 0L, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(1),
            new ThreadPoolExecutor.DiscardOldestPolicy());

    private PlaybackEngine engine;
    private Song currentSong;
    private Duration pendingSeek;
    @SuppressWarnings("JdkObsolete")
    private final LinkedList<Song> songHistory = new LinkedList<>();
    private long artworkRequestId = 0;

    private boolean boostActive = false;
    private double boostRate = 1.0;
    private double playbackSpeed = 1.0;
    private PauseTransition boostTimer;
    private PauseTransition boostClickTimer;

    public PlaybackController(Host host, PlayerPanel playerPanel, AlbumArtPanel albumArtPanel) {
        this.host = host;
        this.playerPanel = playerPanel;
        this.albumArtPanel = albumArtPanel;
    }

    public PlaybackEngine getEngine() {
        return engine;
    }

    public void setEngine(PlaybackEngine engine) {
        this.engine = engine;
    }

    public Song getCurrentSong() {
        return currentSong;
    }

    public boolean isPlaying() {
        return engine != null && engine.getStatus() == MediaPlayer.Status.PLAYING;
    }

    public void setPendingSeek(Duration pendingSeek) {
        this.pendingSeek = pendingSeek;
    }

    /** Stop and release the engine and forget the current track and history. */
    public void resetTrack() {
        if (engine != null) {
            engine.stopAndRelease();
        }
        currentSong = null;
        songHistory.clear();
    }

    public void shutdown() {
        artworkExecutor.shutdownNow();
    }

    public void playSong(Song song) {
        if (song == null) return;

        // One-shot seek requested by a session restore; consume it so a
        // failed start can't leak it into a later song.
        Duration restoreSeek = pendingSeek;
        pendingSeek = null;

        Album album = host.albumForSong(song);
        boolean canPlay = host.canPlay(song, album);

        if (!canPlay) {
            String msg = host.lockedSongMessage(song, album);
            LOGGER.info("Cannot play song. {}", msg);
            showError("Locked Song", "Cannot play song", msg);
            return;
        }

        this.currentSong = song;

        host.queueManager().recordSnapshot(song);

        songHistory.remove(song);
        songHistory.addLast(song);
        while (songHistory.size() > HISTORY_LIMIT) {
            songHistory.removeFirst();
        }

        if (song.getFilePath() == null || !new File(song.getFilePath()).exists()) {
            LOGGER.info("Song trying to be played ({})'s file path ({}) does not exist or is null.",
                song.getTitle(), song.getFilePath());
            showError("File Not Found", "Cannot play song", "File not found for: " + song.getTitle());
            playNextInQueue();
            return;
        }

        if (engine == null) {
            engine = new LocalPlayback();
        }
        playerPanel.resetProgress();

        // Extract album art in background
        long requestId = ++artworkRequestId;
        albumArtPanel.clearArtwork();
        String filePath = song.getFilePath();
        String trackInfo = song.getTitle();
        artworkExecutor.execute(() -> {
            try {
                AudioFile audioFile = AudioFileIO.read(new File(filePath));
                Tag tag = audioFile.getTag();
                if (tag != null) {
                    Artwork artwork = tag.getFirstArtwork();
                    if (artwork != null) {
                        byte[] data = artwork.getBinaryData();
                        Image image = new Image(new ByteArrayInputStream(data), 180, 180, true, true);
                        Platform.runLater(() -> {
                            if (requestId == artworkRequestId) {
                                albumArtPanel.setArtwork(image, trackInfo);
                            }
                        });
                        return;
                    }
                }
            } catch (Exception e) {
                LOGGER.debug("Could not extract album art for {}: {}", trackInfo, e.getMessage());
            }
            Platform.runLater(() -> {
                if (requestId == artworkRequestId) {
                    albumArtPanel.clearArtwork();
                }
            });
        });

        configureEngine(engine, song, album, restoreSeek);

        engine.loadAndPlay(song);
        playerPanel.setCurrentSongLabel("Currently Playing: " + song.getTitle());
        host.updateQueueDisplay();
        host.highlightCurrentSong(album, song.getTitle());
        host.publishRemoteState();
    }

    public void configureEngine(PlaybackEngine target, Song song, Album album, Duration restoreSeek) {
        target.setVolume(playerPanel.getVolumeSlider().getValue() / 100.0);
        target.setRate(currentRate());

        target.setOnCurrentTime(newTime -> {
            if (!playerPanel.getProgressSlider().isValueChanging()) {
                Duration total = target.getTotalDuration();
                if (total != null && total.greaterThan(Duration.ZERO)) {
                    playerPanel.getProgressSlider().setValue(newTime.toMillis() / total.toMillis());
                    playerPanel.getElapsedLabel().setText(formatTime(newTime));
                }
            }
        });

        // Set duration label once media is ready
        target.setOnReady(() -> {
            applyPlaybackRate(target);
            Duration total = target.getTotalDuration();
            if (total != null) {
                playerPanel.getDurationLabel().setText(formatTime(total));
                if (restoreSeek != null
                        && restoreSeek.greaterThan(Duration.ZERO)
                        && restoreSeek.lessThan(total)) {
                    target.seek(restoreSeek);
                }
            }
            host.publishRemoteState();
        });

        target.setOnEnded(() -> {
            // Use the album carried through this playback rather than looking the
            // song up by title again, so duplicate titles stay with the album that
            // was actually played.
            host.onSongFinished(song, album);
            if (host.queueManager().getRepeatMode() == QueueManager.RepeatMode.SONG) {
                playSong(song);
            } else {
                playNextInQueue();
            }
        });

        target.setOnError(errorMessage -> {
            LOGGER.error("Error playing '{}': {}", song.getTitle(), errorMessage);
            showError("Playback Error", "Cannot play song", "Error playing " + song.getTitle() + ": " + errorMessage);
            playNextInQueue();
        });
    }

    public void playNextInQueue() {
        QueueManager queueManager = host.queueManager();
        if (queueManager == null) return;
        Song next = queueManager.nextSong(currentSong);
        host.updateQueueDisplay();
        if (next != null) {
            playSong(next);
        } else {
            playerPanel.setCurrentSongLabel("Currently Playing: None");
            if (engine != null) {
                engine.stopAndRelease();
            }
            long id = ++artworkRequestId;
            Platform.runLater(() -> {
                if (id == artworkRequestId) {
                    albumArtPanel.clearArtwork();
                }
            });
            playerPanel.resetProgress();
        }
    }

    public void playPreviousTrack() {
        QueueManager queueManager = host.queueManager();
        if (queueManager == null) return;

        // If more than a few seconds in, restart the current track instead
        if (engine != null && currentSong != null) {
            Duration current = engine.getCurrentTime();
            if (current != null && current.greaterThan(Duration.seconds(3))) {
                engine.seek(Duration.ZERO);
                return;
            }
        }

        if (songHistory.size() > 1) {
            songHistory.removeLast();
            Song prev = songHistory.removeLast();
            queueManager.addFirst(currentSong);
            playSong(prev);
        }
    }

    public void stopCurrentSong() {
        if (engine != null) {
            engine.stopAndRelease();
        }
        long id = ++artworkRequestId;
        Platform.runLater(() -> {
            if (id == artworkRequestId) {
                albumArtPanel.clearArtwork();
            }
        });
        playerPanel.resetProgress();
    }

    /** The rate the engine should currently run at (boost wins over the chosen speed). */
    public double currentRate() {
        return boostActive ? boostRate : playbackSpeed;
    }

    public void onBoostClick() {
        if (boostClickTimer != null) {
            // Second click within the window → double-click = 2x boost
            boostClickTimer.stop();
            boostClickTimer = null;
            startBoost(BOOST_RATE_200, BOOST_COST_200);
            return;
        }
        boostClickTimer = new PauseTransition(CLICK_WINDOW);
        boostClickTimer.setOnFinished(_ -> {
            boostClickTimer = null;
            // No second click followed within the window → single click = 1.5x boost
            startBoost(BOOST_RATE_150, BOOST_COST_150);
        });
        boostClickTimer.play();
    }

    private void startBoost(double rate, double cost) {
        EnergyLinkListener link = host.energyLink();
        if (link != null && host.clientConnected()) {
            Double balance = link.getBalance();
            if (balance == null) {
                LOGGER.info("Boost rejected: EnergyLink is not ready yet");
                host.appendOutput("EnergyLink is not ready for boost yet");
                return;
            }
            if (balance < cost) {
                LOGGER.info("Boost rejected: need {} but only {} available",
                        EnergyLinkListener.formatEnergy(cost), EnergyLinkListener.formatEnergy(balance));
                host.appendOutput(
                        String.format("Not enough energy (%s needed, %s available)",
                                EnergyLinkListener.formatEnergy(cost), EnergyLinkListener.formatEnergy(balance)));
                return;
            }
            if (!link.withdraw(cost)) {
                host.appendOutput(
                        String.format("Not enough energy to boost (%s available)",
                                EnergyLinkListener.formatEnergy(balance)));
                return;
            }
        }
        applyBoost(rate);
    }

    private void applyBoost(double rate) {
        boostRate = rate;
        boostActive = true;
        if (engine != null) {
            engine.setRate(rate);
        }
        if (boostTimer != null) {
            boostTimer.stop();
        }
        boostTimer = new PauseTransition(BOOST_DURATION);
        boostTimer.setOnFinished(_ -> endBoost());
        boostTimer.play();
        LOGGER.info("Playback boost engaged at {}x for {}s", rate, (int) BOOST_DURATION.toSeconds());
    }

    public String setPlaybackSpeedCommand(String arg) {
        String trimmed = arg == null ? "" : arg.trim();
        double rate;
        if (trimmed.isEmpty()) {
            rate = playbackSpeed == 1.0 ? 3.0 : 1.0;
        } else {
            try {
                rate = Double.parseDouble(trimmed);
            } catch (NumberFormatException e) {
                return "Usage: /speed [rate]";
            }
        }
        if (rate < 0.1 || rate > 4.0) {
            return "Speed must be between 0.1 and 4.0.";
        }
        playbackSpeed = rate;
        if (!boostActive) {
            applyPlaybackRate(engine);
        }
        return playbackSpeed == 1.0
                ? "Playback speed reset."
                : String.format(Locale.ROOT, "Playback speed set to %.2fx", playbackSpeed);
    }

    public void applyPlaybackRate(PlaybackEngine target) {
        if (target == null) {
            return;
        }
        double rate = boostActive ? boostRate : playbackSpeed;
        // macOS resets the native rate to 1.0 when playback (re)starts (JDK-8236146),
        // so force the property to change to make the target rate stick.
        target.setRate(1.0);
        target.setRate(rate);
        LOGGER.info("Applied playback rate {}", rate);
    }

    /** Credit the stored energy for a finished song. */
    public void creditSongEnergy() {
        EnergyLinkListener link = host.energyLink();
        if (link != null && host.clientConnected()) {
            link.deposit(SONG_ENERGY_CREDIT);
        }
    }

    /** Stop a running boost and cancel any pending boost click. */
    public void reset() {
        endBoost();
        if (boostClickTimer != null) {
            boostClickTimer.stop();
            boostClickTimer = null;
        }
    }

    private void endBoost() {
        boostActive = false;
        boostRate = 1.0;
        if (boostTimer != null) {
            boostTimer.stop();
            boostTimer = null;
        }
        applyPlaybackRate(engine);
        LOGGER.info("Playback boost ended");
    }
}
