package archipelago.phone.net;

import android.os.Handler;
import android.os.Looper;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Talks to the desktop's companion WebSocket server on port 8312.
 * State broadcasts are parsed into {@link CompanionState} and handed to the
 * listener on the main thread; desktop commands are forwarded raw so M3 can
 * drive a real player. All outbound requests are the desktop's existing
 * {@code {type:"command", cmd:...}} shape routed by CompanionServer to
 * {@code handleRemoteCommand}.
 */
public final class PhoneSession {

    public interface Listener {
        void onOpen();

        void onClose(String reason);

        void onState(CompanionState state);

        void onCommand(JsonObject command);
    }

    public static final int DEFAULT_PORT = 8312;
    public static final int HTTP_PORT = 8311;

    private static final Gson GSON = new Gson();

    private final Listener listener;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private OkHttpClient client;
    private WebSocket webSocket;
    private String host;

    public PhoneSession(Listener listener) {
        this.listener = listener;
    }

    public void connect(String host) {
        disconnect();
        this.host = host;
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .pingInterval(20, TimeUnit.SECONDS)
                .build();
        this.client = client;
        Request request = new Request.Builder()
                .url("ws://" + host + ":" + DEFAULT_PORT)
                .build();
        this.webSocket = client.newWebSocket(request, listener(host));
    }

    public String getHost() {
        return host;
    }

    public String streamUrl(String streamPath) {
        return "http://" + host + ":" + HTTP_PORT + streamPath;
    }

    public void disconnect() {
        if (webSocket != null) {
            webSocket.close(1000, "bye");
            webSocket = null;
        }
        if (client != null) {
            client.connectionPool().evictAll();
            client = null;
        }
    }

    public void sendToggle() {
        sendCommand("toggle");
    }

    public void sendPlay() {
        sendCommand("play");
    }

    public void sendPause() {
        sendCommand("pause");
    }

    public void sendNext() {
        sendCommand("next");
    }

    public void sendSeek(long positionMs) {
        JsonObject json = new JsonObject();
        json.addProperty("type", "command");
        json.addProperty("cmd", "seek");
        json.addProperty("positionMs", positionMs);
        sendPayload(json.toString());
    }

    public void sendVolume(int value) {
        JsonObject json = new JsonObject();
        json.addProperty("type", "command");
        json.addProperty("cmd", "volume");
        json.addProperty("value", value);
        sendPayload(json.toString());
    }

    public void sendCommand(String cmd) {
        JsonObject json = new JsonObject();
        json.addProperty("type", "command");
        json.addProperty("cmd", cmd);
        sendPayload(json.toString());
    }

    public void sendEvent(String name, long positionMs, long durationMs, String message) {
        JsonObject json = new JsonObject();
        json.addProperty("type", "event");
        json.addProperty("event", name);
        json.addProperty("positionMs", positionMs);
        if (durationMs >= 0) {
            json.addProperty("durationMs", durationMs);
        }
        if (message != null) {
            json.addProperty("message", message);
        }
        sendPayload(json.toString());
    }

    public void sendStarted(String streamPath, String title, long durationMs) {
        JsonObject json = new JsonObject();
        json.addProperty("type", "event");
        json.addProperty("event", "started");
        json.addProperty("positionMs", 0);
        json.addProperty("durationMs", durationMs);
        if (streamPath != null) {
            json.addProperty("streamPath", streamPath);
        }
        if (title != null) {
            json.addProperty("title", title);
        }
        sendPayload(json.toString());
    }

    private void sendPayload(String payload) {
        WebSocket ws = webSocket;
        if (ws != null) {
            ws.send(payload);
        }
    }

    private WebSocketListener listener(String host) {
        return new WebSocketListener() {
            @Override
            public void onOpen(@NotNull WebSocket webSocket, @NotNull Response response) {
                post(() -> listener.onOpen());
            }

            @Override
            public void onMessage(@NotNull WebSocket webSocket, @NotNull String text) {
                post(() -> handleMessage(text));
            }

            @Override
            public void onClosed(@NotNull WebSocket webSocket, int code, @NotNull String reason) {
                post(() -> listener.onClose(reason.isEmpty() ? "closed" : reason));
            }

            @Override
            public void onFailure(@NotNull WebSocket webSocket, @NotNull Throwable t,
                                  @Nullable Response response) {
                post(() -> listener.onClose(t.getMessage() == null ? "connection failed" : t.getMessage()));
            }
        };
    }

    private void handleMessage(String text) {
        try {
            JsonObject json = JsonParser.parseString(text).getAsJsonObject();
            if (json.has("type") && "command".equals(json.get("type").getAsString())) {
                listener.onCommand(json);
                return;
            }
            listener.onState(GSON.fromJson(json, CompanionState.class));
        } catch (Exception e) {
            // ignore malformed messages
        }
    }

    private void post(Runnable action) {
        mainHandler.post(action);
    }
}