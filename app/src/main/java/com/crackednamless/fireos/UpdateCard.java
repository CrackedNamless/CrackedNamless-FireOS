package com.crackednamless.fireos;

import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.LinearInterpolator;
import android.widget.TextView;

// Avviso di aggiornamento piccolo in basso a destra, sopra qualsiasi app. Per 20 s prende il focus: OK aggiorna, Indietro rimanda.
// Senza risposta scade e vale come "Piu' tardi".
final class UpdateCard {

    interface Callback {
        void onUpdateNow();

        void onLater();
    }

    private static final long OFFER_MS = 20_000L;
    private static final long MESSAGE_MS = 6_000L;

    private final Context ui;
    private final WindowManager windowManager;
    private final Callback callback;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private View view;
    private WindowManager.LayoutParams params;
    private boolean offering;
    private ObjectAnimator countdown;

    private final Runnable timeout = this::expire;

    private final Runnable close = this::dismiss;

    UpdateCard(Context service, WindowManager windowManager, Callback callback) {
        this.ui = Scaling.wrap(service);
        this.windowManager = windowManager;
        this.callback = callback;
    }

    private void expire() {
        if (offering) {
            dismiss();
            callback.onLater();
        }
    }

    boolean isShowing() {
        return view != null;
    }

    void offer(String version) {
        if (view != null) {
            return;
        }
        View card = create();
        ((TextView) card.findViewById(R.id.update_card_title)).setText(ui.getString(R.string.update_title, version));
        ((TextView) card.findViewById(R.id.update_card_hint)).setText(R.string.update_hint);
        card.setFocusable(true);
        card.setFocusableInTouchMode(true);
        card.setOnKeyListener((v, keyCode, event) -> onKey(event));
        if (!attach(card, true)) {
            return;
        }
        card.requestFocus();
        offering = true;
        View timer = card.findViewById(R.id.update_card_timer);
        timer.post(() -> {
            timer.setPivotX(0f);
            countdown = ObjectAnimator.ofFloat(timer, View.SCALE_X, 1f, 0f);
            countdown.setDuration(OFFER_MS);
            countdown.setInterpolator(new LinearInterpolator());
            countdown.start();
        });
        handler.postDelayed(timeout, OFFER_MS);
    }

    // Durante l'installazione e per gli errori: stesso riquadro, senza tasti.
    void showMessage(String message, boolean autoClose) {
        if (view == null) {
            View card = create();
            if (!attach(card, false)) {
                return;
            }
        } else if (params != null && view.getParent() != null) {
            params.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
            try {
                windowManager.updateViewLayout(view, params);
            } catch (RuntimeException ignored) {
                // Finestra gia' rimossa dal sistema.
            }
        }
        offering = false;
        handler.removeCallbacks(timeout);
        stopCountdown();
        ((TextView) view.findViewById(R.id.update_card_title)).setText(message);
        view.findViewById(R.id.update_card_hint).setVisibility(View.GONE);
        view.findViewById(R.id.update_card_actions).setVisibility(View.GONE);
        view.findViewById(R.id.update_card_timer).setVisibility(View.GONE);
        handler.removeCallbacks(close);
        if (autoClose) {
            handler.postDelayed(close, MESSAGE_MS);
        }
    }

    void dismiss() {
        offering = false;
        handler.removeCallbacks(timeout);
        handler.removeCallbacks(close);
        stopCountdown();
        if (view != null && view.getParent() != null) {
            try {
                windowManager.removeView(view);
            } catch (RuntimeException ignored) {
                // Gia' rimosso dal sistema.
            }
        }
        view = null;
        params = null;
    }

    // Il filtro tasti dell'accessibilita' non riceve il telecomando su Fire OS: durante l'offerta la finestra prende il focus.
    // OK aggiorna; Indietro o qualsiasi altro tasto di navigazione vale "Piu' tardi" (il tasto non arriva all'app).
    private boolean onKey(KeyEvent event) {
        if (!offering) {
            return false;
        }
        if (event.getAction() != KeyEvent.ACTION_DOWN || event.getRepeatCount() != 0) {
            return true;
        }
        int key = event.getKeyCode();
        if (key == KeyEvent.KEYCODE_DPAD_CENTER || key == KeyEvent.KEYCODE_ENTER) {
            handler.removeCallbacks(timeout);
            callback.onUpdateNow();
        } else {
            dismiss();
            callback.onLater();
        }
        return true;
    }

    private View create() {
        return LayoutInflater.from(ui).inflate(R.layout.update_card, null);
    }

    private boolean attach(View card, boolean focusable) {
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                (focusable ? 0 : WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.BOTTOM | Gravity.RIGHT;
        float density = ui.getResources().getDisplayMetrics().density;
        lp.x = (int) (36 * density);
        lp.y = (int) (30 * density);
        try {
            windowManager.addView(card, lp);
        } catch (RuntimeException e) {
            return false;
        }
        view = card;
        params = lp;
        return true;
    }

    private void stopCountdown() {
        if (countdown != null) {
            countdown.cancel();
            countdown = null;
        }
    }
}
