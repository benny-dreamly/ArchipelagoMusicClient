/*
 * SPDX-License-Identifier: MPL-2.0
 */
package app.player;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ReversedAudioCache {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReversedAudioCache.class);

    private static final String FFMPEG = "ffmpeg";

    private final Path cacheDir;
    private final ExecutorService executor;
    private final Map<Path, CompletableFuture<Path>> inFlight = new ConcurrentHashMap<>();

    public ReversedAudioCache() {
        this(getCacheDir(), Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "reversed-audio");
            thread.setDaemon(true);
            return thread;
        }));
    }

    ReversedAudioCache(Path cacheDir, ExecutorService executor) {
        this.cacheDir = cacheDir;
        this.executor = executor;
    }

    /**
     * Returns a future for the reversed copy of the given audio file. If the copy is
     * already cached the future completes immediately; otherwise ffmpeg is scheduled
     * on a background thread and the result is cached for the session.
     */
    public CompletableFuture<Path> getReversedAsync(Path original) {
        if (cacheDir == null) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("Reversed-audio cache is unavailable"));
        }
        Path target = targetFor(original);
        if (Files.isRegularFile(target)) {
            return CompletableFuture.completedFuture(target);
        }
        return inFlight.computeIfAbsent(original, key -> CompletableFuture.supplyAsync(
                () -> reverse(key, target), executor));
    }

    Path targetFor(Path original) {
        return cacheDir.resolve(Integer.toHexString(original.toString().hashCode()) + ".m4a");
    }

    private Path reverse(Path original, Path target) {
        try {
            Process process = new ProcessBuilder(
                    FFMPEG, "-y", "-hide_banner", "-loglevel", "error",
                    "-i", original.toString(),
                    "-vn", "-af", "areverse",
                    "-c:a", "aac", "-b:a", "192k",
                    target.toString())
                    .redirectErrorStream(true)
                    .start();
            String output;
            try (InputStream stream = process.getInputStream()) {
                output = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            }
            int exit = process.waitFor();
            if (exit != 0 || !Files.isRegularFile(target)) {
                throw new IOException("ffmpeg exited with " + exit + ": " + output);
            }
            return target;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while reversing " + original, e);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to reverse " + original, e);
        }
    }

    private static Path getCacheDir() {
        try {
            return Files.createTempDirectory("MusicAppDemo-reversed-");
        } catch (IOException e) {
            Path fallback = Paths.get(System.getProperty("java.io.tmpdir"),
                    "MusicAppDemo-reversed-" + System.nanoTime());
            try {
                Files.createDirectories(fallback);
                return fallback;
            } catch (IOException e2) {
                LOGGER.warn("Could not create a reversed-audio cache directory; "
                        + "Audio Reversal traps will fall back to normal playback", e2);
                return null;
            }
        }
    }
}