package app.player;

import app.player.json.AlbumMetadata;
import app.player.json.SongJSON;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;

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

            String albumKey = (raw.region == null || raw.region.isBlank()) ? "Songs" : raw.region;

            Album album = albums.computeIfAbsent(
                    albumKey,
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

            String requires = "";
            if (raw.requires != null && !raw.requires.isEmpty()) {
                // If single element already contains pipes, use as-is (new format)
                // Otherwise join with pipes (old format)
                if (raw.requires.size() == 1 && raw.requires.get(0).contains("|")) {
                    requires = raw.requires.get(0);
                } else {
                    requires = String.join("|", raw.requires);
                }
            }

            album.addSong(new Song(raw.name, songType, "", requires));
        }

        return new ArrayList<>(albums.values());
    }

    private String detectAlbumType(List<String> categories) {
        if (categories.contains("Re-recordings")) return "rerecording";
        return "standard";
    }
}