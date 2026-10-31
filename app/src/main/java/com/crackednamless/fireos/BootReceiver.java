package com.crackednamless.fireos;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;
import android.provider.Settings;

public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        boolean updated = Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction());
        if (updated) {
            Updater.cleanup(context);
            enableService(context);
        }
        PowerManager power = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        if (updated && power != null && !power.isInteractive()) {
            return;
        }
        context.startActivity(new Intent(context, HomeActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    // Dopo un aggiornamento il servizio Accessibilita' puo' risultare spento: lo si rimette nell'elenco (serve WRITE_SECURE_SETTINGS).
    private static void enableService(Context context) {
        String service = new ComponentName(context, HomeService.class).flattenToString();
        try {
            String enabled = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (enabled == null || enabled.isEmpty() || enabled.equals("null")) {
                enabled = service;
            } else if (!(":" + enabled + ":").contains(":" + service + ":")) {
                enabled = enabled + ":" + service;
            } else {
                return;
            }
            Settings.Secure.putString(context.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, enabled);
            Settings.Secure.putInt(context.getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED, 1);
        } catch (SecurityException ignored) {
            // Permesso WRITE_SECURE_SETTINGS non concesso: resta da riattivare a mano.
        }
    }
}
