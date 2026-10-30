package com.crackednamless.fireos;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;

public final class SystemIntents {

    private static final ComponentName FIRE_SETTINGS =
            new ComponentName("com.amazon.tv.launcher", "com.amazon.tv.launcher.ui.MainSettingsActivity");

    private SystemIntents() {
    }

    // Su Fire OS ACTION_SETTINGS riporta alla Home di Amazon: si apre direttamente la schermata Impostazioni.
    public static Intent settings(Context context) {
        Intent direct = new Intent().setComponent(FIRE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (direct.resolveActivity(context.getPackageManager()) != null) {
            return direct;
        }
        return new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }

    public static Intent bluetooth() {
        return new Intent("android.settings.CONTROLLERS_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }

    public static Intent wifi() {
        return new Intent(Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }
}
