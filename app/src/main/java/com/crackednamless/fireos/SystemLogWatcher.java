package com.crackednamless.fireos;

import android.os.SystemClock;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

// Il tasto accensione e l'avvio di Amazon Home (riga di ActivityManager, ~270 ms prima di HOME_PRESSED) non arrivano alle app: si leggono dal log di sistema (serve READ_LOGS, concesso via adb).
final class SystemLogWatcher {

    interface Listener {
        void onPowerKey();

        void onHomeStart();
    }

    static final int NONE = 0;
    static final int POWER = 1;
    static final int HOME = 2;

    private static final long REPLAY_GUARD_MS = 1500L;
    private static final long DEBOUNCE_MS = 800L;
    private static final long RESTART_MS = 5000L;

    private final Listener listener;
    private volatile boolean running;
    private volatile Process process;
    private Thread thread;

    SystemLogWatcher(Listener listener) {
        this.listener = listener;
    }

    // Con redirectErrorStream anche gli errori di logcat (es. "read: unexpected EOF!") arrivano qui: non sono tasti.
    static int classify(String line) {
        if (line.contains("START u0 {")) {
            return HOME;
        }
        if (line.contains("KEYCODE_POWER") || line.contains("Received Key name: POWER, code: 26, action: 0")) {
            return POWER;
        }
        return NONE;
    }

    void start() {
        if (running) {
            return;
        }
        running = true;
        thread = new Thread(this::loop, "power-key-watcher");
        thread.setDaemon(true);
        thread.start();
    }

    void stop() {
        running = false;
        Process p = process;
        if (p != null) {
            p.destroy();
        }
        if (thread != null) {
            thread.interrupt();
        }
    }

    private void loop() {
        long lastFired = 0;
        while (running) {
            long startedAt = SystemClock.elapsedRealtime();
            try {
                process = new ProcessBuilder("logcat", "-v", "brief", "-T", "1", "-e",
                        "action=ACTION_DOWN, keyCode=KEYCODE_POWER|Received Key name: POWER, code: 26, action: 0|cat=\\[android.intent.category.HOME\\].* from uid 1000$")
                        .redirectErrorStream(true).start();
                BufferedReader in = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String line;
                while (running && (line = in.readLine()) != null) {
                    long now = SystemClock.elapsedRealtime();
                    if (now - startedAt < REPLAY_GUARD_MS) {
                        continue;
                    }
                    int kind = classify(line);
                    if (kind == POWER) {
                        if (now - lastFired >= DEBOUNCE_MS) {
                            lastFired = now;
                            listener.onPowerKey();
                        }
                    } else if (kind == HOME) {
                        listener.onHomeStart();
                    }
                }
            } catch (IOException ignored) {
                // logcat non disponibile: si riprova.
            } finally {
                Process p = process;
                if (p != null) {
                    p.destroy();
                }
            }
            if (!running) {
                return;
            }
            try {
                Thread.sleep(RESTART_MS);
            } catch (InterruptedException e) {
                return;
            }
        }
    }
}
