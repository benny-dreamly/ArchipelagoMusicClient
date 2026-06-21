package app.player.ui;

import app.archipelago.APClient;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.control.TextArea;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.function.Supplier;

public class TextClientWindow {

    private final Supplier<APClient> clientSupplier;

    private final TextArea outputArea = new TextArea();

    public TextClientWindow(Supplier<APClient> clientSupplier) {
        this.clientSupplier = clientSupplier;
    }

    public void show() {
        Stage textStage = new Stage();
        textStage.setTitle("Text Client");

        VBox root = new VBox(10);
        root.setPadding(new Insets(10));

        // Make root VBox grow with the stage
        VBox.setVgrow(root, Priority.ALWAYS);

        outputArea.setEditable(false); // for displaying messages
        VBox.setVgrow(outputArea, Priority.ALWAYS); // <-- This makes it expand vertically

        TextField inputField = new TextField();
        inputField.setPromptText("Type command here");


        // Define the sending logic as a Runnable
        Runnable sendMessage = () -> {
            String msg = inputField.getText();
            APClient client = clientSupplier.get();
            if (!msg.isEmpty()) {
                // handle the text input here, e.g., send to server
                client.sendChat(msg);
                // SayPacket sayPacket = new SayPacket(msg);
                // APResult<Void> result = client.sendPackets(Collections.singletonList(sayPacket));
                // outputArea.appendText("You: " + msg + "\n");
                inputField.clear();
            }
        };

        Button sendBtn = new Button("Send");
        sendBtn.setOnAction(_ -> sendMessage.run());

        // Press Enter to send
        inputField.setOnAction(_ -> sendMessage.run());

        root.getChildren().addAll(outputArea, inputField, sendBtn);

        Scene scene = new Scene(root, 400, 300);
        textStage.setScene(scene);
        textStage.show();
    }

    public void appendOutput(String text) {
        outputArea.appendText(text);
    }

    public TextArea getOutputArea() {
        return outputArea;
    }
}
