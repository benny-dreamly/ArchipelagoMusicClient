package app.util;

import app.MusicAppDemo;

public class StateManager {

    private final MusicAppDemo app;
    private final AlbumOrderManager albumOrderManager;

    public StateManager(MusicAppDemo app, AlbumOrderManager albumOrderManager) {
        this.app = app;
        this.albumOrderManager = albumOrderManager;
    }

    public void clearUnlocks() {
        app.getEnabledSets().clear();
        app.getUnlockedAlbums().clear();
        app.getUnlockedSongs().clear();
    }

    public void resetGameState() {
        app.getEnabledSets().clear();
        app.getUnlockedSongs().clear();
        albumOrderManager.clearAlbumOrderCache();  // optional, if album order changes per game
    }
}
