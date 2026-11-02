package com.crackednamless.fireos;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.FrameLayout;

// Contenitore 16:9: l'altezza segue la larghezza, cosi le card hanno la stessa forma dei banner Fire TV (320x180).
public final class AspectFrame extends FrameLayout {

    public AspectFrame(Context context) {
        super(context);
    }

    public AspectFrame(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int width = MeasureSpec.getSize(widthSpec);
        int height = Math.round(width * 9f / 16f);
        super.onMeasure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
    }
}
