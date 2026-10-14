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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static app.util.Normalization.levenshteinDistance;
import static app.util.Normalization.normalizeFilename;
import static app.util.Normalization.normalizeSongTitle;
import static app.util.Normalization.stripFeatCredit;

public class SongFileMatcher {

    private static final Logger LOGGER = LoggerFactory.getLogger(SongFileMatcher.class);
    private static final String SEGMENT_SEPARATOR = " - ";

    public static void assignFilesToSongs(List<Album> albums) {
        for (Album album : albums) {
            String folderPath = album.getFolderPath();
            if (folderPath == null) continue;
            File albumDirectory = new File(folderPath);
            if (!albumDirectory.exists() || !albumDirectory.isDirectory()) continue;

            File[] files = albumDirectory.listFiles(file -> isAudioFile(file.getName()));
            if (files == null) continue;

            String folderPrefix = commonFolderPrefix(files);
            for (File file : files) {
                List<String> candidates = buildCandidates(file.getName(), folderPrefix);
                Song matchedSong = findBestMatch(candidates, album.getSongs());

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
        return findBestMatch(List.of(normalizedFilename), songs);
    }

    static Song findBestMatch(List<String> candidates, List<Song> songs) {
        // Exact title matches take precedence over aliases regardless of song
        // order: a later song whose title equals the file wins over an earlier
        // song's alias. Feat-stripped, truncation, and edit-distance tiers keep
        // their relative per-song order below. Every normalized candidate
        // (full name, artist/album prefix stripped, or a separator suffix) is
        // tried so files like "Artist - Album - 01 Title.mp3" still match.
        for (String candidate : candidates) {
            for (Song song : songs) {
                if (normalizedFileEquals(candidate, normalizeSongTitle(song.getTitle()))) {
                    return song;
                }
            }
        }

        Song matchedSong = null;
        int bestDistance = Integer.MAX_VALUE;

        for (String candidate : candidates) {
            String lowerFile = candidate.toLowerCase(Locale.ROOT);

            for (Song song : songs) {
                // Try the title first, then any file-name aliases the song declares.
                for (String matchName : song.getMatchNames()) {
                    String normalizedSong = normalizeSongTitle(matchName);

                    if (normalizedFileEquals(candidate, normalizedSong)) {
                        return song;
                    }

                    if (normalizedFileEquals(stripFeatCredit(candidate), stripFeatCredit(normalizedSong))) {
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
        }

        return matchedSong;
    }

    /**
     * Builds the normalized strings used to look up a song for a file. The raw
     * filename is always tried first; then the filename with a folder-wide
     * prefix removed, then each suffix after a " - " separator so that names
     * such as "Vargskelethor - Super Ghostbusters - 01 Ghostbusters.mp3"
     * collapse down to "Ghostbusters".
     */
    static List<String> buildCandidates(String fileName, String folderPrefix) {
        Set<String> candidates = new LinkedHashSet<>();
        addCandidate(candidates, fileName);

        if (!folderPrefix.isEmpty()
                && fileName.regionMatches(true, 0, folderPrefix, 0, folderPrefix.length())) {
            addCandidate(candidates, fileName.substring(folderPrefix.length()));
        }

        String remainder = fileName;
        int separator = remainder.indexOf(SEGMENT_SEPARATOR);
        while (separator >= 0) {
            remainder = remainder.substring(separator + SEGMENT_SEPARATOR.length());
            addCandidate(candidates, remainder);
            separator = remainder.indexOf(SEGMENT_SEPARATOR);
        }

        return new ArrayList<>(candidates);
    }

    private static void addCandidate(Set<String> candidates, String raw) {
        String normalized = normalizeFilename(raw);
        if (!normalized.isBlank()) {
            candidates.add(normalized);
        }
    }

    /**
     * Returns the longest prefix shared by every file in the folder, trimmed
     * back to the last separator so tracking numbers ("01", "02", ...) are not
     * partially consumed. Returns an empty string when there is no useful
     * common prefix.
     */
    static String commonFolderPrefix(File[] files) {
        if (files == null || files.length < 2) {
            return "";
        }
        String prefix = files[0].getName();
        for (int i = 1; i < files.length && !prefix.isEmpty(); i++) {
            prefix = commonPrefix(prefix, files[i].getName());
        }
        int cut = lastSeparatorIndex(prefix);
        return cut >= 0 ? prefix.substring(0, cut + 1) : "";
    }

    private static String commonPrefix(String a, String b) {
        int limit = Math.min(a.length(), b.length());
        int i = 0;
        while (i < limit && a.charAt(i) == b.charAt(i)) {
            i++;
        }
        return a.substring(0, i);
    }

    private static int lastSeparatorIndex(String s) {
        for (int i = s.length() - 1; i >= 0; i--) {
            char c = s.charAt(i);
            if (c == ' ' || c == '-' || c == '_' || c == '.') {
                return i;
            }
        }
        return -1;
    }

    private static boolean normalizedFileEquals(String normalizedFilename, String normalizedSong) {
        return normalizedFilename.equalsIgnoreCase(normalizedSong);
    }

    private static boolean isMidWordPrefix(String shorter, String longer) {
        if (!longer.startsWith(shorter)) return false;
        if (longer.length() <= shorter.length()) return false;
        char nextChar = longer.charAt(shorter.length());
        return !Character.isWhitespace(nextChar);
    }

    public static boolean isAudioFile(String filename) {
        String lower = filename.toLowerCase(Locale.ROOT);
        return lower.endsWith(".mp3") || lower.endsWith(".m4a") || lower.endsWith(".wav");
    }
}
