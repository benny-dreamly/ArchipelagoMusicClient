/*
 * SPDX-License-Identifier: MPL-2.0
 */
package app.util;

import app.player.Album;
import app.player.Song;

import java.util.List;

public class AlbumLibrary {

    private final List<Album> albums;

    public AlbumLibrary(List<Album> albums) {
        this.albums = albums;
    }

    public List<Album> getAlbums() {
        return albums;
    }

    public Album getAlbumByName(String name) {
        for (Album album : albums) {
            if (album.getName().equals(name)) return album;
        }
        return null;
    }

    public Album getAlbumForSong(String songTitle) {
        for (Album album : albums) {
            for (Song song : album.getSongs()) {
                if (song.getTitle().equals(songTitle)) return album;
            }
        }
        return null;
    }

    /**
     * Resolves the album that actually contains the given song instance. Unlike
     * {@link #getAlbumForSong(String)} this disambiguates songs that share a
     * title across albums, so a queued song stays with the album it came from.
     * Falls back to the title lookup for songs that aren't part of the library.
     */
    public Album getAlbumForSong(Song song) {
        if (song == null) return null;
        for (Album album : albums) {
            for (Song candidate : album.getSongs()) {
                if (candidate == song) return album;
            }
        }
        return getAlbumForSong(song.getTitle());
    }

    public Song getSongByTitle(String songTitle) {
        for (Album album : albums) {
            for (Song song : album.getSongs()) {
                if (song.getTitle().equals(songTitle)) return song;
            }
        }
        return null;
    }
}
