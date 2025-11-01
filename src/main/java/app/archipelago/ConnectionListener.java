package app.archipelago;

import app.MusicAppDemo;
import com.google.gson.JsonElement;
import io.github.archipelagomw.events.ConnectionResultEvent;
import io.github.archipelagomw.events.ArchipelagoEventListener;
import io.github.archipelagomw.network.ConnectionResult;
import javafx.application.Platform;
import javafx.scene.control.Label;


@SuppressWarnings("ClassCanBeRecord")
public class ConnectionListener {

    private final Label statusLabel;

    private final APClient client;
    private final MusicAppDemo app;

    public ConnectionListener(Label statusLabel, APClient client, MusicAppDemo app) {
        this.statusLabel = statusLabel;
        this.client = client;
        this.app = app;
    }

    @SuppressWarnings("unused")
    @ArchipelagoEventListener
    public void onConnectionResult(ConnectionResultEvent event) {
        Platform.runLater(() -> {
            ConnectionResult result = event.getResult();

            if (result == io.github.archipelagomw.network.ConnectionResult.Success) {
                JsonElement slotData = event.getSlotData(JsonElement.class);
                client.setSlotData(slotData);
                statusLabel.setText("Connected!");
            } else {
                // Prevent duplicate error alerts if a socket error already happened
                if (statusLabel.getText().equals("Connection failed")) return;

                statusLabel.setText("Not connected");
                app.showError(
                        "Connection Failed",
                        "Failed to connect to Archipelago server",
                        "Reason: " + result.name()
                );

                app.setConnectButtonText("Connect");
                app.setGameFieldDisabled(false);
            }
        });
    }
}