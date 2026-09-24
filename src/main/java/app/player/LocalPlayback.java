/*
 * SPDX-License-Identifier: MPL-2.0
 */
package app.player;

import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.util.Duration;

import java.nio.file.Paths;
import java.util.function.Consumer;

/**
 * Plays audio locally on the desktop using a JavaFX {@link MediaPlayer}.
 * Owns the whole player lifecycle so MusicAppDemo no longer touches
 * MediaPlayer state directly.
 */
public final class LocalPlayback implements PlaybackEngine {

    private MediaPlayer player;
    private Runnable onReady;
    private Runnable onEnded;
    private Consumer<String> onError;
    private Consumer<Duration> onCurrentTime;

    @Override
    public void loadAndPlay(Song song) {
        release();
        if (song == null || song.getFilePath() == null) {
            return;
        }
        Media media = new Media(Paths.get(song.getFilePath()).toUri().toString());
        player = new MediaPlayer(media);
        player.currentTimeProperty().addListener((_, _, newTime) -> {
            if (onCurrentTime != null) {
                onCurrentTime.accept(newTime);
            }
        });
        player.setOnReady(() -> {
            if (onReady != null) {
                onReady.run();
            }
        });
        player.setOnEndOfMedia(() -> {
            if (onEnded != null) {
                onEnded.run();
            }
        });
        player.setOnError(() -> {
            if (onError != null) {
                String message = player.getError() != null
                        ? player.getError().getMessage() : "Unknown error";
                onError.accept(message);
            }
        });
        player.play();
    }

    @Override
    public void pause() {
        if (player != null) {
            player.pause();
        }
    }

    @Override
    public void resume() {
        if (player != null) {
            player.play();
        }
    }

    @Override
    public void stopAndRelease() {
        release();
    }

    private void release() {
        if (player != null) {
            player.stop();
            player.dispose();
            player = null;
        }
    }

    @Override
    public MediaPlayer.Status getStatus() {
        return player == null ? MediaPlayer.Status.UNKNOWN : player.getStatus();
    }

    @Override
    public boolean isLoaded() {
        return player != null;
    }

    @Override
    public boolean isPlaying() {
        return getStatus() == MediaPlayer.Status.PLAYING;
    }

    @Override
    public boolean isPaused() {
        return getStatus() == MediaPlayer.Status.PAUSED;
    }

    @Override
    public Duration getCurrentTime() {
        return player == null ? Duration.ZERO : player.getCurrentTime();
    }

    @Override
    public Duration getTotalDuration() {
        return player == null ? null : player.getTotalDuration();
    }

    @Override
    public void seek(Duration time) {
        if (player != null) {
            player.seek(time);
        }
    }

    @Override
    public void setRate(double rate) {
        if (player != null) {
            player.setRate(rate);
        }
    }

    @Override
    public void setVolume(double volume) {
        if (player != null) {
            player.setVolume(volume);
        }
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
}