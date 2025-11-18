package app.util;

import app.archipelago.APClient;
import org.slf4j.Logger;

import java.io.File;

public class ConfigPaths {

    private ConfigPaths() {} // utility class

    public static File getConfigDir() {
        File gameDir = APClient.getGameDataFolderStatic();
        if (!gameDir.exists()) {
            gameDir.mkdirs();
        }
        return gameDir;
    }

    public static File getConnectionConfigFile() {
        File configDir = getConfigDir().getParentFile();
        return new File(configDir, "connection.json");
    }

    public static void checkIfGameFolderExists(File gameFolder, Logger logger){
        // Ensure the per-game folder exists
        if (!gameFolder.exists()) {
            //noinspection ResultOfMethodCallIgnored
            gameFolder.mkdirs();
            logger.info("Created game data folder: {}", gameFolder.getAbsolutePath());        }
    }
}
