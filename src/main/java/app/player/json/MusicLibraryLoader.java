package app.player.json;

import com.google.gson.Gson;

import app.player.Album;
import app.player.Song;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class MusicLibraryLoader {

    private static final Logger LOGGER = LoggerFactory.getLogger(MusicLibraryLoader.class);

    private final Gson gson = new Gson();

    public List<Album> loadFromFile(File file) throws Exception {
        try (Reader reader = new FileReader(file, StandardCharsets.UTF_8)) {
            return loadFromReader(reader);
        }
    }

    public List<Album> loadFromReader(Reader reader) {
        MusicLibraryJSON library = gson.fromJson(reader, MusicLibraryJSON.class);
        if (library == null || library.albums == null) {
            LOGGER.warn("Empty or invalid music library file");
            return List.of();
        }

        List<Album> albums = new ArrayList<>();

        for (MusicLibraryJSON.AlbumJSON albumJSON : library.albums) {
            String albumType = albumJSON.type != null ? albumJSON.type : "standard";
            boolean fullUnlock = albumJSON.full_album_unlock;

            Album album = new Album(albumJSON.name, albumType, fullUnlock);

            if (albumJSON.path != null) {
                album.setFolderPath(albumJSON.path);
            }

            for (MusicLibraryJSON.SongJSON songJSON : albumJSON.songs) {
                String songType = songJSON.type != null ? songJSON.type : "normal";
                Song song = new Song(songJSON.title, songType);

                if (songJSON.path != null) {
                    song.setFilePath(songJSON.path);
                }

                album.addSong(song);
            }

            albums.add(album);
        }

        LOGGER.info("Loaded music library: {} albums from music_library.json", albums.size());
        return albums;
    }
}
