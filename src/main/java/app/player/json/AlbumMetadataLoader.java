/*
 * SPDX-License-Identifier: MPL-2.0
 */
package app.player.json;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AlbumMetadataLoader {

    private static final Logger LOGGER = LoggerFactory.getLogger(AlbumMetadataLoader.class);

    public static Map<String, AlbumMetadata> loadAlbumMetadata(File configDir) {
        File file = new File(configDir, "album_metadata.json");
        if (!file.exists()) {
            LOGGER.warn("No album_metadata.json found in {}", configDir.getAbsolutePath());
            return Collections.emptyMap();
        }

        try (FileReader reader = new FileReader(file, StandardCharsets.UTF_8)) {
            Type type = new TypeToken<Map<String, AlbumMetadata>>() {}.getType();
            Map<String, AlbumMetadata> metadata = new Gson().fromJson(reader, type);
            if (metadata == null || metadata.isEmpty()) {
                LOGGER.warn("album_metadata.json is empty in {}", file.getAbsolutePath());
                return Collections.emptyMap();
            }

            Map<String, AlbumMetadata> valid = new LinkedHashMap<>();
            for (Map.Entry<String, AlbumMetadata> entry : metadata.entrySet()) {
                if (entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() == null) {
                    LOGGER.error("Invalid entry in album_metadata.json (null or blank key/value): {}", entry);
                    continue;
                }
                valid.put(entry.getKey(), entry.getValue());
            }

            LOGGER.info("Loaded album metadata for {} albums.", valid.size());
            return Collections.unmodifiableMap(valid);
        } catch (IOException e) {
            LOGGER.error("Failed to load album_metadata.json from {}", file.getAbsolutePath(), e);
            return Collections.emptyMap();
        } catch (JsonSyntaxException e) {
            LOGGER.error("Malformed album_metadata.json in {}", file.getAbsolutePath(), e);
            return Collections.emptyMap();
        }
    }
}