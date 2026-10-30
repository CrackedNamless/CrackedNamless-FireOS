package com.crackednamless.fireos;

import android.accessibilityservice.AccessibilityService;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;

import java.util.List;

public class HomeService extends AccessibilityService implements HomeScreen.Host {

    private static final String AMAZON_LAUNCHER = "com.amazon.tv.launcher";
    private static final String HOME_PRESSED = "com.amazon.tv.launcher.HOME_PRESSED";
    private static final long RETRY_MS = 250L;
    private static final long COVER_MAX_MS = 12000L;
    private static final long LAUNCH_MAX_MS = 9000L;
    private static final long COOLDOWN_MS = 1000L;
    private static final long HANDOFF_MS = 150L;
    private static final long SAVED_FOCUS_MS = 3000L;
    private static final long ADB_CHECK_MS = 30_000L;
    private static final long ALREADY_HOME_MS = 900L;
    private static final long BOOT_WINDOW_MS = 180000L;
    private static final long BOOT_COVER_MAX_MS = 12000L;
    private static final int PROFILE_TRIES = 12;
    private static final String[] PROFILE_LABELS = {"Profiles", "Profili", "Profile", "Perfiles", "Profils", "Perfis", "Profielen"};
    private int profileTries;

    private static HomeService instance;
    private static boolean homeResumed;
    private static boolean homeSeen;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final java.util.concurrent.ExecutorService launcher = java.util.concurrent.Executors.newSingleThreadExecutor();
    private final java.util.concurrent.atomic.AtomicBoolean opening = new java.util.concurrent.atomic.AtomicBoolean();
    private WindowManager windowManager;
    private FrameLayout overlay;
    private WindowManager.LayoutParams overlayParams;
    private boolean overlayShown;
    private static final int IDLE_FLAGS = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            | WindowManager.LayoutParams.FLAG_FULLSCREEN
            | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
    private static final int SHOWN_FLAGS = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            | WindowManager.LayoutParams.FLAG_FULLSCREEN
            | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
    private FrameLayout volumeWindow;
    private VolumeStrip volume;
    private HomeScreen screen;
    private boolean covering;
    private boolean launching;
    private String launchPackage;
    private long coverUntil;
    private long shownAt;
    private View curtain;
    private SystemLogWatcher logWatcher;
    private long coverStart;
    private HomeScreen.FocusState savedFocus;
    private long savedFocusAt;
    private boolean pausedInCover;

    private final Runnable hideCover = () -> {
        if (!covering) {
            hideOverlay();
        }
    };

    private final Runnable retry = new Runnable() {
        @Override
        public void run() {
            if (!covering) {
                return;
            }
            if (SystemClock.uptimeMillis() > coverUntil) {
                finishCover();
                return;
            }
            if (homeResumed) {
                // HOME premuto mentre la nostra Home e' gia' in primo piano: se il launcher Amazon non la copre, si scopre.
                if (!pausedInCover && !launching && SystemClock.uptimeMillis() - coverStart > ALREADY_HOME_MS) {
                    finishCover();
                    return;
                }
            } else {
                pausedInCover = true;
                if (!launching) {
                    openHome();
                }
            }
            handler.postDelayed(this, RETRY_MS);
        }
    };

    private final BroadcastReceiver packageChanged = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            AppRepository.invalidate();
        }
    };

    private final BroadcastReceiver homePressed = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            cover();
        }
    };

    // Se lo schermo si spegne da solo (timeout), si prepara la Home per il risveglio.
    private final BroadcastReceiver screenState = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!homeResumed) {
                openHome();
            }
        }
    };

    public static HomeScreen.FocusState takeOverlayFocus() {
        HomeService s = instance;
        if (s == null) {
            return null;
        }
        HomeScreen.FocusState live = s.overlayShown && s.screen != null ? s.screen.focusState() : null;
        HomeScreen.FocusState saved = SystemClock.uptimeMillis() - s.savedFocusAt < SAVED_FOCUS_MS ? s.savedFocus : null;
        s.savedFocus = null;
        return live != null ? live : saved;
    }

    public static void homeShown() {
        HomeService s = instance;
        if (s != null) {
            s.handler.post(s::onHomeShown);
        }
    }

    // Il filtro tasti dell'accessibilita' non riceve il telecomando su Fire OS: i tasti si leggono da HomeActivity.
    public static boolean onStandbyKey(KeyEvent event) {
        HomeService s = instance;
        if (s == null) {
            return false;
        }
        if (event.getKeyCode() == KeyEvent.KEYCODE_POWER) {
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                s.enterStandby();
            }
            return true;
        }
        return false;
    }

    public static boolean requestProfiles() {
        HomeService s = instance;
        if (s == null) {
            return false;
        }
        s.openProfiles();
        return true;
    }

    public static void setHomeResumed(boolean resumed) {
        homeResumed = resumed;
        if (resumed) {
            homeSeen = true;
            homeShown();
        }
    }

    @Override
    protected void onServiceConnected() {
        instance = this;
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        registerReceiver(homePressed, new IntentFilter(HOME_PRESSED));
        IntentFilter packages = new IntentFilter();
        packages.addAction(Intent.ACTION_PACKAGE_ADDED);
        packages.addAction(Intent.ACTION_PACKAGE_REMOVED);
        packages.addAction(Intent.ACTION_PACKAGE_CHANGED);
        packages.addDataScheme("package");
        registerReceiver(packageChanged, packages);
        buildOverlay();
        attachOverlay();
        buildVolumeWindow();
        registerReceiver(screenState, new IntentFilter(Intent.ACTION_SCREEN_OFF));
        logWatcher = new SystemLogWatcher(new SystemLogWatcher.Listener() {
            @Override
            public void onPowerKey() {
                handler.post(HomeService.this::enterStandby);
            }

            @Override
            public void onHomeStart() {
                AdbBridge.unlockAppSwitches();
                handler.post(HomeService.this::cover);
            }
        });
        logWatcher.start();
        adbKeeper.run();
    }

    private final Runnable adbKeeper = new Runnable() {
        @Override
        public void run() {
            AdbBridge.maintain(HomeService.this);
            handler.postDelayed(this, ADB_CHECK_MS);
        }
    };

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        CharSequence pkg = event.getPackageName();
        CharSequence cls = event.getClassName();
        if (pkg == null) {
            return;
        }
        if (launching) {
            boolean amazonSettings = AMAZON_LAUNCHER.contentEquals(pkg) && cls != null && cls.toString().contains("Settings");
            if (amazonSettings || (launchPackage != null ? launchPackage.contentEquals(pkg) : isForeignWindow(pkg))) {
                finishCover();
            }
            return;
        }
        if (!AMAZON_LAUNCHER.contentEquals(pkg)) {
            return;
        }
        if (cls != null && cls.toString().contains("Settings")) {
            return;
        }
        if (!covering && !amazonIsActive()) {
            return;
        }
        if (!covering && SystemClock.uptimeMillis() - shownAt < COOLDOWN_MS) {
            return;
        }
        cover();
    }

    private boolean amazonIsActive() {
        android.view.accessibility.AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) {
            return true;
        }
        CharSequence active = root.getPackageName();
        root.recycle();
        return active == null || AMAZON_LAUNCHER.contentEquals(active);
    }

    private boolean isForeignWindow(CharSequence pkg) {
        return !AMAZON_LAUNCHER.contentEquals(pkg)
                && !getPackageName().contentEquals(pkg)
                && !"android".contentEquals(pkg)
                && !"com.android.systemui".contentEquals(pkg);
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        return onStandbyKey(event);
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public void onDestroy() {
        instance = null;
        handler.removeCallbacks(adbKeeper);
        launcher.shutdownNow();
        if (logWatcher != null) {
            logWatcher.stop();
        }
        finishCover();
        detachOverlay();
        removeVolumeWindow();
        try {
            unregisterReceiver(screenState);
        } catch (IllegalArgumentException ignored) {
            // Mai registrato.
        }
        try {
            unregisterReceiver(packageChanged);
            unregisterReceiver(homePressed);
        } catch (IllegalArgumentException ignored) {
            // Mai registrato: il servizio e' stato fermato prima della connessione.
        }
        super.onDestroy();
    }

    @Override
    public void launch(AppEntry entry) {
        Recents.record(this, entry, System.currentTimeMillis());
        launchPackage = entry.packageName;
        begin(new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
                .setComponent(new ComponentName(entry.packageName, entry.className))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED));
    }

    @Override
    public void openSettings() {
        launchPackage = null;
        begin(SystemIntents.settings(this));
    }

    @Override
    public void openTools() {
        launchPackage = null;
        begin(new Intent(this, DebloatActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    // Il selettore profili di Amazon e' protetto da permesso di sistema: si apre dalle Impostazioni cliccando la voce "Profili".
    @Override
    public void openProfiles() {
        openSettings();
        profileTries = 0;
        handler.removeCallbacks(profileClicker);
        handler.postDelayed(profileClicker, 600L);
    }

    private final Runnable profileClicker = new Runnable() {
        @Override
        public void run() {
            if (clickProfiles()) {
                return;
            }
            if (++profileTries < PROFILE_TRIES) {
                handler.postDelayed(this, 400L);
            }
        }
    };

    private boolean clickProfiles() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null || !AMAZON_LAUNCHER.contentEquals(String.valueOf(root.getPackageName()))) {
            return false;
        }
        boolean clicked = false;
        for (String label : PROFILE_LABELS) {
            List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText(label);
            for (AccessibilityNodeInfo node : nodes) {
                if (!label.contentEquals(String.valueOf(node.getText()))) {
                    continue;
                }
                AccessibilityNodeInfo target = node;
                while (target != null && !target.isClickable()) {
                    target = target.getParent();
                }
                if (target != null) {
                    clicked = target.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                    break;
                }
            }
            if (clicked) {
                break;
            }
        }
        root.recycle();
        return clicked;
    }

    @Override
    public void openBluetooth() {
        launchPackage = null;
        begin(SystemIntents.bluetooth());
    }

    @Override
    public void openWifi() {
        launchPackage = null;
        begin(SystemIntents.wifi());
    }

    @Override
    public void openSearch() {
        launchPackage = null;
        begin(new Intent(Intent.ACTION_ASSIST).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    @Override
    public void openStore(Intent intent) {
        launchPackage = null;
        begin(intent);
    }

    @Override
    public void openInfo(AppEntry entry) {
        launchPackage = null;
        begin(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", entry.packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    private void begin(Intent intent) {
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException e) {
            return;
        }
        launching = true;
        coverUntil = SystemClock.uptimeMillis() + LAUNCH_MAX_MS;
    }

    // Al boot la Home vera impiega qualche secondo: si copre con il nero, cosi la GIF parte senza lampi di Home.
    private static boolean booting() {
        return !homeSeen && SystemClock.elapsedRealtime() < BOOT_WINDOW_MS;
    }

    private void cover() {
        coverUntil = SystemClock.uptimeMillis() + (booting() ? BOOT_COVER_MAX_MS : COVER_MAX_MS);
        if (covering) {
            return;
        }
        coverStart = SystemClock.uptimeMillis();
        pausedInCover = false;
        covering = true;
        launching = false;
        launchPackage = null;
        showOverlay();
        handler.post(retry);
    }

    private void onHomeShown() {
        if (launching) {
            return;
        }
        shownAt = SystemClock.uptimeMillis();
        covering = false;
        launching = false;
        launchPackage = null;
        handler.removeCallbacks(retry);
        handler.removeCallbacks(hideCover);
        handler.postDelayed(hideCover, HANDOFF_MS);
    }

    private void finishCover() {
        handler.removeCallbacks(hideCover);
        covering = false;
        launching = false;
        launchPackage = null;
        handler.removeCallbacks(retry);
        hideOverlay();
    }

    // startActivity e' una chiamata binder che sotto carico blocca anche 700 ms: fuori dal thread principale l'overlay non si congela.
    private void openHome() {
        if (!opening.compareAndSet(false, true)) {
            return;
        }
        launcher.execute(() -> {
            try {
                startActivity(new Intent(this, HomeActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION));
            } catch (RuntimeException ignored) {
                // Avvio bloccato dal sistema: si riprova al prossimo giro.
            } finally {
                opening.set(false);
            }
        });
    }

    private void buildOverlay() {
        Context ui = Scaling.wrap(this);
        // Android blocca per ~5 s gli avvii dopo HOME: l'overlay prende il focus e gestisce i tasti da solo.
        FrameLayout frame = new FrameLayout(ui) {
            @Override
            public boolean dispatchKeyEvent(KeyEvent event) {
                if (onStandbyKey(event)) {
                    return true;
                }
                if (isVolumeKey(event.getKeyCode())) {
                    return false;
                }
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    handleKey(event.getKeyCode());
                }
                return isHandledKey(event.getKeyCode());
            }
        };
        frame.setBackground(GlassBackground.create(ui.getResources()));
        LayoutInflater.from(ui).inflate(R.layout.home_screen, frame, true);
        screen = new HomeScreen(ui, frame.findViewById(R.id.home_root), this);
        curtain = new View(ui);
        curtain.setBackgroundColor(Color.BLACK);
        curtain.setVisibility(View.GONE);
        frame.addView(curtain, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        overlay = frame;
    }

    // Stessa sospensione di Impostazioni > La mia Fire TV > Standby. Si risveglia con un tasto del telecomando.
    private void enterStandby() {
        PowerManager power = (PowerManager) getSystemService(POWER_SERVICE);
        if (power != null && !power.isInteractive()) {
            return;
        }
        AudioManager audio = (AudioManager) getSystemService(AUDIO_SERVICE);
        if (audio != null) {
            audio.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PAUSE));
            audio.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PAUSE));
        }
        performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN);
    }

    private static boolean isVolumeKey(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_VOLUME_UP
                || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
                || keyCode == KeyEvent.KEYCODE_VOLUME_MUTE;
    }

    private void buildVolumeWindow() {
        Context ui = Scaling.wrap(this);
        FrameLayout frame = new FrameLayout(ui);
        LayoutInflater.from(ui).inflate(R.layout.volume_overlay, frame, true);
        volume = new VolumeStrip(ui, frame);
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.RIGHT | Gravity.CENTER_VERTICAL;
        lp.x = (int) (30 * ui.getResources().getDisplayMetrics().density);
        try {
            windowManager.addView(frame, lp);
            volumeWindow = frame;
            volume.start();
        } catch (RuntimeException ignored) {
            // Overlay non disponibile: niente striscia del volume.
        }
    }

    private void removeVolumeWindow() {
        if (volume != null) {
            volume.stop();
        }
        if (volumeWindow != null && volumeWindow.getParent() != null) {
            try {
                windowManager.removeView(volumeWindow);
            } catch (RuntimeException ignored) {
                // Gia' rimosso dal sistema.
            }
        }
        volumeWindow = null;
    }

    private final Runnable refreshOverlay = new Runnable() {
        @Override
        public void run() {
            if (overlay == null || !overlayShown) {
                return;
            }
            List<AppEntry> apps = AppRepository.load(getPackageManager(), getPackageName());
            screen.setApps(apps);
        }
    };

    private void attachOverlay() {
        overlayParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                IDLE_FLAGS,
                PixelFormat.OPAQUE);
        overlayParams.alpha = 0f;
        try {
            windowManager.addView(overlay, overlayParams);
        } catch (RuntimeException ignored) {
            // Overlay non disponibile: l'attivita' parte comunque, con un possibile sfarfallio.
        }
    }

    // L'overlay resta agganciato e trasparente: all'HOME basta renderlo opaco, senza crearlo da zero.
    private void showOverlay() {
        if (overlay == null || overlay.getParent() == null || overlayShown) {
            return;
        }
        overlayShown = true;
        curtain.setVisibility(booting() ? View.VISIBLE : View.GONE);
        screen.showLibrary(false);
        screen.restoreStatus();
        overlayParams.alpha = 1f;
        overlayParams.flags = SHOWN_FLAGS;
        try {
            windowManager.updateViewLayout(overlay, overlayParams);
            screen.focusDefault();
            handler.post(refreshOverlay);
        } catch (RuntimeException ignored) {
            overlayShown = false;
        }
    }

    private void hideOverlay() {
        if (overlay == null || overlay.getParent() == null || !overlayShown) {
            return;
        }
        savedFocus = screen.focusState();
        savedFocusAt = SystemClock.uptimeMillis();
        overlayShown = false;
        screen.stop();
        overlayParams.alpha = 0f;
        overlayParams.flags = IDLE_FLAGS;
        try {
            windowManager.updateViewLayout(overlay, overlayParams);
        } catch (RuntimeException ignored) {
            // Gia' rimosso dal sistema.
        }
    }

    private void detachOverlay() {
        if (overlay != null && overlay.getParent() != null) {
            try {
                windowManager.removeView(overlay);
            } catch (RuntimeException ignored) {
                // Gia' rimosso dal sistema.
            }
        }
        overlayShown = false;
    }

    private static boolean isHandledKey(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_BACK:
            case KeyEvent.KEYCODE_MENU:
                return true;
            default:
                return false;
        }
    }

    private void handleKey(int keyCode) {
        if (launching) {
            return;
        }
        View focused = overlay.findFocus();
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP:
                move(focused, View.FOCUS_UP);
                break;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                move(focused, View.FOCUS_DOWN);
                break;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                move(focused, View.FOCUS_LEFT);
                break;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                move(focused, View.FOCUS_RIGHT);
                break;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                if (focused != null) {
                    focused.performClick();
                }
                break;
            case KeyEvent.KEYCODE_BACK:
                if (screen.closePanel()) {
                    break;
                }
                if (screen.isLibraryShown()) {
                    screen.showLibrary(false);
                }
                break;
            case KeyEvent.KEYCODE_MENU:
                openSettings();
                break;
            default:
                break;
        }
    }

    private void move(View focused, int direction) {
        if (focused == null) {
            screen.focusDefault();
            return;
        }
        View next = focused.focusSearch(direction);
        if (next != null) {
            next.requestFocus(direction);
        }
    }
}
