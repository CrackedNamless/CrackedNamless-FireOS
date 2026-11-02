package com.crackednamless.fireos;

import android.app.ActivityManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import java.util.Collections;
import java.util.List;

// Pulizia automatica della memoria: chiude solo le app inattive in cache ("am kill-all"), mai quella in uso.
// Parte da sola quando lo Stick va in standby e quando la RAM libera scende sotto soglia. Serve Sblocco rapido Home (sessione ADB gia' pronta).
final class MemoryGuard {

    static final long LOW_MB = 150;
    private static final long LOW_STORAGE_BYTES = 500L * 1024 * 1024;
    private static final long COOLDOWN_MS = 10 * 60_000L;
    private static final long SETTLE_MS = 1500L;
    private static final String PREFS = "memory_guard";
    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_LAST_TIME = "last_time";
    private static final String KEY_LAST_FREED = "last_freed_mb";

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static long lastRunUptime = -COOLDOWN_MS;
    private static boolean running;

    private MemoryGuard() {
    }

    static boolean isEnabled(Context context) {
        return prefs(context).getBoolean(KEY_ENABLED, true);
    }

    static void setEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply();
    }

    static long availableMb(Context context) {
        ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
        ((ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE)).getMemoryInfo(info);
        return info.availMem / (1024 * 1024);
    }

    static long totalMb(Context context) {
        ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
        ((ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE)).getMemoryInfo(info);
        return info.totalMem / (1024 * 1024);
    }

    // Millisecondi epoch dell'ultima pulizia automatica, 0 se mai fatta.
    static long lastTime(Context context) {
        return prefs(context).getLong(KEY_LAST_TIME, 0);
    }

    static long lastFreedMb(Context context) {
        return prefs(context).getLong(KEY_LAST_FREED, 0);
    }

    static void onStandby(Context context) {
        if (isEnabled(context)) {
            clean(context, false);
        }
    }

    static void checkLow(Context context) {
        if (isEnabled(context) && availableMb(context) < LOW_MB) {
            clean(context, true);
        }
    }

    private static void clean(Context context, boolean respectCooldown) {
        Context app = context.getApplicationContext();
        if (!AdbBridge.isFastHome(app) || running) {
            return;
        }
        long now = SystemClock.uptimeMillis();
        if (respectCooldown && now - lastRunUptime < COOLDOWN_MS) {
            return;
        }
        running = true;
        lastRunUptime = now;
        long before = availableMb(app);
        List<String> commands = new java.util.ArrayList<>();
        commands.add("am kill-all");
        if (new android.os.StatFs(android.os.Environment.getDataDirectory().getPath()).getAvailableBytes() < LOW_STORAGE_BYTES) {
            commands.add(Tweaks.TRIM_CACHES);
        }
        AdbBridge.run(app, commands, true, new AdbBridge.Listener() {
            @Override
            public void onStatus(String message) {
            }

            @Override
            public void onDone(List<String> outputs, String error) {
                if (error != null) {
                    running = false;
                    return;
                }
                MAIN.postDelayed(() -> {
                    long freed = Math.max(0, availableMb(app) - before);
                    prefs(app).edit().putLong(KEY_LAST_TIME, System.currentTimeMillis())
                            .putLong(KEY_LAST_FREED, freed).apply();
                    running = false;
                }, SETTLE_MS);
            }
        });
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
