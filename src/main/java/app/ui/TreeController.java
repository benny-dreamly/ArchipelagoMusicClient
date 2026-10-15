/*
 * SPDX-License-Identifier: MPL-2.0
 */
package app.ui;

import app.AppContext;
import app.logic.UnlockManager;
import app.player.Album;
import app.player.PlaybackController;
import app.player.Song;
import app.util.SentBonusStore;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static app.util.Dialogs.showError;

/**
 * Owns the album {@link TreeView}: builds its items from the library, wires the
 * selection and search listeners, the right-click context menu (queue actions)
 * and the "highlight the current song" behaviour. Extracted from
 * {@code MusicAppDemo}; reads shared state through {@link AppContext} and drives
 * playback through {@link PlaybackController}.
 */
public class TreeController {

    private static final Logger LOGGER = LoggerFactory.getLogger(TreeController.class);

    private final AppContext context;
    private final PlaybackController playback;
    private final TreeView<String> treeView;
    private final TextField searchField;
    private final ContextMenu contextMenu;

    private boolean isUpdatingSelection = false;
    private boolean suppressSelection = false;

    public TreeController(AppContext context, PlaybackController playback, TreeView<String> treeView,
            TextField searchField, ContextMenu contextMenu) {
        this.context = context;
        this.playback = playback;
        this.treeView = treeView;
        this.searchField = searchField;
        this.contextMenu = contextMenu;
    }

    public void bind() {
        // When a tree item (song) is selected, add to queue
        treeView.getSelectionModel().selectedItemProperty().addListener((_, _, newSel) -> {
            if (suppressSelection) {
                suppressSelection = false;
                return;
            }
            handleTreeSelection(newSel);
        });

        searchField.textProperty().addListener((_, _, _) -> refreshTree());

        // Suppress selection handling on right-click so context menu doesn't double-queue
        treeView.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
            if (event.getButton() == MouseButton.SECONDARY) {
                suppressSelection = true;
            }
        });

        setupAlbumContextMenu();
    }

    public void refreshTree() {
        List<Album> albums = context.albums();

        // Custom album order
        List<String> albumOrder = context.albumOrder();

        // Sort albums according to albumOrder
        albums.sort(Comparator.comparingInt(a -> {
            int idx = albumOrder.indexOf(a.getName());
            return idx >= 0 ? idx : Integer.MAX_VALUE; // albums not in the list go last
        }));

        TreeItem<String> rootItem = new TreeItem<>("Albums");
        rootItem.setExpanded(true);

        String query = searchField.getText().trim().toLowerCase(Locale.ROOT);
        UnlockManager unlockManager = context.unlockManager();

        for (Album album : context.albums()) {
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

        List<String> bonusLocations = context.bonusLocations();
        if (!bonusLocations.isEmpty()) {
            boolean bonusMatches = "bonus".contains(query);
            Set<String> sentForSlot = Set.copyOf(SentBonusStore.load(context.slot()));
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

    public void installTreeCellFactory() {
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
                        Song song = context.library().getSongByTitle(item);
                        if (song != null && context.unlockManager().isSongUnlocked(song.getTitle())) {
                            getStyleClass().add("song-unlocked");
                        } else {
                            getStyleClass().remove("song-unlocked");
                        }
                        setText(isPlayed(treeItem, song) ? "\u2713 " + item : item);
                    } else {
                        // Album nodes
                        getStyleClass().remove("song-unlocked");
                        if (item.equals("Albums")) {
                            // Root "Albums" node — show overall world completion
                            UnlockManager.AlbumProgress world =
                                    context.unlockManager().getWorldProgress(context.library().getAlbums());
                            int worldPlayed = context.goalManager() != null
                                    ? context.goalManager().getWorldPlayedCount(context.library().getAlbums())
                                    : world.unlocked();
                            setText(item + " (" + worldPlayed + "/" + world.total() + ")");
                            getStyleClass().remove("album-unlocked");
                        } else {
                            // Regular album node
                            Album album = context.library().getAlbumByName(item);
                            if (album != null) {
                                UnlockManager.AlbumProgress progress =
                                        context.unlockManager().getAlbumProgress(album);
                                int albumPlayed = context.goalManager() != null
                                        ? context.goalManager().getPlayedCount(album)
                                        : progress.unlocked();
                                setText(item + " (" + albumPlayed + "/" + progress.total() + ")");
                            } else {
                                setText(item);
                            }
                            if (album != null && context.unlockManager().isAlbumUnlocked(album.getName())) {
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

    /**
     * Whether {@code songItem} has already been listened to in its own album.
     * Uses the tree parent for the album so duplicate titles across albums stay
     * distinct; bonus locations (whose parent is the "Bonus" branch) never count.
     */
    private boolean isPlayed(TreeItem<String> songItem, Song song) {
        if (song == null || context.goalManager() == null) return false;
        TreeItem<String> parent = songItem.getParent();
        if (parent == null || "Bonus".equals(parent.getValue())) return false;
        return context.goalManager().isSongPlayed(song.getTitle(), parent.getValue());
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
                    Album album = context.library().getAlbumByName(item.getParent().getValue());
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
                if (context.goalManager() != null) {
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
        Album album = context.library().getAlbumByName(albumName);
        if (album == null) return;

        UnlockManager unlockManager = context.unlockManager();
        List<Song> queueable = album.getQueueableSongs(unlockManager.getEnabledSets(),
                unlockManager.getUnlockedSongs(), unlockManager.getUnlockedAlbums());
        if (queueable.isEmpty()) {
            LOGGER.info("No queueable songs in album '{}'", albumName);
            return;
        }

        context.queueManager().addAll(queueable);
        LOGGER.info("Queued {} songs from album '{}'", queueable.size(), albumName);
        context.updateQueueDisplay();

        // If nothing is playing, start the first queued song
        if (!playback.isPlaying() && !context.queueManager().isEmpty()) {
            Song next = context.queueManager().poll();
            context.updateQueueDisplay();
            if (next != null) {
                playback.playSong(next);
            }
        }
    }

    private void queueUnplayedAlbum(String albumName) {
        Album album = context.library().getAlbumByName(albumName);
        if (album == null || context.goalManager() == null) return;

        UnlockManager unlockManager = context.unlockManager();
        List<Song> queueable = album.getQueueableSongs(unlockManager.getEnabledSets(),
                unlockManager.getUnlockedSongs(), unlockManager.getUnlockedAlbums());
        List<Song> unplayed = queueable.stream()
                .filter(song -> !context.goalManager().isSongPlayed(song.getTitle(), albumName))
                .toList();
        if (unplayed.isEmpty()) {
            LOGGER.info("No unplayed songs in album '{}'", albumName);
            return;
        }

        context.queueManager().addAll(unplayed);
        LOGGER.info("Queued {} unplayed songs from album '{}'", unplayed.size(), albumName);
        context.updateQueueDisplay();

        // If nothing is playing, start the first queued song
        if (!playback.isPlaying() && !context.queueManager().isEmpty()) {
            Song next = context.queueManager().poll();
            context.updateQueueDisplay();
            if (next != null) {
                playback.playSong(next);
            }
        }
    }

    private void queueSongNext(String songTitle, Album album) {
        Song song = album != null ? album.getSong(songTitle) : null;
        if (song == null) {
            song = context.library().getSongByTitle(songTitle);
        }
        if (song == null) return;

        if (!context.unlockManager().canPlay(song, album)) return;

        // insert at front
        context.queueManager().addFirst(song);

        context.updateQueueDisplay();

        // If nothing is playing, start the first queued song
        if (playback.getEngine() == null && !context.queueManager().isEmpty()) {
            Song next = context.queueManager().poll();
            context.updateQueueDisplay();
            if (next != null) {
                playback.playSong(next);
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
            context.sendBonusCheck(value);
            return;
        }
        if (parent == null) return;

        // Resolve the song via its album first so duplicate titles across albums
        // stay distinguishable; fall back to the title-wide lookup as a safety net.
        Album album = context.library().getAlbumByName(parent.getValue());
        if (album == null) return;
        Song song = album.getSong(value);
        if (song == null) {
            song = context.library().getSongByTitle(value);
        }
        if (song == null) return;

        // Don't re-queue the currently playing song (e.g. from highlightCurrentSong)
        if (song == playback.getCurrentSong()) return;

        if (!context.canPlay(song, album)) {
            showError("Locked Song", "Cannot play song", context.lockedSongMessage(song, album));
            return;
        }

        context.queueManager().add(song);
        context.updateQueueDisplay();

        // If nothing is playing, start the first queued song
        if (!playback.isPlaying() && !context.queueManager().isEmpty()) {
            Song next = context.queueManager().poll();
            context.updateQueueDisplay();
            if (next != null) {
                playback.playSong(next);
            }
        }
    }

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
}
