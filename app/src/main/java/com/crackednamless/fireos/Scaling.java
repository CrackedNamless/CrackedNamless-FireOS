package com.crackednamless.fireos;

import android.content.Context;
import android.content.res.Configuration;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.View;
import android.view.WindowManager;

public final class Scaling {

    private static final float DESIGN_W_DP = 960f;
    private static final float DESIGN_H_DP = 540f;
    private static final float SAFE_X = 0.058f;
    private static final float SAFE_Y = 0.052f;

    private Scaling() {
    }

    /** Fa corrispondere lo schermo a 960x540 dp, qualunque sia la risoluzione o la densita' dichiarata dal TV. */
    public static Context wrap(Context base) {
        WindowManager wm = (WindowManager) base.getSystemService(Context.WINDOW_SERVICE);
        Display display = wm.getDefaultDisplay();
        DisplayMetrics real = new DisplayMetrics();
        display.getRealMetrics(real);
        float scale = Math.min(real.widthPixels / DESIGN_W_DP, real.heightPixels / DESIGN_H_DP);
        Configuration config = new Configuration(base.getResources().getConfiguration());
        config.densityDpi = Math.max(80, Math.min(640, Math.round(160f * scale)));
        config.fontScale = 1f;
        return base.createConfigurationContext(config);
    }

    /** Margini di sicurezza proporzionali alle dimensioni reali del contenitore. */
    public static void applySafeArea(View content) {
        content.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            int w = r - l;
            int h = b - t;
            int px = Math.round(w * SAFE_X);
            int py = Math.round(h * SAFE_Y);
            if (v.getPaddingLeft() != px || v.getPaddingTop() != py) {
                v.setPadding(px, py, px, py);
            }
        });
    }
}
