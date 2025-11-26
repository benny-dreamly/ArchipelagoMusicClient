package app.archipelago;

import app.MusicAppDemo;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import io.github.archipelagomw.Client;
import io.github.archipelagomw.flags.ItemsHandling;
import javafx.application.Platform;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.Reader;
import java.io.Writer;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

public class APClient extends Client {

    private final String address;
    private Consumer<Exception> onErrorCallback;
    private String gameName;
    private JsonElement slotData;

    public APClient(String host, int port, String slot, String password) {
        super();
//        setGame("Manual_TaylorSwiftDiscography_bennydreamly");
        setPassword(password);
        setItemsHandlingFlags(ItemsHandling.SEND_ITEMS + ItemsHandling.SEND_OWN_ITEMS + ItemsHandling.SEND_STARTING_INVENTORY);
        this.address = host + ":" + port;
        setName(slot);

        this.gameName = loadSavedGameName();
        setGame(this.gameName);
    }

    public void setOnErrorCallback(Consumer<Exception> callback) {
        this.onErrorCallback = callback;
    }


    public void connect() throws URISyntaxException {
        super.connect(this.address);
    }

    public void disconnect() {
        if (isConnected()) {
            super.disconnect();
        }
    }

    @Override
    public void onError(Exception e) {
        if (onErrorCallback != null) {
            Platform.runLater(() -> onErrorCallback.accept(e));
        }
        disconnect();
    }

    @Override
    public void onClose(String message, int i) {
        disconnect();
    }

    public void sendCheck(String locationName) {
        Long locationID = getDataPackage().getGame(getGame()).locationNameToId.get(locationName);

        if (locationID != null) {
            checkLocation(locationID);
        } else {
            MusicAppDemo.LOGGER.warn("No location ID found for location: {}", locationName);
        }
    }

    // persistency helpers to save/load the game name

    public void setGameName(String name) {
        this.gameName = name;
        setGame(name);
        saveGameName(name);

        File gameDir = getGameDataFolder();
        if (!gameDir.exists()) {
            if (gameDir.mkdirs()) {
                MusicAppDemo.LOGGER.info("Created new game folder: {}", gameDir.getAbsolutePath());
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
            //noinspection CallToPrintStackTrace
            e.printStackTrace();
        }
    }

    private String loadSavedGameName() {
        File configFile = getGameConfigFile();
        if (configFile.exists()) {
            try (Reader reader = new FileReader(configFile, StandardCharsets.UTF_8)) {
                return new Gson().fromJson(reader, String.class);
            } catch (IOException e) {
                //noinspection CallToPrintStackTrace
                e.printStackTrace();
            }
        }
        // fallback default
        return "Manual_TaylorSwiftDiscography_bennydreamly";
    }

    private File getGameConfigFile() {
        String userHome = System.getProperty("user.home");
        File baseDir;

        String os = System.getProperty("os.name").toLowerCase();
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
        String os = System.getProperty("os.name").toLowerCase();

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
                //noinspection CallToPrintStackTrace
                e.printStackTrace();
            }
        }
        return "Manual_TaylorSwiftDiscography_bennydreamly";
    }

    public static void saveGameNameStatic(String name) {
        File configFile = getGameConfigFileStatic();
        try (Writer writer = new FileWriter(configFile, StandardCharsets.UTF_8)) {
            new GsonBuilder().setPrettyPrinting().create().toJson(name, writer);
        } catch (IOException e) {
            //noinspection CallToPrintStackTrace
            e.printStackTrace();
        }
    }

    private static File getGameConfigFileStatic() {
        String userHome = System.getProperty("user.home");
        String os = System.getProperty("os.name").toLowerCase();
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
        String os = System.getProperty("os.name").toLowerCase();
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
