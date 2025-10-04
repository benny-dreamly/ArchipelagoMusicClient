package app.archipelago;

import app.MusicAppDemo;
import app.player.Album;
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
                    if (album != null) {
                        // Unlock all songs in the album
                        app.unlockAlbum(album.getName());

                        // Enable the set for this album type
                        app.getEnabledSets().add(album.getType());
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