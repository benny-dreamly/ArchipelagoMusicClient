package app.archipelago;

import app.MusicAppDemo;
import app.player.Album;
import app.player.Song;
import io.github.archipelagomw.events.ArchipelagoEventListener;
import io.github.archipelagomw.events.ReceiveItemEvent;
import javafx.application.Platform;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

import static app.MusicAppDemo.LOGGER;

public class ItemListener {

    private final MusicAppDemo app;

    private final Set<String> receivedItems = new HashSet<>();
    private final Set<String> receivedVaultTracks = new HashSet<>();
    private final Set<String> receivedRerecordings = new HashSet<>();
    private final Set<String> receivedSongItems = new HashSet<>();
    private final Set<String> receivedAlbumItems = new HashSet<>();

    private final Deque<Runnable> bufferedEvents = new ArrayDeque<>();
    private final Object bufferLock = new Object();
    private volatile boolean libraryLoading = true;

    public ItemListener(MusicAppDemo app) {
        this.app = app;
    }

    public void setLibraryLoading(boolean loading) {
        this.libraryLoading = loading;
    }

    public void drainBuffer() {
        Deque<Runnable> snapshot;
        synchronized (bufferLock) {
            libraryLoading = false;
            snapshot = new ArrayDeque<>(bufferedEvents);
            bufferedEvents.clear();
        }
        int count = 0;
        for (Runnable event : snapshot) {
            event.run();
            count++;
        }
        if (count > 0) {
            LOGGER.info("Replayed {} buffered item events after library load", count);
        }
    }

    public void discardBuffer() {
        int count;
        synchronized (bufferLock) {
            count = bufferedEvents.size();
            bufferedEvents.clear();
            libraryLoading = false;
        }
        if (count > 0) {
            LOGGER.warn("Discarded {} buffered item events due to library load failure", count);
        }
    }

    @SuppressWarnings("unused")
    @ArchipelagoEventListener
    public void onReceiveItem(ReceiveItemEvent event) {
        String itemName = event.getItemName();
        String locationName = event.getLocationName();
        String playerName = event.getPlayerName();

        Runnable processEvent = () -> processItem(itemName, locationName, playerName);

        synchronized (bufferLock) {
            if (libraryLoading || app.getLibrary() == null) {
                bufferedEvents.addLast(processEvent);
                return;
            }
        }

        Platform.runLater(processEvent);
    }

    private void processItem(String itemName, String locationName, String playerName) {
        receivedItems.add(itemName);

        switch (itemName) {
            case "Vault Tracks" -> // Optionally unlock the vault songs if you want immediate access
                app.getEnabledSets().add("vault");
            case "Re-recordings" -> // Unlock the rerecorded albums
                app.getEnabledSets().add("rerecording");
            default -> {
                // Normalize for album lookup only
                String normalizedItemName = itemName;
                boolean isAlbumItem = false;
                if (itemName.endsWith("(Album)")) {
                    normalizedItemName = itemName.replace("(Album)", "").trim();
                    isAlbumItem = true;
                }

                Album album = app.getLibrary().getAlbumByName(normalizedItemName);
                Song song = app.getLibrary().getSongByTitle(normalizedItemName);

                // 1. Full-album unlocks (Taylor Swift style)
                if (album != null && album.isFullAlbumUnlock()) {
                    // Full-album unlock: only if item name matches album
                    if (normalizedItemName.equalsIgnoreCase(album.getName())) {
                        receivedAlbumItems.add(album.getName());
                        for (Song s : album.getSongs()) {
                            if (s.requiresMet(receivedItems)) {
                                app.getUnlockedSongs().add(s.getTitle());
                            }
                        }
                        app.getUnlockedAlbums().add(album.getName());
                    }
                    // Enable the album type so songs show
                    app.getEnabledSets().add(album.getType());
                }
                // 2. Non-full album item (Glass Animals style)
                else if (album != null && isAlbumItem) {
                    // Glass Animals–style album item received
                    app.getUnlockedAlbums().add(album.getName()); // <— ADD THIS
                    app.getEnabledSets().add(album.getType());
                }
                // 3. Song item (single-song unlock)
                else if (song != null) {
                    // Single-song unlock: only if requirements are met
                    receivedSongItems.add(song.getTitle());
                    if (song.requiresMet(receivedItems)) {
                        app.getUnlockedSongs().add(song.getTitle());
                    }

                    // Also mark the parent album as "unlocked" for play checks
                    Album parentAlbum = app.getLibrary().getAlbumForSong(song.getTitle());
                    if (parentAlbum != null) {
                        // app.getUnlockedAlbums().add(parentAlbum.getName());
                        app.getEnabledSets().add(parentAlbum.getType());
                    }
                } else if (album != null) {
                    // Catch-all for album items that aren't full-album or song items
                    app.getUnlockedAlbums().add(album.getName());
                    app.getEnabledSets().add(album.getType());
                }

            }
        }

        // After processing the item, check all songs for newly satisfied requirements
        unlockRequirementsSatisfied();

        app.refreshTree();

        LOGGER.info("Received item: {} from {}'s {}", itemName, playerName, locationName);
    }

    private void unlockRequirementsSatisfied() {
        for (Album album : app.getLibrary().getAlbums()) {
            boolean albumItemReceived = receivedAlbumItems.contains(album.getName());
            for (Song song : album.getSongs()) {
                boolean songItemReceived = receivedSongItems.contains(song.getTitle());
                if (!songItemReceived && !albumItemReceived) continue;
                if (!app.getUnlockedSongs().contains(song.getTitle()) && song.requiresMet(receivedItems)) {
                    app.getUnlockedSongs().add(song.getTitle());
                    app.getEnabledSets().add(song.getType());
                }
            }
        }
    }

        @SuppressWarnings("unused")
        private void checkVaultAlbums(String albumName) {
            // Only unlock Vault album songs if you got the Vault Tracks item for it
            if (receivedVaultTracks.contains(albumName)) {
                app.unlockAlbum(albumName + " (Taylor's Version)"); // example naming
            }
        }

        @SuppressWarnings("unused")
        private void checkRerecordedAlbums(String albumName) {
            // Only unlock the rerecorded album once you have all the required items
            if (receivedRerecordings.contains(albumName)) {
                app.unlockAlbum(albumName + " (Taylor's Version)");
            }
        }
}