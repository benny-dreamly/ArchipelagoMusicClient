package app.player.json;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class LibraryLoader {

    private static final Type SONG_LIST_TYPE = new TypeToken<List<SongJSON>>() {}.getType();
    private static final Type REQUIRES_TYPE = new TypeToken<List<String>>(){}.getType();

    private Gson createGson() {
        return new GsonBuilder()
                .registerTypeAdapter(REQUIRES_TYPE, new RequiresDeserializer())
                .create();
    }

    public List<SongJSON> loadSongs(String resourcePath) throws Exception {
        InputStream is = getClass().getResourceAsStream(resourcePath);
        if (is == null) {
            throw new IllegalArgumentException("Resource not found: " + resourcePath);
        }

        try (InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
            return loadSongsFromReader(reader);
        }
    }

    public List<SongJSON> loadSongsFromReader(Reader reader) {
        Gson gson = createGson();
        JsonElement root = gson.fromJson(reader, JsonElement.class);

        JsonArray array;
        if (root.isJsonArray()) {
            array = root.getAsJsonArray();
        } else if (root.isJsonObject()) {
            JsonElement data = root.getAsJsonObject().get("data");
            if (data == null || !data.isJsonArray()) {
                throw new IllegalArgumentException("locations JSON has no 'data' array");
            }
            array = data.getAsJsonArray();
        } else {
            throw new IllegalArgumentException("Unexpected locations JSON root type");
        }

        return gson.fromJson(array, SONG_LIST_TYPE);
    }
}