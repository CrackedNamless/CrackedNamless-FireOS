package com.crackednamless.fireos;

import android.content.ComponentName;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

public final class AppArt {

    private static final int MAX_WIDTH = 384;
    private static final int MAX_ICON = 160;
    private static final int DEFAULT_ACCENT = 0xFF3B6BFF;

    public final Drawable drawable;
    public final boolean isBanner;

    private AppArt(Drawable drawable, boolean isBanner) {
        this.drawable = drawable;
        this.isBanner = isBanner;
    }

    private static final java.util.Map<String, Drawable.ConstantState> BANNERS = new java.util.HashMap<>();
    private static final java.util.Map<String, Drawable.ConstantState> ICONS = new java.util.HashMap<>();

    // Il decode da PackageManager e' costoso: si tiene la copia scalata e a ogni uso se ne crea una nuova (le bounds non si condividono).
    public static void clear() {
        BANNERS.clear();
        ICONS.clear();
    }

    private static Drawable fresh(java.util.Map<String, Drawable.ConstantState> cache, String key, Resources res) {
        Drawable.ConstantState s = cache.get(key);
        return s != null ? s.newDrawable(res) : null;
    }

    private static void keep(java.util.Map<String, Drawable.ConstantState> cache, String key, Drawable d) {
        if (d != null && d.getConstantState() != null) {
            cache.put(key, d.getConstantState());
        }
    }

    public static AppArt banner(PackageManager pm, Resources res, AppEntry e) {
        ComponentName cn = new ComponentName(e.packageName, e.className);
        String key = cn.flattenToString();
        Drawable hit = fresh(BANNERS, key, res);
        if (hit != null) {
            return new AppArt(hit, true);
        }
        hit = fresh(ICONS, key, res);
        if (hit != null) {
            return new AppArt(hit, false);
        }
        try {
            Drawable banner = pm.getActivityBanner(cn);
            if (banner != null) {
                Drawable d = scaled(res, banner, MAX_WIDTH);
                keep(BANNERS, key, d);
                return new AppArt(d, true);
            }
            Drawable d = scaled(res, pm.getActivityIcon(cn), MAX_ICON);
            keep(ICONS, key, d);
            return new AppArt(d, false);
        } catch (PackageManager.NameNotFoundException ignored) {
            return new AppArt(null, false);
        }
    }

    public static Drawable icon(PackageManager pm, Resources res, AppEntry e) {
        ComponentName cn = new ComponentName(e.packageName, e.className);
        String key = cn.flattenToString();
        Drawable hit = fresh(ICONS, key, res);
        if (hit != null) {
            return hit;
        }
        try {
            Drawable d = scaled(res, pm.getActivityIcon(cn), MAX_ICON);
            keep(ICONS, key, d);
            return d;
        } catch (PackageManager.NameNotFoundException ignored) {
            return null;
        }
    }

    public static int accent(Drawable d) {
        if (d == null) {
            return DEFAULT_ACCENT;
        }
        Bitmap b = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888);
        d.setBounds(0, 0, 16, 16);
        d.draw(new Canvas(b));
        long r = 0, g = 0, bl = 0, n = 0;
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                int p = b.getPixel(x, y);
                if (Color.alpha(p) < 128) {
                    continue;
                }
                r += Color.red(p);
                g += Color.green(p);
                bl += Color.blue(p);
                n++;
            }
        }
        b.recycle();
        if (n == 0) {
            return DEFAULT_ACCENT;
        }
        float[] hsv = new float[3];
        Color.RGBToHSV((int) (r / n), (int) (g / n), (int) (bl / n), hsv);
        return hsv[1] < 0.2f ? DEFAULT_ACCENT : Color.HSVToColor(hsv);
    }

    public static int shade(int color, float saturation, float value) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[1] = Math.min(1f, hsv[1] * saturation);
        hsv[2] = value;
        return Color.HSVToColor(hsv);
    }

    private static Drawable scaled(Resources res, Drawable d, int maxWidth) {
        if (!(d instanceof BitmapDrawable)) {
            return d;
        }
        Bitmap src = ((BitmapDrawable) d).getBitmap();
        if (src == null || src.getWidth() <= maxWidth) {
            return d;
        }
        int h = Math.max(1, src.getHeight() * maxWidth / src.getWidth());
        return new BitmapDrawable(res, Bitmap.createScaledBitmap(src, maxWidth, h, true));
    }
}
