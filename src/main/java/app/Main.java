/*
 * SPDX-License-Identifier: MPL-2.0
 */
package app;

import java.awt.Taskbar;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import javax.imageio.ImageIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {
    private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);

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
        String timestamp = LocalDateTime.now(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
        String logFile = new File(logDir, "MusicAppDemo-" + timestamp + ".log").getAbsolutePath();
        System.setProperty("org.slf4j.simpleLogger.logFile", logFile);

        setDockIcon();

        MusicAppDemo.main(args);
    }

    private static void setDockIcon() {
        if (!System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("mac")) {
            return;
        }
        try {
            if (!Taskbar.isTaskbarSupported() || !Taskbar.getTaskbar().isSupported(Taskbar.Feature.ICON_IMAGE)) {
                return;
            }
            try (InputStream in = Main.class.getResourceAsStream("/icons/app-icon-512.png")) {
                if (in == null) {
                    return;
                }
                BufferedImage icon = ImageIO.read(in);
                if (icon != null) {
                    Taskbar.getTaskbar().setIconImage(icon);
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Could not set the Dock icon, continuing with the default.", e);
        }
    }
}