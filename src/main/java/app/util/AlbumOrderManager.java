package app.util;

import java.util.List;

public class AlbumOrderManager {

    private List<String> cache;

    public List<String> getAlbumOrderCache() {
        return cache;
    }

    public void clearAlbumOrderCache() {
        cache = null;
    }
}
