package archipelago.phone.ui;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import archipelago.phone.R;
import archipelago.phone.net.CompanionState;
import archipelago.phone.net.PhoneSession;

import com.google.gson.JsonObject;

public class MainActivity extends AppCompatActivity implements PhoneSession.Listener {

    private EditText ipField;
    private TextView statusText;
    private TextView nowPlayingTitle;
    private TextView nowPlayingAlbum;
    private Button toggleButton;
    private Button nextButton;

    private PhoneSession session;
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
        // Real playback lands in M3.
    }
}