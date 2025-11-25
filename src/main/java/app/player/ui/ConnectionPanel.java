package app.player.ui;

import app.archipelago.APClient;
import app.archipelago.SlotDataHelper;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static app.util.ConfigManager.loadConnectionSettings;
import static app.util.ConfigPaths.checkIfGameFolderExists;

public class ConnectionPanel extends VBox {
    private final TextField hostField;
    private final TextField portField;
    private final TextField slotField;
    private final TextField passwordField;
    private final TextField gameField;
    private final Button connectButton;
    private final Label statusLabel;
    private final Button showTextClientBtn;
    private final HBox connectButtonsBox;
    private TextClientWindow textClientWindow;

    private static final Logger logger = LoggerFactory.getLogger(ConnectionPanel.class);

    public ConnectionPanel(AtomicReference<File> gameFolder, Supplier<APClient> clientSupplier) {
        super(5);

        gameField = new TextField();
        gameField.setPromptText("Game / Manual name");

        String savedGameName = APClient.loadSavedGameNameStatic();

        gameField.setText(savedGameName);

        hostField = new TextField("localhost");
        portField = new TextField("38281");
        slotField = new TextField("Player1");
        passwordField = new TextField();

        Map<String, String> saved = loadConnectionSettings();

        hostField.setText(saved.getOrDefault("host", "localhost"));
        portField.setText(saved.getOrDefault("port", "38281"));
        slotField.setText(saved.getOrDefault("slot", "Player1"));
        passwordField.setText(saved.getOrDefault("password", ""));

        connectButton = new Button("Connect");
        statusLabel = new Label("Not connected");

        // Ensure the per-game folder exists
        gameFolder.set(APClient.getGameDataFolderStatic());
        checkIfGameFolderExists(gameFolder.get(), logger);

        // load slot_data.json to help with parsing the slot data, we already know the game
        SlotDataHelper.loadSlotOptions(gameFolder.get());

        showTextClientBtn = new Button("Show Text Client");
        textClientWindow = new TextClientWindow(clientSupplier);
        showTextClientBtn.setOnAction(_ -> textClientWindow.show());

        // Create a horizontal container for connect button and text client button
        connectButtonsBox = new HBox(10);
        connectButtonsBox.setAlignment(Pos.CENTER_LEFT);
        connectButtonsBox.getChildren().addAll(connectButton, showTextClientBtn);

        getChildren().addAll(
                new Label("Game:"), gameField,
                new Label("Host:"), hostField,
                new Label("Port:"), portField,
                new Label("Slot:"), slotField,
                new Label("Password:"), passwordField,
                connectButtonsBox,
                statusLabel
        );
        setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(this, Priority.ALWAYS);
    }

    public Button getConnectButton() {
        return connectButton;
    }

    public String getHost() {
        return hostField.getText();
    }
    
    public int getPort() {
        return Integer.parseInt(portField.getText());
    }

    public String getSlot() {
        return slotField.getText();
    }

    public String getPassword() {
        return passwordField.getText();
    }

    public String getGameName() {
        return gameField.getText().trim();
    }

    public Label getStatusLabel() {
        return statusLabel;
    }

    public void setStatus(String text) {
        statusLabel.setText(text);
    }

    public void disableGameField(boolean disabled) {
        gameField.setDisable(disabled);
    }

    public void setGameFieldTooltip(String text) {
        if (text == null) {
            gameField.setTooltip(null);
        } else {
            gameField.setTooltip(new Tooltip(text));
        }
    }

    public void setConnectButtonText(String text) {
        connectButton.setText(text);
    }

    public TextClientWindow getTextClientWindow() {
        return textClientWindow;
    }

}
