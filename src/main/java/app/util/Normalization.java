package app.util;

public class Normalization {

    private Normalization() {} // utility class

    public static String normalizeFilename(String filename) {
        // 1. Remove extension
        String base = filename.replaceFirst("[.][^.]+$", "");

        // 2. Fix truncated "Taylor's Ver" → "Taylor's Version"
        base = base.replaceAll("(?i)Taylor's Ver(\\b.*)?", "Taylor's Version");

        // 3. Remove leading track/CD numbers
        base = base.replaceFirst("(?i)^(cd\\d+ )?\\d+[-. _]+", "");

        // 4. Normalize “feat.” variations
        base = base.replaceAll("(?i)ft\\.?|feat\\.?","feat.");

        // 5. Clean underscores/spaces
        base = base.replaceAll("_", " ");
        base = base.replaceAll(" +", " ");

        // 6. Trim broken parenthesis at the end
        base = base.replaceAll("\\(\\s*$", "");

        return base.trim();
    }

    // Levenshtein distance helper
    public static int levenshteinDistance(String a, String b) {
        int[] costs = new int[b.length() + 1];
        for (int j = 0; j < costs.length; j++) costs[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            costs[0] = i;
            int nw = i - 1;
            for (int j = 1; j <= b.length(); j++) {
                int cj = Math.min(1 + Math.min(costs[j], costs[j - 1]),
                        a.charAt(i - 1) == b.charAt(j - 1) ? nw : nw + 1);
                nw = costs[j];
                costs[j] = cj;
            }
        }
        return costs[b.length()];
    }

    public static String normalizeSongTitle(String title) {
        String normalized = title;

        // Replace any extra underscores or spaces
        normalized = normalized.replaceAll("_", " ");
        normalized = normalized.replaceAll(" +", " ");

        // Trim
        normalized = normalized.trim();

        return normalized;
    }
}
