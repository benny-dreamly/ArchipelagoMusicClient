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
}
