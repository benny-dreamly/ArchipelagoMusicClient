package app.archipelago;

import io.github.archipelagomw.events.ConnectionResultEvent;
import io.github.archipelagomw.events.ArchipelagoEventListener;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;

public class ConnectionListener {

    private final Label statusLabel;

    public ConnectionListener(Label statusLabel) {
        this.statusLabel = statusLabel;
    }

    @ArchipelagoEventListener
    public void onConnectionResult(ConnectionResultEvent event) {
        Platform.runLater(() -> {
            if (event.getResult() == io.github.archipelagomw.network.ConnectionResult.Success) {
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