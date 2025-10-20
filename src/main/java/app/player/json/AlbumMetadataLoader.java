package app.player.json;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.Map;

public class AlbumMetadataLoader {

    public static Map<String, AlbumMetadata> loadAlbumMetadata(File configDir) {
        File file = new File(configDir, "album_metadata.json");
        if (!file.exists()) {
            System.err.println("No album_metadata.json found in " + configDir.getAbsolutePath());
            return Collections.emptyMap();
        }

        try (FileReader reader = new FileReader(file)) {
            Type type = new TypeToken<Map<String, AlbumMetadata>>() {}.getType();
            Map<String, AlbumMetadata> metadata = new Gson().fromJson(reader, type);
            System.out.println("Loaded album metadata for " + metadata.size() + " albums.");
            return metadata;
        } catch (IOException e) {
            e.printStackTrace();
            return Collections.emptyMap();
        }
    }
}