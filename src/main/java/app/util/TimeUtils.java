package app.util;

import javafx.util.Duration;

public class TimeUtils {

    private TimeUtils() {} // utility class

    public static String formatTime(Duration duration) {
        int totalSeconds = (int) duration.toSeconds();
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return String.format("%d:%02d", minutes, seconds);
    }
}
