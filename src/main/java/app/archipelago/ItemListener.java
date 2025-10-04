package app.archipelago;

import app.MusicAppDemo;
import io.github.archipelagomw.events.ArchipelagoEventListener;
import io.github.archipelagomw.events.ReceiveItemEvent;
import javafx.application.Platform;

public class ItemListener {

    private final MusicAppDemo app;

    public ItemListener(MusicAppDemo app) {
        this.app = app;
    }

    @ArchipelagoEventListener
    public void onReceiveItem(ReceiveItemEvent event) {
        String location = event.getLocationName(); // the song/location unlocked

        Platform.runLater(() -> {
            // Directly modify the main app's unlockedSongs set
            app.unlockSong(location); // you’d need to add this method to MusicAppDemo
        });
    }
}