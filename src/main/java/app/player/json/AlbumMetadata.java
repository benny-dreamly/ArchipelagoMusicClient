package app.player.json;

public class AlbumMetadata {
    private boolean fullAlbumUnlock;
    private String type;

    public AlbumMetadata() {
        this.fullAlbumUnlock = false; // default
        this.type = "album"; // default
    }

    public AlbumMetadata(boolean fullAlbumUnlock, String type) {
        this.fullAlbumUnlock = fullAlbumUnlock;
        this.type = type;
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

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
}