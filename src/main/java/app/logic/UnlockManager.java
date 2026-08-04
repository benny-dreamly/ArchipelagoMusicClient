package app.logic;

import app.archipelago.SlotDataHelper;
import app.player.Album;
import app.player.Song;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static app.util.SlotDataUtils.parseBooleanSlot;

public class UnlockManager {

    private final Set<String> unlockedAlbums = new HashSet<>();
    private final Set<String> unlockedSongs = new HashSet<>();
    private final Set<String> enabledSets = new HashSet<>();
    private final Set<String> enabledAlbums = new HashSet<>();

    private final Runnable onChange;

    public UnlockManager(Runnable onChange) {
        this.onChange = onChange;
    }

    public boolean isSongUnlocked(String songTitle) {
        return unlockedSongs.contains(songTitle);
    }

    public boolean isAlbumUnlocked(String albumName) {
        return unlockedAlbums.contains(albumName);
    }

    public boolean isTypeEnabled(String type) {
        return enabledSets.contains(type);
    }

    public Set<String> getUnlockedAlbums() {
        return unlockedAlbums;
    }

    public Set<String> getUnlockedSongs() {
        return unlockedSongs;
    }

    public Set<String> getEnabledSets() {
        return enabledSets;
    }

    public Set<String> getEnabledAlbums() {
        return enabledAlbums;
    }

    public boolean canPlay(Song song, Album album) {
        if (album == null) {
            // Song not in an album: just check if the song is unlocked
            return isSongUnlocked(song.getTitle());
        }
        // For songs in an album: the album itself must be unlocked, then either
        // full-album unlocked OR the song itself unlocked
        return isAlbumUnlocked(album.getName()) && (album.isFullAlbumUnlock() || isSongUnlocked(song.getTitle()));
    }

    public boolean canQueue(Song song, Album album) {
        return enabledSets.contains(song.getType())
                && isAlbumUnlocked(album.getName())
                && (album.isFullAlbumUnlock() || isSongUnlocked(song.getTitle()));
    }

    public void unlockSong(String songTitle) {
        if (!unlockedSongs.contains(songTitle)) {
            unlockedSongs.add(songTitle);
            if (onChange != null) onChange.run();
        }
    }

    public void unlockAlbum(String albumName, List<Album> albums) {
        for (Album album : albums) {
            if (album.getName().equals(albumName)) {
                // Enable this album's type so songs will show
                enabledSets.add(album.getType());

                for (Song song : album.getSongs()) {
                    unlockSong(song.getTitle());
                }
                break;
            }
        }
    }

    public void applySlotData(Map<String, Object> slotMap, List<Album> albums) {
        applyAlbumUnlocks(slotMap, albums);
        filterSongCategories(slotMap, albums);
        if (onChange != null) onChange.run();
    }

    private void applyAlbumUnlocks(Map<String, Object> slotMap, List<Album> albums) {
        // Get enabled albums dynamically from SlotDataHelper
        Set<String> enabledAlbumsFromSlotData = SlotDataHelper.getEnabledAlbums(slotMap);

        // Clear previously enabled sets and albums
        enabledSets.clear();
        enabledAlbums.clear();

        // Enable only the albums in slot data
        for (Album album : albums) {
            if (enabledAlbumsFromSlotData.contains(album.getName())) {
                enabledAlbums.add(album.getName());   // mark album as enabled
                enabledSets.add(album.getType());      // enable album type for tree filtering
            }
        }

        // 2. Unlock albums by type if the corresponding slot is enabled
        if (enabledAlbumsFromSlotData.contains("Re-recordings")) {
            for (Album album : albums) {
                if ("re-recording".equalsIgnoreCase(album.getType())) {
                    enabledAlbums.add(album.getName());
                    enabledSets.add(album.getType());
                }
            }
        }
    }

    private void filterSongCategories(Map<String, Object> slotMap, List<Album> albums) {
        boolean shortSongsEnabled = parseBooleanSlot(slotMap, "include_short_songs");
        boolean vaultSongsEnabled = parseBooleanSlot(slotMap, "include_vault_songs");

        // Now remove any songs that should not be visible
        for (Album album : albums) {
            for (Song s : new ArrayList<>(album.getSongs())) { // avoid ConcurrentModification
                String type = s.getType();

                // Skip short songs if disabled
                if (!shortSongsEnabled && "short".equalsIgnoreCase(type)) {
                    unlockedSongs.remove(s.getTitle());
                    continue;
                }

                // Skip vault tracks if disabled
                if (!vaultSongsEnabled && "vault".equalsIgnoreCase(type)) {
                    unlockedSongs.remove(s.getTitle());
                }
            }
        }
    }

    public void applyOfflineUnlocks(List<Album> albums) {
        enabledSets.clear();
        enabledAlbums.clear();
        unlockedAlbums.clear();
        unlockedSongs.clear();

        for (Album album : albums) {
            enabledAlbums.add(album.getName());
            enabledSets.add(album.getType());
            unlockedAlbums.add(album.getName());

            for (Song song : album.getSongs()) {
                unlockedSongs.add(song.getTitle());
            }
        }

        if (onChange != null) onChange.run();
    }
}
