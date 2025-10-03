package app;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.FileReader;
import java.lang.reflect.Type;
import java.util.List;

public class LibraryLoader {

    public List<SongJSON> loadSongs(String filePath) throws Exception {
        Gson gson = new Gson();
        Type listType = new TypeToken<List<SongJSON>>() {}.getType();
        try (FileReader reader = new FileReader(filePath)) {
            return gson.fromJson(reader, listType);
        }
    }
}