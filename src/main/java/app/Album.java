package app;

import java.util.*;

public class Album {

    private final String name;
    private final List<Song> songs;

    private final String type;

    public Album(String name, String type) {
        this.name = name;
        this.type = type;
        this.songs = new ArrayList<>();
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

    public Song getSong(String title) {
        for (Song song : songs) {
            if (song.getTitle().equalsIgnoreCase(title)) {
                return song; // Found it
            }
        }
        return null; // Not found
    }
}
