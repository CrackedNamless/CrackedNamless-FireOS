package com.crackednamless.fireos;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

// Alla prima apertura attiva da sola Fast Home, la disattivazione dei servizi superflui, le prestazioni e il blocco di Prime Video in background, cosi' non serve aprire il menu strumenti.
// Dopo la prima volta non tocca piu' nulla: chi spegne una regolazione la ritrova spenta.
final class FirstRun {

    interface Reporter {
        void onMessage(String message);
    }

    private static final String PREFS = "firstrun";
    private static final String KEY_DONE = "done";
    private static final String KEY_TRIES = "tries";
    private static final int MAX_TRIES = 3;
    private static final AtomicBoolean RUNNING = new AtomicBoolean();

    private FirstRun() {
    }

    static boolean pending(Context context) {
        SharedPreferences p = prefs(context);
        return !p.getBoolean(KEY_DONE, false) && p.getInt(KEY_TRIES, 0) < MAX_TRIES;
    }

    // Il consenso VPN va gia' concesso: lo chiede HomeActivity prima di chiamare start.
    static void start(Context context, Reporter reporter) {
        Context app = context.getApplicationContext();
        if (!pending(app) || !RUNNING.compareAndSet(false, true)) {
            return;
        }
        prefs(app).edit().putInt(KEY_TRIES, prefs(app).getInt(KEY_TRIES, 0) + 1).apply();
        reporter.onMessage(app.getString(R.string.first_run_start));
        AdbBridge.setFastHome(app, true, new AdbBridge.Listener() {
            @Override
            public void onStatus(String message) {
                reporter.onMessage(message);
            }

            @Override
            public void onDone(List<String> outputs, String error) {
                if (error != null) {
                    RUNNING.set(false);
                    reporter.onMessage(error);
                    return;
                }
                applyTweaks(app, reporter);
            }
        });
    }

    private static void applyTweaks(Context app, Reporter reporter) {
        List<String> commands = Tweaks.performanceCommands(true);
        commands.addAll(Tweaks.disableCommands(app.getPackageManager()));
        commands.add(Tweaks.PRIME_BLOCK);
        AdbBridge.run(app, commands, new AdbBridge.Listener() {
            @Override
            public void onStatus(String message) {
                reporter.onMessage(message);
            }

            @Override
            public void onDone(List<String> outputs, String error) {
                RUNNING.set(false);
                if (error != null) {
                    reporter.onMessage(error);
                    return;
                }
                prefs(app).edit().putBoolean(KEY_DONE, true).apply();
                reporter.onMessage(app.getString(R.string.first_run_done));
            }
        });
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
