/*
 * SPDX-License-Identifier: MPL-2.0
 */
package app.player.ui;

import app.AppContext;
import app.logic.QueueManager;
import app.player.Album;
import app.player.PlaybackController;
import app.player.PlaybackEngine;
import app.player.Song;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Slider;
import javafx.scene.control.TextInputControl;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.media.MediaPlayer;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static app.util.ConfigPaths.getConfigDir;
import static app.util.Dialogs.showError;

/**
 * Wires the {@link PlayerPanel} buttons, the queue drag-and-drop reordering and
 * the keyboard shortcuts to playback and the queue. Extracted from
 * {@code MusicAppDemo}; shared application state comes from {@link AppContext}
 * and playback flow from {@link PlaybackController}.
 */
public class PlayerControlsController {

    private static final Logger LOGGER = LoggerFactory.getLogger(PlayerControlsController.class);

    private final AppContext context;
    private final PlaybackController playback;
    private final PlayerPanel playerPanel;
    private final ConnectionPanel connectionPanel;

    private boolean volumeAdjustMode = false;
    private final StringBuilder volumeInput = new StringBuilder();
    private final ChangeListener<Boolean> seekListener;

    public PlayerControlsController(AppContext context, PlaybackController playback,
            PlayerPanel playerPanel, ConnectionPanel connectionPanel) {
        this.context = context;
        this.playback = playback;
        this.playerPanel = playerPanel;
        this.connectionPanel = connectionPanel;
        this.seekListener = (_, _, isChanging) -> {
            PlaybackEngine engine = playback.getEngine();
            if (!isChanging && engine != null && engine.isLoaded()) {
                Duration total = engine.getTotalDuration();
                if (total != null) {
                    engine.seek(total.multiply(playerPanel.getProgressSlider().getValue()));
                }
            }
        };
    }

    public void bindPanel() {
        PlayerPanel panel = playerPanel;

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
                    showError("File Not Found", "Cannot play song", "File not found: " + currentSong.getTitle());
                }
            } else if ((engine == null || engine.getStatus() != MediaPlayer.Status.PLAYING)
                && !context.queueManager().isEmpty()) {
                Song next = context.queueManager().poll();
                context.updateQueueDisplay();
                if (next != null) playback.playSong(next);
            }
        });

        playerPanel.getPauseButton().setOnAction(_ -> {
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

        // Repeat button cycles: Off -> Queue -> Song → Album → Off
        playerPanel.getRepeatButton().setOnAction(_ -> {
            QueueManager.RepeatMode nextMode = context.queueManager().getRepeatMode().next();
            context.queueManager().setRepeatMode(nextMode);
            playerPanel.getRepeatButton().setText(nextMode.label());
        });

        // Previous track button
        playerPanel.getPreviousButton().setOnAction(_ -> playback.playPreviousTrack());

        // Next track button
        playerPanel.getNextButton().setOnAction(_ -> playback.playNextInQueue());

        // EnergyLink speed boost: spend energy for a timed playback speed-up
        // single click = 1.5x, double click = 2x
        playerPanel.getBoostButton().setOnAction(_ -> playback.onBoostClick());

        // Remove selected from the queue (both ListView and underlying queue)
        playerPanel.getRemoveSelectedBtn().setOnAction(_ -> {
            Song sel = playerPanel.getQueueListView().getSelectionModel().getSelectedItem();
            if (sel != null) {
                context.queueManager().remove(sel);
                context.updateQueueDisplay();
            }
        });

        // Clear queue
        playerPanel.getClearQueueBtn().setOnAction(_ -> {
            context.queueManager().clear();
            context.updateQueueDisplay();
        });

        // Shuffle queue
        playerPanel.getShuffleQueueBtn().setOnAction(_ -> {
            context.queueManager().shuffle();
            context.updateQueueDisplay();
        });

        // Save queue
        playerPanel.getSaveQueueBtn().setOnAction(_ -> {
            File queueFile = new File(getConfigDir(), "queue.json");
            List<Map<String, String>> entries = context.queueManager().toEntries();
            try (Writer writer = new FileWriter(queueFile, StandardCharsets.UTF_8)) {
                new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(entries, writer);
                LOGGER.info("Queue saved to {}", queueFile.getAbsolutePath());
            } catch (IOException e) {
                LOGGER.error("Failed to save queue", e);
            }
        });

        // Load queue
        playerPanel.getLoadQueueBtn().setOnAction(_ -> {
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
                    for (Album album : context.albums()) {
                        for (Song song : album.getSongs()) {
                            if (song.getTitle().equals(title) && song.getType().equals(type)) {
                                if (context.canQueue(song, album)) {
                                    resolved.add(song);
                                }
                            }
                        }
                    }
                }

                context.queueManager().replaceAll(resolved);
                context.updateQueueDisplay();
                LOGGER.info("Queue loaded from {} ({} songs)",
                        queueFile.getAbsolutePath(), context.queueManager().size());
            } catch (Exception e) {
                LOGGER.error("Failed to load queue from {}", queueFile.getAbsolutePath(), e);
            }
        });

        // Volume slider updates live during playback
        playerPanel.getVolumeSlider().valueProperty().addListener((_, _, newVal) -> {
            PlaybackEngine engine = playback.getEngine();
            if (engine != null) {
                engine.setVolume(newVal.doubleValue() / 100.0);
            }
            context.publishRemoteState();
        });

        // Drag-and-drop reordering for queue
        ListView<Song> queueList = playerPanel.getQueueListView();
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

            context.queueManager().move(fromIndex, toIndex);
            context.updateQueueDisplay();
            event.setDropCompleted(true);
            event.consume();
        });

        playerPanel.bindSeekCheckBox(seekListener);
    }

    public void bindKeyboard(Scene scene) {
        scene.addEventFilter(KeyEvent.KEY_PRESSED, this::handleKeyPress);
    }

    private void handleKeyPress(KeyEvent event) {
        if (event.getTarget() instanceof TextInputControl) return;

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
        } else if (context.queueManager() != null && !context.queueManager().isEmpty()) {
            Song next = context.queueManager().poll();
            context.updateQueueDisplay();
            if (next != null) playback.playSong(next);
        }
        context.publishRemoteState();
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

    private void adjustVolume(double delta) {
        Slider slider = playerPanel.getVolumeSlider();
        slider.setValue(Math.min(100, Math.max(0, slider.getValue() + delta)));
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
        connectionPanel.setStatus(context.offlineMode() ? "Offline Mode"
                : (context.connected() ? "Connected!" : "Not connected"));
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
                adjustVolume(10);
                volumeInput.setLength(0);
            }
            case DOWN -> {
                adjustVolume(-10);
                volumeInput.setLength(0);
            }
            case LEFT -> {
                adjustVolume(-1);
                volumeInput.setLength(0);
            }
            case RIGHT -> {
                adjustVolume(1);
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

    private int getQueueIndexAtY(ListView<Song> listView, double y) {
        for (Node node : listView.lookupAll(".list-cell")) {
            if (node instanceof ListCell<?> cell) {
                if (cell.getItem() == null) continue;
                if (cell.getListView() != listView) continue;
                Bounds cellBounds = listView.sceneToLocal(
                    node.localToScene(node.getBoundsInLocal()));
                if (y >= cellBounds.getMinY() && y < cellBounds.getMaxY()) {
                    double midpoint = cellBounds.getMinY() + cellBounds.getHeight() / 2.0;
                    return y < midpoint ? cell.getIndex() : cell.getIndex() + 1;
                }
            }
        }
        return listView.getItems().size();
    }
}
