/*
 * SPDX-License-Identifier: MPL-2.0
 */
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
import static app.util.Normalization.stripFeatCredit;

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
                    if (matchedSong.getFilePath() == null) {
                        matchedSong.setFilePath(file.getAbsolutePath());
                    }
                    LOGGER.info("Matched: {} -> {} | path: {}", file.getName(), matchedSong.getTitle(),
                matchedSong.getFilePath());
                } else {
                    LOGGER.warn("Could not match file to song: {} in album {}", file.getName(), album.getName());
                }
            }
        }
    }

    static Song findBestMatch(String normalizedFilename, List<Song> songs) {
        // Exact title matches take precedence over aliases regardless of song
        // order: a later song whose title equals the file wins over an earlier
        // song's alias. Feat-stripped, truncation, and edit-distance tiers keep
        // their relative per-song order below.
        for (Song song : songs) {
            if (normalizedFileEquals(normalizedFilename, normalizeSongTitle(song.getTitle()))) {
                return song;
            }
        }

        Song matchedSong = null;
        int bestDistance = Integer.MAX_VALUE;

        String lowerFile = normalizedFilename.toLowerCase(Locale.ROOT);

        for (Song song : songs) {
            // Try the title first, then any file-name aliases the song declares.
            for (String matchName : song.getMatchNames()) {
                String normalizedSong = normalizeSongTitle(matchName);

                if (normalizedFileEquals(normalizedFilename, normalizedSong)) {
                    return song;
                }

                if (normalizedFileEquals(stripFeatCredit(normalizedFilename), stripFeatCredit(normalizedSong))) {
                    return song;
                }

                // --- Catch iTunes/OS truncated filenames ---
                // If the filename was cut off at the end but shares a long prefix (e.g. >= 15 chars).
                // Only treat it as truncation when the cut is mid-word; a clean prefix that ends at
                // a word boundary is a distinct complete title, not a truncated one
                // (e.g. "Is It Over Now? (Taylor's Version)" vs "... (From The Vault)").
                String lowerSong = normalizedSong.toLowerCase(Locale.ROOT);

                if (lowerFile.length() >= 15 && lowerSong.length() >= 15) {
                    if (isMidWordPrefix(lowerFile, lowerSong) || isMidWordPrefix(lowerSong, lowerFile)) {
                        return song;
                    }
                }

                int dist = levenshteinDistance(lowerFile, lowerSong);
                if (dist < 5 && dist < bestDistance) { // tweak threshold if needed
                    matchedSong = song;
                    bestDistance = dist;
                }
            }
        }

        return matchedSong;
    }

    private static boolean normalizedFileEquals(String normalizedFilename, String normalizedSong) {
        return normalizedFilename.equalsIgnoreCase(normalizedSong);
    }

    private static boolean isMidWordPrefix(String shorter, String longer) {
        return longer.length() > shorter.length()
                && longer.startsWith(shorter)
                && !Character.isWhitespace(longer.charAt(shorter.length()));
    }

    static boolean isAudioFile(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.endsWith(".mp3") || lower.endsWith(".m4a") || lower.endsWith(".wav");
    }
}
