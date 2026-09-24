/*
 * SPDX-License-Identifier: MPL-2.0
 */
package app.remote;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Exposes the desktop player over the LAN: an HTTP file server (Range
 * support via {@link RangeFileHandler}) plus a WebSocket server that
 * broadcasts {@link CompanionState} snapshots and receives phone commands.
 */
public final class CompanionServer {

    private static final Logger LOGGER = LoggerFactory.getLogger(CompanionServer.class);

    private final RootedPathResolver resolver;
    private final int httpPort;
    private final int wsPort;

    private HttpServer httpServer;
    private WebSocketServer wsServer;
    private ExecutorService httpPool;
    private volatile Consumer<JsonObject> commandHandler;
    private volatile Consumer<JsonObject> eventHandler;
    private volatile WebSocket activePhone;

    public CompanionServer(File musicRoot, int httpPort, int wsPort) {
        this.resolver = new RootedPathResolver(musicRoot);
        this.httpPort = httpPort;
        this.wsPort = wsPort;
    }

    public void start() throws Exception {
        stop();

        httpPool = Executors.newCachedThreadPool();
        httpServer = HttpServer.create(new InetSocketAddress(httpPort), 64);
        httpServer.createContext("/stream", new RangeFileHandler(resolver));
        httpServer.setExecutor(httpPool);
        httpServer.start();

        wsServer = new WebSocketServer(new InetSocketAddress(wsPort)) {
            @Override
            public void onOpen(WebSocket conn, ClientHandshake handshake) {
                LOGGER.info("Companion: phone connected from {}", conn.getRemoteSocketAddress());
                assignActivePhone();
            }

            @Override
            public void onClose(WebSocket conn, int code, String reason, boolean remote) {
                LOGGER.info("Companion: phone disconnected ({})", reason);
                assignActivePhone();
            }

            @Override
            public void onMessage(WebSocket conn, String message) {
                handleMessage(message);
            }

            @Override
            public void onError(WebSocket conn, Exception ex) {
                LOGGER.warn("Companion: websocket error: {}", ex.getMessage());
            }

            @Override
            public void onStart() {
                LOGGER.info("Companion: websocket server up on port {}", wsPort);
            }
        };
        wsServer.start();

        LOGGER.info("Companion server started. HTTP on port {}, websocket on port {}.",
                httpPort, wsPort);
        LOGGER.info("Companion: phones can reach the desktop at {}", reachableAddresses());
    }

    public void stop() {
        if (httpServer != null) {
            httpServer.stop(0);
            httpServer = null;
        }
        if (httpPool != null) {
            httpPool.shutdownNow();
            httpPool = null;
        }
        if (wsServer != null) {
            try {
                wsServer.stop(100);
            } catch (Exception e) {
                LOGGER.debug("Companion: websocket stop error: {}", e.getMessage());
            }
            wsServer = null;
        }
    }

    public void broadcast(String json) {
        WebSocketServer server = wsServer;
        if (server == null) return;
        List<WebSocket> targets = new ArrayList<>(server.getConnections());
        for (WebSocket conn : targets) {
            if (conn.isOpen()) {
                conn.send(json);
            }
        }
    }

    public boolean hasClients() {
        WebSocketServer server = wsServer;
        if (server == null) return false;
        return !server.getConnections().isEmpty();
    }

    public void setCommandHandler(Consumer<JsonObject> handler) {
        this.commandHandler = handler;
    }

    public void setEventHandler(Consumer<JsonObject> handler) {
        this.eventHandler = handler;
    }

    public boolean hasActivePhone() {
        WebSocket phone = activePhone;
        return phone != null && phone.isOpen();
    }

    /**
     * Sends a payload to the active phone (the first one that connected).
     * No-op when no phone is connected. Used to deliver PhonePlayback
     * commands without touching the broadcast path.
     */
    public void send(String json) {
        WebSocket phone = activePhone;
        if (phone != null && phone.isOpen()) {
            phone.send(json);
        }
    }

    private void assignActivePhone() {
        WebSocket current = activePhone;
        if (current != null && current.isOpen()) {
            return;
        }
        WebSocketServer server = wsServer;
        WebSocket picked = null;
        if (server != null) {
            for (WebSocket conn : server.getConnections()) {
                if (conn.isOpen()) {
                    picked = conn;
                    break;
                }
            }
        }
        activePhone = picked;
    }

    public String streamPath(File file) {
        return resolver.streamPath(file);
    }

    private void handleMessage(String message) {
        try {
            JsonObject json = JsonParser.parseString(message).getAsJsonObject();
            String type = json.has("type") ? json.get("type").getAsString() : "command";
            if ("event".equals(type)) {
                Consumer<JsonObject> handler = eventHandler;
                if (handler != null) {
                    handler.accept(json);
                }
                return;
            }
            Consumer<JsonObject> handler = commandHandler;
            if (handler != null) {
                handler.accept(json);
            }
        } catch (Exception e) {
            LOGGER.debug("Companion: malformed message: {}", message);
        }
    }

    static List<String> reachableAddresses() {
        List<String> addresses = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface netIf = interfaces.nextElement();
                if (!netIf.isUp() || netIf.isLoopback() || netIf.isVirtual()) continue;
                Enumeration<InetAddress> inet = netIf.getInetAddresses();
                while (inet.hasMoreElements()) {
                    InetAddress address = inet.nextElement();
                    if (address instanceof Inet4Address) {
                        addresses.add(address.getHostAddress());
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.debug("Companion: could not enumerate interfaces: {}", e.getMessage());
        }
        return addresses;
    }
}