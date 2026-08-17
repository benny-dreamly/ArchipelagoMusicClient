package app;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class Main {
    public static void main(String[] args) {
        String userHome = System.getProperty("user.home");
        String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        File baseDir;
        if (os.contains("win")) {
            baseDir = new File(userHome, "AppData\\Roaming\\MusicAppDemo");
        } else if (os.contains("mac")) {
            baseDir = new File(userHome, "Library/Application Support/MusicAppDemo");
        } else {
            baseDir = new File(userHome, ".config/MusicAppDemo");
        }

        File logDir = new File(baseDir, "logs");
        logDir.mkdirs();
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
        String logFile = new File(logDir, "MusicAppDemo-" + timestamp + ".log").getAbsolutePath();
        System.setProperty("org.slf4j.simpleLogger.logFile", logFile);

        MusicAppDemo.main(args);
    }
}