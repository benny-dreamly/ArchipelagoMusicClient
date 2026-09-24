/*
 * SPDX-License-Identifier: MPL-2.0
 */
package app.player;

import javafx.scene.media.MediaPlayer;
import javafx.util.Duration;

import java.util.function.Consumer;

/**
 * Abstraction over whatever produces audio for the current song. Phase 2 will
 * add a phone-based implementation; for now only {@link LocalPlayback}
 * exists. The session/Archipelago logic in MusicAppDemo intentionally talks
 * to this interface, never to a concrete player.
 */
public interface PlaybackEngine {

    void loadAndPlay(Song song);

    void pause();

    void resume();

    void stopAndRelease();

    MediaPlayer.Status getStatus();

    boolean isLoaded();

    boolean isPlaying();

    boolean isPaused();

    Duration getCurrentTime();

    Duration getTotalDuration();

    void seek(Duration time);

    void setRate(double rate);

    void setVolume(double volume);

    void setOnReady(Runnable onReady);

    void setOnEnded(Runnable onEnded);

    void setOnError(Consumer<String> onError);

    void setOnCurrentTime(Consumer<Duration> onCurrentTime);
}