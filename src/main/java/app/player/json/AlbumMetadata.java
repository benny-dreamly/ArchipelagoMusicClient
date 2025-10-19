package app.player.json;

public class AlbumMetadata {
    private boolean fullAlbumUnlock;

    public AlbumMetadata() {
        this.fullAlbumUnlock = false; // default
    }

    public AlbumMetadata(boolean fullAlbumUnlock) {
        this.fullAlbumUnlock = fullAlbumUnlock;
    }

    public boolean isFullAlbumUnlock() {
        return fullAlbumUnlock;
    }

    public void setFullAlbumUnlock(boolean fullAlbumUnlock) {
        this.fullAlbumUnlock = fullAlbumUnlock;
    }
}