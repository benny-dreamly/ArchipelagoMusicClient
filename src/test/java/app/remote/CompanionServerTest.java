/*
 * SPDX-License-Identifier: MPL-2.0
 */
package app.remote;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompanionServerTest {

    @TempDir
    Path tempDir;

    private CompanionServer server;

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    void broadcastsStateAndDeliversCommands() throws Exception {
        Files.write(tempDir.resolve("track.mp3"), new byte[]{1, 2, 3});

        int httpPort = freePort();
        int wsPort = freePort();
        server = new CompanionServer(tempDir.toFile(), httpPort, wsPort);
        server.start();

        AtomicReference<String> receivedCommand = new AtomicReference<>();
        CountDownLatch commandLatch = new CountDownLatch(1);
        server.setCommandHandler(json -> {
            receivedCommand.set(json.toString());
            commandLatch.countDown();
        });

        CountDownLatch broadcastLatch = new CountDownLatch(1);
        AtomicReference<String> receivedState = new AtomicReference<>();
        WebSocketClient client = new WebSocketClient(new URI("ws://127.0.0.1:" + wsPort)) {
            @Override
            public void onOpen(ServerHandshake handshake) {
            }

            @Override
            public void onMessage(String message) {
                receivedState.set(message);
                broadcastLatch.countDown();
            }

            @Override
            public void onClose(int code, String reason, boolean remote) {
            }

            @Override
            public void onError(Exception ex) {
            }
        };
        client.connectBlocking();

        assertTrue(server.hasClients());

        String payload = "{\"songTitle\":\"Test Song\",\"playing\":true,\"queue\":[]}";
        server.broadcast(payload);
        assertTrue(broadcastLatch.await(3, TimeUnit.SECONDS));
        assertEquals(payload, receivedState.get());

        client.send("{\"cmd\":\"volume\",\"value\":42}");
        assertTrue(commandLatch.await(3, TimeUnit.SECONDS));
        assertTrue(receivedCommand.get().contains("\"cmd\":\"volume\""));

        client.close();
    }
}