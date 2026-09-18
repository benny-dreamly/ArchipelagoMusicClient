/*
 * SPDX-License-Identifier: MPL-2.0
 */
package app.logic;

import app.player.Album;
import app.player.Song;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static app.util.Normalization.normalizeFilename;

/**
 * Scans a local folder for audio files and builds a playable library of synthetic
 * albums, one per sub-folder, without any Archipelago manual or unlock data.
 */
public class FolderScanner {

    private static final Logger LOGGER = LoggerFactory.getLogger(FolderScanner.class);

    private FolderScanner() {} // utility class

    public static List<Album> scanFolder(File root) {
        if (root == null) {
            throw new IllegalArgumentException("Folder to scan must not be null");
        }
        if (!root.isDirectory()) {
            throw new IllegalArgumentException("Not a directory: " + root);
        }

        Map<String, Album> albumsByName = new LinkedHashMap<>();
        collect(root, root, albumsByName);

        List<Album> albums = new ArrayList<>(albumsByName.values());
        for (Album album : albums) {
            album.getSongs().sort(Comparator.comparing(Song::getTitle, String.CASE_INSENSITIVE_ORDER));
        }

        LOGGER.info("Scanned {} audio files into {} albums under {}",
                albums.stream().mapToInt(a -> a.getSongs().size()).sum(), albums.size(), root);
        return albums;
    }

    private static void collect(File dir, File root, Map<String, Album> albumsByName) {
        File[] files = dir.listFiles();
        if (files == null) return;

        Album album = null;
        Set<String> titles = new HashSet<>();

        for (File file : files) {
            if (file.isDirectory()) {
                collect(file, root, albumsByName);
            } else if (SongFileMatcher.isAudioFile(file.getName())) {
                if (album == null) {
                    String name = uniqueAlbumName(dir, root, albumsByName);
                    album = new Album(name, "standard", true);
                    album.setFolderPath(dir.getAbsolutePath());
                    albumsByName.put(name, album);
                }
                String title = songTitle(file.getName());
                if (!titles.add(title.toLowerCase(Locale.ROOT))) {
                    LOGGER.warn("Skipping duplicate title '{}' in {}", title, dir);
                    continue;
                }
                Song song = new Song(title, "normal", "", "");
                song.setFilePath(file.getAbsolutePath());
                album.addSong(song);
                LOGGER.info("Added {} -> {}", file.getName(), song.getTitle());
            }
        }
    }

    /**
     * Prefer the directory name for the album. Fall back to the path relative to
     * the scanned root (e.g. "Artist / Album") when a sibling directory shares
     * the same name.
     */
    private static String uniqueAlbumName(File dir, File root, Map<String, Album> existing) {
        String name = dir.getName();
        if (!existing.containsKey(name)) {
            return name;
        }
        String relative = relativePath(root, dir);
        if (!existing.containsKey(relative)) {
            return relative;
        }
        int counter = 2;
        String candidate = relative + " (" + counter + ")";
        while (existing.containsKey(candidate)) {
            counter++;
            candidate = relative + " (" + counter + ")";
        }
        return candidate;
    }

    private static String relativePath(File root, File dir) {
        List<String> parts = new ArrayList<>();
        File current = dir;
        while (current != null && !current.equals(root)) {
            parts.add(current.getName());
            current = current.getParentFile();
        }
        Collections.reverse(parts);
        return String.join(" / ", parts);
    }

    private static String songTitle(String filename) {
        String normalized = normalizeFilename(filename);
        if (!normalized.isBlank()) {
            return normalized;
        }
        // e.g. "03.mp3" normalizes to nothing — fall back to the raw name minus extension
        return filename.replaceFirst("[.][^.]+$", "").trim();
    }
}