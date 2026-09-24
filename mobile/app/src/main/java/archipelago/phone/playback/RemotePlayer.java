package archipelago.phone.playback;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.ProgressiveMediaSource;

import android.net.Uri;

/**
 * Streams songs from the desktop's HTTP server (Range requests handled by
 * ExoPlayer's progressive source) and reports player truth back to the
 * desktop as events: {@code started}, {@code position}, {@code paused},
 * {@code resumed}, {@code ended}, {@code error}. The desktop stays the
 * session owner — this class only executes the commands it receives.
 */
public final class RemotePlayer {

    public interface Listener {
        void onStarted(String streamPath, String title, long durationMs);

        void onPosition(long positionMs);

        void onPaused(long positionMs);

        void onResumed(long positionMs);

        void onEnded(long positionMs);

        void onError(String message);

        void onLoadingChanged(boolean isLoading);
    }

    private final Context context;
    private final Listener listener;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Runnable positionTicker = new Runnable() {
        @Override
        public void run() {
            if (player != null && player.isPlaying()) {
                listener.onPosition(player.getCurrentPosition());
            }
            mainHandler.postDelayed(this, 500);
        }
    };

    private ExoPlayer player;
    private boolean prepared;
    private boolean wasPlaying;
    private String currentStreamPath;
    private String currentTitle;

    public RemotePlayer(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
    }

    public void play(String streamUrl, String streamPath, String title) {
        stopTicker();
        if (player != null) {
            player.release();
        }
        prepared = false;
        wasPlaying = false;
        currentStreamPath = streamPath;
        currentTitle = title;

        player = buildPlayer();
        DefaultHttpDataSource.Factory dataSourceFactory = new DefaultHttpDataSource.Factory();
        MediaItem item = new MediaItem.Builder().setUri(Uri.parse(streamUrl)).build();
        ProgressiveMediaSource source = new ProgressiveMediaSource.Factory(dataSourceFactory)
                .createMediaSource(item);
        player.setMediaSource(source);
        player.prepare();
        player.setPlayWhenReady(true);
        mainHandler.postDelayed(positionTicker, 500);
    }

    public void pause() {
        if (player != null) {
            player.setPlayWhenReady(false);
        }
    }

    public void resume() {
        if (player != null) {
            player.setPlayWhenReady(true);
            mainHandler.removeCallbacks(positionTicker);
            mainHandler.postDelayed(positionTicker, 500);
        }
    }

    public void stop() {
        stopTicker();
        if (player != null) {
            player.pause();
            player.clearMediaItems();
            player.release();
            player = null;
        }
        prepared = false;
        wasPlaying = false;
        currentStreamPath = null;
        currentTitle = null;
    }

    public void seek(long positionMs) {
        if (player != null) {
            player.seekTo(positionMs);
        }
    }

    public void setRate(double rate) {
        if (player != null) {
            player.setPlaybackSpeed((float) rate);
        }
    }

    public void setVolume(int volumePercent) {
        if (player != null) {
            player.setVolume(volumePercent / 100f);
        }
    }

    public void release() {
        stopTicker();
        if (player != null) {
            player.release();
            player = null;
        }
    }

    private ExoPlayer buildPlayer() {
        ExoPlayer player = new ExoPlayer.Builder(context).build();
        player.setWakeMode(C.WAKE_MODE_LOCAL);
        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_READY && !prepared) {
                    prepared = true;
                    listener.onStarted(currentStreamPath, currentTitle, player.getDuration());
                } else if (state == Player.STATE_ENDED) {
                    stopTicker();
                    listener.onEnded(player.getCurrentPosition());
                }
            }

            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                if (!prepared) return;
                if (wasPlaying && !isPlaying) {
                    listener.onPaused(player.getCurrentPosition());
                } else if (!wasPlaying && isPlaying) {
                    listener.onResumed(player.getCurrentPosition());
                }
                wasPlaying = isPlaying;
            }

            @Override
            public void onPlayerError(PlaybackException error) {
                String message = error.getMessage() == null ? "Playback error" : error.getMessage();
                listener.onError(message);
            }

            @Override
            public void onLoadingChanged(boolean isLoading) {
                listener.onLoadingChanged(isLoading);
            }
        });
        return player;
    }

    private void stopTicker() {
        mainHandler.removeCallbacks(positionTicker);
    }
}