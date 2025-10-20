package app.archipelago;

import com.google.gson.JsonElement;
import io.github.archipelagomw.events.ConnectionResultEvent;
import io.github.archipelagomw.events.ArchipelagoEventListener;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;

import java.util.HashMap;

public class ConnectionListener {

    private final Label statusLabel;

    private final APClient client;

    public ConnectionListener(Label statusLabel, APClient client) {
        this.statusLabel = statusLabel;
        this.client = client;
    }

    @ArchipelagoEventListener
    public void onConnectionResult(ConnectionResultEvent event) {
        Platform.runLater(() -> {
            if (event.getResult() == io.github.archipelagomw.network.ConnectionResult.Success) {
                JsonElement slotData = event.getSlotData(JsonElement.class);
                client.setSlotData(slotData);
                statusLabel.setText("Connected!");
            } else {
                statusLabel.setText("Not connected");
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Connection Failed");
                alert.setHeaderText("Failed to connect to Archipelago server");
                alert.setContentText("Reason: " + event.getResult().name());
                alert.showAndWait();
            }
        });
    }
}