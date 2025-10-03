package app;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.FileReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.util.List;

public class LibraryLoader {

    public List<SongJSON> loadSongs(String resourcePath) throws Exception {
        Gson gson = new Gson();
        Type listType = new TypeToken<List<SongJSON>>() {}.getType();

        // Load JSON from resources
        InputStream is = getClass().getResourceAsStream(resourcePath);
        if (is == null) {
            throw new IllegalArgumentException("Resource not found: " + resourcePath);
        }

        try (InputStreamReader reader = new InputStreamReader(is)) {
            return gson.fromJson(reader, listType);
        }
    }
}