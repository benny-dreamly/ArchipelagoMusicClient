package app.archipelago;

import app.MusicAppDemo;
import app.player.Album;
import app.player.Song;
import io.github.archipelagomw.events.ArchipelagoEventListener;
import io.github.archipelagomw.events.ReceiveItemEvent;
import javafx.application.Platform;

import java.util.HashSet;
import java.util.Set;

public class ItemListener {

    private final MusicAppDemo app;


    private final Set<String> receivedVaultTracks = new HashSet<>();
    private final Set<String> receivedRerecordings = new HashSet<>();

    public ItemListener(MusicAppDemo app) {
        this.app = app;
    }

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
                    Album album = app.getAlbumByName(itemName);
                    Song song = app.getSongByTitle(itemName);

                    if (album != null && album.isFullAlbumUnlock()) {
                        // Full-album unlock: only if item name matches album
                        if (itemName.equals(album.getName())) {
                            for (Song s : album.getSongs()) {
                                app.getUnlockedSongs().add(s.getTitle());
                            }
                            app.getUnlockedAlbums().add(album.getName());
                        }
                        // Enable the album type so songs show
                        app.getEnabledSets().add(album.getType());
                    } else if (album != null) {
                        // Glass Animals–style album item received
                        app.getUnlockedAlbums().add(album.getName()); // <— ADD THIS
                        app.getEnabledSets().add(album.getType());
                    } else if (song != null) {
                        // Single-song unlock (Glass Animals style)
                        app.getUnlockedSongs().add(song.getTitle());

                        // Also mark the parent album as "unlocked" for play checks
                        Album parentAlbum = app.getAlbumForSong(song.getTitle());
                        if (parentAlbum != null) {
                            app.getUnlockedAlbums().add(parentAlbum.getName());
                            app.getEnabledSets().add(parentAlbum.getType());
                        }
                    }

                    break;
            }

            app.refreshTree();

            System.out.println("Received item: " + itemName + " from " + locationName);
        });
    }

        private void checkVaultAlbums(String albumName) {
            // Only unlock Vault album songs if you got the Vault Tracks item for it
            if (receivedVaultTracks.contains(albumName)) {
                app.unlockAlbum(albumName + " (Taylor's Version)"); // example naming
            }
        }

        private void checkRerecordedAlbums(String albumName) {
            // Only unlock the rerecorded album once you have all the required items
            if (receivedRerecordings.contains(albumName)) {
                app.unlockAlbum(albumName + " (Taylor's Version)");
            }
        }
}