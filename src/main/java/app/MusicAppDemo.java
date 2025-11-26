package app;

import app.archipelago.APClient;
import app.archipelago.ConnectionListener;
import app.archipelago.ItemListener;
import app.archipelago.PrintJsonListener;
import app.archipelago.SlotDataHelper;
import app.player.Album;
import app.player.Song;
import app.player.AlbumConverter;
import app.player.json.AlbumMetadata;
import app.player.json.AlbumMetadataLoader;
import app.player.json.LibraryLoader;
import app.player.json.SongJSON;
import app.player.ui.ConnectionPanel;
import app.player.ui.PlayerPanel;
import app.util.AlbumLibrary;
import app.util.AlbumOrderManager;
import app.util.StateManager;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;
import javafx.application.Application;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.TreeView;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.Media;
import javafx.stage.Stage;
import javafx.concurrent.Task;
import javafx.util.Duration;

import java.io.File;
import java.io.FileReader;
import java.io.Reader;
import java.io.InputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import java.util.LinkedList;
import java.util.Map;
import java.util.HashSet;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Queue;
import java.util.Comparator;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static app.util.AlbumUtils.generateDefaultAlbumFolders;
import static app.util.ConfigManager.saveConnectionSettings;
import static app.util.ConfigPaths.getConfigDir;
import static app.util.ConfigPaths.getAlbumConfigFile;
import static app.util.ConfigPaths.checkIfGameFolderExists;
import static app.util.Normalization.normalizeFilename;
import static app.util.Normalization.normalizeSongTitle;
import static app.util.Normalization.levenshteinDistance;
import static app.util.Dialogs.showError;
import static app.util.SlotDataUtils.parseBooleanSlot;
import static app.util.SlotDataUtils.parseSlotData;
import static app.util.TimeUtils.formatTime;

public class MusicAppDemo extends Application {

    public static final Logger LOGGER = LoggerFactory.getLogger(MusicAppDemo.class);

    private final List<Album> albums = new ArrayList<>();
    private final Set<String> unlockedAlbums = new HashSet<>();
    private final Set<String> unlockedSongs = new HashSet<>();
    private final Set<String> enabledSets = new HashSet<>();
    private final Set<String> enabledAlbums = new HashSet<>();
    private AlbumLibrary library;

    private AlbumOrderManager albumOrderManager;
    private StateManager stateManager;

    private TreeView<String> treeView;

    private APClient client;

    private Song currentSong;

    // queue UI + data
    private final Queue<Song> playQueue = new LinkedList<>();
    private MediaPlayer currentPlayer;


    private boolean isUpdatingSelection = false;

    // various fields and stuff for the UI (the others are above or locally defined)
    private ConnectionPanel connectionPanel;
    private PlayerPanel playerPanel;
    @SuppressWarnings("FieldCanBeLocal")
    private HBox bottomBar;
    @SuppressWarnings("FieldCanBeLocal")
    private VBox root;

    @SuppressWarnings("unused")
    public static void main(String[] args) {
        launch();
    }

    @Override
    public void start(Stage stage) {
        AtomicReference<File> gameFolder = new AtomicReference<>();

        initUIComponents();

        // When a tree item (song) is selected, add to queue
        treeView.getSelectionModel().selectedItemProperty().addListener((_, _, newSel) ->
            handleTreeSelection(newSel)
        );

        albumOrderManager = new AlbumOrderManager();
        stateManager = new StateManager(this, albumOrderManager);

        refreshTree();

        createBottomBar();

        connectionPanel = new ConnectionPanel(gameFolder, () -> client);

        playerPanel = new PlayerPanel();
        setupPlayerPanel(playerPanel);

        // Add panels to bottom bar
        bottomBar.getChildren().addAll(connectionPanel, playerPanel);

        root = new VBox(10, treeView, bottomBar);
        stage.setScene(new Scene(root, 800, 600));
        stage.setTitle("Archipelago Music Client");
        stage.show();

        Task<List<Album>> loadTask = getLoadTask();

        new Thread(loadTask).start();

        // Disable the game field if connected
        connectionPanel.disableGameField(client != null && client.isConnected());
        connectionPanel.setGameFieldTooltip(client != null && client.isConnected()
                ? "Cannot change game while connected"
                : null);

        // Archipelago connection handler
        connectionPanel.getConnectButton().setOnAction(_ -> {
            if (client == null || !client.isConnected()) {
                connectToServer(gameFolder);
            } else {
                disconnectFromServer();
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
                File gameFolder = getConfigDir();
                File localLocations = new File(gameFolder, "locations.json");
                List<SongJSON> rawSongs;

                if (localLocations.exists()) {
                    try (Reader reader = new FileReader(localLocations, StandardCharsets.UTF_8)) {
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

        loadTask.setOnSucceeded(_ -> {
            albums.addAll(loadTask.getValue());

            generateDefaultAlbumFolders(albums);

            // initialize AlbumLibrary now we've added the albums and they exist
            library = new AlbumLibrary(albums);

            treeView.setCellFactory(tv -> new TreeCell<>() {
                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);

                    if (empty || item == null) {
                        setText(null);
                        setStyle(""); // reset style
                    } else {
                        setText(item);

                        TreeItem<String> treeItem = getTreeItem();

                        if (treeItem != null && treeItem.isLeaf()) {
                            // Song nodes
                            Song song = library.getSongByTitle(item);
                            if (song != null && unlockedSongs.contains(song.getTitle())) {
                                setStyle("-fx-font-weight: bold; -fx-text-fill: green;");
                            } else {
                                setStyle("-fx-font-weight: normal; -fx-text-fill: black;");
                            }
                        } else {
                            // Album nodes
                            if (item.equals("Albums")) {
                                // Root "Albums" node — keep it normal black
                                setStyle("-fx-font-weight: normal; -fx-text-fill: black;");
                            } else {
                                // Regular album node
                                Album album = library.getAlbumByName(item);
                                if (album != null && unlockedAlbums.contains(album.getName())) {
                                    // unlocked → bold black
                                    setStyle("-fx-font-weight: bold; -fx-text-fill: black;");
                                } else {
                                    // locked → normal black
                                    setStyle("-fx-font-weight: normal; -fx-text-fill: black;");
                                }
                            }
                        }
                    }
                }
            });

            Map<String, String> albumFolders = new HashMap<>();
            File configFile = getAlbumConfigFile();

            if (configFile.exists()) {
                try (Reader reader = new FileReader(configFile, StandardCharsets.UTF_8)) {
                    Type type = new TypeToken<Map<String, String>>(){}.getType();
                    albumFolders = new Gson().fromJson(reader, type);
                } catch (Exception ex) {
                    //noinspection CallToPrintStackTrace
                    ex.printStackTrace();
                }
            } else {
                LOGGER.info("No config file found at {}, skipping album folder assignment", configFile.getAbsolutePath());
            }

            // add fallback album to unlocked albums
            for (Album album : albums) {
                if ("Songs".equals(album.getName())) {
                    unlockedAlbums.add("Songs");
                    enabledSets.add(album.getType()); // optional: allow its songs to appear
                    break;
                }
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

        loadTask.setOnFailed(_ -> {
            //noinspection CallToPrintStackTrace
            loadTask.getException().printStackTrace();
        });
        return loadTask;
    }

    @SuppressWarnings("unused")
    private void reloadGameLibrary(File gameFolder) {
        // clear old state before reloading
        albums.clear();
        unlockedAlbums.clear();
        unlockedSongs.clear();
        enabledSets.clear();
        albumOrderManager.clearAlbumOrderCache();

        Task<List<Album>> loadTask = getLoadTask();
        new Thread(loadTask).start();
    }

    public void refreshTree() {
        // Custom album order
        List<String> albumOrder = albumOrderManager.getAlbumOrder();

        // Sort albums according to albumOrder
        albums.sort(Comparator.comparingInt(a -> {
            int idx = albumOrder.indexOf(a.getName());
            return idx >= 0 ? idx : Integer.MAX_VALUE; // albums not in the list go last
        }));

        TreeItem<String> rootItem = new TreeItem<>("Albums");
        rootItem.setExpanded(true);

        for (Album album : albums) {
            // Skip albums not unlocked in slot data
            if (!enabledAlbums.contains(album.getName())) continue;

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

    private void playSong(Song song) {
        if (song == null) return;

        boolean canPlay;
        Album album = library.getAlbumForSong(song.getTitle());
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
            LOGGER.info("Song trying to be played ({})'s file path ({}) does not exist or is null.", song.getTitle(), song.getFilePath());
            showError("File Not Found", "Cannot play song", "File not found for: " + song.getTitle());
            return;
        }

        if (currentPlayer != null) {
            currentPlayer.stop();
            currentPlayer.dispose(); // release OS resources
        }

        // Reset progress slider and labels
        playerPanel.resetProgress();

        Media media = new Media(Paths.get(song.getFilePath()).toUri().toString());
        currentPlayer = new MediaPlayer(media);

        currentPlayer.currentTimeProperty().addListener((_, _, newTime) -> {
            if (!playerPanel.getProgressSlider().isValueChanging()) {
                Duration total = currentPlayer.getTotalDuration();
                if (total != null && total.greaterThan(Duration.ZERO)) {
                    playerPanel.getProgressSlider().setValue(newTime.toMillis() / total.toMillis());
                    playerPanel.getElapsedLabel().setText(formatTime(newTime));
                }
            }
        });

        // Set duration label once media is ready
        currentPlayer.setOnReady(() -> {
            Duration total = currentPlayer.getTotalDuration();
            if (total != null) {
                playerPanel.getDurationLabel().setText(formatTime(total));
            }
        });

        currentPlayer.setOnEndOfMedia(() -> {
            if (client != null && client.isConnected()) {
                client.sendCheck(song.getTitle());
            }
            playNextInQueue();
        });

        currentPlayer.play();
        playerPanel.setCurrentSongLabel("Currently Playing: " + song.getTitle());
        updateQueueDisplay();
        highlightCurrentSong(song.getTitle());
    }

    private void playNextInQueue() {
        Song next = playQueue.poll();
        updateQueueDisplay();
        if (next != null) {
            playSong(next);
        } else {
            playerPanel.setCurrentSongLabel("Currently Playing: None");
            currentPlayer = null;
            playerPanel.resetProgress();
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
        playerPanel.clearQueueDisplay();
        for (Song s : playQueue) {
            playerPanel.addToQueueDisplay(s.getTitle());
        }
        if (playQueue.isEmpty()) {
            playerPanel.addToQueueDisplay("(empty)");
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

    private void assignFilesToSongs() {
        for (Album album : albums) {
            String folderPath = album.getFolderPath();
            if (folderPath == null) continue;

            File albumDirectory = new File(folderPath);
            if (!albumDirectory.exists() || !albumDirectory.isDirectory()) continue;

            File[] files = albumDirectory.listFiles((_, name) ->
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
                    if (normalizedFile.equalsIgnoreCase(normalizedSong)) {
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
                    LOGGER.info("Matched: {} -> {} | path: {}", file.getName(), matchedSong.getTitle(), matchedSong.getFilePath());
                } else {
                    LOGGER.warn("Could not match file to song: {} in album {}", file.getName(), album.getName());
                }
            }
        }
    }

    private void ensureGameDefaults(File gameFolder) {
        if (!gameFolder.exists()) {
            //noinspection ResultOfMethodCallIgnored
            gameFolder.mkdirs();
        }

        File albumOrderFile = new File(gameFolder, "albumOrder.json");
        if (!albumOrderFile.exists()) {
            albumOrderManager.getAlbumOrder(); // this method already generates the default if missing
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
                    LOGGER.info("Copied default locations.json to {}", localLocations.getAbsolutePath());                }
            } catch (IOException e) {
                //noinspection CallToPrintStackTrace
                e.printStackTrace();
            }
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
        Map<String, Object> slotMap = parseSlotData(json);

        applyAlbumUnlocks(slotMap);
        filterSongCategories(slotMap);

        refreshTree(); // update the UI
    }

    private void applyAlbumUnlocks(Map<String, Object> slotMap) {
        // Get enabled albums dynamically from SlotDataHelper
        Set<String> enabledAlbumsFromSlotData = SlotDataHelper.getEnabledAlbums(slotMap);

        // Clear previously enabled sets and albums
        enabledSets.clear();
        enabledAlbums.clear();

        // Enable only the albums in slot data
        for (Album album : albums) {
            if (enabledAlbumsFromSlotData.contains(album.getName())) {
                enabledAlbums.add(album.getName());   // mark album as enabled
                enabledSets.add(album.getType());      // enable album type for tree filtering
            }
        }

        // 2. Unlock albums by type if the corresponding slot is enabled
        if (enabledAlbumsFromSlotData.contains("Re-recordings")) {
            for (Album album : albums) {
                if ("re-recording".equalsIgnoreCase(album.getType())) {
                    enabledAlbums.add(album.getName());
                    enabledSets.add(album.getType());
                }
            }
        }
    }

    private void filterSongCategories(Map<String, Object> slotMap) {
        boolean shortSongsEnabled = parseBooleanSlot(slotMap, "include_short_songs");
        boolean vaultSongsEnabled = parseBooleanSlot(slotMap, "include_vault_songs");

        // Now remove any songs that should not be visible
        for (Album album : albums) {
            for (Song s : new ArrayList<>(album.getSongs())) { // avoid ConcurrentModification
                String type = s.getType();

                // Skip short songs if disabled
                if (!shortSongsEnabled && "short".equalsIgnoreCase(type)) {
                    unlockedSongs.remove(s.getTitle());
                    continue;
                }

                // Skip vault tracks if disabled
                if (!vaultSongsEnabled && "vault".equalsIgnoreCase(type)) {
                    unlockedSongs.remove(s.getTitle());
                }
            }
        }
    }

    public void stopCurrentSong() {
        if (currentPlayer != null) {
            currentPlayer.stop();
            currentPlayer = null;
        }
        playerPanel.resetProgress();
    }

    private final ChangeListener<Boolean> seekListener = (_, _, isChanging) -> {
        if (!isChanging && currentPlayer != null) {
            Duration total = currentPlayer.getTotalDuration();
            if (total != null) {
                currentPlayer.seek(total.multiply(playerPanel.getProgressSlider().getValue()));
            }
        }
    };

    public void setConnectButtonText(String text) {
        connectionPanel.setConnectButtonText(text);
    }

    public void setGameFieldDisabled(boolean disabled) {
        connectionPanel.disableGameField(disabled);
    }

    private void disconnectFromServer() {
        // DISCONNECT
        client.disconnect();
        connectionPanel.setStatus("Disconnected");
        connectionPanel.setConnectButtonText("Connect"); // toggle button text

        // Re-enable game field
        connectionPanel.disableGameField(false);
        connectionPanel.setGameFieldTooltip(null);

        // stop playback
        stopCurrentSong();
        playerPanel.clearPlaybackState();

        // CLEAR ALL UNLOCKED / ENABLED DATA
        stateManager.clearUnlocks();

        // Refresh tree so nothing shows
        refreshTree();
    }

    private void connectToServer(AtomicReference<File> gameFolder) {
        String host = connectionPanel.getHost();
        int port = connectionPanel.getPort();
        String slot = connectionPanel.getSlot();
        String password = connectionPanel.getPassword();

        saveConnectionSettings(host, port, slot, password);

        String gameName = connectionPanel.getGameName();
        APClient.saveGameNameStatic(gameName);

        client = new APClient(host, port, slot, password);

        stateManager.resetGameState();
        client.setGameName(gameName);

        gameFolder.set(getConfigDir());
        checkIfGameFolderExists(gameFolder.get(), LOGGER);

        // ✅ reload slot options after game changes
        SlotDataHelper.loadSlotOptions(gameFolder.get());

        ensureGameDefaults(gameFolder.get());
        reloadGameLibrary(gameFolder.get());

        client.setOnErrorCallback(ex -> {
            connectionPanel.setStatus("Connection failed");
            showError("Connection Failed",
                    "Failed to connect to Archipelago server",
                    "Reason: " + ex.getMessage());

            // Reset button and fields so user can try again
            connectionPanel.setConnectButtonText("Connect");
            connectionPanel.disableGameField(false);
        });

        try {
            client.getEventManager().registerListener(new ConnectionListener(connectionPanel.getStatusLabel(), client, this));
            client.getEventManager().registerListener(new ItemListener(this));
            client.getEventManager().registerListener(new PrintJsonListener(client, this, connectionPanel.getTextClientWindow().getOutputArea()));
            client.connect();
            connectionPanel.setStatus("Connected!");
            connectionPanel.setConnectButtonText("Disconnect"); // toggle button text

            // --- Disable the game field after connecting ---
            connectionPanel.disableGameField(true);
            connectionPanel.setGameFieldTooltip("Cannot change game while connected");

        } catch (Exception ex) {
            connectionPanel.setStatus("Connection failed");
            showError("Connection Failed", "Failed to connect to Archipelago server", ex.getMessage());
            connectionPanel.setConnectButtonText("Connect");
        }
    }

    private void createBottomBar() {
        // Bottom controls HBox
        bottomBar = new HBox(20);
        bottomBar.setPadding(new Insets(10));
        bottomBar.setAlignment(Pos.CENTER);
    }

    private void setupPlayerPanel(PlayerPanel panel) {

        // Play button behaviour
        panel.getPlayButton().setOnAction(_ -> {
            // If paused, resume. If nothing playing but queue has items, start next.
            if (currentPlayer != null && currentPlayer.getStatus() == MediaPlayer.Status.PAUSED) {
                currentPlayer.play();
                if (currentSong != null) playerPanel.setCurrentSongLabel("Currently Playing: " + currentSong.getTitle());
                return;
            }

            if (currentSong != null && (currentPlayer == null || currentPlayer.getStatus() != MediaPlayer.Status.PLAYING)) {
                LOGGER.info("Current song ({})'s file path: {}", currentSong.getTitle(), currentSong.getFilePath());
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

        panel.getPauseButton().setOnAction(_ -> {
            if (currentPlayer != null) {
                MediaPlayer.Status status = currentPlayer.getStatus();
                if (status == MediaPlayer.Status.PLAYING) {
                    currentPlayer.pause();
                    if (currentSong != null) playerPanel.setCurrentSongLabel("Paused: " + currentSong.getTitle());
                } else if (status == MediaPlayer.Status.PAUSED) {
                    currentPlayer.play();
                    if (currentSong != null) playerPanel.setCurrentSongLabel("Currently Playing: " + currentSong.getTitle());
                }
            }
        });

        // Remove selected from the queue (both ListView and underlying queue)
        panel.getRemoveSelectedBtn().setOnAction(_ -> {
            String sel = playerPanel.getQueueListView().getSelectionModel().getSelectedItem();
            if (sel != null) {
                removeFromQueue(sel);
                updateQueueDisplay();
            }
        });

        // Clear queue
        panel.getClearQueueBtn().setOnAction(_ -> {
            playQueue.clear();
            updateQueueDisplay();
        });

        panel.bindSeekCheckBox(seekListener);
    }

    private void handleTreeSelection(TreeItem<String> newSel) {
        if (newSel == null) return;

        String songTitle = newSel.getValue();
        Song song = library.getSongByTitle(songTitle);

        if (song == null) return;

        Album album = library.getAlbumForSong(songTitle);
        boolean songUnlocked = unlockedSongs.contains(song.getTitle());
        boolean albumUnlocked = album != null && unlockedAlbums.contains(album.getType());

        // Check unlocking rules
        if (!songUnlocked || !albumUnlocked) {
            if (!songUnlocked) {
                showError("Locked Song", "Cannot play song", song.getTitle() + " is not unlocked yet!");
            } else if (!albumUnlocked && album != null) {
                showError("Locked Song", "Cannot queue song", song.getTitle() + " requires album " + album.getName() + " to be unlocked!");
            }
            return;
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
    }

    private void initUIComponents() {
        treeView = new TreeView<>();
    }

    public AlbumLibrary getLibrary() {
        return library;
    }
}