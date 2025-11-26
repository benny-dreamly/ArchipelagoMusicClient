package app.util;

import app.player.Album;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static app.util.ConfigPaths.getAlbumConfigFile;

public class AlbumUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(AlbumUtils.class);

    private AlbumUtils() {} // utility class

    public static void generateDefaultAlbumFolders(List<Album> albums) {
        File configFile = getAlbumConfigFile();

        // If the file already exists, do nothing
        if (configFile.exists()) return;

        Map<String, String> defaultFolders = new LinkedHashMap<>();
        for (Album album : albums) {
            defaultFolders.put(album.getName(), ""); // empty string as placeholder
        }

        try {
            //noinspection ResultOfMethodCallIgnored
            configFile.createNewFile(); // make sure the file exists
            Gson gson = new GsonBuilder()
                    .setPrettyPrinting()
                    .disableHtmlEscaping() // keeps ' as-is instead of \u0027
                    .create();
            try (Writer writer = new FileWriter(configFile, StandardCharsets.UTF_8)) {
                gson.toJson(defaultFolders, writer);
            }
            LOGGER.info("Generated default albumFolders.json at {}", configFile.getAbsolutePath());
        } catch (Exception e) {
            //noinspection CallToPrintStackTrace
            e.printStackTrace();
        }
    }
}
