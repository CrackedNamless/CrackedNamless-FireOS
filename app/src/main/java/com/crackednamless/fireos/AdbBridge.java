package com.crackednamless.fireos;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.VpnService;
import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Comandi shell sul proprio Stick tramite VPN di appoggio e adbd locale. Con "sblocco rapido Home" attivo la connessione
// resta aperta, cosi' dopo HOME si puo' chiamare subito resumeAppSwitches e saltare i 5 s di blocco degli avvii.
final class AdbBridge {

    interface Listener {
        void onStatus(String message);

        // outputs ha un elemento per comando; error e' null se tutto e' andato a buon fine.
        void onDone(List<String> outputs, String error);
    }

    private static final int ADB_PORT = 5555;
    private static final int AUTH_WAIT_MS = 120_000;
    private static final int QUIET_AUTH_WAIT_MS = 5_000;
    private static final String PREFS = "adb";
    private static final String KEY_FAST = "fast_home";
    private static final String KEY_AUTHORIZED = "authorized";
    private static final String KEY_CODE = "resume_code";
    private static final Pattern CODE = Pattern.compile("CODE=(\\d+)");

    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    // Toccati solo dal thread WORKER, tranne ready e appContext.
    private static AdbClient session;
    private static volatile boolean ready;
    private static volatile Context appContext;
    private static volatile int resumeCode;

    private AdbBridge() {
    }

    static boolean isFastHome(Context context) {
        return prefs(context).getBoolean(KEY_FAST, false);
    }

    // Null se la VPN e' gia' autorizzata, altrimenti l'intent per chiedere il consenso.
    static Intent vpnConsent(Context context) {
        return VpnService.prepare(context);
    }

    static void run(Context context, List<String> commands, Listener listener) {
        Context app = context.getApplicationContext();
        WORKER.execute(() -> {
            List<String> outputs = new ArrayList<>();
            String error = connect(app, listener, AUTH_WAIT_MS);
            if (error == null) {
                try {
                    for (int i = 0; i < commands.size(); i++) {
                        status(listener, app.getString(R.string.adb_command_progress, i + 1, commands.size()));
                        outputs.add(session.shell(commands.get(i)));
                    }
                } catch (IOException e) {
                    dropSession(app);
                    error = app.getString(R.string.err_adb_lost, String.valueOf(e));
                }
                if (!isFastHome(app)) {
                    dropSession(app);
                }
            }
            String result = error;
            MAIN.post(() -> listener.onDone(outputs, result));
        });
    }

    static void setFastHome(Context context, boolean enabled, Listener listener) {
        Context app = context.getApplicationContext();
        prefs(app).edit().putBoolean(KEY_FAST, enabled).apply();
        WORKER.execute(() -> {
            String error = null;
            if (enabled) {
                appContext = app;
                error = connect(app, listener, AUTH_WAIT_MS);
                if (error == null) {
                    error = discoverCode(app);
                }
                if (error != null) {
                    prefs(app).edit().putBoolean(KEY_FAST, false).apply();
                    dropSession(app);
                }
            } else {
                dropSession(app);
            }
            String result = error;
            MAIN.post(() -> listener.onDone(new ArrayList<>(), result));
        });
    }

    // Da chiamare ogni tanto: ricollega la sessione se e' caduta, senza mai mostrare richieste all'utente.
    static void maintain(Context context) {
        Context app = context.getApplicationContext();
        appContext = app;
        if (!isFastHome(app)) {
            return;
        }
        WORKER.execute(() -> {
            if (session != null) {
                try {
                    session.shell("true");
                } catch (IOException e) {
                    dropSession(app);
                }
                return;
            }
            SharedPreferences p = prefs(app);
            if (!p.getBoolean(KEY_AUTHORIZED, false) || VpnService.prepare(app) != null) {
                return;
            }
            if (connect(app, null, QUIET_AUTH_WAIT_MS) == null && discoverCode(app) != null) {
                dropSession(app);
            }
        });
    }

    // Subito dopo HOME: sblocca gli avvii in background. Se la sessione non c'e' non fa nulla e resta l'overlay.
    static void unlockAppSwitches() {
        Context app = appContext;
        int code = resumeCode;
        if (!ready || app == null || code <= 0) {
            return;
        }
        WORKER.execute(() -> {
            if (session == null) {
                return;
            }
            try {
                session.shell("for i in 1 2 3 4 5 6 7 8 9 10 11 12; do service call activity " + code + "; done");
            } catch (IOException e) {
                dropSession(app);
            }
        });
    }

    // Salva lo schermo in un file leggibile dall'app. Apre una sessione breve solo se VPN e ADB sono gia' autorizzati, senza mai chiedere nulla.
    static void screenshot(Context context, File target, Runnable done) {
        Context app = context.getApplicationContext();
        if (target == null) {
            return;
        }
        WORKER.execute(() -> {
            if (session == null && (!prefs(app).getBoolean(KEY_AUTHORIZED, false) || VpnService.prepare(app) != null
                    || connect(app, null, QUIET_AUTH_WAIT_MS) != null)) {
                return;
            }
            try {
                session.shell("screencap -p '" + target.getPath() + "'");
                done.run();
            } catch (IOException e) {
                dropSession(app);
                return;
            }
            if (!isFastHome(app)) {
                dropSession(app);
            }
        });
    }

    // Apre tunnel e connessione se non ci sono gia'. Restituisce null o il messaggio d'errore.
    private static String connect(Context app, Listener listener, int authWaitMs) {
        if (session != null) {
            return null;
        }
        if (VpnService.prepare(app) != null) {
            return app.getString(R.string.err_vpn_consent_denied);
        }
        if (!waitNoVpn(app)) {
            return app.getString(R.string.err_other_vpn);
        }
        AdbClient client = new AdbClient();
        try {
            status(listener, app.getString(R.string.adb_opening_tunnel));
            TunnelVpnService.start(app);
            if (!waitForTunnel()) {
                TunnelVpnService.stop(app);
                return app.getString(R.string.err_tunnel_down);
            }
            KeyPair keys = AdbClient.loadKeys(app.getFilesDir());
            status(listener, app.getString(R.string.adb_connecting));
            client.connect(TunnelVpnService.VIRTUAL_HOST, ADB_PORT, keys, authWaitMs);
            session = client;
            prefs(app).edit().putBoolean(KEY_AUTHORIZED, true).apply();
            return null;
        } catch (AdbClient.AuthException e) {
            client.close();
            TunnelVpnService.stop(app);
            return app.getString(R.string.err_adb_denied, String.valueOf(e.getMessage()));
        } catch (IOException | GeneralSecurityException e) {
            client.close();
            TunnelVpnService.stop(app);
            return app.getString(R.string.err_adb_failed, String.valueOf(e));
        }
    }

    // Legge dal sistema il codice di transazione di resumeAppSwitches (cambia tra versioni) e lo memorizza.
    private static String discoverCode(Context app) {
        int code = prefs(app).getInt(KEY_CODE, 0);
        if (code <= 0) {
            try {
                String out = session.shell("CLASSPATH=" + app.getPackageCodePath()
                        + " app_process /system/bin com.crackednamless.fireos.AppSwitchProbe");
                Matcher m = CODE.matcher(out);
                if (!m.find()) {
                    return app.getString(R.string.err_code_not_found, out);
                }
                code = Integer.parseInt(m.group(1));
                prefs(app).edit().putInt(KEY_CODE, code).apply();
            } catch (IOException e) {
                return app.getString(R.string.err_adb_lost, String.valueOf(e));
            }
        }
        resumeCode = code;
        ready = true;
        return null;
    }

    private static void dropSession(Context app) {
        ready = false;
        if (session != null) {
            session.close();
            session = null;
        }
        TunnelVpnService.stop(app);
    }

    // Il nostro tunnel appena chiuso puo' risultare attivo ancora per un istante.
    private static boolean waitNoVpn(Context app) {
        for (int i = 0; i < 30; i++) {
            if (!TunnelVpnService.otherVpnUp(app)) {
                return true;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    private static boolean waitForTunnel() {
        for (int i = 0; i < 50; i++) {
            if (TunnelVpnService.isActive()) {
                return true;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    private static void status(Listener listener, String message) {
        if (listener != null) {
            MAIN.post(() -> listener.onStatus(message));
        }
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
