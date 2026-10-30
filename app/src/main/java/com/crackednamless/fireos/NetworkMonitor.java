package com.crackednamless.fireos;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Handler;
import android.os.Looper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

public final class NetworkMonitor {

    public static final int OFFLINE = 0;
    public static final int NO_INTERNET = 1;
    public static final int ONLINE = 2;

    public interface Listener {
        void onChanged(int state, int pingMs);
    }

    private static final String PING_HOST = "1.1.1.1";
    private static final int PING_PORT = 443;
    private static final int PING_TIMEOUT_MS = 2500;
    private static final long INTERVAL_MS = 10_000L;

    private final Context context;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private boolean running;
    private boolean busy;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!running) {
                return;
            }
            check();
            main.postDelayed(this, INTERVAL_MS);
        }
    };

    public NetworkMonitor(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
    }

    public void start() {
        if (running) {
            return;
        }
        running = true;
        main.post(tick);
    }

    public void stop() {
        running = false;
        main.removeCallbacks(tick);
    }

    private void check() {
        if (busy) {
            return;
        }
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo info = cm != null ? cm.getActiveNetworkInfo() : null;
        if (info == null || !info.isConnected()) {
            listener.onChanged(OFFLINE, -1);
            return;
        }
        busy = true;
        new Thread(() -> {
            int ping = ping();
            main.post(() -> {
                busy = false;
                if (running) {
                    listener.onChanged(ping >= 0 ? ONLINE : NO_INTERNET, ping);
                }
            });
        }, "net-ping").start();
    }

    static int ping() {
        int best = -1;
        for (int i = 0; i < 2; i++) {
            long start = System.nanoTime();
            try (Socket s = new Socket()) {
                s.connect(new InetSocketAddress(PING_HOST, PING_PORT), PING_TIMEOUT_MS);
                int ms = (int) Math.max(1, (System.nanoTime() - start) / 1_000_000L);
                best = best < 0 ? ms : Math.min(best, ms);
            } catch (IOException e) {
                // Campione perso: si usa l'altro, se c'e.
            }
        }
        return best;
    }
}
