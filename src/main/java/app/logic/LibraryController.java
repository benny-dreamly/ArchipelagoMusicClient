/*
 * SPDX-License-Identifier: MPL-2.0
 */
package app.logic;

import app.AppContext;
import app.player.ui.SettingsWindow;
import app.player.json.MusicLibraryConverter;
import app.player.json.MusicLibraryJSON;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Desktop;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

import static app.util.AlbumUtils.generateDefaultAlbumFolders;
import static app.util.ConfigManager.loadBrowseFolder;
import static app.util.ConfigPaths.getConfigDir;
import static app.util.Dialogs.showError;

/**
 * Library maintenance actions that aren't tied to the loading state machine:
 * converting a legacy {@code locations.json}, opening the config folder,
 * remembering the last browsed folder and seeding a brand-new game folder with
 * defaults. Extracted from {@code MusicAppDemo}.
 */
public class LibraryController {

    private static final Logger LOGGER = LoggerFactory.getLogger(LibraryController.class);

    private final AppContext context;
    private final SettingsWindow settingsWindow;

    public LibraryController(AppContext context, SettingsWindow settingsWindow) {
        this.context = context;
        this.settingsWindow = settingsWindow;
    }

    public void convertLocationsToLibrary() {
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
        javafx.concurrent.Task<MusicLibraryJSON> task = new javafx.concurrent.Task<>() {
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
                context.appendOutput("Converted locations.json -> music_library.json ("
                        + library.albums.size() + " albums, " + songs + " songs)");
                if (savedBrowseFolder() == null) {
                    context.reloadGameLibrary();
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

    public void openConfigFolder() {
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

    public File savedBrowseFolder() {
        String path = loadBrowseFolder();
        if (path == null) {
            return null;
        }
        File folder = new File(path);
        return folder.isDirectory() ? folder : null;
    }

    public void assignFilesToSongs() {
        app.logic.SongFileMatcher.assignFilesToSongs(context.albums());
    }

    public void ensureGameDefaults(File gameFolder) {
        if (!gameFolder.exists()) {
            //noinspection ResultOfMethodCallIgnored
            gameFolder.mkdirs();
        }

        File albumOrderFile = new File(gameFolder, "albumOrder.json");
        if (!albumOrderFile.exists()) {
            context.albumOrder(); // generates the default if missing
        }

        File foldersFile = new File(gameFolder, "albumFolders.json");
        if (!foldersFile.exists()) {
            generateDefaultAlbumFolders(context.albums()); // creates default
        }

        File legacyLocations = new File(gameFolder, "locations.json");
        File musicLibraryFile = new File(gameFolder, "music_library.json");
        // Seed the bundled sample music_library.json for brand-new game folders.
        // Folders with an existing library in either format keep their own data.
        if (!musicLibraryFile.exists() && !legacyLocations.exists()) {
            try (InputStream in = LibraryController.class.getResourceAsStream("/music_library.json");
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
}
