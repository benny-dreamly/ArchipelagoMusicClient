package app.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.lang.reflect.Type;
import java.util.List;

import static app.util.ConfigPaths.getConfigDir;

public class AlbumOrderManager {

    private static final Logger logger = LoggerFactory.getLogger(AlbumOrderManager.class);
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
            try (Reader reader = new FileReader(orderFile)) {
                Type listType = new TypeToken<List<String>>() {}.getType();
                loadedOrder = new Gson().fromJson(reader, listType);
                if (loadedOrder != null && !loadedOrder.isEmpty()) {
                    logger.info("Loaded album order from {}", orderFile.getAbsolutePath());                }
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

            try (Writer writer = new FileWriter(orderFile)) {
                new GsonBuilder().setPrettyPrinting().create().toJson(loadedOrder, writer);
                logger.info("Generated default albumOrder.json at {}", orderFile.getAbsolutePath());            } catch (IOException e) {
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
