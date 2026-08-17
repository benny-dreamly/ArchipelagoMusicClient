package app;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Main {
    public static void main(String[] args) {
        File logDir = new File("logs");
        logDir.mkdirs();
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
        String logFile = new File(logDir, "MusicAppDemo-" + timestamp + ".log").getAbsolutePath();
        System.setProperty("org.slf4j.simpleLogger.logFile", logFile);

        MusicAppDemo.main(args);
    }
}