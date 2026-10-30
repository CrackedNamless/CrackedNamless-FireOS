package com.crackednamless.fireos;

import android.content.ComponentName;
import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import java.util.List;

public final class MediaMonitor {

    public static final class State {
        public final String title;
        public final String artist;
        public final String app;
        public final Bitmap art;
        public final boolean playing;
        public final float progress;

        State(String title, String artist, String app, Bitmap art, boolean playing, float progress) {
            this.title = title;
            this.artist = artist;
            this.app = app;
            this.art = art;
            this.playing = playing;
            this.progress = progress;
        }
    }

    public interface Listener {
        void onMedia(State state);
    }

    private static final long TICK_MS = 1000L;
    private static MediaMonitor active;

    private final Context context;
    private final Listener listener;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final MediaSessionManager manager;
    private final ComponentName component;
    private MediaController controller;

    private final MediaController.Callback callback = new MediaController.Callback() {
        @Override
        public void onPlaybackStateChanged(PlaybackState state) {
            publish();
        }

        @Override
        public void onMetadataChanged(MediaMetadata metadata) {
            publish();
        }

        @Override
        public void onSessionDestroyed() {
            pick();
        }
    };

    private final MediaSessionManager.OnActiveSessionsChangedListener sessions =
            controllers -> pick();

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            publish();
            handler.postDelayed(this, TICK_MS);
        }
    };

    public MediaMonitor(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
        this.manager = (MediaSessionManager) context.getSystemService(Context.MEDIA_SESSION_SERVICE);
        this.component = new ComponentName(context, MediaListener.class);
    }

    public void start() {
        active = this;
        try {
            manager.addOnActiveSessionsChangedListener(sessions, component);
        } catch (SecurityException e) {
            listener.onMedia(null);
            return;
        }
        pick();
        handler.postDelayed(tick, TICK_MS);
    }

    public void stop() {
        if (active == this) {
            active = null;
        }
        handler.removeCallbacks(tick);
        try {
            manager.removeOnActiveSessionsChangedListener(sessions);
        } catch (RuntimeException ignored) {
            // Mai registrato: accesso notifiche non concesso.
        }
        detach();
    }

    public void toggle() {
        if (controller == null) {
            return;
        }
        PlaybackState s = controller.getPlaybackState();
        if (s != null && s.getState() == PlaybackState.STATE_PLAYING) {
            controller.getTransportControls().pause();
        } else {
            controller.getTransportControls().play();
        }
    }

    static void refreshNow() {
        MediaMonitor m = active;
        if (m != null) {
            m.handler.post(m::pick);
        }
    }

    private void detach() {
        if (controller != null) {
            controller.unregisterCallback(callback);
            controller = null;
        }
    }

    private void pick() {
        List<MediaController> list;
        try {
            list = manager.getActiveSessions(component);
        } catch (SecurityException e) {
            detach();
            listener.onMedia(null);
            return;
        }
        MediaController best = null;
        for (MediaController c : list) {
            PlaybackState s = c.getPlaybackState();
            if (s == null || s.getState() == PlaybackState.STATE_NONE
                    || s.getState() == PlaybackState.STATE_STOPPED) {
                continue;
            }
            if (s.getState() == PlaybackState.STATE_PLAYING) {
                best = c;
                break;
            }
            if (best == null) {
                best = c;
            }
        }
        if (best == null || controller == null || !best.getSessionToken().equals(controller.getSessionToken())) {
            detach();
            if (best != null) {
                controller = best;
                controller.registerCallback(callback, handler);
            }
        }
        publish();
    }

    private void publish() {
        if (controller == null) {
            listener.onMedia(null);
            return;
        }
        MediaMetadata md = controller.getMetadata();
        PlaybackState ps = controller.getPlaybackState();
        String title = md != null ? md.getString(MediaMetadata.METADATA_KEY_TITLE) : null;
        if (title == null || title.isEmpty()) {
            listener.onMedia(null);
            return;
        }
        String artist = md.getString(MediaMetadata.METADATA_KEY_ARTIST);
        if (artist == null) {
            artist = md.getString(MediaMetadata.METADATA_KEY_ALBUM);
        }
        Bitmap art = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
        if (art == null) {
            art = md.getBitmap(MediaMetadata.METADATA_KEY_ART);
        }
        boolean playing = ps != null && ps.getState() == PlaybackState.STATE_PLAYING;
        float progress = 0f;
        long duration = md.getLong(MediaMetadata.METADATA_KEY_DURATION);
        if (ps != null && duration > 0) {
            long pos = ps.getPosition();
            if (playing) {
                pos += (long) ((SystemClock.elapsedRealtime() - ps.getLastPositionUpdateTime())
                        * ps.getPlaybackSpeed());
                pos = Math.min(pos, duration);
            }
            progress = Math.max(0f, Math.min(1f, pos / (float) duration));
        }
        listener.onMedia(new State(title, artist != null ? artist : "", controller.getPackageName(),
                art, playing, progress));
    }
}
