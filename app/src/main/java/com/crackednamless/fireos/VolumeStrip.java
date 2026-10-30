package com.crackednamless.fireos;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

public final class VolumeStrip {

    private static final String VOLUME_CHANGED = "android.media.VOLUME_CHANGED_ACTION";
    private static final String EXTRA_STREAM = "android.media.EXTRA_VOLUME_STREAM_TYPE";
    private static final long HIDE_MS = 1800L;

    private final Context context;
    private final AudioManager audio;
    private final View strip;
    private final View track;
    private final View fill;
    private final TextView value;
    private final ImageView icon;
    private final Handler main = new Handler(Looper.getMainLooper());
    private boolean running;

    private final Runnable hide = new Runnable() {
        @Override
        public void run() {
            strip.setVisibility(View.GONE);
        }
    };

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent intent) {
            if (intent.getIntExtra(EXTRA_STREAM, -1) == AudioManager.STREAM_MUSIC) {
                show();
            }
        }
    };

    public VolumeStrip(Context context, View root) {
        this.context = context.getApplicationContext();
        this.audio = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        strip = root.findViewById(R.id.volume_strip);
        track = root.findViewById(R.id.volume_track);
        fill = root.findViewById(R.id.volume_fill);
        value = root.findViewById(R.id.volume_value);
        icon = root.findViewById(R.id.volume_icon);
        float d = context.getResources().getDisplayMetrics().density;
        GradientDrawable trackBg = new GradientDrawable();
        trackBg.setCornerRadius(4 * d);
        trackBg.setColor(0x33FFFFFF);
        track.setBackground(trackBg);
        GradientDrawable fillBg = new GradientDrawable();
        fillBg.setCornerRadius(4 * d);
        fillBg.setColor(0xFFFFFFFF);
        fill.setBackground(fillBg);
    }

    public void start() {
        if (running) {
            return;
        }
        running = true;
        context.registerReceiver(receiver, new IntentFilter(VOLUME_CHANGED));
    }

    public void stop() {
        if (!running) {
            return;
        }
        running = false;
        main.removeCallbacks(hide);
        strip.setVisibility(View.GONE);
        try {
            context.unregisterReceiver(receiver);
        } catch (IllegalArgumentException ignored) {
            // Gia' rimosso.
        }
    }

    private int current() {
        return audio == null ? 0 : audio.getStreamVolume(AudioManager.STREAM_MUSIC);
    }

    private void show() {
        int max = Math.max(1, audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
        int level = current();
        boolean muted = level == 0 || audio.isStreamMute(AudioManager.STREAM_MUSIC);
        value.setText(String.valueOf(level));
        icon.setImageResource(muted ? R.drawable.ic_volume_mute : R.drawable.ic_volume);
        ViewGroup.LayoutParams lp = fill.getLayoutParams();
        int trackH = track.getHeight() > 0 ? track.getHeight() : (int) (150 * context.getResources().getDisplayMetrics().density);
        lp.height = Math.round(trackH * (float) level / max);
        fill.setLayoutParams(lp);
        strip.setVisibility(View.VISIBLE);
        main.removeCallbacks(hide);
        main.postDelayed(hide, HIDE_MS);
    }
}
