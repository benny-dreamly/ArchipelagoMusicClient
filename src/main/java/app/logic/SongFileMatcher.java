package app.logic;

import app.player.Album;
import app.player.Song;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.List;
import java.util.Locale;

import static app.util.Normalization.levenshteinDistance;
import static app.util.Normalization.normalizeFilename;
import static app.util.Normalization.normalizeSongTitle;

public class SongFileMatcher {

    private static final Logger LOGGER = LoggerFactory.getLogger(SongFileMatcher.class);

    public static void assignFilesToSongs(List<Album> albums) {
        for (Album album : albums) {
            String folderPath = album.getFolderPath();
            if (folderPath == null) continue;

            File albumDirectory = new File(folderPath);
            if (!albumDirectory.exists() || !albumDirectory.isDirectory()) continue;


            File[] files = albumDirectory.listFiles((_, name) -> isAudioFile(name));
            if (files == null) continue;

            for (File file : files) {
                String normalizedFile = normalizeFilename(file.getName());
                Song matchedSong = findBestMatch(normalizedFile, album.getSongs());

                if (matchedSong != null) {
                    matchedSong.setFilePath(file.getAbsolutePath());
                    LOGGER.info("Matched: {} -> {} | path: {}", file.getName(), matchedSong.getTitle(), matchedSong.getFilePath());
                } else {
                    LOGGER.warn("Could not match file to song: {} in album {}", file.getName(), album.getName());
                }
            }
        }
    }

    static Song findBestMatch(String normalizedFilename, List<Song> songs) {
        Song matchedSong = null;
        int bestDistance = Integer.MAX_VALUE;

        for (Song song : songs) {
            String normalizedSong = normalizeSongTitle(song.getTitle());

            if (normalizedFileEquals(normalizedFilename, normalizedSong)) {
                return song;
            }

            int dist = levenshteinDistance(normalizedFilename.toLowerCase(Locale.ROOT), normalizedSong.toLowerCase(Locale.ROOT));
            if (dist < 5 && dist < bestDistance) { // tweak threshold if needed
                matchedSong = song;
                bestDistance = dist;
            }
        }

        return matchedSong;
    }

    private static boolean normalizedFileEquals(String normalizedFilename, String normalizedSong) {
        return normalizedFilename.equalsIgnoreCase(normalizedSong);
    }

    static boolean isAudioFile(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.endsWith(".mp3") || lower.endsWith(".m4a") || lower.endsWith(".wav");
    }
}
