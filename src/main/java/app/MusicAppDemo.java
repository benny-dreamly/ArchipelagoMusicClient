package app;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.concurrent.Task;

import java.util.*;

public class MusicAppDemo extends Application {

    private final List<Album> albums = new ArrayList<>();
    private final Set<String> unlockedSongs = new HashSet<>();
    private final Set<String> enabledSets = new HashSet<>();

    public static void main(String[] args) {
        launch();
    }

    @Override
    public void start(Stage stage) {
        TreeView<String> treeView = new TreeView<>();
        refreshTree(treeView);

        // Bottom controls HBox
        HBox bottomBar = new HBox(20);
        bottomBar.setPadding(new Insets(10));
        bottomBar.setAlignment(Pos.CENTER);

        // Left: Archipelago connection panel
        VBox connectionPanel = new VBox(5);
        TextField hostField = new TextField("localhost");
        TextField portField = new TextField("38281");
        TextField slotField = new TextField("Player1");
        Button connectButton = new Button("Connect");
        Label statusLabel = new Label("Not connected");

        connectionPanel.getChildren().addAll(
                new Label("Host:"), hostField,
                new Label("Port:"), portField,
                new Label("Slot:"), slotField,
                connectButton,
                statusLabel
        );
        connectionPanel.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(connectionPanel, Priority.ALWAYS);

        // Right: Music player panel
        HBox playerControls = new HBox(5);
        Button playButton = new Button("▶");
        Button pauseButton = new Button("⏸");
        Label currentSongLabel = new Label("No song");

        playerControls.getChildren().addAll(playButton, pauseButton, currentSongLabel);
        playerControls.setAlignment(Pos.CENTER_RIGHT);
        HBox.setHgrow(playerControls, Priority.ALWAYS);

        // Add panels to bottom bar
        bottomBar.getChildren().addAll(connectionPanel, playerControls);

        VBox root = new VBox(10, treeView, bottomBar);
        stage.setScene(new Scene(root, 600, 600));
        stage.setTitle("MusicApp Demo");
        stage.show();

        Task<List<Album>> loadTask = getLoadTask(treeView);

        new Thread(loadTask).start();

//        // Archipelago connection handler
//        connectButton.setOnAction(e -> {
//            String host = hostField.getText();
//            int port = Integer.parseInt(portField.getText());
//            String slot = slotField.getText();
//
//            try {
//                // Example AP client code (replace with your library usage)
//                APClient client = new APClient(host, port, slot, (message) -> {
//                    Platform.runLater(() -> statusLabel.setText(message));
//                });
//                client.connect();
//                statusLabel.setText("Connected!");
//            } catch (Exception ex) {
//                statusLabel.setText("Connection failed");
//                ex.printStackTrace();
//            }
//        });
    }

    private Task<List<Album>> getLoadTask(TreeView<String> treeView) {
        Task<List<Album>> loadTask = new Task<>() {
            @Override
            protected List<Album> call() throws Exception {
                LibraryLoader loader = new LibraryLoader();
                List<SongJSON> rawSongs = loader.loadSongs("/locations.json");
                AlbumConverter converter = new AlbumConverter();
                return converter.convert(rawSongs);
            }
        };

        loadTask.setOnSucceeded(e -> {
            albums.addAll(loadTask.getValue());
            refreshTree(treeView); // populate TreeView after loading
        });

        loadTask.setOnFailed(e -> {
            loadTask.getException().printStackTrace();
        });
        return loadTask;
    }

    private void refreshTree(TreeView<String> treeView) {
        // Custom album order
        List<String> albumOrder = List.of(
                "Taylor Swift",
                "Fearless",
                "Fearless (Taylor's Version)",
                "Speak Now",
                "Speak Now (Taylor's Version)",
                "Red",
                "Red (Taylor's Version)",
                "1989",
                "1989 (Taylor's Version)",
                "Reputation",
                "Lover",
                "Folklore",
                "Evermore",
                "Midnights",
                "The Tortured Poets Department"
                // add more albums here if needed
        );

        // Sort albums according to albumOrder
        albums.sort(Comparator.comparingInt(a -> {
            int idx = albumOrder.indexOf(a.getName());
            return idx >= 0 ? idx : Integer.MAX_VALUE; // albums not in the list go last
        }));

        TreeItem<String> rootItem = new TreeItem<>("Albums");
        rootItem.setExpanded(true);

        for (Album album : albums) {
            TreeItem<String> albumItem = new TreeItem<>(album.getName());
            boolean hasSongs = false;

            for (Song song : album.getSongs()) {
                if (enabledSets.contains(song.getType())) {
                    TreeItem<String> songItem = new TreeItem<>(song.getTitle());
                    albumItem.getChildren().add(songItem);
                    hasSongs = true;
                }
            }

            if (hasSongs) rootItem.getChildren().add(albumItem);
        }

        treeView.setRoot(rootItem);
    }
}