package com.crackednamless.fireos;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

public final class GlassBackground {

    private static final int W = 480;
    private static final int H = 270;

    private GlassBackground() {
    }

    public static Drawable create(Resources res) {
        Bitmap bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);

        p.setShader(new LinearGradient(0, 0, W, H, 0xFF08090C, 0xFF14161B, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, W, H, p);

        blob(c, p, W * 0.15f, H * 0.10f, W * 0.55f, 0x30475A80);
        blob(c, p, W * 0.90f, H * 0.30f, W * 0.45f, 0x26566078);
        blob(c, p, W * 0.55f, H * 1.05f, W * 0.55f, 0x1F4A5568);

        BitmapDrawable d = new BitmapDrawable(res, bmp);
        d.setFilterBitmap(true);
        return d;
    }

    private static void blob(Canvas c, Paint p, float cx, float cy, float r, int color) {
        p.setShader(new RadialGradient(cx, cy, r, color, color & 0x00FFFFFF, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, W, H, p);
    }
}
