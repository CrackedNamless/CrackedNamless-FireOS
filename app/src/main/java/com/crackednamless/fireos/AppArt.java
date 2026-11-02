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

    private static final java.util.Map<String, Drawable.ConstantState> BANNERS =
            java.util.Collections.synchronizedMap(new java.util.HashMap<>());
    private static final java.util.Map<String, Drawable.ConstantState> ICONS =
            java.util.Collections.synchronizedMap(new java.util.HashMap<>());
    private static final java.util.Map<String, Integer> ACCENTS =
            java.util.Collections.synchronizedMap(new java.util.HashMap<>());
    private static final java.util.concurrent.ExecutorService LOADER =
            java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "app-art");
                t.setPriority(Thread.NORM_PRIORITY - 1);
                t.setDaemon(true);
                return t;
            });
    private static final android.os.Handler MAIN = new android.os.Handler(android.os.Looper.getMainLooper());

    // Il decode da PackageManager e' costoso: si tiene la copia scalata e a ogni uso se ne crea una nuova (le bounds non si condividono).
    public static void clear() {
        BANNERS.clear();
        ICONS.clear();
        HEROES.clear();
        HERO_BANNER.clear();
        ACCENTS.clear();
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
            ACCENTS.put(key, accent(d));
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

    public interface Done<T> {
        void done(T value);
    }

    private static final java.util.Set<String> HERO_BANNER =
            java.util.Collections.synchronizedSet(new java.util.HashSet<>());
    private static final java.util.Map<String, Drawable.ConstantState> HEROES =
            java.util.Collections.synchronizedMap(new java.util.HashMap<>());

    // Versione ad alta risoluzione per il banner grande della Home: icone vettoriali/adattive ridisegnate a 512 px, bitmap prese alla densita' massima.
    public static void loadHero(PackageManager pm, Resources res, AppEntry e, Done<AppArt> done) {
        LOADER.execute(() -> {
            AppArt art = hiRes(pm, res, e);
            if (art != null) {
                MAIN.post(() -> done.done(art));
            }
        });
    }

    private static AppArt hiRes(PackageManager pm, Resources res, AppEntry e) {
        ComponentName cn = new ComponentName(e.packageName, e.className);
        String key = cn.flattenToString();
        Drawable.ConstantState cached = HEROES.get(key);
        if (cached != null) {
            return new AppArt(cached.newDrawable(res), HERO_BANNER.contains(key));
        }
        try {
            android.content.pm.ActivityInfo ai = pm.getActivityInfo(cn, 0);
            Resources ar = pm.getResourcesForApplication(ai.applicationInfo);
            int bannerId = ai.banner != 0 ? ai.banner : ai.applicationInfo.banner;
            boolean banner = bannerId != 0;
            int id = banner ? bannerId : ai.getIconResource() != 0 ? ai.getIconResource() : ai.applicationInfo.icon;
            Drawable src = id != 0 ? ar.getDrawableForDensity(id, 640, null) : null;
            if (src == null) {
                return null;
            }
            Drawable out;
            if (src instanceof BitmapDrawable) {
                Bitmap b = ((BitmapDrawable) src).getBitmap();
                out = banner ? scaled(res, src, 1280) : (b.getWidth() < 512 ? new BitmapDrawable(res, Bitmap.createScaledBitmap(b, 512, 512 * b.getHeight() / b.getWidth(), true)) : src);
            } else {
                Bitmap bmp = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888);
                src.setBounds(0, 0, 512, 512);
                src.draw(new Canvas(bmp));
                out = new BitmapDrawable(res, bmp);
            }
            if (out.getConstantState() != null) {
                HEROES.put(key, out.getConstantState());
                if (banner) {
                    HERO_BANNER.add(key);
                }
            }
            return new AppArt(out, banner);
        } catch (PackageManager.NameNotFoundException | Resources.NotFoundException ex) {
            return null;
        }
    }

    // Solo dalla cache, senza toccare il PackageManager: va bene sul thread principale.
    public static AppArt peek(Resources res, AppEntry e) {
        String key = new ComponentName(e.packageName, e.className).flattenToString();
        Drawable d = fresh(BANNERS, key, res);
        if (d != null) {
            return new AppArt(d, true);
        }
        d = fresh(ICONS, key, res);
        return d != null ? new AppArt(d, false) : null;
    }

    public static int cachedAccent(AppEntry e) {
        Integer c = ACCENTS.get(new ComponentName(e.packageName, e.className).flattenToString());
        return c != null ? c : DEFAULT_ACCENT;
    }

    // Decodifica in coda su un thread a bassa priorita' e consegna il risultato sul thread principale.
    public static void load(PackageManager pm, Resources res, AppEntry e, Done<AppArt> done) {
        LOADER.execute(() -> {
            AppArt art = banner(pm, res, e);
            MAIN.post(() -> done.done(art));
        });
    }

    // Scalda la cache in background, cosi' quando si apre la Home le card sono gia' pronte.
    public static void preload(PackageManager pm, Resources res, java.util.List<AppEntry> apps) {
        java.util.List<AppEntry> copy = new java.util.ArrayList<>(apps);
        LOADER.execute(() -> {
            for (AppEntry e : copy) {
                if (peek(res, e) == null) {
                    banner(pm, res, e);
                }
            }
        });
    }

    // Anteprima di una cartella: fino a 4 icone in griglia 2x2 su fondo vetro, disegnata in background.
    public static void folder(PackageManager pm, Resources res, java.util.List<AppEntry> members, Done<Drawable> done) {
        java.util.List<AppEntry> copy = new java.util.ArrayList<>(members);
        LOADER.execute(() -> {
            int w = 320, h = 180, size = 64, gap = 12;
            Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(bmp);
            android.graphics.Paint bg = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            bg.setShader(new android.graphics.LinearGradient(0, 0, 0, h, 0x44FFFFFF, 0x18FFFFFF,
                    android.graphics.Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, h, bg);
            int n = Math.min(4, copy.size());
            int left = (w - (2 * size + gap)) / 2;
            int top = (h - (2 * size + gap)) / 2 - 8;
            for (int i = 0; i < n; i++) {
                AppEntry e = copy.get(i);
                Drawable icon = icon(pm, res, e);
                if (icon == null) {
                    continue;
                }
                int x = left + (i % 2) * (size + gap);
                int y = top + (i / 2) * (size + gap);
                icon.setBounds(x, y, x + size, y + size);
                icon.draw(c);
            }
            Drawable out = new BitmapDrawable(res, bmp);
            MAIN.post(() -> done.done(out));
        });
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
