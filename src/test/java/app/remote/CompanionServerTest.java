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
import java.util.List;
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
        WebSocketClient client = client(wsPort, receivedState, broadcastLatch);
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

    @Test
    void routesEventsToEventHandlerAndLegacyCommandsToCommandHandler() throws Exception {
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

        AtomicReference<String> receivedEvent = new AtomicReference<>();
        CountDownLatch eventLatch = new CountDownLatch(1);
        server.setEventHandler(json -> {
            receivedEvent.set(json.toString());
            eventLatch.countDown();
        });

        CountDownLatch ignored = new CountDownLatch(1);
        AtomicReference<String> state = new AtomicReference<>();
        WebSocketClient client = client(wsPort, state, ignored);
        client.connectBlocking();

        client.send("{\"type\":\"event\",\"event\":\"position\",\"positionMs\":9000}");
        assertTrue(eventLatch.await(3, TimeUnit.SECONDS));
        assertTrue(receivedEvent.get().contains("\"event\":\"position\""));
        assertTrue(receivedEvent.get().contains("\"type\":\"event\""));

        client.send("{\"type\":\"command\",\"cmd\":\"next\"}");
        assertTrue(commandLatch.await(3, TimeUnit.SECONDS));
        assertTrue(receivedCommand.get().contains("\"cmd\":\"next\""));

        client.close();
    }

    @Test
    void sendTargetsTheActivePhone() throws Exception {
        int httpPort = freePort();
        int wsPort = freePort();
        server = new CompanionServer(tempDir.toFile(), httpPort, wsPort);
        server.start();

        CountDownLatch firstMessage = new CountDownLatch(1);
        AtomicReference<String> first = new AtomicReference<>();
        WebSocketClient firstClient = client(wsPort, first, firstMessage);
        firstClient.connectBlocking();

        awaitActivePhone();
        assertTrue(server.hasActivePhone());

        CountDownLatch secondMessage = new CountDownLatch(1);
        AtomicReference<String> second = new AtomicReference<>();
        WebSocketClient secondClient = client(wsPort, second, secondMessage);
        secondClient.connectBlocking();

        String command = "{\"type\":\"command\",\"cmd\":\"play\",\"streamPath\":\"/stream/x.mp3\"}";
        server.send(command);

        assertTrue(firstMessage.await(3, TimeUnit.SECONDS));
        assertEquals(command, first.get());
        assertEquals(1, secondMessage.getCount(), "second phone must not receive the active send");

        firstClient.close();
        secondClient.close();
    }

    private void awaitActivePhone() throws InterruptedException {
        long deadline = System.currentTimeMillis() + 3_000;
        while (!server.hasActivePhone() && System.currentTimeMillis() < deadline) {
            Thread.sleep(5);
        }
    }

    @Test
    void stateJsonIncludesActiveSource() {
        CompanionState state = new CompanionState("Title", "Album", "/stream/x", 100, 50, true,
                70, List.of("Title"), CompanionState.SOURCE_PHONE);
        assertTrue(state.toJson().contains("\"activeSource\":\"phone\""));
    }

    private static WebSocketClient client(int wsPort, AtomicReference<String> target, CountDownLatch latch)
            throws Exception {
        return new WebSocketClient(new URI("ws://127.0.0.1:" + wsPort)) {
            @Override
            public void onOpen(ServerHandshake handshake) {
            }

            @Override
            public void onMessage(String message) {
                target.set(message);
                latch.countDown();
            }

            @Override
            public void onClose(int code, String reason, boolean remote) {
            }

            @Override
            public void onError(Exception ex) {
            }
        };
    }
}