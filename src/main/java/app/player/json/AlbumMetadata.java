package app.player.json;

public class AlbumMetadata {
    private boolean fullAlbumUnlock;
    private String type;

    @SuppressWarnings("unused")
    public AlbumMetadata() {
        this.fullAlbumUnlock = false; // default
        this.type = "album"; // default
    }

    @SuppressWarnings("unused")
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

    @SuppressWarnings("unused")
    public void setFullAlbumUnlock(boolean fullAlbumUnlock) {
        this.fullAlbumUnlock = fullAlbumUnlock;
    }

    @SuppressWarnings("unused")
    public String getType() {
        return type;
    }

    @SuppressWarnings("unused")
    public void setType(String type) {
        this.type = type;
    }
}