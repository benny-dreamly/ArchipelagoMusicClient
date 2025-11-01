package app.player;

import app.player.json.AlbumMetadata;
import app.player.json.SongJSON;

import java.util.*;

@SuppressWarnings("ClassCanBeRecord")
public class AlbumConverter {

    private final Map<String, AlbumMetadata> albumMetadata;

    public AlbumConverter(Map<String, AlbumMetadata> albumMetadata) {
        this.albumMetadata = albumMetadata;
    }

    public List<Album> convert(List<SongJSON> rawSongs) {
        Map<String, Album> albums = new HashMap<>();

        for (SongJSON raw : rawSongs) {
            // Skip bonus locations
            if ("Bonus Locations".equalsIgnoreCase(raw.region)) {
                continue;
            }

            Album album = albums.computeIfAbsent(
                    raw.region,
                    name -> {
                        boolean fullUnlock = albumMetadata.getOrDefault(name, new AlbumMetadata(false)).isFullAlbumUnlock();
                        return new Album(name, detectAlbumType(raw.category), fullUnlock);
                    }
            );

            String songType;
            if (raw.category.contains("Short Songs")) {
                songType = "short";
            } else if (raw.category.contains("Re-recordings")) {
                songType = "rerecording";
            } else {
                songType = "standard";
            }

            album.addSong(new Song(raw.name, songType));
        }

        return new ArrayList<>(albums.values());
    }

    private String detectAlbumType(List<String> categories) {
        if (categories.contains("Re-recordings")) return "rerecording";
        return "standard";
    }
}