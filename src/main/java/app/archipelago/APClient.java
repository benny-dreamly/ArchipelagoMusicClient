package app.archipelago;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.github.archipelagomw.Client;
import io.github.archipelagomw.flags.ItemsHandling;
import javafx.application.Platform;

import java.io.*;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.function.Consumer;

public class APClient extends Client {

    private final String address;
    private Consumer<Exception> onErrorCallback;
    private String gameName;

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
            System.out.println("No location ID found for location: " + locationName);
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
                System.out.println("Created new game folder: " + gameDir.getAbsolutePath());
            }
        }
    }

    public String getGameName() {
        return this.gameName;
    }

    private void saveGameName(String name) {
        File configFile = getGameConfigFile();
        try (Writer writer = new FileWriter(configFile)) {
            new GsonBuilder().setPrettyPrinting().create().toJson(name, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String loadSavedGameName() {
        File configFile = getGameConfigFile();
        if (configFile.exists()) {
            try (Reader reader = new FileReader(configFile)) {
                return new Gson().fromJson(reader, String.class);
            } catch (IOException e) {
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

        File configDir = new File(baseDir, "config");
        if (!configDir.exists()) configDir.mkdirs();

        return new File(configDir, "currentGame.json");
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

}
