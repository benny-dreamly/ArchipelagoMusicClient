package app.archipelago;

import app.MusicAppDemo;
import app.logic.GoalManager;
import com.google.gson.JsonElement;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import io.github.archipelagomw.events.ConnectionResultEvent;
import io.github.archipelagomw.events.ArchipelagoEventListener;
import io.github.archipelagomw.events.RetrievedEvent;
import io.github.archipelagomw.network.ConnectionResult;
import javafx.application.Platform;
import javafx.scene.control.Label;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static app.util.Dialogs.showError;


@SuppressWarnings("ClassCanBeRecord")
public class ConnectionListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConnectionListener.class);
    private static final String PLAYED_SONGS_KEY = "musictools/played_songs_";

    private final Label statusLabel;

    private record PendingRequest(int slot, int generation) {}

    private final APClient client;
    private final MusicAppDemo app;

    private volatile PendingRequest pendingRequest;

    public ConnectionListener(Label statusLabel, APClient client, MusicAppDemo app) {
        this.statusLabel = statusLabel;
        this.client = client;
        this.app = app;
    }

    @SuppressWarnings("unused")
    @ArchipelagoEventListener
    public void onConnectionResult(ConnectionResultEvent event) {
        Platform.runLater(() -> {
            ConnectionResult result = event.getResult();

            if (result == io.github.archipelagomw.network.ConnectionResult.Success) {
                JsonElement slotData = event.getSlotData(JsonElement.class);
                client.setSlotData(slotData);
                statusLabel.setText("Connected!");

                app.applySlotData();

                // Load played songs from server data storage
                pendingRequest = new PendingRequest(client.getSlot(), app.getLoadGeneration());
                String key = PLAYED_SONGS_KEY + pendingRequest.slot();
                LOGGER.info("Requesting played songs from data storage: key={}", key);
                client.dataStorageGet(List.of(key));
            } else {
                // Prevent duplicate error alerts if a socket error already happened
                if (statusLabel.getText().equals("Connection failed")) return;

                statusLabel.setText("Not connected");
                showError(
                        "Connection Failed",
                        "Failed to connect to Archipelago server",
                        "Reason: " + result.name()
                );

                app.setConnectButtonText("Connect");
                app.setGameFieldDisabled(false);
            }
        });
    }

    @SuppressWarnings("unused")
    @ArchipelagoEventListener
    public void onRetrieved(RetrievedEvent event) {
        PendingRequest req = pendingRequest;
        if (req == null) return;

        int slot = req.slot();
        int generation = req.generation();
        String key = PLAYED_SONGS_KEY + slot;
        if (!event.containsKey(key)) {
            LOGGER.info("No played songs found in data storage for key={}", key);
            Platform.runLater(() -> {
                if (generation != app.getLoadGeneration()) return;
                GoalManager goalManager = app.getGoalManager();
                if (goalManager != null) {
                    goalManager.loadFromServer(Collections.emptySet(), client);
                    LOGGER.info("Loaded empty played-song state for slot {}", slot);
                }
                app.setPendingPlayedSongs(
                        Collections.emptySet(),
                        String.valueOf(slot),
                        generation
                );
            });
            return;
        }

        List<String> playedList;
        try {
            playedList = event.getValueAsObject(key, new TypeToken<List<String>>(){}.getType());
        } catch (JsonSyntaxException e) {
            LOGGER.error("Failed to deserialize played songs from data storage for key={}; loading empty state so markPlayed can persist", key, e);
            Platform.runLater(() -> {
                if (generation != app.getLoadGeneration()) return;
                GoalManager goalManager = app.getGoalManager();
                if (goalManager != null) {
                    goalManager.loadFromServer(Collections.emptySet(), client);
                } else {
                    app.setPendingPlayedSongs(Collections.emptySet(), String.valueOf(slot), generation);
                }
            });
            return;
        }
        if (playedList == null) playedList = Collections.emptyList();
        playedList = playedList.stream().filter(java.util.Objects::nonNull).toList();

        Set<String> playedSongs = Set.copyOf(playedList);
        LOGGER.info("Retrieved {} played songs from data storage", playedSongs.size());

        Platform.runLater(() -> {
            if (generation != app.getLoadGeneration()) return;
            GoalManager goalManager = app.getGoalManager();
            if (goalManager != null) {
                goalManager.loadFromServer(playedSongs, client);
            } else {
                // GoalManager not yet initialized — store for deferred restoration
                app.setPendingPlayedSongs(playedSongs, String.valueOf(slot), generation);
                LOGGER.info("Stored {} played songs for deferred GoalManager restoration (slot={})", playedSongs.size(), slot);
            }
        });
    }
}