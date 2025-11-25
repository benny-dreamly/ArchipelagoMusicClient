package app.player;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;

public class Album {

    private final String name;
    private final List<Song> songs;

    private final String type;

    private String folderPath; // path to album folder

    private Map<String,String> filenameOverrides = new HashMap<>();

    private boolean fullAlbumUnlock = false; // <-- non Taylor Swift style flag

    public Album(String name, String type) {
        this.name = name;
        this.type = type;
        this.songs = new ArrayList<>();
    }

    public Album(String name, String type, boolean fullUnlock) {
        this(name, type);
        this.fullAlbumUnlock = fullUnlock;
    }

    public void addSong(Song song) {
        songs.add(song);
    }

    public String getName() {
        return name;
    }

    public List<Song> getSongs() {
        return songs;
    }

    public String getType() {
        return type;
    }

    @SuppressWarnings("unused")
    public Song getSong(String title) {
        for (Song song : songs) {
            if (song.getTitle().equalsIgnoreCase(title)) {
                return song; // Found it
            }
        }
        return null; // Not found
    }

    public String getFolderPath() { return folderPath; }
    public void setFolderPath(String folderPath) { this.folderPath = folderPath; }

    @SuppressWarnings("unused")
    public Map<String,String> getFilenameOverrides() { return filenameOverrides; }
    @SuppressWarnings("unused")
    public void setFilenameOverrides(Map<String,String> overrides) { this.filenameOverrides = overrides; }

    // Flag for whether getting the album unlocks all songs
    public boolean isFullAlbumUnlock() { return fullAlbumUnlock; }
    @SuppressWarnings("unused")
    public void setFullAlbumUnlock(boolean fullAlbumUnlock) { this.fullAlbumUnlock = fullAlbumUnlock; }
}
