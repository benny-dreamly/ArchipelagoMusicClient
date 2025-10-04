package app;

import app.archipelago.APClient;
import app.archipelago.ConnectionListener;
import app.archipelago.ItemListener;
import app.player.*;
import app.player.json.LibraryLoader;
import app.player.json.SongJSON;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.Media;
import javafx.stage.Stage;
import javafx.concurrent.Task;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.Paths;
import java.util.*;

public class MusicAppDemo extends Application {

    private final List<Album> albums = new ArrayList<>();
    private final Set<String> unlockedSongs = new HashSet<>();
    private final Set<String> enabledSets = new HashSet<>();

    private TreeView<String> treeView;

    private APClient client;
    private Button connectButton;
    private Label statusLabel;

    private Song currentSong;
    private Label currentSongLabel;

    private Queue<Song> playQueue = new LinkedList<>();
    private MediaPlayer currentPlayer;

    public static void main(String[] args) {
        launch();
    }

    @Override
    public void start(Stage stage) {
        treeView = new TreeView<>();
        currentSongLabel = new Label("No song");

        treeView.getSelectionModel().selectedItemProperty().addListener((obs, oldSel, newSel) -> {
            if (newSel == null) return;
            String songTitle = newSel.getValue();
            Song song = getSongByTitle(songTitle);
            if (song != null) {
                playQueue.add(song);
                currentSongLabel.setText("Queued: " + song.getTitle());
                // Auto-play if nothing is currently playing
                if (currentPlayer == null || currentPlayer.getStatus() != MediaPlayer.Status.PLAYING) {
                    playSong(Objects.requireNonNull(playQueue.poll()));
                }
            }
        });

        refreshTree();

        // Bottom controls HBox
        HBox bottomBar = new HBox(20);
        bottomBar.setPadding(new Insets(10));
        bottomBar.setAlignment(Pos.CENTER);

        // Left: Archipelago connection panel
        VBox connectionPanel = new VBox(5);
        TextField hostField = new TextField("localhost");
        TextField portField = new TextField("38281");
        TextField slotField = new TextField("Player1");
        TextField passwordField = new TextField();
        connectButton = new Button("Connect");
        statusLabel = new Label("Not connected");

        connectionPanel.getChildren().addAll(
                new Label("Host:"), hostField,
                new Label("Port:"), portField,
                new Label("Slot:"), slotField,
                new Label("Password:"), passwordField,
                connectButton,
                statusLabel
        );
        connectionPanel.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(connectionPanel, Priority.ALWAYS);

        // Right: Music player panel
        HBox playerControls = new HBox(5);
        Button playButton = new Button("▶");
        Button pauseButton = new Button("⏸");

        playButton.setOnAction(e -> {
            if (currentSong != null) {
                playSong(currentSong);
            }
        });

        pauseButton.setOnAction(e -> {
            if (currentPlayer != null) {
                MediaPlayer.Status status = currentPlayer.getStatus();
                if (status == MediaPlayer.Status.PLAYING) {
                    currentPlayer.pause();
                    currentSongLabel.setText("Paused: " + currentSong.getTitle());
                } else if (status == MediaPlayer.Status.PAUSED) {
                    currentPlayer.play();
                    currentSongLabel.setText("Playing: " + currentSong.getTitle());
                }
            }
        });

        playerControls.getChildren().addAll(playButton, pauseButton, currentSongLabel);
        playerControls.setAlignment(Pos.CENTER_RIGHT);
        HBox.setHgrow(playerControls, Priority.ALWAYS);

        // Add panels to bottom bar
        bottomBar.getChildren().addAll(connectionPanel, playerControls);

        VBox root = new VBox(10, treeView, bottomBar);
        stage.setScene(new Scene(root, 600, 600));
        stage.setTitle("MusicApp Demo");
        stage.show();

        Task<List<Album>> loadTask = getLoadTask();

        new Thread(loadTask).start();

        // Archipelago connection handler
        connectButton.setOnAction(e -> {
            if (client == null || !client.isConnected()) {
                String host = hostField.getText();
                int port = Integer.parseInt(portField.getText());
                String slot = slotField.getText();
                String password = passwordField.getText();

                client = new APClient(host, port, slot, password);

                client.setOnErrorCallback(ex -> {
                    statusLabel.setText("Connection failed");
                    Alert alert = new Alert(Alert.AlertType.ERROR);
                    alert.setTitle("Connection Failed");
                    alert.setHeaderText("Failed to connect to Archipelago server");
                    alert.setContentText("Reason: " + ex.getMessage());
                    alert.showAndWait();
                });

                try {
                    client.getEventManager().registerListener(new ConnectionListener(statusLabel));
                    client.getEventManager().registerListener(new ItemListener(this));
                    client.connect();
                    statusLabel.setText("Connected!");
                    connectButton.setText("Disconnect"); // toggle button text
                } catch (Exception ex) {
                    statusLabel.setText("Connection failed");
                    showError(ex.getMessage());
                    connectButton.setText("Connect");
                }
            } else {
                // DISCONNECT
                client.disconnect();
                statusLabel.setText("Disconnected");
                connectButton.setText("Connect"); // toggle button text
            }
        });
    }

    @Override
    public void stop() throws Exception {
        super.stop();
        if (client != null && client.isConnected()) {
            client.disconnect();
        }
        System.exit(0); // ensures all threads are killed
    }

    private Task<List<Album>> getLoadTask() {
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

            generateDefaultAlbumFolders(albums);

            Map<String, String> albumFolders = new HashMap<>();
            File configFile = getConfigFile();

            if (configFile.exists()) {
                try (Reader reader = new FileReader(configFile)) {
                    Type type = new TypeToken<Map<String, String>>(){}.getType();
                    albumFolders = new Gson().fromJson(reader, type);
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            } else {
                System.out.println("No config file found at " + configFile.getAbsolutePath() + ", skipping album folder assignment");
            }

            // assign folder paths to albums
            for (Album album : albums) {
                if (albumFolders.containsKey(album.getName())) {
                    album.setFolderPath(albumFolders.get(album.getName()));
                }
            }

            // After assigning folder paths in loadTask.setOnSucceeded
            assignFilesToSongs();

//            // Temporarily unlock all songs
//            for (Album album : albums) {
//                for (Song song : album.getSongs()) {
//                    unlockedSongs.add(song.getTitle());
//                }
//            }

//            // Enable all sets for now
//            enabledSets.add("standard");
//            enabledSets.add("rerecording");
//            enabledSets.add("vault"); // if you have vault songs

            refreshTree(); // populate TreeView after loading
        });

        loadTask.setOnFailed(e -> {
            loadTask.getException().printStackTrace();
        });
        return loadTask;
    }

    public void refreshTree() {
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

    private void showError(String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Connection Failed");
        alert.setHeaderText("Failed to connect to Archipelago server");
        alert.setContentText(content);
        alert.showAndWait();
    }

    public void unlockSong(String songTitle) {
        if (!unlockedSongs.contains(songTitle)) {
            unlockedSongs.add(songTitle);
            refreshTree();
        }
    }

    public void unlockAlbum(String albumName) {
        for (Album album : albums) {
            if (album.getName().equals(albumName)) {
                for (Song song : album.getSongs()) {
                    unlockSong(song.getTitle());
                }
                break;
            }
        }
    }

    public Set<String> getEnabledSets() {
        return enabledSets;
    }

    public Album getAlbumByName(String name) {
        for (Album album : albums) {
            if (album.getName().equals(name)) return album;
        }
        return null;
    }

    private Album getAlbumForSong(String songTitle) {
        for (Album album : albums) {
            for (Song song : album.getSongs()) {
                if (song.getTitle().equals(songTitle)) return album;
            }
        }
        return null;
    }

    private Song getSongByTitle(String songTitle) {
        for (Album album : albums) {
            for (Song song : album.getSongs()) {
                if (song.getTitle().equals(songTitle)) return song;
            }
        }
        return null;
    }

    private void playSong(Song song) {
        if (song == null) return;

        if (song.getFilePath() == null || !new File(song.getFilePath()).exists()) {
            showError("File not found for " + song.getTitle());
            playNextInQueue();
            return;
        }

        if (currentPlayer != null) {
            currentPlayer.stop();
        }

        Media media = new Media(Paths.get(song.getFilePath()).toUri().toString());
        currentPlayer = new MediaPlayer(media);

        currentPlayer.setOnEndOfMedia(() -> {
            if (client != null && client.isConnected()) {
                client.sendCheck(song.getTitle());
            }
            playNextInQueue();
        });

        currentPlayer.play();
        currentSongLabel.setText("Playing: " + song.getTitle());
        highlightCurrentSong(song.getTitle());
    }

    private void playNextInQueue() {
        Song next = playQueue.poll();
        if (next != null) {
            playSong(next);
        } else {
            currentSongLabel.setText("No song");
            currentPlayer = null;
        }
    }

    private void highlightCurrentSong(String songTitle) {
        TreeItem<String> root = treeView.getRoot();
        if (root == null) return;

        for (TreeItem<String> albumItem : root.getChildren()) {
            for (TreeItem<String> songItem : albumItem.getChildren()) {
                if (songItem.getValue().equals(songTitle)) {
                    treeView.getSelectionModel().select(songItem);
                    treeView.scrollTo(treeView.getSelectionModel().getSelectedIndex());
                    return;
                }
            }
        }
    }

    private File getConfigFile() {
        String userHome = System.getProperty("user.home");
        File configDir;

        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            configDir = new File(userHome, "AppData\\Roaming\\MusicAppDemo");
        } else if (os.contains("mac")) {
            configDir = new File(userHome, "Library/Application Support/MusicAppDemo");
        } else { // Linux / others
            configDir = new File(userHome, ".config/MusicAppDemo");
        }

        // Ensure the directory exists
        if (!configDir.exists()) {
            if (!configDir.mkdirs()) {
                System.err.println("Failed to create config directory: " + configDir.getAbsolutePath());
            }
        }

        return new File(configDir, "albumFolders.json");
    }

    private void generateDefaultAlbumFolders(List<Album> albums) {
        File configFile = getConfigFile();

        // If the file already exists, do nothing
        if (configFile.exists()) return;

        Map<String, String> defaultFolders = new LinkedHashMap<>();
        for (Album album : albums) {
            defaultFolders.put(album.getName(), ""); // empty string as placeholder
        }

        try {
            configFile.createNewFile(); // make sure the file exists
            Gson gson = new GsonBuilder()
                    .setPrettyPrinting()
                    .disableHtmlEscaping() // keeps ' as-is instead of \u0027
                    .create();
            try (Writer writer = new FileWriter(configFile)) {
                gson.toJson(defaultFolders, writer);
            }
            System.out.println("Generated default albumFolders.json at " + configFile.getAbsolutePath());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void assignFilesToSongs() {
        for (Album album : albums) {
            String folderPath = album.getFolderPath();
            if (folderPath == null) continue;

            File folder = new File(folderPath);
            if (!folder.exists() || !folder.isDirectory()) continue;

            // Load overrides if any
            Map<String, String> overrides = album.getFilenameOverrides(); // implement this in Album

            File[] files = folder.listFiles((dir, name) ->
                    name.toLowerCase().endsWith(".mp3") ||
                            name.toLowerCase().endsWith(".m4a") ||
                            name.toLowerCase().endsWith(".wav")
            );

            if (files == null) continue;

            for (File file : files) {
                String baseName = file.getName().replaceFirst("[.][^.]+$", "");

                // Strip track numbers like "01 - " or "1. "
                baseName = baseName.replaceFirst("^\\d+[-. ]+", "");

                Song song = null;

                // Check overrides first
                for (Map.Entry<String, String> entry : overrides.entrySet()) {
                    if (entry.getValue().equalsIgnoreCase(file.getName())) {
                        song = getSongByTitle(entry.getKey());
                        break;
                    }
                }

                // Fuzzy match if no override
                if (song == null) {
                    String normalizedBase = normalize(baseName);
                    for (Song s : album.getSongs()) {
                        if (normalize(s.getTitle()).contains(normalizedBase) ||
                                normalizedBase.contains(normalize(s.getTitle()))) {
                            song = s;
                            break;
                        }
                    }
                }

                if (song != null) {
                    song.setFilePath(file.getAbsolutePath());
                } else {
                    System.out.println("Could not match file to song: " + file.getName() + " in album " + album.getName());
                }
            }
        }
    }

    private String normalize(String s) {
        return s.toLowerCase().replaceAll("[^a-z0-9]", "");
    }
}