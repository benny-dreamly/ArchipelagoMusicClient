/*
 * SPDX-License-Identifier: MPL-2.0
 */
package app;

import java.awt.Taskbar;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import javax.imageio.ImageIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {
    private static final Logger LOGGER;

    static {
        configureLogFile();
        LOGGER = LoggerFactory.getLogger(Main.class);
    }

    public static void main(String[] args) {
        // Route otherwise-invisible failures (e.g. on the JavaFX thread) to the log.
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) ->
                LOGGER.error("Uncaught exception on thread '{}'", thread.getName(), throwable));

        setAppIcon();

        MusicAppDemo.main(args);
    }

    private static void configureLogFile() {
        try {
            File logDir = new File(getBaseDir(), "logs");
            if (!logDir.mkdirs() && !logDir.exists()) {
                return;
            }

            String timestamp = LocalDateTime.now(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
            File logFile = new File(logDir, "MusicAppDemo-" + timestamp + ".log");

            // Point SLF4J at stderr, then tee stderr/stdout into the log file. This
            // captures structured logging plus anything written directly to the
            // streams (stack traces, JavaFX messages, third-party libraries).
            if (redirectStreamsToFile(logFile)) {
                System.setProperty("org.slf4j.simpleLogger.logFile", "System.err");
            }
        } catch (SecurityException e) {
            // Fall back to the simplelogger.properties default rather than aborting startup
        }
    }

    private static File getBaseDir() {
        String userHome = System.getProperty("user.home");
        String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            return new File(userHome, "AppData\\Roaming\\MusicAppDemo");
        } else if (os.contains("mac")) {
            return new File(userHome, "Library/Application Support/MusicAppDemo");
        }
        return new File(userHome, ".config/MusicAppDemo");
    }

    private static boolean redirectStreamsToFile(File logFile) {
        try {
            OutputStream fileOut = new FileOutputStream(logFile, true);
            System.setErr(tee(System.err, fileOut));
            System.setOut(tee(System.out, fileOut));
            return true;
        } catch (IOException e) {
            // Could not open the log file; leave the streams untouched.
            return false;
        }
    }

    /**
     * Wraps a stream so every write is mirrored to both the original stream
     * (console) and the log file, without losing either.
     */
    private static PrintStream tee(OutputStream original, OutputStream fileOut) {
        OutputStream combined = new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                original.write(b);
                synchronized (fileOut) {
                    fileOut.write(b);
                }
            }

            @Override
            public void write(byte[] b, int off, int len) throws IOException {
                original.write(b, off, len);
                synchronized (fileOut) {
                    fileOut.write(b, off, len);
                }
            }

            @Override
            public void flush() throws IOException {
                original.flush();
                synchronized (fileOut) {
                    fileOut.flush();
                }
            }
        };
        return new PrintStream(combined, true, StandardCharsets.UTF_8);
    }

    private static void setAppIcon() {
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
            LOGGER.warn("Could not set the app icon, continuing with the default.", e);
        }
    }
}