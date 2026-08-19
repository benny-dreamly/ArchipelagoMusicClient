package app;

import app.archipelago.APClient;
import app.archipelago.ConnectionListener;
import app.archipelago.ItemListener;
import app.archipelago.PrintJsonListener;
import app.archipelago.SlotDataHelper;
import app.logic.GoalManager;
import app.logic.QueueManager;
import app.logic.SongFileMatcher;
import app.logic.UnlockManager;
import app.player.Album;
import app.player.Song;
import app.player.AlbumConverter;
import app.player.json.AlbumMetadata;
import app.player.json.AlbumMetadataLoader;
import app.player.json.LibraryLoader;
import app.player.json.MusicLibraryLoader;
import app.player.json.SongJSON;
import app.player.ui.AlbumArtPanel;
import app.player.ui.ConnectionPanel;
import app.player.ui.PlayerPanel;
import app.util.AlbumLibrary;
import app.util.AlbumOrderManager;
import app.util.StateManager;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Slider;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.TreeView;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.Media;
import javafx.stage.Stage;
import javafx.concurrent.Task;
import javafx.util.Duration;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.Reader;
import java.io.Writer;
import java.io.InputStream;
import java.io.FileOutputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import java.util.Collections;
import java.util.LinkedList;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.images.Artwork;

import static app.util.AlbumUtils.generateDefaultAlbumFolders;
import static app.util.ConfigManager.saveConnectionSettings;
import static app.util.ConfigPaths.getConfigDir;
import static app.util.ConfigPaths.getAlbumConfigFile;
import static app.util.ConfigPaths.checkIfGameFolderExists;
import static app.util.Dialogs.showError;
import static app.util.SlotDataUtils.parseSlotData;
import static app.util.TimeUtils.formatTime;

public class MusicAppDemo extends Application {

    public static final Logger LOGGER = LoggerFactory.getLogger(MusicAppDemo.class);

    private final List<Album> albums = new ArrayList<>();
    private final List<String> bonusLocations = new ArrayList<>();
    private final UnlockManager unlockManager = new UnlockManager(this::refreshTree);
    private GoalManager goalManager;
    private Set<String> pendingPlayedSongs = Collections.emptySet();
    private String pendingPlayedSlot;
    private boolean usingMusicLibrary = false;
    private boolean offlineMode = false;
    private boolean volumeAdjustMode = false;
    private final StringBuilder volumeInput = new StringBuilder();
    private AlbumLibrary library;

    private AlbumOrderManager albumOrderManager;
    private StateManager stateManager;

    private TreeView<String> treeView;

    private APClient client;
    private ItemListener itemListener;

    private Song currentSong;

    private QueueManager queueManager;

    // playback
    private MediaPlayer currentPlayer;


    private boolean isUpdatingSelection = false;
    private boolean suppressSelection = false;
    private long artworkRequestId = 0;
    private final ExecutorService artworkExecutor = new ThreadPoolExecutor(
            1, 1, 0L, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(1),
            new ThreadPoolExecutor.DiscardOldestPolicy());

    // various fields and stuff for the UI (the others are above or locally defined)
    private ConnectionPanel connectionPanel;
    private PlayerPanel playerPanel;
    private AlbumArtPanel albumArtPanel;
    private HBox albumPanel;
    private final ContextMenu contextMenu = new ContextMenu();
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
        treeView.getSelectionModel().selectedItemProperty().addListener((_, _, newSel) -> {
            if (suppressSelection) {
                suppressSelection = false;
                return;
            }
            handleTreeSelection(newSel);
        });

        setupAlbumContextMenu();

        // Suppress selection handling on right-click so context menu doesn't double-queue
        treeView.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
            if (event.getButton() == MouseButton.SECONDARY) {
                suppressSelection = true;
            }
        });

        albumOrderManager = new AlbumOrderManager();
        stateManager = new StateManager(this, albumOrderManager);

        refreshTree();

        createBottomBar();

        connectionPanel = new ConnectionPanel(gameFolder, () -> client);

        playerPanel = new PlayerPanel();
        setupPlayerPanel(playerPanel);

        // Add panels to bottom bar
        bottomBar.getChildren().addAll(connectionPanel, playerPanel);

        albumArtPanel = new AlbumArtPanel();
        albumPanel = new HBox(10);
        albumPanel.getChildren().addAll(treeView, albumArtPanel);
        HBox.setHgrow(treeView, javafx.scene.layout.Priority.ALWAYS);
        VBox.setVgrow(albumPanel, javafx.scene.layout.Priority.ALWAYS);

        root = new VBox(10, albumPanel, bottomBar);
        Scene scene = new Scene(root, 800, 600);
        stage.setScene(scene);
        stage.setTitle("Archipelago Music Client");
        stage.show();

        setupKeyboardShortcuts(scene);

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

        // Offline mode toggle
        connectionPanel.getOfflineCheck().selectedProperty().addListener((_, _, isOffline) -> {
            if (isOffline) {
                enableOfflineMode();
            } else {
                disableOfflineMode();
            }
        });
    }


    @Override
    public void stop() throws Exception {
        super.stop();
        artworkExecutor.shutdownNow();
        if (client != null && client.isConnected()) {
            client.disconnect();
        }
        System.exit(0); // ensures all threads are killed
    }

    private Task<List<Album>> getLoadTask() {
        Task<List<Album>> loadTask = new Task<>() {
            @Override
            protected List<Album> call() throws Exception {
                usingMusicLibrary = false;
                File gameFolder = getConfigDir();
                File musicLibraryFile = new File(gameFolder, "music_library.json");

                // Primary: music_library.json (new hierarchical format)
                if (musicLibraryFile.exists()) {
                    MusicLibraryLoader musicLoader = new MusicLibraryLoader();
                    try {
                        usingMusicLibrary = true;
                        bonusLocations.clear();
                        bonusLocations.addAll(musicLoader.loadBonusLocations(musicLibraryFile));
                        return musicLoader.loadFromFile(musicLibraryFile);
                    } catch (Exception e) {
                        usingMusicLibrary = false;
                        LOGGER.warn("Failed to load music_library.json, falling back to locations.json", e);
                    }
                }

                // Fallback: locations.json (legacy flat format)
                LibraryLoader loader = new LibraryLoader();
                File localLocations = new File(gameFolder, "locations.json");
                List<SongJSON> rawSongs;

                if (localLocations.exists()) {
                    try (Reader reader = new FileReader(localLocations, StandardCharsets.UTF_8)) {
                        rawSongs = loader.loadSongsFromReader(reader);
                    }
                } else {
                    rawSongs = loader.loadSongs("/locations.json");
                }

                Map<String, AlbumMetadata> metadata = AlbumMetadataLoader.loadAlbumMetadata(gameFolder);
                AlbumConverter converter = new AlbumConverter(metadata);
                List<Album> result = converter.convert(rawSongs);
                bonusLocations.clear();
                bonusLocations.addAll(converter.getBonusLocations());
                return result;
            }
        };

        loadTask.setOnSucceeded(_ -> {
            albums.addAll(loadTask.getValue());

            if (!usingMusicLibrary) {
                generateDefaultAlbumFolders(albums);
            }

            // initialize AlbumLibrary now we've added the albums and they exist
            library = new AlbumLibrary(albums);
            queueManager = new QueueManager(library, unlockManager);
            goalManager = new GoalManager(unlockManager, albums);

            // Apply any played songs that arrived before GoalManager was ready
            if (!pendingPlayedSongs.isEmpty()) {
                if (client != null && client.isConnected() && pendingPlayedSlot != null
                        && pendingPlayedSlot.equals(client.getSlot())) {
                    goalManager.loadFromServer(pendingPlayedSongs, client);
                } else {
                    LOGGER.warn("Discarding deferred played songs: slot mismatch (expected={}, current={})",
                            pendingPlayedSlot, client != null ? client.getSlot() : "null");
                }
                pendingPlayedSongs = Collections.emptySet();
                pendingPlayedSlot = null;
            }

            // Replay any item events that buffered while library was loading
            if (itemListener != null) {
                itemListener.drainBuffer();
            }

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
                            if (song != null && unlockManager.isSongUnlocked(song.getTitle())) {
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
                                if (album != null && unlockManager.isAlbumUnlocked(album.getName())) {
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

            // Fallback: load albumFolders.json for albums without a path from music_library.json
            {
                boolean anyMissingPaths = albums.stream().anyMatch(a -> a.getFolderPath() == null || a.getFolderPath().isEmpty());
                if (anyMissingPaths) {
                    Map<String, String> albumFolders = new HashMap<>();
                    File configFile = getAlbumConfigFile();

                    if (configFile.exists()) {
                        try (Reader reader = new FileReader(configFile, StandardCharsets.UTF_8)) {
                            Type type = new TypeToken<Map<String, String>>(){}.getType();
                            Map<String, String> parsed = new Gson().fromJson(reader, type);
                            if (parsed != null) albumFolders = parsed;
                        } catch (Exception ex) {
                            LOGGER.error("Error loading album folders configuration", ex);
                        }
                    }

                    for (Album album : albums) {
                        if (album.getFolderPath() == null || album.getFolderPath().isEmpty()) {
                            String path = albumFolders.get(album.getName());
                            if (path != null && !path.isEmpty()) {
                                album.setFolderPath(path);
                            }
                        }
                    }
                }
            }

            // add fallback album to unlocked albums
            for (Album album : albums) {
                if ("Songs".equals(album.getName())) {
                    unlockManager.getUnlockedAlbums().add("Songs");
                    unlockManager.getEnabledSets().add(album.getType()); // optional: allow its songs to appear
                    break;
                }
            }

            // After assigning folder paths in loadTask.setOnSucceeded
            assignFilesToSongs();

            refreshTree(); // populate TreeView after loading

            // If offline mode was activated before load finished, apply it now
            if (offlineMode) {
                applyOfflineUnlocks();
            }

            playerPanel.getLoadQueueBtn().setDisable(false);
            playerPanel.getPlayButton().setDisable(false);
            playerPanel.getPauseButton().setDisable(false);
            playerPanel.getRepeatButton().setDisable(false);
            playerPanel.getSaveQueueBtn().setDisable(false);
            playerPanel.getShuffleQueueBtn().setDisable(false);
            playerPanel.getClearQueueBtn().setDisable(false);
            playerPanel.getRemoveSelectedBtn().setDisable(false);
            treeView.setDisable(false);
        });

        loadTask.setOnFailed(_ -> {
            //noinspection CallToPrintStackTrace
            loadTask.getException().printStackTrace();
            // Discard buffered item events — library failed to load
            if (itemListener != null) {
                itemListener.discardBuffer();
            }
            // Re-enable controls on failure so UI doesn't get stuck
            playerPanel.getLoadQueueBtn().setDisable(false);
            playerPanel.getPlayButton().setDisable(false);
            playerPanel.getPauseButton().setDisable(false);
            playerPanel.getRepeatButton().setDisable(false);
            playerPanel.getSaveQueueBtn().setDisable(false);
            playerPanel.getShuffleQueueBtn().setDisable(false);
            playerPanel.getClearQueueBtn().setDisable(false);
            playerPanel.getRemoveSelectedBtn().setDisable(false);
            treeView.setDisable(false);
        });
        return loadTask;
    }

    @SuppressWarnings("unused")
    private void reloadGameLibrary(File gameFolder) {
        // Stop and dispose current playback
        if (currentPlayer != null) {
            currentPlayer.stop();
            currentPlayer.dispose();
            currentPlayer = null;
        }
        currentSong = null;

        // Clear old state before reloading
        albums.clear();
        unlockManager.getUnlockedAlbums().clear();
        unlockManager.getUnlockedSongs().clear();
        unlockManager.getEnabledSets().clear();
        albumOrderManager.clearAlbumOrderCache();
        if (queueManager != null) {
            queueManager.clear();
        }
        playerPanel.clearQueueDisplay();
        playerPanel.clearPlaybackState();
        playerPanel.setCurrentSongLabel("Loading...");

        // Disable tree and playback controls during load
        treeView.setDisable(true);
        playerPanel.getPlayButton().setDisable(true);
        playerPanel.getPauseButton().setDisable(true);
        playerPanel.getRepeatButton().setDisable(true);
        playerPanel.getSaveQueueBtn().setDisable(true);
        playerPanel.getLoadQueueBtn().setDisable(true);
        playerPanel.getShuffleQueueBtn().setDisable(true);
        playerPanel.getClearQueueBtn().setDisable(true);
        playerPanel.getRemoveSelectedBtn().setDisable(true);

        // Buffer item events while library reloads
        if (itemListener != null) {
            itemListener.setLibraryLoading(true);
        }

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
            if (!unlockManager.getEnabledAlbums().contains(album.getName())) continue;

            TreeItem<String> albumItem = new TreeItem<>(album.getName());
            boolean hasSongs = false;

            for (Song song : album.getSongs()) {
                if (unlockManager.getEnabledSets().contains(song.getType())) {
                    TreeItem<String> songItem = new TreeItem<>(song.getTitle());
                    albumItem.getChildren().add(songItem);
                    hasSongs = true;
                }
            }

            if (hasSongs) rootItem.getChildren().add(albumItem);
        }

        if (!bonusLocations.isEmpty()) {
            TreeItem<String> bonusItem = new TreeItem<>("Bonus");
            for (String location : bonusLocations) {
                bonusItem.getChildren().add(new TreeItem<>(location));
            }
            rootItem.getChildren().add(bonusItem);
        }

        treeView.setRoot(rootItem);
    }

    private void setupAlbumContextMenu() {
        treeView.setOnContextMenuRequested(event -> {
            suppressSelection = false;
            TreeItem<String> item = treeView.getSelectionModel().getSelectedItem();
            if (item == null || item.getParent() == null) return;

            contextMenu.getItems().clear(); // reset the context menu

            if (item.isLeaf() && item.getParent().getParent() != null && !"Bonus".equals(item.getParent().getValue())) {
                MenuItem queueNext = new MenuItem("Play Next");
                queueNext.setOnAction(_ -> queueSongNext(item.getValue()));
                contextMenu.getItems().add(queueNext);
                contextMenu.show(treeView, event.getScreenX(), event.getScreenY());
                event.consume();
            }

            // Only show for album nodes (non-leaf, child of root, not Bonus)
            if (!item.isLeaf() && item.getParent().getParent() == null && !"Bonus".equals(item.getValue())) {
                MenuItem queueAll = new MenuItem("Queue All Songs");
                queueAll.setOnAction(_ -> queueAlbum(item.getValue()));
                contextMenu.getItems().add(queueAll);
                contextMenu.show(treeView, event.getScreenX(), event.getScreenY());
                event.consume();
            }
        });
    }

    private void queueAlbum(String albumName) {
        Album album = library.getAlbumByName(albumName);
        if (album == null) return;

        List<Song> queueable = album.getQueueableSongs(unlockManager.getEnabledSets(), unlockManager.getUnlockedSongs(), unlockManager.getUnlockedAlbums());
        if (queueable.isEmpty()) {
            LOGGER.info("No queueable songs in album '{}'", albumName);
            return;
        }

        queueManager.addAll(queueable);
        LOGGER.info("Queued {} songs from album '{}'", queueable.size(), albumName);
        updateQueueDisplay();

        // If nothing is playing, start the first queued song
        if ((currentPlayer == null || currentPlayer.getStatus() != MediaPlayer.Status.PLAYING) && !queueManager.isEmpty()) {
            Song next = queueManager.poll();
            updateQueueDisplay();
            if (next != null) {
                playSong(next);
            }
        }
    }

    private void queueSongNext(String songTitle) {
        Song song = library.getSongByTitle(songTitle);
        if (song == null) return;

        Album album = library.getAlbumForSong(songTitle);
        if (!unlockManager.canPlay(song, album)) return;

        // insert at front
        queueManager.addFirst(song);
        updateQueueDisplay();

        // If nothing is playing, start the first queued song
        if (currentPlayer == null && !queueManager.isEmpty()) {
            Song next = queueManager.poll();
            updateQueueDisplay();
            if (next != null) {
                playSong(next);
            }
        }
    }

    public void unlockSong(String songTitle) {
        unlockManager.unlockSong(songTitle);
    }

    public void unlockAlbum(String albumName) {
        unlockManager.unlockAlbum(albumName, albums);
    }

    public Set<String> getEnabledSets() {
        return unlockManager.getEnabledSets();
    }

    private void playSong(Song song) {
        if (song == null) return;

        Album album = library.getAlbumForSong(song.getTitle());
        boolean albumUnlocked = album != null && unlockManager.isAlbumUnlocked(album.getName());
        boolean canPlay = unlockManager.canPlay(song, album);

        if (!canPlay) {
            String msg;
            if (album != null && !albumUnlocked) {
                msg = song.getTitle() + " requires album " + album.getName() + " to be unlocked!";
            } else {
                msg = song.getTitle() + " is not unlocked yet!";
            }
            LOGGER.info("Cannot play song. {}", msg);
            showError("Locked Song", "Cannot play song", msg);
            return;
        }

        this.currentSong = song;

        queueManager.recordSnapshot(song);

        if (song.getFilePath() == null || !new File(song.getFilePath()).exists()) {
            LOGGER.info("Song trying to be played ({})'s file path ({}) does not exist or is null.", song.getTitle(), song.getFilePath());
            showError("File Not Found", "Cannot play song", "File not found for: " + song.getTitle());
            playNextInQueue();
            return;
        }

        if (currentPlayer != null) {
            currentPlayer.stop();
            currentPlayer.dispose(); // release OS resources
        }

        // Reset progress slider and labels
        playerPanel.resetProgress();

        // Extract album art in background
        long requestId = ++artworkRequestId;
        albumArtPanel.clearArtwork();
        String filePath = song.getFilePath();
        String trackInfo = song.getTitle();
        artworkExecutor.execute(() -> {
            try {
                AudioFile audioFile = AudioFileIO.read(new File(filePath));
                Tag tag = audioFile.getTag();
                if (tag != null) {
                    Artwork artwork = tag.getFirstArtwork();
                    if (artwork != null) {
                        byte[] data = artwork.getBinaryData();
                        Image image = new Image(new ByteArrayInputStream(data), 180, 180, true, true);
                        Platform.runLater(() -> {
                            if (requestId == artworkRequestId) {
                                albumArtPanel.setArtwork(image, trackInfo);
                            }
                        });
                        return;
                    }
                }
            } catch (Exception e) {
                LOGGER.debug("Could not extract album art for {}: {}", trackInfo, e.getMessage());
            }
            Platform.runLater(() -> {
                if (requestId == artworkRequestId) {
                    albumArtPanel.clearArtwork();
                }
            });
        });

        Media media = new Media(Paths.get(song.getFilePath()).toUri().toString());
        currentPlayer = new MediaPlayer(media);
        currentPlayer.setVolume(playerPanel.getVolumeSlider().getValue() / 100.0);

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
                client.sendCheck(song.getLocation());
                Album songAlbum = library.getAlbumForSong(song.getTitle());
                if (goalManager != null && songAlbum != null) {
                    goalManager.markPlayed(song.getTitle(), songAlbum.getName(), client);
                }
            }
            if (queueManager.getRepeatMode() == QueueManager.RepeatMode.SONG) {
                playSong(song);
            } else {
                playNextInQueue();
            }
        });

        currentPlayer.setOnError(() -> {
            MediaPlayer player = currentPlayer;
            String errorMessage = player != null && player.getError() != null
                    ? player.getError().getMessage() : "Unknown error";
            LOGGER.error("Error playing '{}': {}", song.getTitle(), errorMessage);
            showError("Playback Error", "Cannot play song", "Error playing " + song.getTitle() + ": " + errorMessage);
            playNextInQueue();
        });

        currentPlayer.play();
        playerPanel.setCurrentSongLabel("Currently Playing: " + song.getTitle());
        updateQueueDisplay();
        highlightCurrentSong(song.getTitle());
    }

    private void playNextInQueue() {
        if (queueManager == null) return;
        Song next = queueManager.nextSong(currentSong);
        updateQueueDisplay();
        if (next != null) {
            playSong(next);
        } else {
            playerPanel.setCurrentSongLabel("Currently Playing: None");
            currentPlayer = null;
            long id = ++artworkRequestId;
            Platform.runLater(() -> {
                if (id == artworkRequestId) {
                    albumArtPanel.clearArtwork();
                }
            });
            playerPanel.resetProgress();
        }
    }

    private void highlightCurrentSong(String songTitle) {
        if (isUpdatingSelection) return;

        TreeItem<String> root = treeView.getRoot();
        if (root == null) return;

        isUpdatingSelection = true;

        for (TreeItem<String> albumItem : root.getChildren()) {
            for (TreeItem<String> songItem : albumItem.getChildren()) {
                if (songItem.getValue().equals(songTitle)) {
                    treeView.getSelectionModel().select(songItem);
                    int row = treeView.getRow(songItem);
                    if (row >= 0) {
                        treeView.scrollTo(row);
                    }
                    isUpdatingSelection = false;
                    return;
                }
            }
        }

        isUpdatingSelection = false;
    }

    // Queue helpers -----------------------------------------------------

    private void updateQueueDisplay() {
        playerPanel.clearQueueDisplay();
        for (Song s : queueManager.asList()) {
            playerPanel.addToQueueDisplay(s);
        }
    }

    private void removeFromQueue(Song song) {
        queueManager.remove(song);
    }

    private void moveInQueue(int fromIndex, int toIndex) {
        queueManager.move(fromIndex, toIndex);
        updateQueueDisplay();
    }

    private int getQueueIndexAtY(ListView<Song> listView, double y) {
        for (javafx.scene.Node node : listView.lookupAll(".list-cell")) {
            if (node instanceof javafx.scene.control.ListCell<?> cell) {
                if (cell.getItem() == null) continue;
                if (cell.getListView() != listView) continue;
                javafx.geometry.Bounds cellBounds = listView.sceneToLocal(
                    node.localToScene(node.getBoundsInLocal()));
                if (y >= cellBounds.getMinY() && y < cellBounds.getMaxY()) {
                    double midpoint = cellBounds.getMinY() + cellBounds.getHeight() / 2.0;
                    return y < midpoint ? cell.getIndex() : cell.getIndex() + 1;
                }
            }
        }
        return listView.getItems().size();
    }

    private void assignFilesToSongs() {
        SongFileMatcher.assignFilesToSongs(albums);
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
                LOGGER.error("Failed to copy default locations.json to {}", localLocations.getAbsolutePath(), e);
            }
        }
    }

    public Set<String> getUnlockedSongs() {
        return unlockManager.getUnlockedSongs();
    }

    public Set<String> getUnlockedAlbums() {
        return unlockManager.getUnlockedAlbums();
    }

    public void applySlotData() {
        if (client == null || client.getSlotData() == null) return;

        JsonElement json = client.getSlotData();
        Map<String, Object> slotMap = parseSlotData(json);

        unlockManager.applySlotData(slotMap, albums);
    }

    private void enableOfflineMode() {
        offlineMode = true;

        // Disconnect any active connection first
        if (client != null && client.isConnected()) {
            client.disconnect();
            connectionPanel.setConnectButtonText("Connect");
            connectionPanel.disableGameField(false);
            connectionPanel.setGameFieldTooltip(null);
        }

        connectionPanel.setStatus("Offline Mode");
        connectionPanel.setConnectionFieldsDisabled(true);

        if (!albums.isEmpty()) {
            applyOfflineUnlocks();
        }
        // If albums aren't loaded yet, applyOfflineUnlocks will be called in onSucceeded
    }

    private void disableOfflineMode() {
        offlineMode = false;
        connectionPanel.setStatus("Not connected");
        connectionPanel.setConnectionFieldsDisabled(false);

        if (client != null) {
            client.disconnect();
        }

        stateManager.clearUnlocks();
        unlockManager.getEnabledAlbums().clear();
        refreshTree();
    }

    private void applyOfflineUnlocks() {
        unlockManager.applyOfflineUnlocks(albums);
    }

    public void stopCurrentSong() {
        if (currentPlayer != null) {
            currentPlayer.stop();
            currentPlayer = null;
        }
        long id = ++artworkRequestId;
        Platform.runLater(() -> {
            if (id == artworkRequestId) {
                albumArtPanel.clearArtwork();
            }
        });
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

        String gameName = connectionPanel.getGameName();
        saveConnectionSettings(host, port, slot, password, gameName);
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
            itemListener = new ItemListener(this);
            client.getEventManager().registerListener(itemListener);
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
            } else if ((currentPlayer == null || currentPlayer.getStatus() != MediaPlayer.Status.PLAYING) && !queueManager.isEmpty()) {
                Song next = queueManager.poll();
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

        // Repeat button cycles: Off → Queue → Song → Album → Off
        panel.getRepeatButton().setOnAction(_ -> {
            QueueManager.RepeatMode nextMode = queueManager.getRepeatMode().next();
            queueManager.setRepeatMode(nextMode);
            panel.getRepeatButton().setText(nextMode.label());
        });

        // Remove selected from the queue (both ListView and underlying queue)
        panel.getRemoveSelectedBtn().setOnAction(_ -> {
            Song sel = playerPanel.getQueueListView().getSelectionModel().getSelectedItem();
            if (sel != null) {
                removeFromQueue(sel);
                updateQueueDisplay();
            }
        });

        // Clear queue
        panel.getClearQueueBtn().setOnAction(_ -> {
            queueManager.clear();
            updateQueueDisplay();
        });

        // Shuffle queue
        panel.getShuffleQueueBtn().setOnAction(_ -> {
            queueManager.shuffle();
            updateQueueDisplay();
        });

        // Save queue
        panel.getSaveQueueBtn().setOnAction(_ -> {
            File queueFile = new File(getConfigDir(), "queue.json");
            List<Map<String, String>> entries = queueManager.toEntries();
            try (Writer writer = new FileWriter(queueFile, StandardCharsets.UTF_8)) {
                new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(entries, writer);
                LOGGER.info("Queue saved to {}", queueFile.getAbsolutePath());
            } catch (IOException e) {
                LOGGER.error("Failed to save queue", e);
            }
        });

        // Load queue
        panel.getLoadQueueBtn().setOnAction(_ -> {
            File queueFile = new File(getConfigDir(), "queue.json");
            if (!queueFile.exists()) return;
            try (Reader reader = new FileReader(queueFile, StandardCharsets.UTF_8)) {
                Type listType = new TypeToken<List<Map<String, String>>>() {}.getType();
                List<Map<String, String>> entries = new Gson().fromJson(reader, listType);
                if (entries == null) return;

                LinkedList<Song> resolved = new LinkedList<>();
                for (Map<String, String> entry : entries) {
                    String title = entry.get("title");
                    String type = entry.get("type");
                    if (title == null) continue;
                    for (Album album : albums) {
                        for (Song song : album.getSongs()) {
                            if (song.getTitle().equals(title) && song.getType().equals(type)) {
                                if (unlockManager.canQueue(song, album)) {
                                    resolved.add(song);
                                }
                            }
                        }
                    }
                }

                queueManager.replaceAll(resolved);
                updateQueueDisplay();
                LOGGER.info("Queue loaded from {} ({} songs)", queueFile.getAbsolutePath(), queueManager.size());
            } catch (Exception e) {
                LOGGER.error("Failed to load queue from {}", queueFile.getAbsolutePath(), e);
            }
        });

        // Volume slider updates live during playback
        panel.getVolumeSlider().valueProperty().addListener((_, _, newVal) -> {
            if (currentPlayer != null) {
                currentPlayer.setVolume(newVal.doubleValue() / 100.0);
            }
        });

        // Drag-and-drop reordering for queue
        ListView<Song> queueList = panel.getQueueListView();
        final int[] dragToIndex = {-1};

        queueList.setOnDragDetected(event -> {
            int index = queueList.getSelectionModel().getSelectedIndex();
            if (index < 0) return;

            Dragboard db = queueList.startDragAndDrop(TransferMode.MOVE);
            ClipboardContent content = new ClipboardContent();
            content.putString(String.valueOf(index));
            db.setContent(content);
            dragToIndex[0] = index;
            event.consume();
        });

        queueList.setOnDragOver(event -> {
            if (event.getGestureSource() == queueList && event.getDragboard().hasString()) {
                event.acceptTransferModes(TransferMode.MOVE);
                int target = getQueueIndexAtY(queueList, event.getY());
                if (target >= 0) {
                    dragToIndex[0] = target;
                }
            }
            event.consume();
        });

        queueList.setOnDragDropped(event -> {
            if (event.getGestureSource() != queueList || !event.getDragboard().hasString()) {
                event.setDropCompleted(false);
                event.consume();
                return;
            }

            int fromIndex = Integer.parseInt(event.getDragboard().getString());
            int toIndex = dragToIndex[0];
            dragToIndex[0] = -1;
            if (fromIndex == toIndex) return;

            moveInQueue(fromIndex, toIndex);
            event.setDropCompleted(true);
            event.consume();
        });

        panel.bindSeekCheckBox(seekListener);
    }

    private void setupKeyboardShortcuts(Scene scene) {
        scene.addEventFilter(KeyEvent.KEY_PRESSED, this::handleKeyPress);
    }

    private void handleKeyPress(KeyEvent event) {
        if (event.getTarget() instanceof TextInputControl) {
            return;
        }

        if (volumeAdjustMode) {
            handleVolumeModeKey(event.getCode());
            event.consume();
            return;
        }

        switch (event.getCode()) {
            case SPACE -> {
                togglePlayPause();
                event.consume();
            }
            case LEFT -> {
                if (playerPanel.getEnableSeekCheck().isSelected()) {
                    seekRelative(-5);
                    event.consume();
                }
            }
            case RIGHT -> {
                if (playerPanel.getEnableSeekCheck().isSelected()) {
                    seekRelative(5);
                    event.consume();
                }
            }
            case T -> {
                connectionPanel.getTextClientWindow().show();
                event.consume();
            }
            case O -> {
                connectionPanel.getOfflineCheck().setSelected(
                    !connectionPanel.getOfflineCheck().isSelected()
                );
                event.consume();
            }
            case N -> {
                playNextInQueue();
                event.consume();
            }
            case V -> {
                enterVolumeAdjustMode();
                event.consume();
            }
            default -> { /* ignore other keys */ }
        }
    }

    private void togglePlayPause() {
        if (currentPlayer != null) {
            MediaPlayer.Status status = currentPlayer.getStatus();
            if (status == MediaPlayer.Status.PLAYING) {
                currentPlayer.pause();
                if (currentSong != null) playerPanel.setCurrentSongLabel("Paused: " + currentSong.getTitle());
            } else if (status == MediaPlayer.Status.PAUSED) {
                currentPlayer.play();
                if (currentSong != null) playerPanel.setCurrentSongLabel("Currently Playing: " + currentSong.getTitle());
            }
        } else if (queueManager != null && !queueManager.isEmpty()) {
            Song next = queueManager.poll();
            updateQueueDisplay();
            if (next != null) playSong(next);
        }
    }

    private void seekRelative(int seconds) {
        if (currentPlayer == null || currentPlayer.getStatus() == MediaPlayer.Status.STOPPED) return;
        Duration current = currentPlayer.getCurrentTime();
        Duration total = currentPlayer.getTotalDuration();
        if (total == null) return;
        Duration newTime = current.add(Duration.seconds(seconds));
        if (newTime.greaterThan(total)) newTime = total;
        if (newTime.lessThan(Duration.ZERO)) newTime = Duration.ZERO;
        currentPlayer.seek(newTime);
    }

    private void enterVolumeAdjustMode() {
        volumeAdjustMode = true;
        volumeInput.setLength(0);
        connectionPanel.setStatus("Volume: " + (int) playerPanel.getVolumeSlider().getValue() + "% (arrows/numbers, Enter=set, Esc=cancel)");
    }

    private void exitVolumeAdjustMode() {
        volumeAdjustMode = false;
        volumeInput.setLength(0);
        connectionPanel.setStatus(offlineMode ? "Offline Mode" : (client != null && client.isConnected() ? "Connected!" : "Not connected"));
    }

    private void handleVolumeModeKey(KeyCode code) {
        switch (code) {
            case ESCAPE -> exitVolumeAdjustMode();
            case ENTER -> {
                if (volumeInput.length() > 0) {
                    playerPanel.getVolumeSlider().setValue(
                        Math.min(100, Math.max(0, Integer.parseInt(volumeInput.toString())))
                    );
                }
                exitVolumeAdjustMode();
            }
            case UP -> {
                Slider slider = playerPanel.getVolumeSlider();
                slider.setValue(Math.min(100, slider.getValue() + 10));
                volumeInput.setLength(0);
            }
            case DOWN -> {
                Slider slider = playerPanel.getVolumeSlider();
                slider.setValue(Math.max(0, slider.getValue() - 10));
                volumeInput.setLength(0);
            }
            case LEFT -> {
                Slider slider = playerPanel.getVolumeSlider();
                slider.setValue(Math.max(0, slider.getValue() - 1));
                volumeInput.setLength(0);
            }
            case RIGHT -> {
                Slider slider = playerPanel.getVolumeSlider();
                slider.setValue(Math.min(100, slider.getValue() + 1));
                volumeInput.setLength(0);
            }
            case DIGIT0, DIGIT1, DIGIT2, DIGIT3, DIGIT4, DIGIT5, DIGIT6, DIGIT7, DIGIT8, DIGIT9 -> {
                int digit = switch (code) {
                    case DIGIT0 -> 0; case DIGIT1 -> 1; case DIGIT2 -> 2; case DIGIT3 -> 3;
                    case DIGIT4 -> 4; case DIGIT5 -> 5; case DIGIT6 -> 6; case DIGIT7 -> 7;
                    case DIGIT8 -> 8; default -> 9;
                };
                if (volumeInput.length() < 3) {
                    volumeInput.append(digit);
                }
                connectionPanel.setStatus("Volume: " + volumeInput.toString() + "% (Enter=set)");
            }
            default -> { /* ignore */ }
        }
    }

    private void sendBonusCheck(String location) {
        if (client != null && client.isConnected()) {
            client.sendCheck(location);
            bonusLocations.remove(location);
            Platform.runLater(this::refreshTree);
            LOGGER.info("Sent bonus check: {}", location);
        }
    }

    private void handleTreeSelection(TreeItem<String> newSel) {
        if (newSel == null) return;

        // Only leaf nodes are actionable; ignore album and root nodes
        if (!newSel.isLeaf()) return;

        String value = newSel.getValue();

        // Bonus locations: send check if selected leaf is under the "Bonus" branch
        TreeItem<String> parent = newSel.getParent();
        if (parent != null && "Bonus".equals(parent.getValue())) {
            sendBonusCheck(value);
            return;
        }

        Song song = library.getSongByTitle(value);

        if (song == null) return;

        // Don't re-queue the currently playing song (e.g. from highlightCurrentSong)
        if (song == currentSong) return;

        Album album = library.getAlbumForSong(value);
        if (!unlockManager.canPlay(song, album)) {
            if (album == null || !unlockManager.isAlbumUnlocked(album.getName())) {
                showError("Locked Song", "Cannot play song", song.getTitle() + " is not unlocked yet!");
            } else {
                showError("Locked Song", "Cannot queue song", song.getTitle() + " requires album " + album.getName() + " to be unlocked!");
            }
            return;
        }

        // Add to queue, song has passed checks
        queueManager.add(song);
        updateQueueDisplay();

        // If nothing is playing, start immediately
        if (currentPlayer == null || currentPlayer.getStatus() != MediaPlayer.Status.PLAYING) {
            Song next = queueManager.poll();
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

    public GoalManager getGoalManager() {
        return goalManager;
    }

    public void setPendingPlayedSongs(Set<String> songs, String slot) {
        this.pendingPlayedSongs = songs;
        this.pendingPlayedSlot = slot;
    }
}