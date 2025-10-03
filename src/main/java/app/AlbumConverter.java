package app;

import java.util.*;

public class AlbumConverter {

    public List<Album> convert(List<SongJSON> rawSongs) {
        Map<String, Album> albums = new HashMap<>();

        for (SongJSON raw : rawSongs) {
            Album album = albums.computeIfAbsent(
                    raw.region,
                    name -> new Album(name, detectAlbumType(raw.category))
            );

            String songType = raw.category.contains("Re-recordings") ? "rerecording" : "standard";
            album.addSong(new Song(raw.name, songType));
        }

        return new ArrayList<>(albums.values());
    }

    private String detectAlbumType(List<String> categories) {
        if (categories.contains("Re-recordings")) return "rerecording";
        return "standard";
    }
}