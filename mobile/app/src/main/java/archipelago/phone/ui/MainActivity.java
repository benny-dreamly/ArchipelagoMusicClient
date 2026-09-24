package archipelago.phone.ui;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import archipelago.phone.R;
import archipelago.phone.net.CompanionState;
import archipelago.phone.net.PhoneSession;
import archipelago.phone.playback.RemotePlayer;

import com.google.gson.JsonObject;

public class MainActivity extends AppCompatActivity implements PhoneSession.Listener {

    private EditText ipField;
    private TextView statusText;
    private TextView nowPlayingTitle;
    private TextView nowPlayingAlbum;
    private Button toggleButton;
    private Button nextButton;

    private PhoneSession session;
    private RemotePlayer remotePlayer;
    private boolean playing;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ipField = findViewById(R.id.ipField);
        statusText = findViewById(R.id.statusText);
        nowPlayingTitle = findViewById(R.id.nowPlayingTitle);
        nowPlayingAlbum = findViewById(R.id.nowPlayingAlbum);
        toggleButton = findViewById(R.id.toggleButton);
        nextButton = findViewById(R.id.nextButton);

        session = new PhoneSession(this);
        remotePlayer = new RemotePlayer(this, playerEvents);

        findViewById(R.id.connectButton).setOnClickListener(v -> connect());
        toggleButton.setOnClickListener(v -> session.sendToggle());
        nextButton.setOnClickListener(v -> session.sendNext());
    }

    private void connect() {
        String host = ipField.getText().toString().trim();
        if (host.isEmpty()) {
            statusText.setText(R.string.status_no_host);
            return;
        }
        statusText.setText(R.string.status_connecting);
        session.connect(host);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        remotePlayer.release();
        session.disconnect();
    }

    @Override
    public void onOpen() {
        statusText.setText(R.string.status_connected);
    }

    @Override
    public void onClose(String reason) {
        statusText.setText(getString(R.string.status_disconnected, reason));
        playing = false;
        toggleButton.setText(R.string.play_pause);
        remotePlayer.release();
    }

    @Override
    public void onState(CompanionState state) {
        playing = state.playing;
        nowPlayingTitle.setText(
                state.songTitle == null || state.songTitle.isEmpty()
                        ? getString(R.string.now_playing_none) : state.songTitle);
        nowPlayingAlbum.setText(state.album == null ? "" : state.album);
        toggleButton.setText(playing ? R.string.pause : R.string.play);
        statusText.setText(playing ? R.string.status_playing : R.string.status_paused);
    }

    @Override
    public void onCommand(JsonObject command) {
        String cmd = command.has("cmd") ? command.get("cmd").getAsString() : "";
        switch (cmd) {
            case "play" -> {
                String streamPath = command.has("streamPath")
                        ? command.get("streamPath").getAsString() : null;
                String title = command.has("title")
                        ? command.get("title").getAsString() : null;
                if (streamPath != null) {
                    remotePlayer.play(session.streamUrl(streamPath), streamPath, title);
                }
            }
            case "pause" -> remotePlayer.pause();
            case "resume" -> remotePlayer.resume();
            case "stop" -> remotePlayer.stop();
            case "seek" -> {
                if (command.has("positionMs")) {
                    remotePlayer.seek(command.get("positionMs").getAsLong());
                }
            }
            case "rate" -> {
                if (command.has("rate")) {
                    remotePlayer.setRate(command.get("rate").getAsDouble());
                }
            }
            case "volume" -> {
                if (command.has("value")) {
                    remotePlayer.setVolume(command.get("value").getAsInt());
                }
            }
            default -> {
            }
        }
    }

    private final RemotePlayer.Listener playerEvents = new RemotePlayer.Listener() {
        @Override
        public void onStarted(String streamPath, String title, long durationMs) {
            session.sendStarted(streamPath, title, durationMs);
        }

        @Override
        public void onPosition(long positionMs) {
            session.sendEvent("position", positionMs, -1, null);
        }

        @Override
        public void onPaused(long positionMs) {
            session.sendEvent("paused", positionMs, -1, null);
        }

        @Override
        public void onResumed(long positionMs) {
            session.sendEvent("resumed", positionMs, -1, null);
        }

        @Override
        public void onEnded(long positionMs) {
            session.sendEvent("ended", positionMs, -1, null);
        }

        @Override
        public void onError(String message) {
            session.sendEvent("error", 0, -1, message);
        }
    };
}