package archipelago.phone.ui;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import archipelago.phone.R;
import archipelago.phone.net.CompanionState;
import archipelago.phone.net.PhoneSession;
import archipelago.phone.playback.RemotePlayer;

import com.google.gson.JsonObject;

import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements PhoneSession.Listener {

    private static final int MAX_RECONNECT_ATTEMPTS = 8;

    private EditText ipField;
    private TextView statusText;
    private TextView bufferStatus;
    private TextView nowPlayingTitle;
    private TextView nowPlayingAlbum;
    private TextView timeText;
    private TextView queueList;
    private Button toggleButton;
    private Button nextButton;
    private SeekBar seekBar;
    private SeekBar volumeBar;

    private PhoneSession session;
    private RemotePlayer remotePlayer;
    private final Handler uiHandler = new Handler(Looper.getMainLooper());

    private String host;
    private boolean playing;
    private boolean isDraggingSeek;
    private boolean adjustingVolume;
    private int reconnectAttempts;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ipField = findViewById(R.id.ipField);
        statusText = findViewById(R.id.statusText);
        bufferStatus = findViewById(R.id.bufferStatus);
        nowPlayingTitle = findViewById(R.id.nowPlayingTitle);
        nowPlayingAlbum = findViewById(R.id.nowPlayingAlbum);
        timeText = findViewById(R.id.timeText);
        queueList = findViewById(R.id.queueList);
        toggleButton = findViewById(R.id.toggleButton);
        nextButton = findViewById(R.id.nextButton);
        seekBar = findViewById(R.id.seekBar);
        volumeBar = findViewById(R.id.volumeBar);

        session = new PhoneSession(this);
        remotePlayer = new RemotePlayer(this, playerEvents);

        findViewById(R.id.connectButton).setOnClickListener(v -> connect());
        toggleButton.setOnClickListener(v -> session.sendToggle());
        nextButton.setOnClickListener(v -> session.sendNext());

        setUpSeekBar();
        setUpVolumeBar();
    }

    private void setUpSeekBar() {
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (fromUser) {
                    updateTimeText(progress, bar.getMax());
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar bar) {
                isDraggingSeek = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar bar) {
                isDraggingSeek = false;
                session.sendSeek(bar.getProgress());
            }
        });
    }

    private void setUpVolumeBar() {
        volumeBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (fromUser && !adjustingVolume) {
                    session.sendVolume(progress);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar bar) {
                // no-op
            }

            @Override
            public void onStopTrackingTouch(SeekBar bar) {
                // send on change
            }
        });
    }

    private void connect() {
        host = ipField.getText().toString().trim();
        if (host.isEmpty()) {
            statusText.setText(R.string.status_no_host);
            return;
        }
        reconnectAttempts = 0;
        uiHandler.removeCallbacks(reconnectTask);
        statusText.setText(R.string.status_connecting);
        session.checkHealth(host, reachable -> {
            if (reachable) {
                session.connect(host);
            } else {
                statusText.setText(getString(R.string.status_no_desktop, host));
                Toast.makeText(MainActivity.this, getString(R.string.status_no_desktop, host),
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        uiHandler.removeCallbacks(reconnectTask);
        remotePlayer.release();
        session.disconnect();
    }

    @Override
    public void onOpen() {
        reconnectAttempts = 0;
        uiHandler.removeCallbacks(reconnectTask);
        statusText.setText(R.string.status_connected);
        bufferStatus.setVisibility(TextView.GONE);
    }

    @Override
    public void onClose(String reason) {
        statusText.setText(getString(R.string.status_reconnecting, reason));
        bufferStatus.setVisibility(TextView.GONE);
        playing = false;
        toggleButton.setText(R.string.play_pause);
        remotePlayer.release();
        if (host != null && reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
            reconnectAttempts++;
            long delay = Math.min(1_000L * (1L << (reconnectAttempts - 1)), 30_000L);
            uiHandler.removeCallbacks(reconnectTask);
            uiHandler.postDelayed(reconnectTask, delay);
        }
    }

    private final Runnable reconnectTask = () -> {
        if (host != null && reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
            session.connect(host);
        } else {
            statusText.setText(R.string.status_reconnect_stopped);
        }
    };

    @Override
    public void onState(CompanionState state) {
        playing = state.playing;
        nowPlayingTitle.setText(
                state.songTitle == null || state.songTitle.isEmpty()
                        ? getString(R.string.now_playing_none) : state.songTitle);
        nowPlayingAlbum.setText(state.album == null ? "" : state.album);
        toggleButton.setText(playing ? R.string.pause : R.string.play);
        statusText.setText(playing ? R.string.status_playing : R.string.status_paused);

        if (state.durationMs > 0) {
            seekBar.setMax((int) state.durationMs);
        }
        if (!isDraggingSeek) {
            seekBar.setProgress((int) state.positionMs);
            updateTimeText(state.positionMs, state.durationMs);
        }
        adjustingVolume = true;
        volumeBar.setProgress(state.volume);
        adjustingVolume = false;

        queueList.setText(queueText(state.queue));
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
                    adjustingVolume = true;
                    volumeBar.setProgress(command.get("value").getAsInt());
                    adjustingVolume = false;
                }
            }
            default -> {
            }
        }
    }

    private final RemotePlayer.Listener playerEvents = new RemotePlayer.Listener() {
        @Override
        public void onStarted(String streamPath, String title, long durationMs) {
            if (durationMs > 0) {
                seekBar.setMax((int) durationMs);
            }
            seekBar.setProgress(0);
            updateTimeText(0, durationMs);
            session.sendStarted(streamPath, title, durationMs);
        }

        @Override
        public void onPosition(long positionMs) {
            if (!isDraggingSeek) {
                seekBar.setProgress((int) positionMs);
                updateTimeText(positionMs, seekBar.getMax());
            }
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
            Toast.makeText(MainActivity.this, getString(R.string.error_playback, message),
                    Toast.LENGTH_LONG).show();
        }

        @Override
        public void onLoadingChanged(boolean isLoading) {
            bufferStatus.setVisibility(isLoading ? TextView.VISIBLE : TextView.GONE);
        }
    };

    private static String queueText(List<String> queue) {
        if (queue == null || queue.isEmpty()) {
            return "";
        }
        return String.join("\n", queue);
    }

    private void updateTimeText(long positionMs, long durationMs) {
        timeText.setText(formatTime(positionMs) + " / " + formatTime(durationMs));
    }

    private static String formatTime(long millis) {
        long totalSeconds = Math.max(0, millis) / 1000;
        return String.format(Locale.US, "%d:%02d", totalSeconds / 60, totalSeconds % 60);
    }
}