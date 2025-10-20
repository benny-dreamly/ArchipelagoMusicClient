package app;

import app.archipelago.APClient;
import app.archipelago.ConnectionListener;
import app.archipelago.ItemListener;
import app.archipelago.SlotDataHelper;
import app.player.*;
import app.player.json.AlbumMetadata;
import app.player.json.AlbumMetadataLoader;
import app.player.json.LibraryLoader;
import app.player.json.SongJSON;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
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
    private final Set<String> unlockedAlbums = new HashSet<>();
    private final Set<String> unlockedSongs = new HashSet<>();
    private final Set<String> enabledSets = new HashSet<>();

    private List<String> albumOrderCache;

    private TreeView<String> treeView;

    private APClient client;
    private Button connectButton;
    private Label statusLabel;

    private Song currentSong;
    private Label currentSongLabel;

    // queue UI + data
    private final Queue<Song> playQueue = new LinkedList<>();
    private ListView<String> queueListView;   // visual queue (titles)
    private MediaPlayer currentPlayer;

    private boolean isUpdatingSelection = false;

    public static void main(String[] args) {
        launch();
    }

    @Override
    public void start(Stage stage) {
        treeView = new TreeView<>();
        currentSongLabel = new Label("Currently Playing: None");
        queueListView = new ListView<>();
        queueListView.setPrefHeight(120);

        ScrollPane queueScrollPane = new ScrollPane(queueListView);
        queueScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.ALWAYS);
        queueScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

        // Fit the ListView nicely inside the ScrollPane
        queueScrollPane.setFitToHeight(true);
        queueListView.setMinWidth(Region.USE_PREF_SIZE);

        // Map vertical scroll to horizontal scroll
        queueScrollPane.addEventFilter(ScrollEvent.SCROLL, event -> {
            if (event.getDeltaY() != 0) {
                double h = queueScrollPane.getHvalue() - event.getDeltaY() * 0.005; // smoother scaling
                queueScrollPane.setHvalue(Math.min(Math.max(h, 0), 1));
                event.consume();
            }
        });

        // When a tree item (song) is selected, add to queue
        treeView.getSelectionModel().selectedItemProperty().addListener((obs, oldSel, newSel) -> {
            if (newSel == null) return;

            String songTitle = newSel.getValue();
            Song song = getSongByTitle(songTitle);

            if (song == null) return; // nothing to do if song not found

            Album album = getAlbumForSong(songTitle);
            boolean songUnlocked = unlockedSongs.contains(song.getTitle());
            boolean albumUnlocked = album != null && enabledSets.contains(album.getType());

            // Check unlocking rules
            if (!songUnlocked || !albumUnlocked) {
                if (!songUnlocked) {
                    showError("Locked Song", "Cannot play song", song.getTitle() + " is not unlocked yet!");
                } else if (!albumUnlocked && album != null) {
                    showError("Locked Song", "Cannot queue song", song.getTitle() + " requires album " + album.getName() + " to be unlocked!");
                }
                return; // do not queue
            }

            // Add to queue, song has passed checks
            playQueue.add(song);
            updateQueueDisplay();

            // If nothing is playing, start immediately
            if (currentPlayer == null || currentPlayer.getStatus() != MediaPlayer.Status.PLAYING) {
                Song next = playQueue.poll();
                updateQueueDisplay();
                if (next != null) {
                    playSong(next);
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
        TextField gameField = new TextField();
        gameField.setPromptText("Game / Manual name");

        // Load saved game name
        String savedGameName = APClient.loadSavedGameNameStatic();
        gameField.setText(savedGameName);
        TextField hostField = new TextField("localhost");
        TextField portField = new TextField("38281");
        TextField slotField = new TextField("Player1");
        TextField passwordField = new TextField();
        Map<String, String> saved = loadConnectionSettings();
        hostField.setText(saved.getOrDefault("host", "localhost"));
        portField.setText(saved.getOrDefault("port", "38281"));
        slotField.setText(saved.getOrDefault("slot", "Player1"));
        passwordField.setText(saved.getOrDefault("password", ""));
        connectButton = new Button("Connect");
        statusLabel = new Label("Not connected");

        // Ensure the per-game folder exists
        File gameFolder = APClient.getGameDataFolderStatic();
        checkIfGameFolderExists(gameFolder);

        // load slot_data.json to help with parsing the slot data, we already know the game
        SlotDataHelper.loadSlotOptions(gameFolder);

        connectionPanel.getChildren().addAll(
                new Label("Game:"), gameField,
                new Label("Host:"), hostField,
                new Label("Port:"), portField,
                new Label("Slot:"), slotField,
                new Label("Password:"), passwordField,
                connectButton,
                statusLabel
        );
        connectionPanel.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(connectionPanel, Priority.ALWAYS);

        // Right: Music player panel (with queue ListView and queue controls)
        VBox queueBox = new VBox(6);
        queueBox.setAlignment(Pos.CENTER_RIGHT);

        HBox playerButtons = new HBox(6);
        Button playButton = new Button("▶");
        Button pauseButton = new Button("⏸");
        playerButtons.getChildren().addAll(playButton, pauseButton);

        // Queue control buttons
        HBox queueButtons = new HBox(6);
        Button removeSelectedBtn = new Button("Remove Selected");
        Button clearQueueBtn = new Button("Clear Queue");
        queueButtons.getChildren().addAll(removeSelectedBtn, clearQueueBtn);

        queueBox.getChildren().addAll(currentSongLabel, playerButtons, new Label("Queue:"), queueScrollPane, queueButtons);
        queueBox.setAlignment(Pos.CENTER_RIGHT);
        HBox.setHgrow(queueBox, Priority.ALWAYS);

        // Play button behaviour
        playButton.setOnAction(e -> {
            // If paused, resume. If nothing playing but queue has items, start next.
            if (currentPlayer != null && currentPlayer.getStatus() == MediaPlayer.Status.PAUSED) {
                currentPlayer.play();
                if (currentSong != null) currentSongLabel.setText("Currently Playing: " + currentSong.getTitle());
                return;
            }

            if (currentSong != null && (currentPlayer == null || currentPlayer.getStatus() != MediaPlayer.Status.PLAYING)) {
                // start current (if file exists)
                if (currentSong.getFilePath() != null) {
                    playSong(currentSong);
                } else {
                    showError("File Not Found", "Cannot play song", "File not found for: " + currentSong.getTitle());
                }
            } else if ((currentPlayer == null || currentPlayer.getStatus() != MediaPlayer.Status.PLAYING) && !playQueue.isEmpty()) {
                Song next = playQueue.poll();
                updateQueueDisplay();
                if (next != null) playSong(next);
            }
        });

        pauseButton.setOnAction(e -> {
            if (currentPlayer != null) {
                MediaPlayer.Status status = currentPlayer.getStatus();
                if (status == MediaPlayer.Status.PLAYING) {
                    currentPlayer.pause();
                    if (currentSong != null) currentSongLabel.setText("Paused: " + currentSong.getTitle());
                } else if (status == MediaPlayer.Status.PAUSED) {
                    currentPlayer.play();
                    if (currentSong != null) currentSongLabel.setText("Currently Playing: " + currentSong.getTitle());
                }
            }
        });

        // Remove selected from the queue (both ListView and underlying queue)
        removeSelectedBtn.setOnAction(e -> {
            String sel = queueListView.getSelectionModel().getSelectedItem();
            if (sel != null) {
                removeFromQueue(sel);
                updateQueueDisplay();
            }
        });

        // Clear queue
        clearQueueBtn.setOnAction(e -> {
            playQueue.clear();
            updateQueueDisplay();
        });

        // Add panels to bottom bar
        bottomBar.getChildren().addAll(connectionPanel, queueBox);

        VBox root = new VBox(10, treeView, bottomBar);
        stage.setScene(new Scene(root, 800, 600));
        stage.setTitle("MusicApp Demo");
        stage.show();

        Task<List<Album>> loadTask = getLoadTask();

        new Thread(loadTask).start();

        // Disable the game field if connected
        gameField.setDisable(client != null && client.isConnected());
        gameField.setTooltip(client != null && client.isConnected()
                ? new Tooltip("Cannot change game while connected")
                : null);

        // Archipelago connection handler
        connectButton.setOnAction(e -> {
            if (client == null || !client.isConnected()) {
                String host = hostField.getText();
                int port = Integer.parseInt(portField.getText());
                String slot = slotField.getText();
                String password = passwordField.getText();

                saveConnectionSettings(host, port, slot, password);

                String gameName = gameField.getText().trim();
                APClient.saveGameNameStatic(gameName);

                client = new APClient(host, port, slot, password);

                resetGameState();
                client.setGameName(gameName);

                ensureGameDefaults(APClient.getGameDataFolderStatic());

                client.setOnErrorCallback(ex -> {
                    statusLabel.setText("Connection failed");
                    Alert alert = new Alert(Alert.AlertType.ERROR);
                    alert.setTitle("Connection Failed");
                    alert.setHeaderText("Failed to connect to Archipelago server");
                    alert.setContentText("Reason: " + ex.getMessage());
                    alert.showAndWait();
                });

                try {
                    client.getEventManager().registerListener(new ConnectionListener(statusLabel, client));
                    client.getEventManager().registerListener(new ItemListener(this));
                    client.connect();
                    statusLabel.setText("Connected!");
                    connectButton.setText("Disconnect"); // toggle button text

                    // --- Disable the game field after connecting ---
                    gameField.setDisable(true);
                    gameField.setTooltip(new Tooltip("Cannot change game while connected"));

                    applySlotData();
                } catch (Exception ex) {
                    statusLabel.setText("Connection failed");
                    showError("Connection Failed", "Failed to connect to Archipelago server", ex.getMessage());
                    connectButton.setText("Connect");
                }
            } else {
                // DISCONNECT
                client.disconnect();
                statusLabel.setText("Disconnected");
                connectButton.setText("Connect"); // toggle button text

                // Re-enable game field
                gameField.setDisable(false);
                gameField.setTooltip(null);

                // stop playback
                stopCurrentSong();
                clearPlaybackState();

                // CLEAR ALL UNLOCKED / ENABLED DATA
                enabledSets.clear();
                unlockedAlbums.clear();
                unlockedSongs.clear();

                // Refresh tree so nothing shows
                refreshTree();
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
                File gameFolder = APClient.getGameDataFolderStatic();
                File localLocations = new File(gameFolder, "locations.json");
                List<SongJSON> rawSongs;

                if (localLocations.exists()) {
                    try (Reader reader = new FileReader(localLocations)) {
                        rawSongs = loader.loadSongsFromReader(reader); // new method
                    }
                } else {
                    rawSongs = loader.loadSongs("/locations.json"); // fallback to bundled
                }

                Map<String, AlbumMetadata> metadata = AlbumMetadataLoader.loadAlbumMetadata(gameFolder);
                AlbumConverter converter = new AlbumConverter(metadata);
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

            refreshTree(); // populate TreeView after loading
        });

        loadTask.setOnFailed(e -> {
            loadTask.getException().printStackTrace();
        });
        return loadTask;
    }

    public void refreshTree() {
        // Custom album order
        List<String> albumOrder = getAlbumOrder();

        // Sort albums according to albumOrder
        albums.sort(Comparator.comparingInt(a -> {
            int idx = albumOrder.indexOf(a.getName());
            return idx >= 0 ? idx : Integer.MAX_VALUE; // albums not in the list go last
        }));

        TreeItem<String> rootItem = new TreeItem<>("Albums");
        rootItem.setExpanded(true);

        for (Album album : albums) {
            // Skip albums not unlocked in slot data
            if (!unlockedAlbums.contains(album.getName())) continue;

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

    private void showError(String title, String header, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(header);
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
                // Enable this album’s type so songs will show
                enabledSets.add(album.getType());

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

    public Album getAlbumForSong(String songTitle) {
        for (Album album : albums) {
            for (Song song : album.getSongs()) {
                if (song.getTitle().equals(songTitle)) return album;
            }
        }
        return null;
    }

    public Song getSongByTitle(String songTitle) {
        for (Album album : albums) {
            for (Song song : album.getSongs()) {
                if (song.getTitle().equals(songTitle)) return song;
            }
        }
        return null;
    }

    private void playSong(Song song) {
        if (song == null) return;

        boolean canPlay;
        Album album = getAlbumForSong(song.getTitle());
        boolean albumUnlocked = album != null && unlockedAlbums.contains(album.getName());
        boolean songUnlocked = unlockedSongs.contains(song.getTitle());

        if (album != null) {
            // For songs in an album: must either be full-album unlocked OR both the song and album unlocked
            canPlay = album.isFullAlbumUnlock() || (songUnlocked && albumUnlocked);
        } else {
            // Song not in an album: just check if the song is unlocked
            canPlay = songUnlocked;
        }

        if (!canPlay) {
            String msg;
            if (album != null && !albumUnlocked) {
                msg = song.getTitle() + " requires album " + album.getName() + " to be unlocked!";
            } else {
                msg = song.getTitle() + " is not unlocked yet!";
            }
            showError("Locked Song", "Cannot play song", msg);
            return;
        }

        this.currentSong = song;

        if (song.getFilePath() == null || !new File(song.getFilePath()).exists()) {
            showError("File Not Found", "Cannot play song", "File not found for: " + song.getTitle());
            return;
        }

        if (currentPlayer != null) {
            currentPlayer.stop();
            currentPlayer.dispose(); // release OS resources
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
        currentSongLabel.setText("Currently Playing: " + song.getTitle());
        updateQueueDisplay();
        highlightCurrentSong(song.getTitle());
    }

    private void playNextInQueue() {
        Song next = playQueue.poll();
        updateQueueDisplay();
        if (next != null) {
            playSong(next);
        } else {
            currentSongLabel.setText("Currently Playing: None");
            currentPlayer = null;
        }
    }

    private void highlightCurrentSong(String songTitle) {
        if (isUpdatingSelection) return; // FIX 2: skip if already running

        TreeItem<String> root = treeView.getRoot();
        if (root == null) return;

        isUpdatingSelection = true; // guard on

        outerLoop:
        for (TreeItem<String> albumItem : root.getChildren()) {
            for (TreeItem<String> songItem : albumItem.getChildren()) {
                if (songItem.getValue().equals(songTitle)) {
                    treeView.getSelectionModel().select(songItem);

                    // Optional Improvement 1: only scroll if not already visible
                    int index = treeView.getSelectionModel().getSelectedIndex();
                    if (index < treeView.getFixedCellSize() || index >= treeView.getHeight() / treeView.getFixedCellSize()) {
                        treeView.scrollTo(index);
                    }

                    break outerLoop; // Optional Improvement 2: stop after first match
                }
            }
        }

        isUpdatingSelection = false; // guard off
    }

    // Queue helpers -----------------------------------------------------

    private void updateQueueDisplay() {
        queueListView.getItems().clear();
        for (Song s : playQueue) {
            queueListView.getItems().add(s.getTitle());
        }
        if (playQueue.isEmpty()) {
            queueListView.getItems().add("(empty)");
        }
    }

    private void removeFromQueue(String title) {
        // remove first occurrence in queue matching title
        Iterator<Song> it = playQueue.iterator();
        while (it.hasNext()) {
            Song s = it.next();
            if (s.getTitle().equals(title)) {
                it.remove();
                return;
            }
        }
    }

    private File getConfigFile() {
        File gameDir = APClient.getGameDataFolderStatic();
        if (!gameDir.exists()) gameDir.mkdirs();
        return new File(gameDir, "albumFolders.json");
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

            File albumDirectory = new File(folderPath);
            if (!albumDirectory.exists() || !albumDirectory.isDirectory()) continue;

            File[] files = albumDirectory.listFiles((dir, name) ->
                    name.toLowerCase().endsWith(".mp3") ||
                            name.toLowerCase().endsWith(".m4a") ||
                            name.toLowerCase().endsWith(".wav")
            );

            if (files == null) continue;

            for (File file : files) {
                String normalizedFile = normalizeFilename(file.getName());
                Song matchedSong = null;
                int bestDistance = Integer.MAX_VALUE;

                for (Song song : album.getSongs()) {
                    String normalizedSong = normalizeSongTitle(song.getTitle());

                    // exact or substring match first
                    if (normalizedFile.equalsIgnoreCase(normalizedSong) ||
                            normalizedFile.contains(normalizedSong)) {
                        matchedSong = song;
                        break;
                    }

                    // fallback fuzzy match if close enough
                    int dist = levenshteinDistance(normalizedFile.toLowerCase(), normalizedSong.toLowerCase());
                    if (dist < 5 && dist < bestDistance) { // tweak threshold if needed
                        matchedSong = song;
                        bestDistance = dist;
                    }
                }

                if (matchedSong != null) {
                    matchedSong.setFilePath(file.getAbsolutePath());
                    System.out.println("Matched: " + file.getName() + " -> " +
                            matchedSong.getTitle() + " | path: " + matchedSong.getFilePath());
                } else {
                    System.out.println("Could not match file to song: " +
                            file.getName() + " in album " + album.getName());
                }
            }
        }
    }

    // Levenshtein distance helper
    private int levenshteinDistance(String a, String b) {
        int[] costs = new int[b.length() + 1];
        for (int j = 0; j < costs.length; j++) costs[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            costs[0] = i;
            int nw = i - 1;
            for (int j = 1; j <= b.length(); j++) {
                int cj = Math.min(1 + Math.min(costs[j], costs[j - 1]),
                        a.charAt(i - 1) == b.charAt(j - 1) ? nw : nw + 1);
                nw = costs[j];
                costs[j] = cj;
            }
        }
        return costs[b.length()];
    }

    private String normalizeFilename(String filename) {
        // 1. Remove extension
        String base = filename.replaceFirst("[.][^.]+$", "");

        // 2. Fix truncated "Taylor's Ver" → "Taylor's Version"
        base = base.replaceAll("(?i)Taylor's Ver(\\b.*)?", "Taylor's Version");

        // 3. Remove leading track/CD numbers
        base = base.replaceFirst("(?i)^(cd\\d+ )?\\d+[-. _]+", "");

        // 4. Normalize “feat.” variations
        base = base.replaceAll("(?i)ft\\.?|feat\\.?","feat.");

        // 5. Clean underscores/spaces
        base = base.replaceAll("_", " ");
        base = base.replaceAll(" +", " ");

        // 6. Trim broken parenthesis at the end
        base = base.replaceAll("\\(\\s*$", "");

        return base.trim();
    }

    private String normalizeSongTitle(String title) {
        String normalized = title;

        // Replace any extra underscores or spaces
        normalized = normalized.replaceAll("_", " ");
        normalized = normalized.replaceAll(" +", " ");

        // Trim
        normalized = normalized.trim();

        return normalized;
    }

    private File getConnectionConfigFile() {
        File configDir = getConfigFile().getParentFile();
        return new File(configDir, "connection.json");
    }

    private void saveConnectionSettings(String host, int port, String slot, String password) {
        Map<String, String> data = new HashMap<>();
        data.put("host", host);
        data.put("port", String.valueOf(port));
        data.put("slot", slot);
        data.put("password", password);

        File file = getConnectionConfigFile();
        try (Writer writer = new FileWriter(file)) {
            new GsonBuilder().setPrettyPrinting().create().toJson(data, writer);
            System.out.println("Saved connection settings to " + file.getAbsolutePath());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private Map<String, String> loadConnectionSettings() {
        File file = getConnectionConfigFile();
        if (!file.exists()) return new HashMap<>();

        try (Reader reader = new FileReader(file)) {
            Type type = new TypeToken<Map<String, String>>(){}.getType();
            return new Gson().fromJson(reader, type);
        } catch (IOException e) {
            e.printStackTrace();
            return new HashMap<>();
        }
    }

    private List<String> getAlbumOrder() {
        if (albumOrderCache != null) {
            return albumOrderCache;
        }

        File gameDir = APClient.getGameDataFolderStatic();
        File orderFile = new File(gameDir, "albumOrder.json");

        List<String> loadedOrder = null;
        if (orderFile.exists()) {
            try (Reader reader = new FileReader(orderFile)) {
                Type listType = new TypeToken<List<String>>() {}.getType();
                loadedOrder = new Gson().fromJson(reader, listType);
                if (loadedOrder != null && !loadedOrder.isEmpty()) {
                    System.out.println("Loaded album order from " + orderFile.getAbsolutePath());
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        if (loadedOrder == null || loadedOrder.isEmpty()) {
            loadedOrder = List.of(
                    "Album 1",
                    "Album 2",
                    "Album 3",
                    "Album 4"
            );
//            loadedOrder = List.of(
//                    "Taylor Swift",
//                    "Fearless",
//                    "Fearless (Taylor's Version)",
//                    "Speak Now",
//                    "Speak Now (Taylor's Version)",
//                    "Red",
//                    "Red (Taylor's Version)",
//                    "1989",
//                    "1989 (Taylor's Version)",
//                    "Reputation",
//                    "Lover",
//                    "Folklore",
//                    "Evermore",
//                    "Midnights",
//                    "The Tortured Poets Department"
//            );

            try (Writer writer = new FileWriter(orderFile)) {
                new GsonBuilder().setPrettyPrinting().create().toJson(loadedOrder, writer);
                System.out.println("Generated default albumOrder.json at " + orderFile.getAbsolutePath());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        albumOrderCache = loadedOrder; // cache it
        return albumOrderCache;
    }

    private void ensureGameDefaults(File gameFolder) {
        if (!gameFolder.exists()) gameFolder.mkdirs();

        File albumOrderFile = new File(gameFolder, "albumOrder.json");
        if (!albumOrderFile.exists()) {
            getAlbumOrder(); // this method already generates the default if missing
        }

        File foldersFile = new File(gameFolder, "albumFolders.json");
        if (!foldersFile.exists()) {
            generateDefaultAlbumFolders(albums); // creates default
        }

        // Optionally copy default locations.json
        File localLocations = new File(gameFolder, "locations.json");
        if (!localLocations.exists()) {
            try (InputStream in = getClass().getResourceAsStream("/locations.json");
                 FileOutputStream out = new FileOutputStream(localLocations)) {
                if (in != null) {
                    in.transferTo(out);
                    System.out.println("Copied default locations.json to " + localLocations.getAbsolutePath());
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    // Call this when switching games
    private void clearAlbumOrderCache() {
        albumOrderCache = null;
    }

    private void resetGameState() {
        enabledSets.clear();
        unlockedSongs.clear();
        clearAlbumOrderCache();  // optional, if album order changes per game
    }

    private void checkIfGameFolderExists(File gameFolder){
        // Ensure the per-game folder exists
        if (!gameFolder.exists()) {
            gameFolder.mkdirs();
            System.out.println("Created game data folder: " + gameFolder.getAbsolutePath());
        }
    }

    public Set<String> getUnlockedSongs() {
        return unlockedSongs;
    }

    public Set<String> getUnlockedAlbums() {
        return unlockedAlbums;
    }

    public void applySlotData() {
        if (client == null || client.getSlotData() == null) return;

        JsonElement json = client.getSlotData();
        Map<String, Object> slotMap = new Gson().fromJson(json, new TypeToken<Map<String, Object>>() {}.getType());

        // Get enabled albums dynamically from SlotDataHelper
        Set<String> enabledAlbumsFromSlotData = SlotDataHelper.getEnabledAlbums(slotMap);

        // Clear previously enabled sets
        enabledSets.clear();

        // Enable only the albums in slot data
        for (Album album : albums) {
            if (enabledAlbumsFromSlotData.contains(album.getName())) {
                unlockedAlbums.add(album.getName());   // mark album as unlocked
                enabledSets.add(album.getType());      // enable album type for tree filtering
            }
        }

        refreshTree(); // update the UI
    }

    public void stopCurrentSong() {
        if (currentPlayer != null) {
            currentPlayer.stop();
            currentPlayer = null;
        }
    }

    public void clearPlaybackState() {
        currentSongLabel.setText("");
        // any other UI cleanup (like resetting progress bar, etc.)
    }
}