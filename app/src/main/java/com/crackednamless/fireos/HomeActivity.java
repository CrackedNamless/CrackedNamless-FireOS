package com.crackednamless.fireos;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Toast;

import java.util.List;

public class HomeActivity extends Activity implements HomeScreen.Host {

    private HomeScreen screen;
    private View emptyView;
    private StartupSplash splash;
    private android.app.AlertDialog updateDialog;
    private boolean resumed;
    private static final int REQUEST_VPN = 1;
    private static boolean consentAsked;
    private final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
    private static final long MONITORS_DELAY_MS = 400L;
    private final Runnable startMonitors = new Runnable() {
        @Override
        public void run() {
            screen.refreshWeather();
            screen.start();
        }
    };

    private final BroadcastReceiver packageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            AppRepository.invalidate();
            reload();
        }
    };

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(Scaling.wrap(base));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);
        findViewById(R.id.root).setBackground(GlassBackground.create(getResources()));

        emptyView = findViewById(R.id.empty);
        screen = new HomeScreen(this, findViewById(R.id.home_root), this);

        splash = new StartupSplash(findViewById(R.id.splash));
        if (savedInstanceState == null) {
            splash.play();
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (HomeService.onStandbyKey(event)) {
            return true;
        }
        if (splash.isShowing()) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                splash.dismiss();
            }
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    protected void onStart() {
        super.onStart();
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        filter.addDataScheme("package");
        registerReceiver(packageReceiver, filter);
        reload();
        handler.postDelayed(startMonitors, MONITORS_DELAY_MS);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (hasWindowFocus()) {
            return;
        }
        screen.closePanel();
        screen.showLibrary(false);
        screen.focusDefault();
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        HomeService.setHomeResumed(true);
        Updater.checkIfDue(this, this::offerUpdate);
        setUpFirstRun();
    }

    @Override
    protected void onPause() {
        resumed = false;
        HomeService.setHomeResumed(false);
        super.onPause();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            screen.restoreFocus(HomeService.takeOverlayFocus());
            HomeService.homeShown();
        }
    }

    @Override
    protected void onStop() {
        handler.removeCallbacks(startMonitors);
        screen.stop();
        Updater.clearListener();
        if (updateDialog != null) {
            updateDialog.dismiss();
        }
        unregisterReceiver(packageReceiver);
        super.onStop();
    }

    private void setUpFirstRun() {
        if (!FirstRun.pending(this)) {
            return;
        }
        Intent consent = AdbBridge.vpnConsent(this);
        if (consent == null) {
            FirstRun.start(this, message -> Toast.makeText(this, message, Toast.LENGTH_LONG).show());
            return;
        }
        if (consentAsked) {
            return;
        }
        consentAsked = true;
        try {
            startActivityForResult(consent, REQUEST_VPN);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, getString(R.string.tools_vpn_dialog_missing, getPackageName()), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_VPN && resultCode == RESULT_OK) {
            setUpFirstRun();
        }
    }

    private void offerUpdate() {
        String version = Updater.pendingVersion(this);
        if (!resumed || isFinishing() || version == null || Updater.isPostponed(this) || updateDialog != null) {
            return;
        }
        updateDialog = new android.app.AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(R.string.update_title)
                .setMessage(getString(R.string.update_message, version, Updater.pendingNotes(this)))
                .setPositiveButton(R.string.update_now, null)
                .setNegativeButton(R.string.update_later, (d, w) -> Updater.postpone(this))
                .setOnCancelListener(d -> Updater.postpone(this))
                .setOnDismissListener(d -> updateDialog = null)
                .create();
        updateDialog.show();
        android.widget.Button now = updateDialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE);
        now.setOnClickListener(v -> startUpdate());
        now.requestFocus();
    }

    private void startUpdate() {
        android.app.AlertDialog dialog = updateDialog;
        if (dialog == null) {
            return;
        }
        dialog.setMessage(getString(R.string.update_installing));
        dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setEnabled(false);
        dialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE).setEnabled(false);
        dialog.setCancelable(false);
        boolean started = Updater.install(this, false, new AdbBridge.Listener() {
            @Override
            public void onStatus(String message) {
                if (updateDialog == dialog) {
                    dialog.setMessage(message);
                }
            }

            @Override
            public void onDone(List<String> outputs, String error) {
                if (error != null) {
                    Toast.makeText(HomeActivity.this, error, Toast.LENGTH_LONG).show();
                }
                if (updateDialog == dialog) {
                    dialog.dismiss();
                }
            }
        });
        if (!started) {
            dialog.dismiss();
        }
    }

    private void reload() {
        List<AppEntry> apps = AppRepository.load(getPackageManager(), getPackageName());
        screen.setApps(apps);
        emptyView.setVisibility(apps.isEmpty() ? View.VISIBLE : View.GONE);
        if (getCurrentFocus() == null) {
            screen.focusDefault();
        }
    }

    @Override
    public void launch(AppEntry entry) {
        Recents.record(this, entry, System.currentTimeMillis());
        Intent intent = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
                .setComponent(new ComponentName(entry.packageName, entry.className))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        start(intent);
    }

    @Override
    public void openSettings() {
        start(SystemIntents.settings(this));
    }

    @Override
    public void openTools() {
        start(new Intent(this, DebloatActivity.class));
    }

    @Override
    public void openProfiles() {
        if (!HomeService.requestProfiles()) {
            openSettings();
        }
    }

    @Override
    public void openBluetooth() {
        start(SystemIntents.bluetooth());
    }

    @Override
    public void openWifi() {
        start(SystemIntents.wifi());
    }

    @Override
    public void openStore(Intent intent) {
        start(intent);
    }

    @Override
    public void openInfo(AppEntry entry) {
        start(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", entry.packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    private void start(Intent intent) {
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, R.string.app_unavailable, Toast.LENGTH_SHORT).show();
            reload();
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            openSettings();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void onBackPressed() {
        if (screen.closePanel()) {
            return;
        }
        if (screen.isLibraryShown()) {
            screen.showLibrary(false);
        }
    }
}
