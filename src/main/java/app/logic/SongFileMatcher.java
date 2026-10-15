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
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static app.util.Normalization.levenshteinDistance;
import static app.util.Normalization.normalizeFilename;
import static app.util.Normalization.normalizeSongTitle;
import static app.util.Normalization.stripFeatCredit;

public class SongFileMatcher {

    private static final Logger LOGGER = LoggerFactory.getLogger(SongFileMatcher.class);
    private static final String SEGMENT_SEPARATOR = " - ";

    // Edit-distance matching only fires below this distance.
    private static final int MAX_EDIT_DISTANCE = 5;
    // A fuzzy match is thrown away when a sibling song is within this margin.
    private static final int AMBIGUITY_MARGIN = 1;
    // Albums whose titles are this close are treated as collision-prone, which
    // enables the track-number mapping used to disambiguate them.
    private static final int MIN_COLLIDING_TITLE_LENGTH = 5;
    private static final int COLLIDING_TITLE_DISTANCE = 3;
    private static final Pattern TRACK_NUMBER =
            Pattern.compile("(?:^|[\\s\\-_])(\\d{1,3})(?=[\\s\\-_]|$)");

    public static void assignFilesToSongs(List<Album> albums) {
        for (Album album : albums) {
            String folderPath = album.getFolderPath();
            if (folderPath == null) continue;
            File albumDirectory = new File(folderPath);
            if (!albumDirectory.exists() || !albumDirectory.isDirectory()) continue;

            File[] files = albumDirectory.listFiles(file -> isAudioFile(file.getName()));
            if (files == null) continue;

            List<Song> songs = album.getSongs();
            Map<String, Song> positional = buildPositionalMapping(files, songs);
            String folderPrefix = commonFolderPrefix(files);
            for (File file : files) {
                List<String> candidates = buildCandidates(file.getName(), folderPrefix);
                Song matchedSong = findBestMatch(candidates, songs, positional.get(file.getName()));

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
        return findBestMatch(List.of(normalizedFilename), songs, null);
    }

    static Song findBestMatch(List<String> candidates, List<Song> songs) {
        return findBestMatch(candidates, songs, null);
    }

    /**
     * Resolves a file to a song, trying the strategies in descending confidence:
     * exact title/alias/feat/truncation equality, then a track-number mapping
     * (only supplied for collision-prone albums), then edit distance. Every
     * normalized candidate (full name, artist/album prefix stripped, or a
     * separator suffix) is tried so files like "Artist - Album - 01 Title.mp3"
     * still match.
     */
    static Song findBestMatch(List<String> candidates, List<Song> songs, Song positionalSong) {
        Song exact = findExactMatch(candidates, songs);
        if (exact != null) {
            return exact;
        }
        // A track-number mapping beats edit-distance guessing: on albums whose
        // titles are near-identical to each other the closest string is often a
        // sibling song, while track order is unambiguous. Only supplied for
        // albums that actually have such collisions (see buildPositionalMapping).
        if (positionalSong != null) {
            return positionalSong;
        }
        return findFuzzyMatch(candidates, songs);
    }

    private static Song findExactMatch(List<String> candidates, List<Song> songs) {
        // Exact title matches take precedence over aliases regardless of song
        // order: a later song whose title equals the file wins over an earlier
        // song's alias. Feat-stripped and truncation tiers keep their relative
        // per-song order below. Every normalized candidate (full name,
        // artist/album prefix stripped, or a separator suffix) is tried so files
        // like "Artist - Album - 01 Title.mp3" still match.
        for (String candidate : candidates) {
            for (Song song : songs) {
                if (normalizedFileEquals(candidate, normalizeSongTitle(song.getTitle()))) {
                    return song;
                }
            }
        }

        for (String candidate : candidates) {
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
                    String lowerFile = candidate.toLowerCase(Locale.ROOT);
                    String lowerSong = normalizedSong.toLowerCase(Locale.ROOT);

                    if (lowerFile.length() >= 15 && lowerSong.length() >= 15) {
                        if (isMidWordPrefix(lowerFile, lowerSong) || isMidWordPrefix(lowerSong, lowerFile)) {
                            return song;
                        }
                    }
                }
            }
        }
        return null;
    }

    private static Song findFuzzyMatch(List<String> candidates, List<Song> songs) {
        Song bestSong = null;
        String bestCandidate = null;
        int bestDistance = Integer.MAX_VALUE;

        for (String candidate : candidates) {
            String lowerFile = candidate.toLowerCase(Locale.ROOT);
            for (Song song : songs) {
                int dist = shortestDistance(lowerFile, song);
                if (dist < MAX_EDIT_DISTANCE && dist < bestDistance) {
                    bestDistance = dist;
                    bestSong = song;
                    bestCandidate = lowerFile;
                }
            }
        }

        if (bestSong == null) {
            return null;
        }
        // Refuse to guess when a sibling song is just as close: on troll albums
        // like "Super Ghost Busters" every title is a 1-3 edit neighbour, and
        // playing the wrong file is worse than playing none.
        int near = 0;
        for (Song song : songs) {
            int dist = shortestDistance(bestCandidate, song);
            if (dist < MAX_EDIT_DISTANCE && dist <= bestDistance + AMBIGUITY_MARGIN) {
                near++;
            }
        }
        return near > 1 ? null : bestSong;
    }

    private static int shortestDistance(String lowerCandidate, Song song) {
        int best = Integer.MAX_VALUE;
        for (String matchName : song.getMatchNames()) {
            String normalizedSong = normalizeSongTitle(matchName).toLowerCase(Locale.ROOT);
            best = Math.min(best, levenshteinDistance(lowerCandidate, normalizedSong));
        }
        return best;
    }

    /**
     * Builds a filename -> song mapping from track numbers, but only for albums
     * whose song titles collide (near-identical to one another), such as the
     * "Super Ghost Busters" troll world where "Ghost Buster", "Ghostdubster"
     * and friends are 1-3 edits apart. For those albums edit-distance matching
     * is a coin flip, so track order is the more reliable signal. Returns an
     * empty map whenever a clean 1:1 mapping cannot be established, leaving
     * every other album on its existing name-based path.
     */
    static Map<String, Song> buildPositionalMapping(File[] files, List<Song> songs) {
        if (files == null || songs.isEmpty() || files.length != songs.size()) {
            return Map.of();
        }
        if (!hasCollidingTitles(songs)) {
            return Map.of();
        }

        Map<File, Integer> trackByFile = new HashMap<>();
        Set<Integer> seenTracks = new HashSet<>();
        for (File file : files) {
            int track = parseTrackNumber(file.getName());
            if (track <= 0 || !seenTracks.add(track)) {
                return Map.of();
            }
            trackByFile.put(file, track);
        }

        List<File> ordered = new ArrayList<>(List.of(files));
        ordered.sort(Comparator.comparingInt(trackByFile::get));

        Map<String, Song> mapping = new HashMap<>();
        for (int i = 0; i < ordered.size(); i++) {
            mapping.put(ordered.get(i).getName(), songs.get(i));
        }
        return mapping;
    }

    private static boolean hasCollidingTitles(List<Song> songs) {
        for (int i = 0; i < songs.size(); i++) {
            String a = comparableTitle(songs.get(i).getTitle());
            if (a.length() < MIN_COLLIDING_TITLE_LENGTH) continue;
            for (int j = i + 1; j < songs.size(); j++) {
                String b = comparableTitle(songs.get(j).getTitle());
                if (b.length() < MIN_COLLIDING_TITLE_LENGTH) continue;
                if (levenshteinDistance(a, b) <= COLLIDING_TITLE_DISTANCE) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String comparableTitle(String title) {
        return normalizeSongTitle(title).toLowerCase(Locale.ROOT);
    }

    /**
     * Extracts the last standalone 1-3 digit number from a filename, which is
     * the track number in common layouts such as "05 Title.mp3",
     * "2-05 Title.mp3" and "Artist - Album - 05 Title.mp3". Returns -1 when no
     * track number is present.
     */
    static int parseTrackNumber(String fileName) {
        String base = fileName.replaceFirst("[.][^.]+$", "");
        Matcher matcher = TRACK_NUMBER.matcher(base);
        int track = -1;
        while (matcher.find()) {
            track = Integer.parseInt(matcher.group(1));
        }
        return track;
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
