package com.crackednamless.fireos;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

// Aggiornamenti dalle Release di GitHub: controllo periodico, download in background, installazione con pm install via ADB locale.
// Android accetta l'APK solo se e' firmato con la nostra stessa chiave.
final class Updater {

    static final class Release {
        final String version;
        final String apkUrl;
        final String notes;

        Release(String version, String apkUrl, String notes) {
            this.version = version;
            this.apkUrl = apkUrl;
            this.notes = notes;
        }
    }

    private static final String LATEST_URL = "https://api.github.com/repos/CrackedNamless/CrackedNamless-FireOS/releases/latest";
    private static final String PREFS = "update";
    private static final String KEY_VERSION = "pending_version";
    private static final String KEY_NOTES = "pending_notes";
    private static final String KEY_LATER = "later_version";
    private static final String KEY_NEXT_CHECK = "next_check";
    private static final long CHECK_EVERY_MS = 6 * 60 * 60 * 1000L;
    private static final long RETRY_MS = 5 * 60 * 1000L;
    private static final int TIMEOUT_MS = 20_000;
    private static final int NOTES_MAX = 500;
    private static final long WAKE_MS = 3 * 60 * 1000L;

    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor();
    private static Handler main;
    private static final AtomicBoolean CHECKING = new AtomicBoolean();
    private static final AtomicBoolean INSTALLING = new AtomicBoolean();

    private static synchronized Handler main() {
        if (main == null) {
            main = new Handler(Looper.getMainLooper());
        }
        return main;
    }

    private Updater() {
    }

    static Release parse(String json) throws JSONException {
        JSONObject root = new JSONObject(json);
        String tag = root.getString("tag_name");
        JSONArray assets = root.optJSONArray("assets");
        if (assets == null) {
            return null;
        }
        for (int i = 0; i < assets.length(); i++) {
            JSONObject asset = assets.getJSONObject(i);
            if (asset.optString("name").endsWith(".apk")) {
                String version = tag.startsWith("v") ? tag.substring(1) : tag;
                return new Release(version, asset.getString("browser_download_url"), root.optString("body"));
            }
        }
        return null;
    }

    static boolean isNewer(String candidate, String current) {
        int[] a = numbers(candidate);
        int[] b = numbers(current);
        for (int i = 0; i < Math.max(a.length, b.length); i++) {
            int x = i < a.length ? a[i] : 0;
            int y = i < b.length ? b[i] : 0;
            if (x != y) {
                return x > y;
            }
        }
        return false;
    }

    private static int[] numbers(String version) {
        String[] parts = version.split("[^0-9]+");
        int[] out = new int[parts.length];
        int n = 0;
        for (String part : parts) {
            if (!part.isEmpty()) {
                out[n++] = Integer.parseInt(part);
            }
        }
        return java.util.Arrays.copyOf(out, n);
    }

    // Le note della Release sono Markdown: qui si tolgono i simboli e si limita la lunghezza per il popup.
    static String cleanNotes(String markdown) {
        StringBuilder out = new StringBuilder();
        for (String line : markdown.replace("\r", "").split("\n")) {
            String t = line.trim().replace("**", "").replace("`", "");
            if (t.isEmpty()) {
                continue;
            }
            if (t.startsWith("#")) {
                t = t.replaceFirst("^#+\\s*", "");
            } else if (t.startsWith("- ")) {
                t = "• " + t.substring(2);
            }
            out.append(t).append('\n');
        }
        String text = out.toString().trim();
        return text.length() > NOTES_MAX ? text.substring(0, NOTES_MAX).trim() + "…" : text;
    }

    static String pendingVersion(Context context) {
        return hasPending(context.getApplicationContext()) ? prefs(context).getString(KEY_VERSION, null) : null;
    }

    static String pendingNotes(Context context) {
        return prefs(context).getString(KEY_NOTES, "");
    }

    // L'utente ha scelto "Piu' tardi" (o chiuso il popup): si installa quando la Fire sta per andare in standby.
    static boolean isPostponed(Context context) {
        String version = pendingVersion(context);
        return version != null && version.equals(prefs(context).getString(KEY_LATER, null));
    }

    static void postpone(Context context) {
        prefs(context).edit().putString(KEY_LATER, prefs(context).getString(KEY_VERSION, null)).apply();
    }

    // Dopo l'aggiornamento: toglie l'APK scaricato e lo stato.
    static void cleanup(Context context) {
        File apk = apkFile(context);
        if (apk != null) {
            apk.delete();
        }
        prefs(context).edit().clear().apply();
    }

    // Controlla GitHub se e' ora; ready viene chiamato sul thread principale quando c'e' un aggiornamento scaricato.
    static void checkIfDue(Context context, Runnable ready) {
        Context app = context.getApplicationContext();
        if (hasPending(app)) {
            if (ready != null) {
                main().post(ready);
            }
            return;
        }
        if (System.currentTimeMillis() < prefs(app).getLong(KEY_NEXT_CHECK, 0) || !CHECKING.compareAndSet(false, true)) {
            return;
        }
        WORKER.execute(() -> {
            long next = System.currentTimeMillis() + CHECK_EVERY_MS;
            try {
                check(app);
            } catch (IOException | JSONException e) {
                next = System.currentTimeMillis() + RETRY_MS;
            } finally {
                prefs(app).edit().putLong(KEY_NEXT_CHECK, next).apply();
                CHECKING.set(false);
            }
            if (ready != null && hasPending(app)) {
                main().post(ready);
            }
        });
    }

    private static void check(Context app) throws IOException, JSONException {
        Release release = parse(new String(download(LATEST_URL, null), "UTF-8"));
        if (release == null || !isNewer(release.version, currentVersion(app))) {
            return;
        }
        File target = apkFile(app);
        if (target == null) {
            return;
        }
        File part = new File(target.getPath() + ".part");
        download(release.apkUrl, part);
        PackageInfo info = app.getPackageManager().getPackageArchiveInfo(part.getPath(), 0);
        if (info == null || !app.getPackageName().equals(info.packageName) || !release.version.equals(info.versionName)) {
            part.delete();
            throw new IOException("Downloaded file is not the expected APK");
        }
        target.delete();
        if (!part.renameTo(target)) {
            throw new IOException("Cannot store the update");
        }
        prefs(app).edit().putString(KEY_VERSION, release.version).putString(KEY_NOTES, cleanNotes(release.notes)).apply();
    }

    // Se target e' null restituisce il corpo della risposta, altrimenti lo scrive nel file.
    private static byte[] download(String address, File target) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(address).openConnection();
        conn.setConnectTimeout(TIMEOUT_MS);
        conn.setReadTimeout(TIMEOUT_MS);
        conn.setRequestProperty("User-Agent", "CrackedNamless-FireOS");
        conn.setRequestProperty("Accept", "application/vnd.github+json");
        try {
            if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
                throw new IOException("HTTP " + conn.getResponseCode());
            }
            try (InputStream in = conn.getInputStream()) {
                byte[] buffer = new byte[16 * 1024];
                if (target != null) {
                    try (FileOutputStream out = new FileOutputStream(target)) {
                        for (int n; (n = in.read(buffer)) > 0; ) {
                            out.write(buffer, 0, n);
                        }
                    }
                    return null;
                }
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                for (int n; (n = in.read(buffer)) > 0; ) {
                    out.write(buffer, 0, n);
                }
                return out.toByteArray();
            }
        } finally {
            conn.disconnect();
        }
    }

    // pm install -r sul proprio pacchetto: il processo viene fermato e BootReceiver riavvia il launcher.
    static boolean install(Context context, boolean quiet, AdbBridge.Listener listener) {
        Context app = context.getApplicationContext();
        File apk = apkFile(app);
        if (!hasPending(app) || apk == null || !INSTALLING.compareAndSet(false, true)) {
            return false;
        }
        List<String> commands = Collections.singletonList("pm install -r '" + apk.getPath() + "'");
        AdbBridge.run(app, commands, quiet, new AdbBridge.Listener() {
            @Override
            public void onStatus(String message) {
                if (listener != null) {
                    listener.onStatus(message);
                }
            }

            @Override
            public void onDone(List<String> outputs, String error) {
                INSTALLING.set(false);
                String result = error;
                if (result == null && (outputs.isEmpty() || !outputs.get(0).contains("Success"))) {
                    result = app.getString(R.string.update_failed, outputs.isEmpty() ? "" : outputs.get(0).trim());
                }
                if (listener != null) {
                    listener.onDone(outputs, result);
                }
            }
        });
        return true;
    }

    // Prima dello standby: installa solo se l'utente ha scelto "Piu' tardi" e ADB e' gia' autorizzato (nessuna richiesta a schermo).
    static void installOnStandby(Context context) {
        Context app = context.getApplicationContext();
        if (!isPostponed(app)) {
            return;
        }
        PowerManager power = (PowerManager) app.getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock lock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "fireos:update");
        lock.acquire(WAKE_MS);
        boolean started = install(app, true, new AdbBridge.Listener() {
            @Override
            public void onStatus(String message) {
            }

            @Override
            public void onDone(List<String> outputs, String error) {
                if (lock.isHeld()) {
                    lock.release();
                }
            }
        });
        if (!started && lock.isHeld()) {
            lock.release();
        }
    }

    private static boolean hasPending(Context app) {
        String version = prefs(app).getString(KEY_VERSION, null);
        File apk = apkFile(app);
        return version != null && apk != null && apk.exists() && isNewer(version, currentVersion(app));
    }

    private static String currentVersion(Context app) {
        try {
            return app.getPackageManager().getPackageInfo(app.getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "0";
        }
    }

    private static File apkFile(Context context) {
        File dir = context.getExternalFilesDir(null);
        return dir == null ? null : new File(dir, "update.apk");
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
