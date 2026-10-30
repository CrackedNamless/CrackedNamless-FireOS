package com.crackednamless.fireos;

import android.os.Handler;
import android.os.Looper;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public final class SpeedTest {

    public static final int PHASE_PING = 0;
    public static final int PHASE_DOWN = 1;
    public static final int PHASE_UP = 2;

    public interface Listener {
        void onPhase(int phase);

        void onSpeed(int phase, double mbps);

        void onResult(int pingMs, double downMbps, double upMbps);

        void onError();
    }

    private static final String DOWN_URL = "https://speed.cloudflare.com/__down?bytes=25000000";
    private static final String UP_URL = "https://speed.cloudflare.com/__up";
    private static final long DOWN_MS = 7000L;
    private static final long UP_MS = 5000L;
    private static final int UP_BYTES = 40_000_000;

    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile boolean cancelled;
    private Thread thread;

    public SpeedTest(Listener listener) {
        this.listener = listener;
    }

    public void start() {
        cancel();
        cancelled = false;
        thread = new Thread(this::run, "speedtest");
        thread.start();
    }

    public void cancel() {
        cancelled = true;
        if (thread != null) {
            thread.interrupt();
            thread = null;
        }
    }

    private void run() {
        try {
            post(() -> listener.onPhase(PHASE_PING));
            int ping = NetworkMonitor.ping();
            if (ping < 0) {
                throw new IOException("offline");
            }
            post(() -> listener.onPhase(PHASE_DOWN));
            double down = download();
            post(() -> listener.onPhase(PHASE_UP));
            double up = upload();
            post(() -> listener.onResult(ping, down, up));
        } catch (IOException e) {
            post(listener::onError);
        }
    }

    private void post(Runnable r) {
        if (!cancelled) {
            main.post(() -> {
                if (!cancelled) {
                    r.run();
                }
            });
        }
    }

    private double download() throws IOException {
        long bytes = 0;
        long start = System.nanoTime();
        long lastReport = start;
        double mbps = 0;
        byte[] buf = new byte[64 * 1024];
        while (!cancelled && (System.nanoTime() - start) / 1_000_000L < DOWN_MS) {
            HttpURLConnection c = open(DOWN_URL);
            try (InputStream in = c.getInputStream()) {
                int n;
                while (!cancelled && (n = in.read(buf)) >= 0) {
                    bytes += n;
                    long now = System.nanoTime();
                    mbps = rate(bytes, now - start);
                    if (now - lastReport > 200_000_000L) {
                        lastReport = now;
                        double shown = mbps;
                        post(() -> listener.onSpeed(PHASE_DOWN, shown));
                    }
                    if ((now - start) / 1_000_000L >= DOWN_MS) {
                        break;
                    }
                }
            } finally {
                c.disconnect();
            }
        }
        return mbps;
    }

    private double upload() throws IOException {
        HttpURLConnection c = open(UP_URL);
        c.setDoOutput(true);
        c.setRequestMethod("POST");
        c.setFixedLengthStreamingMode(UP_BYTES);
        long bytes = 0;
        long start = System.nanoTime();
        double mbps = 0;
        try (OutputStream out = c.getOutputStream()) {
            byte[] buf = new byte[64 * 1024];
            long lastReport = start;
            while (!cancelled && bytes < UP_BYTES) {
                out.write(buf);
                out.flush();
                bytes += buf.length;
                long now = System.nanoTime();
                mbps = rate(bytes, now - start);
                if (now - lastReport > 200_000_000L) {
                    lastReport = now;
                    double shown = mbps;
                    post(() -> listener.onSpeed(PHASE_UP, shown));
                }
                if ((now - start) / 1_000_000L >= UP_MS) {
                    break;
                }
            }
        } catch (IOException e) {
            if (bytes == 0) {
                throw e;
            }
        } finally {
            c.disconnect();
        }
        return mbps;
    }

    private static HttpURLConnection open(String url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(4000);
        c.setReadTimeout(6000);
        c.setUseCaches(false);
        return c;
    }

    static double rate(long bytes, long nanos) {
        return nanos <= 0 ? 0 : bytes * 8.0 / (nanos / 1e9) / 1e6;
    }
}
