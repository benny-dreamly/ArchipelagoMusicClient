package app.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.Reader;
import java.io.Writer;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static app.util.ConfigPaths.getConfigDir;

public class AlbumOrderManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(AlbumOrderManager.class);
    private List<String> cache;

    public List<String> getAlbumOrderCache() {
        return cache;
    }

    public List<String> getAlbumOrder() {
        if (cache != null) {
            return cache;
        }

        File gameDir = getConfigDir();
        File orderFile = new File(gameDir, "albumOrder.json");

        List<String> loadedOrder = null;
        if (orderFile.exists()) {
            try (Reader reader = new FileReader(orderFile, StandardCharsets.UTF_8)) {
                Type listType = new TypeToken<List<String>>() {}.getType();
                loadedOrder = new Gson().fromJson(reader, listType);
                if (loadedOrder != null && !loadedOrder.isEmpty()) {
                    LOGGER.info("Loaded album order from {}", orderFile.getAbsolutePath());                }
            } catch (IOException e) {
                //noinspection CallToPrintStackTrace
                e.printStackTrace();
            }
        }

        if (loadedOrder == null || loadedOrder.isEmpty()) {
            loadedOrder = List.of(
                    "Album 1",
                    "Album 2",
                    "Album 3",
                    "Album 4"
            );

            try (Writer writer = new FileWriter(orderFile, StandardCharsets.UTF_8)) {
                new GsonBuilder().setPrettyPrinting().create().toJson(loadedOrder, writer);
                LOGGER.info("Generated default albumOrder.json at {}", orderFile.getAbsolutePath());            } catch (IOException e) {
                //noinspection CallToPrintStackTrace
                e.printStackTrace();
            }
        }

        cache = loadedOrder; // cache it
        return cache;
    }

    public void clearAlbumOrderCache() {
        cache = null;
    }
}
