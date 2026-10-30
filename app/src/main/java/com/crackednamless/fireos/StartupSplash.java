package com.crackednamless.fireos;

import android.graphics.ImageDecoder;
import android.graphics.drawable.AnimatedImageDrawable;
import android.graphics.drawable.Animatable2;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.View;
import android.widget.ImageView;

import java.io.IOException;

public final class StartupSplash {

    private static final long MAX_MS = 4000L;
    private static final long FADE_MS = 300L;
    private static final int TARGET_W = 960;
    private static final int TARGET_H = 540;

    private final ImageView view;
    private AnimatedImageDrawable animation;

    public StartupSplash(ImageView view) {
        this.view = view;
    }

    public boolean isShowing() {
        return view.getVisibility() == View.VISIBLE;
    }

    public void play() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            return;
        }
        try {
            Drawable d = ImageDecoder.decodeDrawable(
                    ImageDecoder.createSource(view.getResources(), R.raw.startup),
                    (decoder, info, src) -> decoder.setTargetSize(TARGET_W, TARGET_H));
            if (!(d instanceof AnimatedImageDrawable)) {
                return;
            }
            animation = (AnimatedImageDrawable) d;
        } catch (IOException | RuntimeException e) {
            return;
        }
        animation.setRepeatCount(0);
        animation.registerAnimationCallback(new Animatable2.AnimationCallback() {
            @Override
            public void onAnimationEnd(Drawable drawable) {
                dismiss();
            }
        });
        view.setImageDrawable(animation);
        view.setAlpha(1f);
        view.setVisibility(View.VISIBLE);
        animation.start();
        view.postDelayed(this::dismiss, MAX_MS);
    }

    public void dismiss() {
        if (!isShowing()) {
            return;
        }
        view.animate().alpha(0f).setDuration(FADE_MS).withEndAction(() -> {
            view.setVisibility(View.GONE);
            if (animation != null) {
                animation.stop();
            }
            view.setImageDrawable(null);
            animation = null;
            Runtime.getRuntime().gc();
        }).start();
    }
}
