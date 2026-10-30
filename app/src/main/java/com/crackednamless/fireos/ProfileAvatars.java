package com.crackednamless.fireos;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

// Amazon non espone l'avatar del profilo: si ritaglia dallo screenshot del selettore profili e si ricorda per nome.
final class ProfileAvatars {
    static final class Entry {
        final String name;
        final Rect bounds;

        Entry(String name, Rect bounds) {
            this.name = name;
            this.bounds = bounds;
        }
    }

    private static final String PREFS = "profiles";
    private static final String KEY_ACTIVE = "active";
    private static final int SIZE = 160;

    private ProfileAvatars() {
    }

    static File shotFile(Context context) {
        File dir = context.getExternalFilesDir(null);
        return dir == null ? null : new File(dir, "picker.png");
    }

    static void setActive(Context context, String name) {
        if (new File(context.getFilesDir(), fileName(name)).exists()) {
            prefs(context).edit().putString(KEY_ACTIVE, name).apply();
        }
    }

    static Bitmap activeAvatar(Context context) {
        String name = prefs(context).getString(KEY_ACTIVE, null);
        if (name == null) {
            return null;
        }
        return BitmapFactory.decodeFile(new File(context.getFilesDir(), fileName(name)).getPath());
    }

    // Ritaglia ogni avatar dallo screenshot e lo salva circolare; poi segna come attivo quello con il focus.
    static void save(Context context, File shot, List<Entry> entries, String activeName) {
        Bitmap full = BitmapFactory.decodeFile(shot.getPath());
        if (full == null) {
            return;
        }
        for (Entry entry : entries) {
            Rect r = entry.bounds;
            if (r.left < 0 || r.top < 0 || r.right > full.getWidth() || r.bottom > full.getHeight() || r.width() < 8 || r.height() < 8) {
                continue;
            }
            Bitmap crop = Bitmap.createBitmap(full, r.left, r.top, r.width(), r.height());
            Bitmap scaled = Bitmap.createScaledBitmap(crop, SIZE, SIZE, true);
            if (isBlack(scaled)) {
                continue;
            }
            Bitmap round = circle(scaled);
            try (FileOutputStream out = new FileOutputStream(new File(context.getFilesDir(), fileName(entry.name)))) {
                round.compress(Bitmap.CompressFormat.PNG, 100, out);
            } catch (IOException ignored) {
                // Resta l'icona precedente.
            }
        }
        full.recycle();
        shot.delete();
        if (activeName != null) {
            setActive(context, activeName);
        }
    }

    // Con lo schermo spento screencap restituisce nero: non deve sostituire un avatar buono.
    private static boolean isBlack(Bitmap bitmap) {
        for (int y = 0; y < bitmap.getHeight(); y += 8) {
            for (int x = 0; x < bitmap.getWidth(); x += 8) {
                int c = bitmap.getPixel(x, y);
                if (android.graphics.Color.red(c) + android.graphics.Color.green(c) + android.graphics.Color.blue(c) > 60) {
                    return false;
                }
            }
        }
        return true;
    }

    private static Bitmap circle(Bitmap square) {
        Bitmap out = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(out);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        canvas.drawCircle(SIZE / 2f, SIZE / 2f, SIZE / 2f, paint);
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        canvas.drawBitmap(square, 0, 0, paint);
        return out;
    }

    private static String fileName(String name) {
        return "avatar_" + Integer.toHexString(name.hashCode()) + ".png";
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
