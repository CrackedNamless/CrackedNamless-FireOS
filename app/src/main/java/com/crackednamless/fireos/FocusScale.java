package com.crackednamless.fireos;

import android.view.View;

public final class FocusScale {

    private FocusScale() {
    }

    // Niente zoom: su Fire OS lo scale taglia l'anello di focus sui bordi.
    public static void apply(View v, Runnable onChange) {
        v.setOnFocusChangeListener((view, focused) -> {
            if (onChange != null) {
                onChange.run();
            }
        });
    }
}
