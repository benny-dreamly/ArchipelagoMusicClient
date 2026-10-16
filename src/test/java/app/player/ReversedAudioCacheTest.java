/*
 * SPDX-License-Identifier: MPL-2.0
 */
package app.player;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReversedAudioCacheTest {

    private static final ExecutorService NOOP_EXECUTOR = new AbstractExecutorService() {
        @Override
        public void execute(Runnable command) {
        }

        @Override
        public void shutdown() {
        }

        @Override
        public List<Runnable> shutdownNow() {
            return List.of();
        }

        @Override
        public boolean isShutdown() {
            return true;
        }

        @Override
        public boolean isTerminated() {
            return true;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
            return true;
        }
    };

    @TempDir
    Path tempDir;

    @Test
    void targetFor_isDeterministicAndDistinctPerSource() {
        ReversedAudioCache cache = new ReversedAudioCache(tempDir, NOOP_EXECUTOR);
        Path sourceA = Paths.get("/music/a/song.m4a");
        Path sourceB = Paths.get("/music/b/song.m4a");

        assertEquals(cache.targetFor(sourceA), cache.targetFor(sourceA));
        assertNotEquals(cache.targetFor(sourceA), cache.targetFor(sourceB));
        assertTrue(cache.targetFor(sourceA).getFileName().toString().endsWith(".m4a"));
    }

    @Test
    void alreadyCached_returnsCompletedFuture() throws Exception {
        ReversedAudioCache cache = new ReversedAudioCache(tempDir, NOOP_EXECUTOR);
        Path source = tempDir.resolve("song.m4a");
        Files.writeString(source, "fake audio", StandardCharsets.UTF_8);
        Path target = cache.targetFor(source);
        Files.writeString(target, "fake reversed audio", StandardCharsets.UTF_8);

        CompletableFuture<Path> result = cache.getReversedAsync(source);

        assertTrue(result.isDone());
        assertEquals(target, result.join());
    }

    @Test
    void sameSourceWhileGenerationPending_sharesTheSameFuture() throws Exception {
        ReversedAudioCache cache = new ReversedAudioCache(tempDir, NOOP_EXECUTOR);
        Path source = tempDir.resolve("song.m4a");
        Files.writeString(source, "fake audio", StandardCharsets.UTF_8);

        CompletableFuture<Path> first = cache.getReversedAsync(source);
        assertFalse(first.isDone());

        assertSame(first, cache.getReversedAsync(source));
    }

    @Test
    void failedReversal_completesExceptionally() throws Exception {
        ReversedAudioCache cache = new ReversedAudioCache(tempDir,
                Executors.newSingleThreadExecutor(r -> {
                    Thread thread = new Thread(r, "reversed-audio-test");
                    thread.setDaemon(true);
                    return thread;
                }));
        Path missing = tempDir.resolve("does-not-exist.m4a");

        CompletableFuture<Path> result = cache.getReversedAsync(missing);

        assertThrows(ExecutionException.class, () -> result.get(30, TimeUnit.SECONDS));
    }
}
