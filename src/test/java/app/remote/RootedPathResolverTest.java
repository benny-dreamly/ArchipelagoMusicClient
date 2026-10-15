/*
 * SPDX-License-Identifier: MPL-2.0
 */
package app.remote;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class RootedPathResolverTest {

    @TempDir
    Path tempDir;

    @Test
    void singleRootResolvesAndMapsBack() throws Exception {
        Path song = tempDir.resolve("album/song.mp3");
        Files.createDirectories(song.getParent());
        Files.write(song, new byte[]{1});

        RootedPathResolver resolver = new RootedPathResolver(tempDir.toFile());

        assertEquals(song.toFile(), resolver.resolve("/album/song.mp3"));
        assertEquals("/stream/album/song.mp3", resolver.streamPath(song.toFile()));
    }

    @Test
    void multipleRootsResolveFromAnyFolder() throws Exception {
        Path albumA = Files.createDirectories(tempDir.resolve("a"));
        Path albumB = Files.createDirectories(tempDir.resolve("b"));
        Files.write(albumA.resolve("one.mp3"), new byte[]{1});
        Files.write(albumB.resolve("two.mp3"), new byte[]{2});

        RootedPathResolver resolver =
                new RootedPathResolver(List.of(albumA.toFile(), albumB.toFile()));

        assertNotNull(resolver.resolve("/one.mp3"));
        assertNotNull(resolver.resolve("/two.mp3"));
        assertEquals("/stream/one.mp3", resolver.streamPath(albumA.resolve("one.mp3").toFile()));
        assertEquals("/stream/two.mp3", resolver.streamPath(albumB.resolve("two.mp3").toFile()));
    }

    @Test
    void rejectsPathsOutsideRootsAndUnmappedFiles() throws Exception {
        Path album = Files.createDirectories(tempDir.resolve("album"));
        Path outside = Files.createDirectories(tempDir.resolve("outside"));
        Files.write(outside.resolve("secret.mp3"), new byte[]{1});

        RootedPathResolver resolver = new RootedPathResolver(album.toFile());

        assertNull(resolver.resolve("/../outside/secret.mp3"));
        assertNull(resolver.streamPath(outside.resolve("secret.mp3").toFile()));
    }

    @Test
    void rejectsSymlinksThatEscapeTheRoot() throws Exception {
        Path album = Files.createDirectories(tempDir.resolve("album"));
        Path outside = Files.createDirectories(tempDir.resolve("outside"));
        Files.write(outside.resolve("secret.mp3"), new byte[]{1});
        try {
            Files.createSymbolicLink(album.resolve("escape.mp3"), outside.resolve("secret.mp3"));
        } catch (UnsupportedOperationException | IOException e) {
            Assumptions.abort("symlinks unavailable: " + e.getMessage());
        }

        RootedPathResolver resolver = new RootedPathResolver(album.toFile());

        assertNull(resolver.resolve("/escape.mp3"));
    }
}
