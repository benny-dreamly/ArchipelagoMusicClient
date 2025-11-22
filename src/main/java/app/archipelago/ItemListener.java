package app.archipelago;

import app.MusicAppDemo;
import app.player.Album;
import app.player.Song;
import io.github.archipelagomw.events.ArchipelagoEventListener;
import io.github.archipelagomw.events.ReceiveItemEvent;
import javafx.application.Platform;

import java.util.HashSet;
import java.util.Set;

import static app.MusicAppDemo.logger;

public class ItemListener {

    private final MusicAppDemo app;


    private final Set<String> receivedVaultTracks = new HashSet<>();
    private final Set<String> receivedRerecordings = new HashSet<>();

    public ItemListener(MusicAppDemo app) {
        this.app = app;
    }

    @SuppressWarnings("unused")
    @ArchipelagoEventListener
    public void onReceiveItem(ReceiveItemEvent event) {
        String itemName = event.getItemName();
        String locationName = event.getLocationName();

        Platform.runLater(() -> {
            switch (itemName) {
                case "Vault Tracks":
                    app.getEnabledSets().add("vault");
                    // Optionally unlock the vault songs if you want immediate access
                    break;
                case "Re-recordings":
                    app.getEnabledSets().add("rerecording");
                    // Unlock the rerecorded albums
                    break;
                default:
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
                            for (Song s : album.getSongs()) {
                                app.getUnlockedSongs().add(s.getTitle());
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
                        // Single-song unlock (Glass Animals style)
                        app.getUnlockedSongs().add(song.getTitle());

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

                    break;
            }

            app.refreshTree();

            logger.info("Received item: {} from {}", itemName, locationName);
        });
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