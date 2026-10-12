/*
 * SPDX-License-Identifier: MPL-2.0
 */
package app.player.json;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MusicLibraryConverter {

    private static final Logger LOGGER = LoggerFactory.getLogger(MusicLibraryConverter.class);

    private final LibraryLoader locationsLoader = new LibraryLoader();

    public MusicLibraryJSON convert(File locationsFile, File configDir) throws IOException {
        Map<String, AlbumMetadata> metadata = AlbumMetadataLoader.loadAlbumMetadata(configDir);
        Map<String, String> albumFolders = loadAlbumFolders(configDir);
        List<SongJSON> rawSongs;
        try (Reader reader = Files.newBufferedReader(locationsFile.toPath(), StandardCharsets.UTF_8)) {
            rawSongs = locationsLoader.loadSongsFromReader(reader);
        }
        return convert(rawSongs, metadata, albumFolders);
    }

    public MusicLibraryJSON convert(List<SongJSON> rawSongs,
            Map<String, AlbumMetadata> metadata, Map<String, String> albumFolders) {
        Map<String, AlbumMetadata> meta = metadata != null ? metadata : Map.of();
        Map<String, String> folders = albumFolders != null ? albumFolders : Map.of();

        MusicLibraryJSON library = new MusicLibraryJSON();
        library.artist = "";
        List<String> bonus = new ArrayList<>();
        Map<String, MusicLibraryJSON.AlbumJSON> albums = new LinkedHashMap<>();

        for (SongJSON raw : rawSongs) {
            if ("Bonus Locations".equalsIgnoreCase(raw.region)) {
                bonus.add(raw.name);
                continue;
            }

            String albumName = (raw.region == null || raw.region.isBlank()) ? "Songs" : raw.region;
            MusicLibraryJSON.AlbumJSON album = albums.computeIfAbsent(albumName, name -> {
                MusicLibraryJSON.AlbumJSON a = new MusicLibraryJSON.AlbumJSON();
                a.name = name;
                a.type = detectAlbumType(raw.category);
                a.full_album_unlock = meta.getOrDefault(name, new AlbumMetadata(false)).isFullAlbumUnlock();
                a.path = folders.getOrDefault(name, "");
                a.songs = new ArrayList<>();
                return a;
            });

            MusicLibraryJSON.SongJSON song = new MusicLibraryJSON.SongJSON();
            song.title = raw.name;
            song.location = raw.name;
            song.type = inferSongType(raw.category);
            song.requires = normalizeRequires(raw.requires);
            album.songs.add(song);
        }

        library.albums = new ArrayList<>(albums.values());
        if (!bonus.isEmpty()) {
            library.bonus_locations = bonus;
        }
        return library;
    }

    public void write(MusicLibraryJSON library, File out) throws IOException {
        Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
        Path outPath = out.toPath().toAbsolutePath();
        Path temp = Files.createTempFile(outPath.getParent(), out.getName(), ".tmp");
        try {
            try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
                gson.toJson(library, writer);
            }
            try {
                Files.move(temp, outPath, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, outPath, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private static String detectAlbumType(List<String> categories) {
        return containsIgnoreCase(categories, "Re-recordings") ? "re-recording" : "standard";
    }

    private static String inferSongType(List<String> category) {
        if (containsIgnoreCase(category, "Vault Tracks")) return "vault";
        if (containsIgnoreCase(category, "Re-recordings")) return "re-recording";
        if (containsIgnoreCase(category, "Short Songs")) return "short";
        return "normal";
    }

    private static boolean containsIgnoreCase(List<String> values, String needle) {
        if (values == null) return false;
        for (String value : values) {
            if (value != null && value.equalsIgnoreCase(needle)) return true;
        }
        return false;
    }

    private static String normalizeRequires(List<String> requires) {
        if (requires == null || requires.isEmpty()) return null;
        if (requires.size() == 1 && requires.get(0).contains("|")) return requires.get(0);
        return String.join("|", requires);
    }

    private static Map<String, String> loadAlbumFolders(File configDir) {
        File file = new File(configDir, "albumFolders.json");
        if (!file.isFile()) return Map.of();
        try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            Type type = new TypeToken<Map<String, String>>() {}.getType();
            Map<String, String> folders = new Gson().fromJson(reader, type);
            return folders == null ? Map.of() : folders;
        } catch (IOException | JsonSyntaxException e) {
            LOGGER.warn("Could not read albumFolders.json in {}", configDir.getAbsolutePath(), e);
            return Map.of();
        }
    }

    public static void main(String[] args) {
        if (args.length < 1 || args.length > 2) {
            LOGGER.error("Usage: MusicLibraryConverter <locations.json> [output-music_library.json]");
            System.exit(1);
        }
        File locationsFile = new File(args[0]);
        if (!locationsFile.isFile()) {
            LOGGER.error("locations.json not found: {}", locationsFile.getAbsolutePath());
            System.exit(1);
        }
        File configDir = locationsFile.getAbsoluteFile().getParentFile();
        File out = args.length == 2 ? new File(args[1]) : new File(configDir, "music_library.json");

        MusicLibraryConverter converter = new MusicLibraryConverter();
        try {
            MusicLibraryJSON library = converter.convert(locationsFile, configDir);
            converter.write(library, out);
            int songs = 0;
            for (MusicLibraryJSON.AlbumJSON album : library.albums) {
                songs += album.songs.size();
            }
            LOGGER.info("Wrote music_library.json with {} albums and {} songs to {}",
                    library.albums.size(), songs, out.getAbsolutePath());
        } catch (IOException | RuntimeException e) {
            LOGGER.error("Conversion failed for {}", locationsFile.getAbsolutePath(), e);
            System.exit(1);
        }
    }
}