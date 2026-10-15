/*
 * SPDX-License-Identifier: MPL-2.0
 */
package app.remote;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RangeFileHandlerTest {

    private static final byte[] CONTENT = "0123456789abcdef".getBytes(StandardCharsets.UTF_8);

    @TempDir
    Path tempDir;

    private HttpServer server;
    private String baseUrl;

    @BeforeEach
    void startServer() throws Exception {
        Files.write(tempDir.resolve("track.mp3"), CONTENT);

        RootedPathResolver resolver = new RootedPathResolver(tempDir.toFile());
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/stream", new RangeFileHandler(resolver));
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/stream/";
    }

    private HttpResponse<byte[]> get(String path) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newHttpClient();
        return client.send(HttpRequest.newBuilder(URI.create(baseUrl + path)).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
    }

    @org.junit.jupiter.api.AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void fullGetReturns200WithEntireFile() throws Exception {
        HttpResponse<byte[]> response = get("track.mp3");
        assertEquals(200, response.statusCode());
        assertEquals("bytes", response.headers().firstValue("Accept-Ranges").orElse(""));
        assertEquals("audio/mpeg", response.headers().firstValue("Content-Type").orElse(""));
        assertEquals(CONTENT.length, response.body().length);
        org.junit.jupiter.api.Assertions.assertArrayEquals(CONTENT, response.body());
    }

    @Test
    void partialRangeReturns206() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "track.mp3"))
                .header("Range", "bytes=4-7").GET().build();
        HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(206, response.statusCode());
        assertEquals("bytes 4-7/" + CONTENT.length, response.headers().firstValue("Content-Range").orElse(""));
        assertEquals(4, response.body().length);
        org.junit.jupiter.api.Assertions.assertArrayEquals(
                new byte[]{'4', '5', '6', '7'}, response.body());
    }

    @Test
    void openEndedRangeStartsAtOffset() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "track.mp3"))
                .header("Range", "bytes=10-").GET().build();
        HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(206, response.statusCode());
        assertEquals("bytes 10-" + (CONTENT.length - 1) + "/" + CONTENT.length,
                response.headers().firstValue("Content-Range").orElse(""));
        assertEquals(CONTENT.length - 10, response.body().length);
    }

    @Test
    void suffixRangeReturnsLastBytes() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "track.mp3"))
                .header("Range", "bytes=-5").GET().build();
        HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(206, response.statusCode());
        assertEquals(5, response.body().length);
        assertEquals("bcdef", new String(response.body(), StandardCharsets.UTF_8));
    }

    @Test
    void outOfRangeReturns416() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "track.mp3"))
                .header("Range", "bytes=99-100").GET().build();
        HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(416, response.statusCode());
    }

    @Test
    void traversalIsBlocked() throws Exception {
        Path outside = tempDir.getParent().resolve("secret.txt");
        Files.write(outside, "topsecret".getBytes(StandardCharsets.UTF_8));
        try {
            HttpResponse<byte[]> response = get("..%2F" + outside.getFileName());
            assertEquals(404, response.statusCode());
        } finally {
            Files.deleteIfExists(outside);
        }
    }

    @Test
    void missingFileReturns404() throws Exception {
        assertEquals(404, get("nope.mp3").statusCode());
    }

    @Test
    void streamPathStaysWithinRoot() {
        RootedPathResolver resolver = new RootedPathResolver(tempDir.toFile());
        assertTrue(resolver.streamPath(tempDir.resolve("track.mp3").toFile()).startsWith("/stream/"));
        assertNull(resolver.streamPath(tempDir.getParent().toFile()));
        assertNull(resolver.streamPath(tempDir.resolve("..").resolve("track.mp3").toFile()));
    }
}