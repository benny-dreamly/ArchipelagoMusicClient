/*
 * SPDX-License-Identifier: MPL-2.0
 */
package app;

import app.archipelago.EnergyLinkListener;
import app.logic.QueueManager;
import app.player.Album;
import app.player.Song;

import java.util.List;

/**
 * The application surface the extracted controllers talk to. {@code MusicAppDemo}
 * implements this once, reading its live fields, so a new controller can be
 * wired up without adding another set of per-controller callbacks.
 */
public interface AppContext {

    Album albumForSong(Song song);

    List<Album> albums();

    boolean libraryReady();

    boolean canPlay(Song song, Album album);

    boolean canQueue(Song song, Album album);

    String lockedSongMessage(Song song, Album album);

    QueueManager queueManager();

    void updateQueueDisplay();

    void publishRemoteState();

    void highlightCurrentSong(Album album, String songTitle);

    void onSongFinished(Song song, Album album);

    EnergyLinkListener energyLink();

    boolean connected();

    void appendOutput(String message);

    double volume();

    void setVolume(double percent);

    void togglePlayPause();

    boolean sessionRestoreEnabled();

    boolean offlineMode();

    String gameName();

    String slot();

    int loadGeneration();
}
