/*
 * SPDX-License-Identifier: MPL-2.0
 */
package app.archipelago;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import io.github.archipelagomw.Client;
import io.github.archipelagomw.flags.ItemsHandling;
import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.Reader;
import java.io.Writer;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class APClient extends Client {

    private static final int MAX_RECONNECT_ATTEMPTS = 12;
    private static final long INITIAL_RECONNECT_DELAY_MS = 3000;
    private static final long MAX_RECONNECT_DELAY_MS = 30_000;
    public static final int MAX_RECONNECT_ATTEMPTS_PUBLIC = MAX_RECONNECT_ATTEMPTS;

    private final String address;
    private Consumer<Exception> onErrorCallback;
    private Consumer<String> onReconnectStatusCallback;
    private Runnable onReconnectFailedCallback;
    private String gameName;
    private JsonElement slotData;

    private final ScheduledExecutorService reconnectExecutor =
            Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "ap-reconnect");
                thread.setDaemon(true);
                return thread;
            });
    private final AtomicInteger reconnectAttempt = new AtomicInteger();
    private ScheduledFuture<?> pendingReconnect;
    private volatile boolean reconnectTaskScheduled;
    private volatile boolean hasConnected;
    private volatile boolean manualDisconnect;
    private volatile boolean gaveUp;

    public static final Logger LOGGER = LoggerFactory.getLogger(APClient.class);

    public APClient(String host, int port, String slot, String password) {
        super();
//        setGame("Manual_TaylorSwiftDiscography_bennydreamly");
        setPassword(password);
        setItemsHandlingFlags(ItemsHandling.SEND_ITEMS + ItemsHandling.SEND_OWN_ITEMS
                + ItemsHandling.SEND_STARTING_INVENTORY);
        this.address = host + ":" + port;
        setName(slot);

        this.gameName = loadSavedGameName();
        setGame(this.gameName);
    }

    public void setOnErrorCallback(Consumer<Exception> callback) {
        this.onErrorCallback = callback;
    }

    public void setOnReconnectStatusCallback(Consumer<String> callback) {
        this.onReconnectStatusCallback = callback;
    }

    public void setOnReconnectFailedCallback(Runnable callback) {
        this.onReconnectFailedCallback = callback;
    }


    public void connect() throws URISyntaxException {
        manualDisconnect = false;
        hasConnected = false;
        gaveUp = false;
        reconnectAttempt.set(0);
        cancelPendingReconnect();
        super.connect(this.address);
    }

    @Override
    public void disconnect() {
        manualDisconnect = true;
        reconnectAttempt.set(0);
        cancelPendingReconnect();
        // Always tear down so an in-progress connect attempt (isConnected() == false)
        // is also cancelled instead of silently coming up after a manual disconnect.
        super.disconnect();
    }

    @Override
    public void close() {
        disconnect();
        reconnectExecutor.shutdownNow();
    }

    @Override
    public void reconnect() {
        // The library schedules its own reconnect with a large backoff. We
        // neutralize that here and run our own bounded retry loop instead so
        // the app can surface status and give up within a sane window.
        LOGGER.info("Library-requested reconnect suppressed; using app-controlled loop");
    }

    @Override
    public void onError(Exception e) {
        if (manualDisconnect) {
            return;
        }
        if (!hasConnected) {
            if (onErrorCallback != null) {
                Platform.runLater(() -> onErrorCallback.accept(e));
            }
        } else {
            LOGGER.warn("Connection error during session: {}", e.getMessage());
            scheduleReconnect();
        }
    }

    @Override
    public void onClose(String message, int i) {
        if (manualDisconnect) {
            LOGGER.info("Manual disconnect: {}", message);
            return;
        }
        if (!hasConnected) {
            LOGGER.info("Connection closed before successful handshake: {}", message);
            return;
        }
        LOGGER.warn("Connection closed unexpectedly: {}", message);
        scheduleReconnect();
    }

    public boolean isReconnecting() {
        return reconnectAttempt.get() > 0;
    }

    public boolean isManualDisconnect() {
        return manualDisconnect;
    }

    public void continueReconnect() {
        scheduleReconnect();
    }

    public void markConnected() {
        this.hasConnected = true;
        this.gaveUp = false;
        reconnectAttempt.set(0);
        cancelPendingReconnect();
    }

    private void scheduleReconnect() {
        if (manualDisconnect || !hasConnected || gaveUp) {
            return;
        }
        synchronized (reconnectExecutor) {
            // Recheck inside the lock: a connect(), disconnect(), or give-up may
            // have raced with the fast-path check above before we acquired it.
            if (reconnectTaskScheduled || manualDisconnect || !hasConnected || gaveUp) {
                return;
            }
            int attempt = reconnectAttempt.incrementAndGet();
            if (attempt > MAX_RECONNECT_ATTEMPTS) {
                reconnectAttempt.set(0);
                fireReconnectFailed();
                return;
            }
            long delay = Math.min(MAX_RECONNECT_DELAY_MS,
                    INITIAL_RECONNECT_DELAY_MS * (1L << (attempt - 1)));
            if (onReconnectStatusCallback != null) {
                Platform.runLater(() -> onReconnectStatusCallback.accept(
                        "Connection lost - reconnecting (attempt " + attempt + "/"
                                + MAX_RECONNECT_ATTEMPTS + ")"));
            }
            reconnectTaskScheduled = true;
            pendingReconnect = reconnectExecutor.schedule(() -> {
                synchronized (reconnectExecutor) {
                    reconnectTaskScheduled = false;
                }
                // Guard against a stale task firing after a manual connect, manual
                // disconnect, or successful reconnect reset the attempt counter.
                if (manualDisconnect || reconnectAttempt.get() == 0) {
                    return;
                }
                attemptReconnect();
            }, delay, TimeUnit.MILLISECONDS);
        }
    }

    private void attemptReconnect() {
        LOGGER.info("Attempting to reconnect to {}", address);
        try {
            super.connect(this.address);
        } catch (URISyntaxException e) {
            LOGGER.error("Reconnect failed to parse address {}", address, e);
            scheduleReconnect();
        }
    }

    private void cancelPendingReconnect() {
        synchronized (reconnectExecutor) {
            reconnectTaskScheduled = false;
            if (pendingReconnect != null) {
                pendingReconnect.cancel(false);
                pendingReconnect = null;
            }
        }
    }

    private void fireReconnectFailed() {
        gaveUp = true;
        if (onReconnectFailedCallback != null) {
            Platform.runLater(onReconnectFailedCallback);
        }
    }

    public boolean sendCheck(String locationName) {
        Long locationID = getDataPackage().getGame(getGame()).locationNameToId.get(locationName);

        if (locationID != null) {
            checkLocation(locationID);
            return true;
        }
        LOGGER.warn("No location ID found for location: {}", locationName);
        return false;
    }

    // persistency helpers to save/load the game name

    public void setGameName(String name) {
        this.gameName = name;
        setGame(name);
        saveGameName(name);

        File gameDir = getGameDataFolder();
        if (!gameDir.exists()) {
            if (gameDir.mkdirs()) {
                LOGGER.info("Created new game folder: {}", gameDir.getAbsolutePath());
            }
        }
    }

    @SuppressWarnings("unused")
    public String getGameName() {
        return this.gameName;
    }

    private void saveGameName(String name) {
        File configFile = getGameConfigFile();
        try (Writer writer = new FileWriter(configFile, StandardCharsets.UTF_8)) {
            new GsonBuilder().setPrettyPrinting().create().toJson(name, writer);
        } catch (IOException e) {
            LOGGER.error("Failed to save game name to {}", configFile.getAbsolutePath(), e);
        }
    }

    private String loadSavedGameName() {
        File configFile = getGameConfigFile();
        if (configFile.exists()) {
            try (Reader reader = new FileReader(configFile, StandardCharsets.UTF_8)) {
                return new Gson().fromJson(reader, String.class);
            } catch (IOException e) {
                LOGGER.error("Failed to load saved game name from {}", configFile.getAbsolutePath(), e);
            }
        }
        // fallback default
        return "Manual_TaylorSwiftDiscography_bennydreamly";
    }

    private File getGameConfigFile() {
        String userHome = System.getProperty("user.home");
        File baseDir;

        String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            baseDir = new File(userHome, "AppData\\Roaming\\MusicAppDemo");
        } else if (os.contains("mac")) {
            baseDir = new File(userHome, "Library/Application Support/MusicAppDemo");
        } else {
            baseDir = new File(userHome, ".config/MusicAppDemo");
        }


        if (!baseDir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            baseDir.mkdirs();
        }

        return new File(baseDir, "currentGame.json"); // <-- removed config subfolder
    }

    private File getGameDataFolder() {
        if (gameName == null || gameName.isEmpty()) {
            gameName = loadSavedGameName();
        }

        File baseDir;
        String userHome = System.getProperty("user.home");
        String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);

        if (os.contains("win")) {
            baseDir = new File(userHome, "AppData\\Roaming\\MusicAppDemo");
        } else if (os.contains("mac")) {
            baseDir = new File(userHome, "Library/Application Support/MusicAppDemo");
        } else {
            baseDir = new File(userHome, ".config/MusicAppDemo");
        }

        return new File(baseDir, gameName);
    }

    public static String loadSavedGameNameStatic() {
        File configFile = getGameConfigFileStatic();
        if (configFile.exists()) {
            try (Reader reader = new FileReader(configFile, StandardCharsets.UTF_8)) {
                return new Gson().fromJson(reader, String.class);
            } catch (IOException e) {
                LOGGER.error("Failed to load saved game name from {}", configFile.getAbsolutePath(), e);
            }
        }
        return "Manual_TaylorSwiftDiscography_bennydreamly";
    }

    public static void saveGameNameStatic(String name) {
        File configFile = getGameConfigFileStatic();
        try (Writer writer = new FileWriter(configFile, StandardCharsets.UTF_8)) {
            new GsonBuilder().setPrettyPrinting().create().toJson(name, writer);
        } catch (IOException e) {
            LOGGER.error("Failed to save game name to {}", configFile.getAbsolutePath(), e);
        }
    }

    private static File getGameConfigFileStatic() {
        String userHome = System.getProperty("user.home");
        String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        File baseDir;

        if (os.contains("win")) {
            baseDir = new File(userHome, "AppData\\Roaming\\MusicAppDemo");
        } else if (os.contains("mac")) {
            baseDir = new File(userHome, "Library/Application Support/MusicAppDemo");
        } else {
            baseDir = new File(userHome, ".config/MusicAppDemo");
        }

        if (!baseDir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            baseDir.mkdirs();
        }

        return new File(baseDir, "currentGame.json"); // <-- removed config subfolder
    }


    public static File getGameDataFolderStatic() {
        String gameName = loadSavedGameNameStatic();
        String userHome = System.getProperty("user.home");
        String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        File baseDir;

        if (os.contains("win")) {
            baseDir = new File(userHome, "AppData\\Roaming\\MusicAppDemo");
        } else if (os.contains("mac")) {
            baseDir = new File(userHome, "Library/Application Support/MusicAppDemo");
        } else {
            baseDir = new File(userHome, ".config/MusicAppDemo");
        }

        File dir = new File(baseDir, gameName);
        if (!dir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
        }
        return dir;
    }

    public void setSlotData(JsonElement slotData) {
        this.slotData = slotData;
    }

    public JsonElement getSlotData() {
        return this.slotData;
    }

}
