package app;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.*;
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
        refreshTree(treeView); // initially empty

        // Buttons to simulate unlocking songs and toggling sets
        VBox root = getVBox(treeView);
        stage.setScene(new Scene(root, 400, 400));
        stage.setTitle("MusicApp Demo");
        stage.show();

        // Add sets
        enabledSets.add("standard");
        enabledSets.add("rerecording");

        // === Background loading of JSON ===
        Task<List<Album>> loadTask = getLoadTask(treeView);

        new Thread(loadTask).start();
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

    private VBox getVBox(TreeView<String> treeView) {
        Button unlockLoveStory = new Button("Unlock 'Love Story'");
        unlockLoveStory.setOnAction(e -> {
            unlockedSongs.add("Love Story");
            refreshTree(treeView);
        });

        Button toggleVault = new Button("Toggle Vault songs");
        toggleVault.setOnAction(e -> {
            if (enabledSets.contains("vault")) enabledSets.remove("vault");
            else enabledSets.add("vault");
            refreshTree(treeView);
        });

        return new VBox(10, treeView, unlockLoveStory, toggleVault);
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