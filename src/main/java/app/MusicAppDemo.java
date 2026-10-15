/*
 * SPDX-License-Identifier: MPL-2.0
 */
package app;

import app.archipelago.APClient;
import app.archipelago.ConnectionListener;
import app.archipelago.DeathLinkListener;
import app.archipelago.EnergyLinkListener;
import app.archipelago.ItemListener;
import app.archipelago.NameGroupsListener;
import app.archipelago.PrintJsonListener;
import app.archipelago.SlotDataHelper;
import app.logic.FolderScanner;
import app.logic.GoalManager;
import app.logic.QueueManager;
import app.logic.SessionRestoreController;
import app.logic.SongFileMatcher;
import app.logic.UnlockManager;
import app.player.Album;
import app.player.AlbumConverter;
import app.player.PlaybackController;
import app.player.PlaybackEngine;
import app.player.Song;
import app.player.json.AlbumMetadata;
import app.player.json.AlbumMetadataLoader;
import app.player.json.LibraryLoader;
import app.player.json.MusicLibraryConverter;
import app.player.json.MusicLibraryJSON;
import app.player.json.MusicLibraryLoader;
import app.player.json.SongJSON;
import app.player.ui.AlbumArtPanel;
import app.player.ui.ConnectionPanel;
import app.player.ui.PlayerPanel;
import app.player.ui.SettingsWindow;
import app.remote.CompanionController;
import app.util.AlbumLibrary;
import app.util.AlbumOrderManager;
import app.util.PlayHistoryStore;
import app.util.SentBonusStore;
import app.util.StateManager;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
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
import javafx.stage.Stage;
import javafx.stage.DirectoryChooser;
import javafx.concurrent.Task;
import javafx.util.Duration;

import java.awt.Desktop;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.Reader;
import java.io.Writer;
import java.io.InputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.Collections;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static app.util.AlbumUtils.generateDefaultAlbumFolders;
import static app.util.ConfigManager.saveConnectionSettings;
import static app.util.ConfigManager.loadDarkMode;
import static app.util.ConfigManager.saveDarkMode;
import static app.util.ConfigManager.loadDeathLink;
import static app.util.ConfigManager.saveDeathLink;
import static app.util.ConfigManager.loadBrowseFolder;
import static app.util.ConfigManager.saveBrowseFolder;
import static app.util.ConfigManager.saveSessionRestore;
import static app.util.ConfigManager.saveCompanionServer;
import static app.util.ConfigPaths.getConfigDir;
import static app.util.ConfigPaths.getAlbumConfigFile;
import static app.util.ConfigPaths.checkIfGameFolderExists;
import static app.util.Dialogs.showError;
import static app.util.SlotDataUtils.parseSlotData;

public class MusicAppDemo extends Application
        implements PlaybackController.Host, CompanionController.Host, SessionRestoreController.Host {

    public static final Logger LOGGER = LoggerFactory.getLogger(MusicAppDemo.class);

    private final List<Album> albums = new ArrayList<>();
    private final List<String> bonusLocations = new ArrayList<>();
    private final UnlockManager unlockManager = new UnlockManager(this::refreshTree);
    private GoalManager goalManager;
    private Set<String> pendingPlayedSongs = Collections.emptySet();
    private String pendingPlayedSlot;
    private boolean pendingPlayedSnapshotReceived;
    private int pendingPlayedGeneration = -1;
    private boolean usingMusicLibrary = false;
    private boolean offlineMode = false;
    private final AtomicInteger loadGeneration = new AtomicInteger(0);
    private SessionRestoreController sessionRestore;
    private boolean volumeAdjustMode = false;
    private final StringBuilder volumeInput = new StringBuilder();
    private AlbumLibrary library;

    private AlbumOrderManager albumOrderManager;
    private StateManager stateManager;

    private TreeView<String> treeView;
    private TextField searchField;

    private APClient client;
    private ItemListener itemListener;
    private EnergyLinkListener energyLinkListener;

    private QueueManager queueManager;

    // playback
    private PlaybackController playback;

    // companion phone server
    private CompanionController companion;

    private boolean isUpdatingSelection = false;
    private boolean suppressSelection = false;

    // various fields and stuff for the UI (the others are above or locally defined)
    private ConnectionPanel connectionPanel;
    private PlayerPanel playerPanel;
    private AlbumArtPanel albumArtPanel;
    private SettingsWindow settingsWindow;
    private HBox albumPanel;
    private Scene scene;
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

        searchField.textProperty().addListener((_, _, _) -> refreshTree());

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

        playerPanel = new PlayerPanel();
        albumArtPanel = new AlbumArtPanel();
        playback = new PlaybackController(this, playerPanel, albumArtPanel);
        companion = new CompanionController(this, playback);
        sessionRestore = new SessionRestoreController(this, playback);
        setupPlayerPanel(playerPanel);

        connectionPanel = new ConnectionPanel(gameFolder, () -> client,
                playback::setPlaybackSpeedCommand);

        // Add panels to bottom bar
        bottomBar.getChildren().addAll(connectionPanel, playerPanel);

        albumPanel = new HBox(10);
        VBox treeBox = new VBox(10);
        treeBox.setPrefWidth(400);
        treeBox.getChildren().addAll(searchField, treeView);
        HBox.setHgrow(treeBox, javafx.scene.layout.Priority.ALWAYS);
        VBox.setVgrow(treeView, javafx.scene.layout.Priority.ALWAYS);
        albumPanel.getChildren().addAll(treeBox, albumArtPanel);
        VBox.setVgrow(albumPanel, javafx.scene.layout.Priority.ALWAYS);

        root = new VBox(10, albumPanel, bottomBar);
        scene = new Scene(root, 800, 600);
        scene.getStylesheets().add(getClass().getResource("/app.css").toExternalForm());
        stage.setScene(scene);
        stage.setTitle("Archipelago Music Client");
        setWindowIcons(stage);
        stage.show();

        setupKeyboardShortcuts(scene);

        File savedBrowse = savedBrowseFolder();
        if (savedBrowse != null) {
            browseFolder(savedBrowse);
        } else {
            Task<LoadResult> loadTask = getLoadTask();
            new Thread(loadTask).start();
        }

        // Disable the game field if connected
        connectionPanel.disableGameField(client != null && client.isConnected());
        connectionPanel.setGameFieldTooltip(client != null && client.isConnected()
                ? "Cannot change game while connected"
                : null);

        // Archipelago connection handler
        connectionPanel.getConnectButton().setOnAction(_ -> {
            if (client == null || (!client.isConnected() && !client.isReconnecting())) {
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

        // Browse Folder mode
        connectionPanel.getBrowseFolderBtn().setOnAction(_ -> {
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle("Select Music Folder");
            File selected = chooser.showDialog(stage);
            if (selected != null) {
                browseFolder(selected);
            }
        });

        // Dark mode toggle
        if (loadDarkMode()) {
            scene.getStylesheets().add(getClass().getResource("/dark.css").toExternalForm());
        }

        // Dedicated settings window: dark mode, deathlink, session restore
        settingsWindow = new SettingsWindow(stage);
        connectionPanel.getOpenSettingsButton().setOnAction(_ -> settingsWindow.show());
        settingsWindow.getDarkModeCheck().selectedProperty().addListener((_, _, isDark) -> {
            String darkCss = getClass().getResource("/dark.css").toExternalForm();
            scene.getStylesheets().remove(darkCss);
            if (isDark) {
                scene.getStylesheets().add(darkCss);
            }
            settingsWindow.setDarkMode(isDark);
            saveDarkMode(isDark);
        });

        // Deathlink toggle
        settingsWindow.getDeathLinkCheck().selectedProperty().addListener((_, _, enabled) -> {
            saveDeathLink(enabled);
            applyDeathLinkEnabled(enabled);
        });

        // Session restore toggle
        settingsWindow.getSessionRestoreCheck().selectedProperty().addListener((_, _, enabled) ->
                saveSessionRestore(enabled));

        // Companion phone server toggle: start/stop live so the LAN ports are
        // only reserved while the setting is on.
        settingsWindow.getCompanionServerCheck().selectedProperty().addListener((_, _, enabled) -> {
            saveCompanionServer(enabled);
            if (enabled) {
                companion.restart();
            } else {
                companion.stop();
            }
        });

        // Library converter: rebuild music_library.json from locations.json
        settingsWindow.getConvertLibraryButton().setOnAction(_ -> convertLocationsToLibrary());

        // Open this game's config folder in the OS file manager
        settingsWindow.getOpenConfigFolderButton().setOnAction(_ -> openConfigFolder());

        // Periodically snapshot the queue and playback position while the
        // library exists, so a later same-slot connect can restore it.
        Timeline sessionSaver = new Timeline(new KeyFrame(Duration.seconds(5),
                _ -> sessionRestore.saveSnapshot()));
        sessionSaver.setCycleCount(Timeline.INDEFINITE);
        sessionSaver.play();
    }


    @Override
    public void stop() throws Exception {
        super.stop();
        if (sessionRestore != null) {
            sessionRestore.saveSnapshot(); // persist queue + position for session restore
        }
        if (playback != null) {
            playback.shutdown();
        }
        if (client != null) {
            client.close();
        }
        System.exit(0); // ensures all threads are killed
    }

    private void setWindowIcons(Stage stage) {
        try {
            Image icon = new Image(getClass().getResourceAsStream("/icons/app-icon-512.png"));
            if (!icon.isError()) {
                stage.getIcons().add(icon);
            }
        } catch (Exception e) {
            LOGGER.warn("Could not set the window icon, continuing with the default.", e);
        }
    }

    private record LoadResult(List<Album> albums, boolean usingMusicLibrary, List<String> bonusLocations) {}

    private Task<LoadResult> getLoadTask() {
        final int generation = loadGeneration.get();
        Task<LoadResult> loadTask = new Task<>() {
            @Override
            protected LoadResult call() throws Exception {
                File gameFolder = getConfigDir();
                File musicLibraryFile = new File(gameFolder, "music_library.json");
                File localLocations = new File(gameFolder, "locations.json");
                MusicLibraryLoader musicLoader = new MusicLibraryLoader();
                LibraryLoader loader = new LibraryLoader();

                // Primary: music_library.json (new hierarchical format). The user's
                // own data — in either format — must win over the bundled sample.
                if (musicLibraryFile.exists()) {
                    try {
                        List<Album> loaded = musicLoader.loadFromFile(musicLibraryFile);
                        List<String> bonus = new ArrayList<>(musicLoader.loadBonusLocations(musicLibraryFile));
                        return new LoadResult(loaded, true, bonus);
                    } catch (Exception e) {
                        LOGGER.warn("Failed to load music_library.json, trying locations.json", e);
                    }
                }

                // Legacy: locations.json in the game folder (flat format)
                if (localLocations.exists()) {
                    try (Reader reader = new FileReader(localLocations, StandardCharsets.UTF_8)) {
                        List<SongJSON> rawSongs = loader.loadSongsFromReader(reader);
                        Map<String, AlbumMetadata> metadata = AlbumMetadataLoader.loadAlbumMetadata(gameFolder);
                        AlbumConverter converter = new AlbumConverter(metadata);
                        List<Album> result = converter.convert(rawSongs);
                        List<String> bonus = new ArrayList<>(converter.getBonusLocations());
                        LOGGER.info("Loaded {} albums from locations.json in game folder", result.size());
                        return new LoadResult(result, false, bonus);
                    } catch (Exception e) {
                        LOGGER.warn("Failed to load locations.json in game folder, falling back to bundled sample", e);
                    }
                }

                // Fallback: bundled music_library.json (current sample), so a fresh
                // install or a game without per-game config still loads a sample library.
                try {
                    List<Album> loaded = musicLoader.loadFromResource("/music_library.json");
                    List<String> bonus = new ArrayList<>(musicLoader.loadBonusLocationsResource("/music_library.json"));
                    LOGGER.info("No music_library.json or locations.json in game folder; using bundled sample library");
                    return new LoadResult(loaded, true, bonus);
                } catch (Exception e) {
                    LOGGER.warn("Bundled music_library.json unavailable, falling back to bundled locations.json", e);
                }

                // Fallback: bundled locations.json (legacy sample)
                List<SongJSON> rawSongs = loader.loadSongs("/locations.json");
                Map<String, AlbumMetadata> metadata = AlbumMetadataLoader.loadAlbumMetadata(gameFolder);
                AlbumConverter converter = new AlbumConverter(metadata);
                List<Album> result = converter.convert(rawSongs);
                List<String> bonus = new ArrayList<>(converter.getBonusLocations());
                return new LoadResult(result, false, bonus);
            }
        };

        loadTask.setOnSucceeded(_ -> {
            LoadResult result = loadTask.getValue();
            if (generation != loadGeneration.get()) return; // stale load — discard

            albums.addAll(result.albums());
            usingMusicLibrary = result.usingMusicLibrary();
            bonusLocations.clear();
            bonusLocations.addAll(result.bonusLocations());

            if (!usingMusicLibrary) {
                generateDefaultAlbumFolders(albums);
            }

            // initialize AlbumLibrary now we've added the albums and they exist
            library = new AlbumLibrary(albums);
            queueManager = new QueueManager(library, unlockManager);
            goalManager = new GoalManager(unlockManager, albums, this::onGoalProgressChanged);
            // Seed locally-tracked plays; a later server response replaces them (strict
            // separation so offline listening never inflates a slot's server state).
            goalManager.loadFromLocal(PlayHistoryStore.load());

            // Re-apply slot data against the populated album list in case the
            // connection callback fired before the library finished loading.
            applySlotData();

            // Replay any item events that buffered while library was loading
            if (itemListener != null) {
                itemListener.drainBuffer();
            }

            // Apply any played-songs snapshot that arrived before GoalManager was ready
            if (pendingPlayedSnapshotReceived && pendingPlayedGeneration == loadGeneration.get()) {
                if (client != null && client.isConnected() && pendingPlayedSlot != null
                        && pendingPlayedSlot.equals(String.valueOf(client.getSlot()))) {
                    goalManager.loadFromServer(pendingPlayedSongs, client);
                } else {
                    LOGGER.warn("Discarding deferred played songs: slot mismatch (expected={}, current={})",
                            pendingPlayedSlot, client != null ? client.getSlot() : "null");
                }
                pendingPlayedSongs = Collections.emptySet();
                pendingPlayedSlot = null;
                pendingPlayedSnapshotReceived = false;
                pendingPlayedGeneration = -1;
            }

            installTreeCellFactory();

            // Fallback: load albumFolders.json for albums without a path from music_library.json
            {
                boolean anyMissingPaths = albums.stream().anyMatch(a -> a.getFolderPath() == null
                        || a.getFolderPath().isBlank());
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
                        if (album.getFolderPath() == null || album.getFolderPath().isBlank()) {
                            String path = albumFolders.get(album.getName());
                            if (path != null && !path.isBlank()) {
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
                    unlockManager.getEnabledAlbums().add("Songs");
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

            companion.start(albumMusicRoots());

            restoreLibraryControls();

            // Library is ready — schedule a pending session restore so it applies
            // only after the starting-inventory items have updated unlock state.
            scheduleSessionRestoreApply();
        });

        loadTask.setOnFailed(_ -> {
            if (generation != loadGeneration.get()) return; // stale load — discard
            Throwable err = loadTask.getException();
            LOGGER.error("Library load task failed", err);
            // Discard buffered item events — library failed to load
            if (itemListener != null) {
                itemListener.discardBuffer();
            }
            // Re-enable safe controls on failure; keep playback/queue controls
            // disabled when library or queueManager is null to avoid NPE on dereference
            applyFailureControls();

            String detail = err == null ? "Unknown error"
                    : (err.getMessage() == null || err.getMessage().isBlank()
                    ? err.getClass().getSimpleName() : err.getMessage());
            showError("Library Load Failed", "Could not load the music library", detail);
        });
        return loadTask;
    }

    @SuppressWarnings("unused")
    private void convertLocationsToLibrary() {
        File gameFolder = getConfigDir();
        File locationsFile = new File(gameFolder, "locations.json");
        if (!locationsFile.isFile()) {
            showError("No locations.json", "locations.json not found",
                    "This game folder has no locations.json to convert:\n" + gameFolder.getAbsolutePath());
            return;
        }

        File musicLibraryFile = new File(gameFolder, "music_library.json");
        MusicLibraryConverter converter = new MusicLibraryConverter();
        settingsWindow.getConvertLibraryButton().setDisable(true);
        Task<MusicLibraryJSON> task = new Task<>() {
            @Override
            protected MusicLibraryJSON call() throws Exception {
                File backup = new File(gameFolder, "music_library.json.bak");
                if (musicLibraryFile.exists() && !backup.exists()) {
                    Files.copy(musicLibraryFile.toPath(), backup.toPath());
                }
                MusicLibraryJSON library = converter.convert(locationsFile, gameFolder);
                converter.write(library, musicLibraryFile);
                return library;
            }
        };
        task.setOnSucceeded(_ -> {
            try {
                MusicLibraryJSON library = task.getValue();
                int songs = 0;
                for (MusicLibraryJSON.AlbumJSON album : library.albums) {
                    songs += album.songs.size();
                }
                connectionPanel.getTextClientWindow().appendOutput("Converted locations.json -> music_library.json ("
                        + library.albums.size() + " albums, " + songs + " songs)");
                if (savedBrowseFolder() == null) {
                    reloadGameLibrary();
                }
            } finally {
                settingsWindow.getConvertLibraryButton().setDisable(false);
            }
        });
        task.setOnFailed(_ -> {
            try {
                Throwable err = task.getException();
                LOGGER.error("Failed to convert locations.json to music_library.json", err);
                String detail = err == null ? "Unknown error"
                        : (err.getMessage() == null || err.getMessage().isBlank()
                        ? err.getClass().getSimpleName() : err.getMessage());
                showError("Conversion Failed", "Could not convert locations.json", detail);
            } finally {
                settingsWindow.getConvertLibraryButton().setDisable(false);
            }
        });
        new Thread(task).start();
    }

    private void openConfigFolder() {
        File gameFolder = getConfigDir();
        if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
            showError("Open Folder Unsupported", "Opening the file manager isn't supported here",
                    gameFolder.getAbsolutePath());
            return;
        }
        try {
            Desktop.getDesktop().open(gameFolder);
        } catch (IOException e) {
            LOGGER.error("Could not open the config folder {}", gameFolder.getAbsolutePath(), e);
            showError("Open Folder Failed", "Could not open the config folder",
                    gameFolder.getAbsolutePath());
        }
    }

    private void reloadGameLibrary() {
        // Buffer item events before invalidating generation
        if (itemListener != null) {
            itemListener.setLibraryLoading(true);
        }
        loadGeneration.incrementAndGet(); // invalidate any in-flight load task
        companion.stop();
        // Stop and dispose current playback
        playback.resetTrack();

        // Clear old state before reloading
        albums.clear();
        unlockManager.getUnlockedAlbums().clear();
        unlockManager.getUnlockedSongs().clear();
        unlockManager.getEnabledSets().clear();
        unlockManager.getEnabledAlbums().clear();
        albumOrderManager.clearAlbumOrderCache();
        goalManager = null;
        library = null;
        queueManager = null;
        pendingPlayedSongs = Collections.emptySet();
        pendingPlayedSlot = null;
        pendingPlayedSnapshotReceived = false;
        pendingPlayedGeneration = -1;
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

        Task<LoadResult> loadTask = getLoadTask();
        new Thread(loadTask).start();
    }

    /**
     * Loads a local folder into a playable library without any Archipelago
     * manual. Disconnects (offline mode), clears the previous library, and scans
     * the folder on a background thread. Synthetic albums are fully unlocked so
     * every scanned song is immediately playable.
     */
    private void browseFolder(File folder) {
        // Enter offline mode first; this disconnects any active connection
        enableOfflineMode();
        connectionPanel.getOfflineCheck().setSelected(true);

        // Buffer item events before invalidating generation
        if (itemListener != null) {
            itemListener.setLibraryLoading(true);
        }
        loadGeneration.incrementAndGet(); // invalidate any in-flight load task
        companion.stop();

        // Stop and dispose current playback
        playback.resetTrack();

        // Clear old state before loading the scanned library
        albums.clear();
        bonusLocations.clear();
        unlockManager.getUnlockedAlbums().clear();
        unlockManager.getUnlockedSongs().clear();
        unlockManager.getEnabledSets().clear();
        unlockManager.getEnabledAlbums().clear();
        albumOrderManager.clearAlbumOrderCache();
        goalManager = null;
        library = null;
        queueManager = null;
        pendingPlayedSongs = Collections.emptySet();
        pendingPlayedSlot = null;
        pendingPlayedSnapshotReceived = false;
        pendingPlayedGeneration = -1;
        playerPanel.clearQueueDisplay();
        playerPanel.clearPlaybackState();
        playerPanel.setCurrentSongLabel("Scanning...");

        // Disable tree and playback controls during scan
        treeView.setDisable(true);
        playerPanel.getPlayButton().setDisable(true);
        playerPanel.getPauseButton().setDisable(true);
        playerPanel.getRepeatButton().setDisable(true);
        playerPanel.getSaveQueueBtn().setDisable(true);
        playerPanel.getLoadQueueBtn().setDisable(true);
        playerPanel.getShuffleQueueBtn().setDisable(true);
        playerPanel.getClearQueueBtn().setDisable(true);
        playerPanel.getRemoveSelectedBtn().setDisable(true);

        connectionPanel.setStatus("Scanning " + folder.getName() + "...");
        final String persistedBrowsePath = loadBrowseFolder();

        final int generation = loadGeneration.get();
        Task<List<Album>> scanTask = new Task<>() {
            @Override
            protected List<Album> call() throws Exception {
                return FolderScanner.scanFolder(folder);
            }
        };

        scanTask.setOnSucceeded(_ -> {
            if (generation != loadGeneration.get()) return; // stale scan — discard
            List<Album> scanned = scanTask.getValue();
            if (scanned == null || scanned.isEmpty()) {
                LOGGER.warn("No audio files found under {}", folder);
                if (persistedBrowsePath != null
                        && persistedBrowsePath.equals(folder.getAbsolutePath())) {
                    saveBrowseFolder(null); // clear stale remembered folder so we can fall back to the normal library
                }
                applyFailureControls();
                showError("No Music Found", "Folder scan found nothing",
                        "No .mp3, .m4a or .wav files were found in: " + folder.getAbsolutePath());
                return;
            }

            saveBrowseFolder(folder.getAbsolutePath()); // persist only once playable files are found
            albums.addAll(scanned);
            usingMusicLibrary = true;
            bonusLocations.clear();

            library = new AlbumLibrary(albums);
            queueManager = new QueueManager(library, unlockManager);
            goalManager = new GoalManager(unlockManager, albums, this::onGoalProgressChanged);
            goalManager.loadFromLocal(PlayHistoryStore.load());

            installTreeCellFactory();
            applyOfflineUnlocks();
            refreshTree();

            companion.start(List.of(folder));
            restoreLibraryControls();
            connectionPanel.setStatus("Browsing " + folder.getName()
                    + " (" + albums.size() + " albums, "
                    + albums.stream().mapToInt(a -> a.getSongs().size()).sum() + " tracks)");
        });

        scanTask.setOnFailed(_ -> {
            if (generation != loadGeneration.get()) return; // stale scan — discard
            Throwable err = scanTask.getException();
            LOGGER.error("Folder scan failed", err);
            if (itemListener != null) {
                itemListener.discardBuffer();
            }
            if (persistedBrowsePath != null
                    && persistedBrowsePath.equals(folder.getAbsolutePath())) {
                saveBrowseFolder(null); // clear stale remembered folder so the normal library restores
            }
            applyFailureControls();

            String detail = err == null ? "Unknown error"
                    : (err.getMessage() == null || err.getMessage().isBlank()
                    ? err.getClass().getSimpleName() : err.getMessage());
            showError("Folder Scan Failed", "Could not scan the selected folder", detail);
        });

        new Thread(scanTask).start();
    }

    private void installTreeCellFactory() {
        treeView.setCellFactory(tv -> new TreeCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);

                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                    getStyleClass().removeAll("song-unlocked", "album-unlocked");
                } else {
                    TreeItem<String> treeItem = getTreeItem();

                    if (treeItem != null && treeItem.isLeaf()) {
                        // Song nodes
                        getStyleClass().remove("album-unlocked");
                        setText(item);
                        Song song = library.getSongByTitle(item);
                        if (song != null && unlockManager.isSongUnlocked(song.getTitle())) {
                            getStyleClass().add("song-unlocked");
                        } else {
                            getStyleClass().remove("song-unlocked");
                        }
                    } else {
                        // Album nodes
                        getStyleClass().remove("song-unlocked");
                        if (item.equals("Albums")) {
                            // Root "Albums" node — show overall world completion
                            UnlockManager.AlbumProgress world = unlockManager.getWorldProgress(library.getAlbums());
                            int worldPlayed = goalManager != null
                                    ? goalManager.getWorldPlayedCount(library.getAlbums())
                                    : world.unlocked();
                            setText(item + " (" + worldPlayed + "/" + world.total() + ")");
                            getStyleClass().remove("album-unlocked");
                        } else {
                            // Regular album node
                            Album album = library.getAlbumByName(item);
                            if (album != null) {
                                UnlockManager.AlbumProgress progress = unlockManager.getAlbumProgress(album);
                                int albumPlayed = goalManager != null
                                        ? goalManager.getPlayedCount(album)
                                        : progress.unlocked();
                                setText(item + " (" + albumPlayed + "/" + progress.total() + ")");
                            } else {
                                setText(item);
                            }
                            if (album != null && unlockManager.isAlbumUnlocked(album.getName())) {
                                // unlocked → bold
                                getStyleClass().add("album-unlocked");
                            } else {
                                // locked → normal
                                getStyleClass().remove("album-unlocked");
                            }
                        }
                    }
                }
            }
        });
    }

    private void restoreLibraryControls() {
        playerPanel.getLoadQueueBtn().setDisable(false);
        playerPanel.getPlayButton().setDisable(false);
        playerPanel.getPauseButton().setDisable(false);
        playerPanel.getRepeatButton().setDisable(false);
        playerPanel.getSaveQueueBtn().setDisable(false);
        playerPanel.getShuffleQueueBtn().setDisable(false);
        playerPanel.getClearQueueBtn().setDisable(false);
        playerPanel.getRemoveSelectedBtn().setDisable(false);
        treeView.setDisable(false);
        playerPanel.setCurrentSongLabel("Currently Playing: None");
    }

    private void applyFailureControls() {
        playerPanel.getLoadQueueBtn().setDisable(queueManager == null);
        treeView.setDisable(library == null || queueManager == null);
        playerPanel.getPlayButton().setDisable(library == null || queueManager == null);
        playerPanel.getPauseButton().setDisable(library == null || queueManager == null);
        playerPanel.getRepeatButton().setDisable(library == null || queueManager == null);
        playerPanel.getSaveQueueBtn().setDisable(library == null || queueManager == null);
        playerPanel.getShuffleQueueBtn().setDisable(library == null || queueManager == null);
        playerPanel.getClearQueueBtn().setDisable(library == null || queueManager == null);
        playerPanel.getRemoveSelectedBtn().setDisable(library == null || queueManager == null);
        playerPanel.setCurrentSongLabel("Currently Playing: None");
    }

    private File savedBrowseFolder() {
        String path = loadBrowseFolder();
        if (path == null) {
            return null;
        }
        File folder = new File(path);
        return folder.isDirectory() ? folder : null;
    }

    private void onGoalProgressChanged() {
        Platform.runLater(() -> refreshTree());
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

        String query = searchField.getText().trim().toLowerCase(Locale.ROOT);

        for (Album album : albums) {
            // Skip albums not unlocked in slot data
            if (!unlockManager.getEnabledAlbums().contains(album.getName())) continue;

            boolean albumMatches = album.getName().toLowerCase(Locale.ROOT).contains(query);
            TreeItem<String> albumItem = new TreeItem<>(album.getName());
            boolean hasSongs = false;

            for (Song song : album.getSongs()) {
                if (unlockManager.getEnabledSets().contains(song.getType())) {
                    if (!albumMatches && !song.getTitle().toLowerCase(Locale.ROOT).contains(query)) continue;
                    TreeItem<String> songItem = new TreeItem<>(song.getTitle());
                    albumItem.getChildren().add(songItem);
                    hasSongs = true;
                }
            }

            if (hasSongs) rootItem.getChildren().add(albumItem);
        }

        if (!bonusLocations.isEmpty()) {
            boolean bonusMatches = "bonus".contains(query);
            Set<String> sentForSlot = Set.copyOf(SentBonusStore.load(connectionPanel.getSlot()));
            TreeItem<String> bonusItem = new TreeItem<>("Bonus");
            for (String location : bonusLocations) {
                if (sentForSlot.contains(location)) continue;
                if (bonusMatches || location.toLowerCase(Locale.ROOT).contains(query)) {
                    bonusItem.getChildren().add(new TreeItem<>(location));
                }
            }
            if (!bonusItem.getChildren().isEmpty()) {
                rootItem.getChildren().add(bonusItem);
            }
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
                queueNext.setOnAction(_ -> {
                    Album album = library.getAlbumByName(item.getParent().getValue());
                    queueSongNext(item.getValue(), album);
                });
                contextMenu.getItems().add(queueNext);
                contextMenu.show(treeView, event.getScreenX(), event.getScreenY());
                event.consume();
            }

            // Only show for album nodes (non-leaf, child of root, not Bonus)
            if (!item.isLeaf() && item.getParent().getParent() == null && !"Bonus".equals(item.getValue())) {
                MenuItem queueAll = new MenuItem("Queue All Songs");
                queueAll.setOnAction(_ -> queueAlbum(item.getValue()));
                contextMenu.getItems().add(queueAll);
                if (goalManager != null) {
                    MenuItem queueUnplayed = new MenuItem("Queue Unplayed");
                    queueUnplayed.setOnAction(_ -> queueUnplayedAlbum(item.getValue()));
                    contextMenu.getItems().add(queueUnplayed);
                }
                contextMenu.show(treeView, event.getScreenX(), event.getScreenY());
                event.consume();
            }
        });
    }

    private void queueAlbum(String albumName) {
        Album album = library.getAlbumByName(albumName);
        if (album == null) return;

        List<Song> queueable = album.getQueueableSongs(unlockManager.getEnabledSets(),
                unlockManager.getUnlockedSongs(), unlockManager.getUnlockedAlbums());
        if (queueable.isEmpty()) {
            LOGGER.info("No queueable songs in album '{}'", albumName);
            return;
        }

        queueManager.addAll(queueable);
        LOGGER.info("Queued {} songs from album '{}'", queueable.size(), albumName);
        updateQueueDisplay();

        // If nothing is playing, start the first queued song
        if (!playback.isPlaying() && !queueManager.isEmpty()) {
            Song next = queueManager.poll();
            updateQueueDisplay();
            if (next != null) {
                playback.playSong(next);
            }
        }
    }

    private void queueUnplayedAlbum(String albumName) {
        Album album = library.getAlbumByName(albumName);
        if (album == null || goalManager == null) return;

        List<Song> queueable = album.getQueueableSongs(unlockManager.getEnabledSets(),
                unlockManager.getUnlockedSongs(), unlockManager.getUnlockedAlbums());
        List<Song> unplayed = queueable.stream()
                .filter(song -> !goalManager.isSongPlayed(song.getTitle(), albumName))
                .toList();
        if (unplayed.isEmpty()) {
            LOGGER.info("No unplayed songs in album '{}'", albumName);
            return;
        }

        queueManager.addAll(unplayed);
        LOGGER.info("Queued {} unplayed songs from album '{}'", unplayed.size(), albumName);
        updateQueueDisplay();

        // If nothing is playing, start the first queued song
        if (!playback.isPlaying() && !queueManager.isEmpty()) {
            Song next = queueManager.poll();
            updateQueueDisplay();
            if (next != null) {
                playback.playSong(next);
            }
        }
    }

    private void queueSongNext(String songTitle, Album album) {
        Song song = album != null ? album.getSong(songTitle) : null;
        if (song == null) {
            song = library.getSongByTitle(songTitle);
        }
        if (song == null) return;

        if (!unlockManager.canPlay(song, album)) return;

        // insert at front
        queueManager.addFirst(song);
        updateQueueDisplay();

        // If nothing is playing, start the first queued song
        if (playback.getEngine() == null && !queueManager.isEmpty()) {
            Song next = queueManager.poll();
            updateQueueDisplay();
            if (next != null) {
                playback.playSong(next);
            }
        }
    }

    public void unlockAlbum(String albumName) {
        unlockManager.unlockAlbum(albumName, albums);
    }

    public Set<String> getEnabledSets() {
        return unlockManager.getEnabledSets();
    }

    public void enableAlbumForDisplay(String albumName) {
        unlockManager.enableAlbumForDisplay(albumName);
    }

    private List<File> albumMusicRoots() {
        return albums.stream()
                .map(Album::getFolderPath)
                .filter(path -> path != null && !path.isBlank())
                .map(File::new)
                .filter(File::isDirectory)
                .distinct()
                .toList();
    }

    @Override
    public void publishRemoteState() {
        companion.publishState();
    }

    @Override
    public void highlightCurrentSong(Album album, String songTitle) {
        if (isUpdatingSelection) return;

        TreeItem<String> root = treeView.getRoot();
        if (root == null) return;

        isUpdatingSelection = true;

        for (TreeItem<String> albumItem : root.getChildren()) {
            if (!albumItem.getValue().equals(album.getName())) {
                continue; // different album — skip (duplicate titles across albums stay distinct)
            }
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

    @Override
    public void updateQueueDisplay() {
        playerPanel.clearQueueDisplay();
        for (Song s : queueManager.asList()) {
            playerPanel.addToQueueDisplay(s);
        }
        publishRemoteState();
        sessionRestore.saveSnapshot();
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

        File legacyLocations = new File(gameFolder, "locations.json");
        File musicLibraryFile = new File(gameFolder, "music_library.json");
        // Seed the bundled sample music_library.json for brand-new game folders.
        // Folders with an existing library in either format keep their own data.
        if (!musicLibraryFile.exists() && !legacyLocations.exists()) {
            try (InputStream in = getClass().getResourceAsStream("/music_library.json");
                 FileOutputStream out = new FileOutputStream(musicLibraryFile)) {
                if (in != null) {
                    in.transferTo(out);
                    LOGGER.info("Copied bundled sample music_library.json to {}",
                            musicLibraryFile.getAbsolutePath());
                }
            } catch (IOException e) {
                LOGGER.error("Failed to copy bundled sample music_library.json to {}",
                        musicLibraryFile.getAbsolutePath(), e);
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
        if (client != null && (client.isConnected() || client.isReconnecting())) {
            client.close();
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
            client.close();
        }

        stateManager.clearUnlocks();
        unlockManager.getEnabledAlbums().clear();
        refreshTree();
    }

    private void applyOfflineUnlocks() {
        unlockManager.applyOfflineUnlocks(albums);
    }

    private void applyDeathLinkEnabled(boolean enabled) {
        if (client != null) {
            client.setDeathLinkEnabled(enabled);
            LOGGER.info("Deathlink {} for connection", enabled ? "enabled" : "disabled");
        }
    }

    public void handleDeathLink(String source, String cause) {
        LOGGER.info("Deathlink activated by {}: {}", source, cause);

        // Stop current playback on death
        playback.stopCurrentSong();
        playerPanel.setCurrentSongLabel("Currently Playing: None");
        connectionPanel.setStatus("Deathlink from " + source);
        connectionPanel.getTextClientWindow().appendOutput(
                "You died from " + source + (cause == null || cause.isBlank() ? "" : " (" + cause + ")") + "\n");
    }

    private final ChangeListener<Boolean> seekListener = (_, _, isChanging) -> {
        PlaybackEngine engine = playback.getEngine();
        if (!isChanging && engine != null && engine.isLoaded()) {
            Duration total = engine.getTotalDuration();
            if (total != null) {
                engine.seek(total.multiply(playerPanel.getProgressSlider().getValue()));
            }
        }
    };

    public void resetTextClientReadyState() {
        connectionPanel.getTextClientWindow().resetReadyState();
    }

    public void setConnectButtonText(String text) {
        connectionPanel.setConnectButtonText(text);
    }

    public void setGameFieldDisabled(boolean disabled) {
        connectionPanel.disableGameField(disabled);
    }

    public void startEnergyLinkSync() {
        EnergyLinkListener link = energyLinkListener;
        if (link != null) {
            link.sync();
        }
    }

    private void disconnectFromServer() {
        // Snapshot the session before playback is torn down so reconnecting to
        // the same slot can restore it.
        sessionRestore.saveSnapshot();

        // DISCONNECT
        client.close();
        connectionPanel.setStatus("Disconnected");
        connectionPanel.setConnectButtonText("Connect"); // toggle button text

        // Re-enable game field
        connectionPanel.disableGameField(false);
        connectionPanel.setGameFieldTooltip(null);

        // stop playback
        playback.stopCurrentSong();
        playerPanel.clearPlaybackState();

        // stop any active playback boost and clear energy state
        playback.reset();
        energyLinkListener = null;
        playerPanel.setEnergyLabel("Energy: --");

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
        if (!saveConnectionSettings(host, port, slot, password, gameName)) {
            LOGGER.warn("Failed to save connection settings; changes will not persist across restarts");
        }
        APClient.saveGameNameStatic(gameName);

        if (client != null) {
            client.close();
        }
        client = new APClient(host, port, slot, password);

        stateManager.resetGameState();
        connectionPanel.getTextClientWindow().clearNameGroups();
        client.setGameName(gameName);

        gameFolder.set(getConfigDir());
        checkIfGameFolderExists(gameFolder.get(), LOGGER);

        // ✅ reload slot options after game changes
        SlotDataHelper.loadSlotOptions(gameFolder.get());

        ensureGameDefaults(gameFolder.get());
        reloadGameLibrary();
        // After the reload bumped the load generation, so the pending restore
        // tracks the library load this connect kicked off.
        sessionRestore.arm(gameName, slot);

        client.setOnErrorCallback(ex -> {
            connectionPanel.setStatus("Connection failed");
            showError("Connection Failed",
                    "Failed to connect to Archipelago server",
                    "Reason: " + ex.getMessage());

            // Reset button and fields so user can try again
            connectionPanel.setConnectButtonText("Connect");
            connectionPanel.disableGameField(false);
        });

        client.setOnReconnectStatusCallback(status ->
                connectionPanel.setStatus(status));
        client.setOnReconnectFailedCallback(() -> {
            LOGGER.warn("Auto-reconnect failed after {} attempts", APClient.MAX_RECONNECT_ATTEMPTS_PUBLIC);
            connectionPanel.setStatus("Connection lost");
            connectionPanel.setConnectButtonText("Connect");
            connectionPanel.disableGameField(false);
            connectionPanel.setGameFieldTooltip(null);
        });

        try {
            client.getEventManager().registerListener(new ConnectionListener(connectionPanel.getStatusLabel(),
                client, this));
            itemListener = new ItemListener(this);
            client.getEventManager().registerListener(itemListener);
            client.getEventManager().registerListener(new PrintJsonListener(client,
                    connectionPanel.getTextClientWindow()::appendMessage));
            APClient source = client;
            client.getEventManager().registerListener(new NameGroupsListener(
                    result -> Platform.runLater(() ->
                            connectionPanel.getTextClientWindow()
                                    .onNameGroupsRetrieved(source, result))));
            energyLinkListener = new EnergyLinkListener(client, value -> Platform.runLater(() ->
                    playerPanel.setEnergyLabel("Energy: " + EnergyLinkListener.formatEnergy(value))));
            client.getEventManager().registerListener(energyLinkListener);
            client.getEventManager().registerListener(new DeathLinkListener(this));
            applyDeathLinkEnabled(loadDeathLink());
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

    /**
     * Re-arms the pending-restore debounce; called on every item event.
     * Must be called on the FX thread.
     */
    public void scheduleSessionRestoreApply() {
        sessionRestore.scheduleApply();
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
            PlaybackEngine engine = playback.getEngine();
            Song currentSong = playback.getCurrentSong();
            // If paused, resume. If nothing playing but queue has items, start next.
            if (engine != null && engine.isPaused()) {
                engine.resume();
                if (currentSong != null) {
                    playerPanel.setCurrentSongLabel("Currently Playing: " + currentSong.getTitle());
                }
                return;
            }

            if (currentSong != null
                && (engine == null || engine.getStatus() != MediaPlayer.Status.PLAYING)) {
                LOGGER.info("Current song ({})'s file path: {}", currentSong.getTitle(), currentSong.getFilePath());
                // start current (if file exists)
                if (currentSong.getFilePath() != null) {
                    playback.playSong(currentSong);
                } else {
                    showError("File Not Found", "Cannot play song", "File not found for: " + currentSong.getTitle());
                }
            } else if ((engine == null || engine.getStatus() != MediaPlayer.Status.PLAYING)
                && !queueManager.isEmpty()) {
                Song next = queueManager.poll();
                updateQueueDisplay();
                if (next != null) playback.playSong(next);
            }
        });

        panel.getPauseButton().setOnAction(_ -> {
            PlaybackEngine engine = playback.getEngine();
            Song currentSong = playback.getCurrentSong();
            if (engine != null) {
                MediaPlayer.Status status = engine.getStatus();
                if (status == MediaPlayer.Status.PLAYING) {
                    engine.pause();
                    if (currentSong != null) playerPanel.setCurrentSongLabel("Paused: " + currentSong.getTitle());
                } else if (status == MediaPlayer.Status.PAUSED) {
                    engine.resume();
                    if (currentSong != null) {
                        playerPanel.setCurrentSongLabel("Currently Playing: " + currentSong.getTitle());
                    }
                }
            }
        });

        // Repeat button cycles: Off → Queue → Song → Album → Off
        panel.getRepeatButton().setOnAction(_ -> {
            QueueManager.RepeatMode nextMode = queueManager.getRepeatMode().next();
            queueManager.setRepeatMode(nextMode);
            panel.getRepeatButton().setText(nextMode.label());
        });

        // Previous track button
        panel.getPreviousButton().setOnAction(_ -> playback.playPreviousTrack());

        // Next track button
        panel.getNextButton().setOnAction(_ -> playback.playNextInQueue());

        // EnergyLink speed boost: spend energy for a timed playback speed-up
        // single click = 1.5x, double click = 2x
        panel.getBoostButton().setOnAction(_ -> playback.onBoostClick());

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

                List<Song> resolved = new ArrayList<>();
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
            PlaybackEngine engine = playback.getEngine();
            if (engine != null) {
                engine.setVolume(newVal.doubleValue() / 100.0);
            }
            publishRemoteState();
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
                playback.playNextInQueue();
                event.consume();
            }
            case V -> {
                enterVolumeAdjustMode();
                event.consume();
            }
            default -> { /* ignore other keys */ }
        }
    }

    @Override
    public void togglePlayPause() {
        PlaybackEngine engine = playback.getEngine();
        Song currentSong = playback.getCurrentSong();
        if (engine != null) {
            MediaPlayer.Status status = engine.getStatus();
            if (status == MediaPlayer.Status.PLAYING) {
                engine.pause();
                if (currentSong != null) playerPanel.setCurrentSongLabel("Paused: " + currentSong.getTitle());
            } else if (status == MediaPlayer.Status.PAUSED) {
                engine.resume();
                if (currentSong != null) {
                    playerPanel.setCurrentSongLabel("Currently Playing: " + currentSong.getTitle());
                }
            }
        } else if (queueManager != null && !queueManager.isEmpty()) {
            Song next = queueManager.poll();
            updateQueueDisplay();
            if (next != null) playback.playSong(next);
        }
        publishRemoteState();
    }

    private void seekRelative(int seconds) {
        PlaybackEngine engine = playback.getEngine();
        if (engine == null || engine.getStatus() == MediaPlayer.Status.STOPPED) return;
        Duration current = engine.getCurrentTime();
        Duration total = engine.getTotalDuration();
        if (total == null) return;
        Duration newTime = current.add(Duration.seconds(seconds));
        if (newTime.greaterThan(total)) newTime = total;
        if (newTime.lessThan(Duration.ZERO)) newTime = Duration.ZERO;
        engine.seek(newTime);
    }

    private void enterVolumeAdjustMode() {
        volumeAdjustMode = true;
        volumeInput.setLength(0);
        connectionPanel.setStatus("Volume: " + (int) playerPanel.getVolumeSlider().getValue()
                + "% (arrows/numbers, Enter=set, Esc=cancel)");
    }

    private void exitVolumeAdjustMode() {
        volumeAdjustMode = false;
        volumeInput.setLength(0);
        connectionPanel.setStatus(offlineMode ? "Offline Mode"
                : (client != null && client.isConnected() ? "Connected!" : "Not connected"));
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

    @Override
    public String lockedSongMessage(Song song, Album album) {
        if (album != null && !unlockManager.isAlbumUnlocked(album.getName())) {
            return song.getTitle() + " requires album " + album.getName() + " to be unlocked!";
        }
        return song.getTitle() + " is not unlocked yet!";
    }

    @Override
    public Album albumForSong(Song song) {
        return library == null ? null : library.getAlbumForSong(song);
    }

    @Override
    public boolean canPlay(Song song, Album album) {
        return unlockManager.canPlay(song, album);
    }

    @Override
    public boolean canQueue(Song song, Album album) {
        return unlockManager.canQueue(song, album);
    }

    @Override
    public boolean libraryReady() {
        return library != null;
    }

    @Override
    public List<Album> albums() {
        return albums;
    }

    @Override
    public boolean connected() {
        return client != null && client.isConnected();
    }

    @Override
    public boolean sessionRestoreEnabled() {
        return settingsWindow != null && settingsWindow.getSessionRestoreCheck().isSelected();
    }

    @Override
    public String gameName() {
        return connectionPanel.getGameName();
    }

    @Override
    public String slot() {
        return connectionPanel.getSlot();
    }

    @Override
    public int loadGeneration() {
        return loadGeneration.get();
    }

    @Override
    public QueueManager queueManager() {
        return queueManager;
    }

    @Override
    public double volume() {
        return playerPanel.getVolumeSlider().getValue();
    }

    @Override
    public void setVolume(double percent) {
        playerPanel.getVolumeSlider().setValue(Math.max(0, Math.min(100, percent)));
    }

    @Override
    public void onSongFinished(Song song, Album album) {
        if (client != null && client.isConnected()) {
            client.sendCheck(song.getLocation());
            playback.creditSongEnergy();
        }
        // Connected or not, record the play; GoalManager routes it to
        // the server or the local play_history.json accordingly.
        if (goalManager != null && album != null) {
            goalManager.markPlayed(song.getTitle(), album.getName(),
                    client != null && client.isConnected() ? client : null);
        }
    }

    @Override
    public EnergyLinkListener energyLink() {
        return energyLinkListener;
    }

    @Override
    public boolean clientConnected() {
        return client != null && client.isConnected();
    }

    @Override
    public void appendOutput(String message) {
        connectionPanel.getTextClientWindow().appendOutput(message);
    }

    private void sendBonusCheck(String location) {
        if (client != null && client.isConnected()) {
            if (client.sendCheck(location)) {
                bonusLocations.remove(location);
                SentBonusStore.markSent(connectionPanel.getSlot(), location);
                Platform.runLater(this::refreshTree);
                LOGGER.info("Sent bonus check: {}", location);
            } else {
                LOGGER.info("Cannot send bonus check '{}': location does not exist on this server", location);
                showError("Bonus Check Failed", "Cannot send bonus check",
                        "The server does not recognize the location \"" + location + "\". "
                        + "This bonus location is not defined for your game on this server.");
            }
        }
    }

    private void handleTreeSelection(TreeItem<String> newSel) {
        if (newSel == null) return;
        if (isUpdatingSelection) return;

        // Only leaf nodes are actionable; ignore album and root nodes
        if (!newSel.isLeaf()) return;

        String value = newSel.getValue();

        // Bonus locations: send check if selected leaf is under the "Bonus" branch
        TreeItem<String> parent = newSel.getParent();
        if (parent != null && "Bonus".equals(parent.getValue())) {
            sendBonusCheck(value);
            return;
        }
        if (parent == null) return;

        // Resolve the song via its album first so duplicate titles across albums
        // stay distinguishable; fall back to the title-wide lookup as a safety net.
        Album album = library.getAlbumByName(parent.getValue());
        if (album == null) return;
        Song song = album.getSong(value);
        if (song == null) {
            song = library.getSongByTitle(value);
        }

        if (song == null) return;

        // Don't re-queue the currently playing song (e.g. from highlightCurrentSong)
        if (song == playback.getCurrentSong()) return;

        if (!unlockManager.canPlay(song, album)) {
            showError("Locked Song", "Cannot play song", lockedSongMessage(song, album));
            return;
        }

        // Add to queue, song has passed checks
        queueManager.add(song);
        updateQueueDisplay();

        // If nothing is playing, start immediately
        if (!playback.isPlaying()) {
            Song next = queueManager.poll();
            updateQueueDisplay();
            if (next != null) {
                playback.playSong(next);
            }
        }
    }

    private void initUIComponents() {
        treeView = new TreeView<>();
        searchField = new TextField();
        searchField.setPromptText("Search songs or albums...");
        searchField.setText("");
    }

    public AlbumLibrary getLibrary() {
        return library;
    }

    public int getLoadGeneration() {
        return loadGeneration.get();
    }

    public GoalManager getGoalManager() {
        return goalManager;
    }

    public void setPendingPlayedSongs(Set<String> songs, String slot, int generation) {
        this.pendingPlayedSongs = songs;
        this.pendingPlayedSlot = slot;
        this.pendingPlayedSnapshotReceived = (slot != null);
        this.pendingPlayedGeneration = generation;
    }
}