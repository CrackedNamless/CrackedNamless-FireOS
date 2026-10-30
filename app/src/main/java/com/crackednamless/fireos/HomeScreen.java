package com.crackednamless.fireos;

import android.animation.ArgbEvaluator;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class HomeScreen {

    public interface Host {
        void launch(AppEntry entry);

        void openSettings();

        void openTools();

        void openProfiles();

        void openWifi();

        void openBluetooth();


        void openStore(Intent intent);

        void openInfo(AppEntry entry);
    }

    private static final int LIBRARY_SPAN = 8;
    private static final int RECENT_SPAN = 5;

    private final Context context;
    private final ImageView profileView;
    private final PackageManager pm;
    private final TextView credit;
    private final Host host;

    private final View homeContent;
    private final View library;
    private final View tabHome;
    private final View tabLibrary;
    private final View hero;
    private final ImageView heroArt;
    private final View heroFade;
    private final TextView heroChip;
    private final TextView heroTitle;
    private final TextView heroSubtitle;
    private final TextView heroDesc;
    private final View heroPlay;
    private final View heroInfo;
    private final View recentTitle;
    private final RecyclerView recentRow;
    private final ImageView wifi;
    private final View wifiButton;
    private final TextView wifiPing;
    private final NetworkMonitor network;
    private int netState = NetworkMonitor.OFFLINE;
    private final View safeArea;
    private final View speedPanel;
    private final TextView speedValue;
    private final TextView speedPhase;
    private final TextView speedPing;
    private final TextView speedDown;
    private final TextView speedUp;
    private final View speedAgain;
    private final View speedClose;
    private SpeedTest speedTest;
    private final ImageView btIcon;
    private final TextView btCount;
    private final View btButton;
    private final View btPanel;
    private final TextView btStatus;
    private final TextView btHint;
    private final LinearLayout btList;
    private final View btAdd;
    private final View btClose;
    private final BluetoothMonitor bluetooth;
    private BluetoothMonitor.State btState;
    private static final int NOTE_CONNECTING = 1;
    private static final int NOTE_FAILED = 2;
    private final Map<String, Integer> btNotes = new HashMap<>();
    private final View rightCol;
    private final View weatherCard;
    private final TextView weatherCity;
    private final TextView weatherTemp;
    private final TextView weatherDesc;
    private final ImageView weatherIcon;
    private final View mediaCard;
    private final TextView mediaLabel;
    private final TextView mediaTitle;
    private final TextView mediaArtist;
    private final ImageView mediaArt;
    private final ImageView mediaToggle;
    private final ProgressBar mediaProgress;
    private final MediaMonitor media;
    private final AppAdapter rowAdapter;
    private final AppAdapter libraryAdapter;
    private final RecentAdapter recentAdapter;
    private final RecyclerView libraryGrid;
    private final RecyclerView appsRow;

    private AppEntry heroApp;
    private boolean libraryShown;

    public HomeScreen(Context context, View root, Host host) {
        this.context = context;
        this.pm = context.getPackageManager();
        this.host = host;

        homeContent = root.findViewById(R.id.home_content);
        library = root.findViewById(R.id.library);
        tabHome = root.findViewById(R.id.tab_home);
        tabLibrary = root.findViewById(R.id.tab_library);
        hero = root.findViewById(R.id.hero);
        heroArt = root.findViewById(R.id.hero_art);
        heroFade = root.findViewById(R.id.hero_fade);
        heroChip = root.findViewById(R.id.hero_chip);
        heroTitle = root.findViewById(R.id.hero_title);
        heroSubtitle = root.findViewById(R.id.hero_subtitle);
        heroDesc = root.findViewById(R.id.hero_desc);
        heroPlay = root.findViewById(R.id.hero_play);
        heroInfo = root.findViewById(R.id.hero_info);
        recentTitle = root.findViewById(R.id.recent_title);
        recentRow = root.findViewById(R.id.recent_row);
        appsRow = root.findViewById(R.id.apps_row);
        libraryGrid = root.findViewById(R.id.library_grid);
        wifi = root.findViewById(R.id.wifi);
        wifiButton = root.findViewById(R.id.wifi_btn);
        wifiPing = root.findViewById(R.id.wifi_ping);
        safeArea = root.findViewById(R.id.safe_area);
        speedPanel = root.findViewById(R.id.speed_panel);
        speedValue = root.findViewById(R.id.speed_value);
        speedPhase = root.findViewById(R.id.speed_phase);
        speedPing = root.findViewById(R.id.speed_ping);
        speedDown = root.findViewById(R.id.speed_down);
        speedUp = root.findViewById(R.id.speed_up);
        speedAgain = root.findViewById(R.id.speed_again);
        speedClose = root.findViewById(R.id.speed_close);
        network = new NetworkMonitor(context, this::onNetwork);
        btButton = root.findViewById(R.id.bt_btn);
        btIcon = root.findViewById(R.id.bt_icon);
        btCount = root.findViewById(R.id.bt_count);
        btPanel = root.findViewById(R.id.bt_panel);
        btStatus = root.findViewById(R.id.bt_status);
        btHint = root.findViewById(R.id.bt_hint);
        btList = root.findViewById(R.id.bt_list);
        btAdd = root.findViewById(R.id.bt_add);
        btClose = root.findViewById(R.id.bt_close);
        bluetooth = new BluetoothMonitor(context, this::onBluetooth);
        rightCol = root.findViewById(R.id.right_col);
        weatherCard = root.findViewById(R.id.weather_card);
        weatherCity = root.findViewById(R.id.weather_city);
        weatherTemp = root.findViewById(R.id.weather_temp);
        weatherDesc = root.findViewById(R.id.weather_desc);
        weatherIcon = root.findViewById(R.id.weather_icon);
        mediaCard = root.findViewById(R.id.media_card);
        mediaLabel = root.findViewById(R.id.media_label);
        mediaTitle = root.findViewById(R.id.media_title);
        mediaArtist = root.findViewById(R.id.media_artist);
        mediaArt = root.findViewById(R.id.media_art);
        mediaToggle = root.findViewById(R.id.media_toggle);
        mediaProgress = root.findViewById(R.id.media_progress);
        Scaling.applySafeArea(root.findViewById(R.id.safe_area));
        media = new MediaMonitor(context, this::bindMedia);
        mediaToggle.setOnClickListener(v -> media.toggle());
        FocusScale.apply(mediaToggle, null);

        rowAdapter = new AppAdapter(pm, host::launch, () -> showLibrary(true));
        appsRow.setLayoutManager(new GridLayoutManager(context, LIBRARY_SPAN));
        appsRow.setAdapter(rowAdapter);

        recentAdapter = new RecentAdapter(pm, host::launch);
        recentRow.setLayoutManager(new GridLayoutManager(context, RECENT_SPAN));
        recentRow.setAdapter(recentAdapter);

        libraryAdapter = new AppAdapter(pm, host::launch, null);
        libraryGrid.setLayoutManager(new GridLayoutManager(context, LIBRARY_SPAN));
        libraryGrid.setAdapter(libraryAdapter);

        tabHome.setSelected(true);
        tabHome.setOnClickListener(v -> showLibrary(false));
        tabLibrary.setOnClickListener(v -> showLibrary(true));
        View tabStore = root.findViewById(R.id.tab_store);
        tabStore.setOnClickListener(v -> openStore());
        credit = root.findViewById(R.id.credit);
        credit.setText(context.getString(R.string.made_by, versionName()));
        View settings = root.findViewById(R.id.settings);
        settings.setOnClickListener(v -> host.openSettings());
        settings.setOnLongClickListener(v -> {
            host.openTools();
            return true;
        });
        profileView = root.findViewById(R.id.profile);
        View profile = profileView;
        profile.setOnClickListener(v -> host.openProfiles());
        root.findViewById(R.id.hero_all).setOnClickListener(v -> showLibrary(true));
        heroPlay.setOnClickListener(v -> {
            if (heroApp != null) {
                host.launch(heroApp);
            }
        });
        heroInfo.setOnClickListener(v -> {
            if (heroApp != null) {
                host.openInfo(heroApp);
            }
        });

        wifiButton.setOnClickListener(v -> {
            if (netState == NetworkMonitor.ONLINE) {
                showSpeed(true);
            } else {
                host.openWifi();
            }
        });
        btButton.setOnClickListener(v -> showBluetooth(true));
        btAdd.setOnClickListener(v -> host.openBluetooth());
        btClose.setOnClickListener(v -> showBluetooth(false));
        FocusScale.apply(btAdd, null);
        FocusScale.apply(btClose, null);
        speedAgain.setOnClickListener(v -> runSpeedTest());
        speedClose.setOnClickListener(v -> showSpeed(false));
        FocusScale.apply(speedAgain, null);
        FocusScale.apply(speedClose, null);
        onNetwork(NetworkMonitor.OFFLINE, -1);

        for (View v : new View[]{profile, tabHome, tabLibrary, tabStore, settings, wifiButton, btButton, heroPlay,
                root.findViewById(R.id.hero_all), heroInfo}) {
            FocusScale.apply(v, null);
        }
    }

    private String appsKey;

    public void setApps(List<AppEntry> apps) {
        Map<String, AppEntry> byKey = new HashMap<>();
        StringBuilder key = new StringBuilder();
        for (AppEntry e : apps) {
            byKey.put(e.packageName, e);
            key.append(e.packageName).append('/').append(e.className).append('=').append(e.label).append(';');
        }
        long now = System.currentTimeMillis();
        List<RecentAdapter.Entry> recents = new ArrayList<>();
        for (Recents.Item i : Recents.load(context)) {
            AppEntry e = byKey.get(i.packageName);
            if (e != null) {
                recents.add(new RecentAdapter.Entry(e, i.time));
                key.append('|').append(e.packageName).append(Recents.ago(context.getResources(), i.time, now));
            }
        }
        // Stessi dati di prima: niente ricostruzione, cosi la Home non sfarfalla ne perde il focus.
        String next = key.toString();
        if (next.equals(appsKey)) {
            return;
        }
        appsKey = next;

        rowAdapter.submit(apps.subList(0, Math.min(apps.size(), LIBRARY_SPAN - 1)));
        libraryAdapter.submit(apps);

        if (!recents.isEmpty()) {
            RecentAdapter.Entry first = recents.get(0);
            bindHero(first.app, context.getString(R.string.hero_last_opened),
                    context.getString(R.string.opened_ago, Recents.ago(context.getResources(), first.time, now)),
                    context.getString(R.string.hero_resume));
        } else if (!apps.isEmpty()) {
            bindHero(apps.get(0), context.getString(R.string.hero_featured), context.getString(R.string.hero_installed),
                    context.getString(R.string.hero_pick));
        } else {
            bindHero(null, "", "", "");
        }

        List<RecentAdapter.Entry> rest = recents.size() > 1
                ? recents.subList(1, Math.min(recents.size(), RECENT_SPAN + 1)) : Collections.<RecentAdapter.Entry>emptyList();
        recentAdapter.submit(rest);
        int vis = rest.isEmpty() ? View.GONE : View.VISIBLE;
        recentTitle.setVisibility(vis);
        recentRow.setVisibility(vis);
    }

    public void refreshWeather() {
        Weather.load(context, this::bindWeather);
    }

    private void bindWeather(Weather.Data d) {
        lastWeather = d;
        weatherCity.setText(d.city);
        weatherTemp.setText(Weather.temperature(d));
        weatherDesc.setText(Weather.description(d.code, d.day));
        weatherIcon.setImageResource(Weather.icon(d));
        weatherCard.setVisibility(View.VISIBLE);
        updateRightColumn();
    }

    public void refreshProfile() {
        android.graphics.Bitmap avatar = ProfileAvatars.activeAvatar(context);
        if (avatar != null) {
            profileView.setImageBitmap(avatar);
        } else {
            profileView.setImageResource(R.drawable.profile_avatar);
        }
    }

    public void start() {
        refreshProfile();
        media.start();
        network.start();
        bluetooth.start();
    }

    public void stop() {
        media.stop();
        network.stop();
        bluetooth.stop();
        showBluetooth(false);
        showSpeed(false);
    }

    private void bindMedia(MediaMonitor.State s) {
        if (s == null) {
            mediaCard.setVisibility(View.GONE);
            updateRightColumn();
            return;
        }
        mediaLabel.setText(context.getString(R.string.now_playing, appLabel(s.app).toUpperCase(Locale.getDefault())));
        mediaTitle.setText(s.title);
        mediaArtist.setText(s.artist);
        mediaArt.setImageBitmap(s.art);
        mediaToggle.setImageResource(s.playing ? R.drawable.ic_pause : R.drawable.ic_play);
        mediaProgress.setProgress(Math.round(s.progress * mediaProgress.getMax()));
        mediaCard.setVisibility(View.VISIBLE);
        updateRightColumn();
    }

    private String appLabel(String pkg) {
        try {
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (PackageManager.NameNotFoundException e) {
            return pkg;
        }
    }

    private void updateRightColumn() {
        boolean any = weatherCard.getVisibility() == View.VISIBLE
                || mediaCard.getVisibility() == View.VISIBLE;
        rightCol.setVisibility(any ? View.VISIBLE : View.GONE);
    }

    public boolean isLibraryShown() {
        return libraryShown;
    }

    private String versionName() {
        try {
            return pm.getPackageInfo(context.getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "";
        }
    }

    public void showLibrary(boolean show) {
        libraryShown = show;
        credit.setVisibility(show ? View.GONE : View.VISIBLE);
        homeContent.setVisibility(show ? View.GONE : View.VISIBLE);
        library.setVisibility(show ? View.VISIBLE : View.GONE);
        tabHome.setSelected(!show);
        tabLibrary.setSelected(show);
        focusDefault();
    }

    public void focusDefault() {
        View target = libraryShown ? libraryGrid : heroPlay;
        target.post(target::requestFocus);
    }

    public static final class FocusState {
        final boolean library;
        final int viewId;
        final int position;

        FocusState(boolean library, int viewId, int position) {
            this.library = library;
            this.viewId = viewId;
            this.position = position;
        }
    }

    public FocusState focusState() {
        View focused = safeArea.findFocus();
        if (focused == null) {
            return null;
        }
        for (RecyclerView rv : new RecyclerView[]{appsRow, recentRow, libraryGrid}) {
            if (rv.indexOfChild(focused) >= 0) {
                return new FocusState(libraryShown, rv.getId(), rv.getChildAdapterPosition(focused));
            }
        }
        return new FocusState(libraryShown, focused.getId(), -1);
    }

    public void restoreFocus(FocusState state) {
        if (state == null || state.viewId == View.NO_ID) {
            return;
        }
        if (state.library != libraryShown) {
            showLibrary(state.library);
        }
        View target = safeArea.findViewById(state.viewId);
        if (target == null) {
            return;
        }
        if (state.position < 0 || !(target instanceof RecyclerView)) {
            target.post(target::requestFocus);
            return;
        }
        RecyclerView rv = (RecyclerView) target;
        rv.post(() -> {
            RecyclerView.ViewHolder h = rv.findViewHolderForAdapterPosition(state.position);
            if (h != null) {
                h.itemView.requestFocus();
                return;
            }
            rv.scrollToPosition(state.position);
            rv.post(() -> {
                RecyclerView.ViewHolder late = rv.findViewHolderForAdapterPosition(state.position);
                if (late != null) {
                    late.itemView.requestFocus();
                }
            });
        });
    }

    public boolean hasFocus() {
        return hero.getRootView().findFocus() != null;
    }

    private void bindHero(AppEntry app, String chip, String subtitle, String desc) {
        heroApp = app;
        heroChip.setText(chip);
        heroSubtitle.setText(subtitle);
        heroDesc.setText(desc);
        heroPlay.setEnabled(app != null);
        heroInfo.setEnabled(app != null);
        AppArt art = app != null ? AppArt.banner(pm, context.getResources(), app) : null;
        int accent = art != null ? AppArt.accent(art.drawable) : 0xFF3B6BFF;
        paintHero(accent);
        if (app == null) {
            heroTitle.setText(R.string.no_apps);
            heroArt.setImageDrawable(null);
            return;
        }
        heroTitle.setText(app.label);
        if (art.isBanner) {
            heroArt.setScaleType(ImageView.ScaleType.CENTER_CROP);
            heroArt.setPadding(0, 0, 0, 0);
        } else {
            int pad = (int) (40 * context.getResources().getDisplayMetrics().density);
            heroArt.setScaleType(ImageView.ScaleType.FIT_CENTER);
            heroArt.setPadding(pad, pad, pad, pad);
        }
        heroArt.setImageDrawable(art.drawable);
    }

    private void paintHero(int accent) {
        float density = context.getResources().getDisplayMetrics().density;
        int left = AppArt.shade(accent, 0.55f, 0.16f);
        int right = AppArt.shade(accent, 0.65f, 0.26f);
        float radius = 22 * density;

        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{left, right});
        bg.setCornerRadius(radius);
        hero.setClipToOutline(true);

        hero.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override
            public void getOutline(View v, android.graphics.Outline outline) {
                outline.setRoundRect(0, 0, v.getWidth(), v.getHeight(), radius);
            }
        });
        hero.setBackground(bg);

        heroArt.setBackgroundColor(right);

        int edge = (Integer) new ArgbEvaluator().evaluate(0.54f, left, right);
        GradientDrawable fade = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{edge, (edge & 0x00FFFFFF) | 0x99000000, edge & 0x00FFFFFF});
        heroFade.setBackground(fade);
    }

    private static final int COLOR_ONLINE = 0xFF34D399;
    private static final int COLOR_NO_INTERNET = 0xFFFBBF24;
    private static final int COLOR_OFFLINE = 0xFFF87171;

    private static int lastNetState = -1;
    private static int lastPing;
    private static BluetoothMonitor.State lastBt;
    private static Weather.Data lastWeather;

    // La Home sovrapposta non avvia i monitor: riprende l'ultimo stato noto, cosi' icone e meteo non si ricaricano al passaggio all'activity.
    public void restoreStatus() {
        refreshProfile();
        if (lastNetState >= 0) {
            onNetwork(lastNetState, lastPing);
        }
        if (lastBt != null) {
            onBluetooth(lastBt);
        }
        if (lastWeather != null) {
            bindWeather(lastWeather);
        }
    }

    private void onNetwork(int state, int pingMs) {
        lastNetState = state;
        lastPing = pingMs;
        netState = state;
        int color = state == NetworkMonitor.ONLINE ? COLOR_ONLINE
                : state == NetworkMonitor.NO_INTERNET ? COLOR_NO_INTERNET : COLOR_OFFLINE;
        wifi.setColorFilter(color);
        if (state == NetworkMonitor.ONLINE) {
            wifiPing.setText(context.getString(R.string.ms_value, pingMs));
            wifiPing.setTextColor(color);
            wifiPing.setVisibility(View.VISIBLE);
        } else {
            wifiPing.setVisibility(View.GONE);
        }
        wifiButton.setContentDescription(context.getString(state == NetworkMonitor.ONLINE ? R.string.net_run_speed_test
                : state == NetworkMonitor.NO_INTERNET ? R.string.net_no_internet
                : R.string.net_offline));
    }

    private static final int COLOR_BT_CONNECTED = 0xFF60A5FA;

    private void onBluetooth(BluetoothMonitor.State s) {
        lastBt = s;
        btState = s;
        int color = !s.enabled ? 0x66FFFFFF : s.connectedCount > 0 ? COLOR_BT_CONNECTED : 0xFFFFFFFF;
        btIcon.setColorFilter(color);
        if (s.connectedCount > 0) {
            btCount.setText(String.valueOf(s.connectedCount));
            btCount.setTextColor(color);
            btCount.setVisibility(View.VISIBLE);
        } else {
            btCount.setVisibility(View.GONE);
        }
        if (isBluetoothShown()) {
            bindBluetoothPanel();
        }
    }

    private void connectDevice(BluetoothMonitor.Device dev) {
        btNotes.put(dev.address, NOTE_CONNECTING);
        bindBluetoothPanel();
        bluetooth.connect(dev, () -> {
            btNotes.put(dev.address, NOTE_FAILED);
            if (isBluetoothShown()) {
                bindBluetoothPanel();
            }
        });
        btList.postDelayed(() -> {
            if (Integer.valueOf(NOTE_CONNECTING).equals(btNotes.get(dev.address))) {
                btNotes.put(dev.address, NOTE_FAILED);
                if (isBluetoothShown()) {
                    bindBluetoothPanel();
                }
            }
        }, 15000L);
    }

    private void bindBluetoothPanel() {
        BluetoothMonitor.State s = btState;
        View focusedRow = btList.findFocus();
        Object focusedTag = focusedRow != null ? focusedRow.getTag() : null;
        btList.removeAllViews();
        if (s == null || !s.supported) {
            btStatus.setText(R.string.bt_unavailable);
            btHint.setText(R.string.bt_unavailable_hint);
            return;
        }
        if (!s.enabled) {
            btStatus.setText(R.string.bt_off);
            btHint.setText(R.string.bt_off_hint);
            return;
        }
        btStatus.setText(s.connectedCount > 0
                ? context.getResources().getQuantityString(R.plurals.bt_connected_count, s.connectedCount, s.connectedCount)
                : context.getString(R.string.bt_none_connected));
        boolean hasIdle = s.connectedCount < s.devices.size();
        btHint.setText(s.devices.isEmpty()
                ? context.getString(R.string.bt_hint_pair)
                : context.getString(hasIdle ? R.string.bt_hint_select : R.string.bt_hint_paired));
        int shown = Math.min(s.devices.size(), 5);
        float d = context.getResources().getDisplayMetrics().density;
        for (int i = 0; i < shown; i++) {
            final BluetoothMonitor.Device dev = s.devices.get(i);
            final Integer note = dev.connected ? null : btNotes.get(dev.address);
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            row.setBackgroundResource(R.drawable.row_bg);
            row.setTag(dev.address);
            if (!dev.connected) {
                row.setFocusable(true);
                row.setClickable(true);
                row.setOnClickListener(v -> connectDevice(dev));
            }
            row.setPadding((int) (14 * d), 0, (int) (14 * d), 0);
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, (int) (44 * d));
            rowLp.topMargin = i == 0 ? 0 : (int) (6 * d);
            row.setLayoutParams(rowLp);

            View dot = new View(context);
            GradientDrawable dotBg = new GradientDrawable();
            dotBg.setShape(GradientDrawable.OVAL);
            dotBg.setColor(dev.connected ? COLOR_BT_CONNECTED : 0x55FFFFFF);
            dot.setBackground(dotBg);
            row.addView(dot, new LinearLayout.LayoutParams((int) (8 * d), (int) (8 * d)));

            LinearLayout texts = new LinearLayout(context);
            texts.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams textsLp = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            textsLp.leftMargin = (int) (12 * d);
            row.addView(texts, textsLp);

            TextView name = new TextView(context);
            name.setText(dev.name);
            name.setTextColor(0xFFFFFFFF);
            name.setTextSize(13);
            name.setTypeface(null, android.graphics.Typeface.BOLD);
            name.setSingleLine();
            name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            texts.addView(name);

            TextView kind = new TextView(context);
            kind.setText(dev.kind);
            kind.setTextColor(0xB3FFFFFF);
            kind.setTextSize(10);
            kind.setSingleLine();
            texts.addView(kind);

            TextView state = new TextView(context);
            state.setText(dev.connected ? R.string.bt_connected : note == null ? R.string.bt_tap_to_connect
                    : note == NOTE_CONNECTING ? R.string.bt_connecting : R.string.bt_failed);
            state.setTextColor(dev.connected ? COLOR_BT_CONNECTED
                    : note != null && note == NOTE_FAILED ? 0xFFF87171 : 0x99FFFFFF);
            state.setTextSize(11);
            row.addView(state);

            btList.addView(row);
            if (focusedTag != null && focusedTag.equals(dev.address)) {
                row.requestFocus();
            }
        }
        if (focusedTag != null && btList.findFocus() == null) {
            btAdd.requestFocus();
        }
    }

    public boolean isBluetoothShown() {
        return btPanel.getVisibility() == View.VISIBLE;
    }

    public void showBluetooth(boolean show) {
        if (show == isBluetoothShown()) {
            return;
        }
        btPanel.setVisibility(show ? View.VISIBLE : View.GONE);
        blockUnderlying(show);
        if (show) {
            bluetooth.refreshNow();
            bindBluetoothPanel();
            btAdd.requestFocus();
        } else {
            focusDefault();
        }
    }

    private void openStore() {
        Intent store = Store.intent(pm);
        if (store == null) {
            Toast.makeText(context, R.string.store_missing, Toast.LENGTH_LONG).show();
            return;
        }
        host.openStore(store);
    }

    public boolean closePanel() {
        if (isSpeedShown()) {
            showSpeed(false);
            return true;
        }
        if (isBluetoothShown()) {
            showBluetooth(false);
            return true;
        }
        return false;
    }

    private void blockUnderlying(boolean block) {
        ((ViewGroup) safeArea).setDescendantFocusability(block
                ? ViewGroup.FOCUS_BLOCK_DESCENDANTS : ViewGroup.FOCUS_AFTER_DESCENDANTS);
    }

    public boolean isSpeedShown() {
        return speedPanel.getVisibility() == View.VISIBLE;
    }

    public void showSpeed(boolean show) {
        if (show == isSpeedShown()) {
            return;
        }
        speedPanel.setVisibility(show ? View.VISIBLE : View.GONE);
        blockUnderlying(show);
        if (show) {
            runSpeedTest();
            speedClose.requestFocus();
        } else {
            cancelSpeedTest();
            focusDefault();
        }
    }

    private void cancelSpeedTest() {
        if (speedTest != null) {
            speedTest.cancel();
            speedTest = null;
        }
    }

    private void runSpeedTest() {
        cancelSpeedTest();
        speedValue.setText("0");
        speedPhase.setText(R.string.speed_ping);
        speedPing.setText("-");
        speedDown.setText("-");
        speedUp.setText("-");
        speedTest = new SpeedTest(new SpeedTest.Listener() {
            @Override
            public void onPhase(int phase) {
                speedPhase.setText(phase == SpeedTest.PHASE_PING ? R.string.speed_ping
                        : phase == SpeedTest.PHASE_DOWN ? R.string.speed_download : R.string.speed_upload);
            }

            @Override
            public void onSpeed(int phase, double mbps) {
                speedValue.setText(formatMbps(mbps));
            }

            @Override
            public void onResult(int pingMs, double down, double up) {
                speedValue.setText(formatMbps(down));
                speedPhase.setText(R.string.speed_done);
                speedPing.setText(context.getString(R.string.ms_value, pingMs));
                speedDown.setText(context.getString(R.string.mbps_value, formatMbps(down)));
                speedUp.setText(context.getString(R.string.mbps_value, formatMbps(up)));
            }

            @Override
            public void onError() {
                speedValue.setText("-");
                speedPhase.setText(R.string.speed_failed);
            }
        });
        speedTest.start();
    }

    private static String formatMbps(double mbps) {
        return mbps >= 100 ? String.format(Locale.getDefault(), "%.0f", mbps) : String.format(Locale.getDefault(), "%.1f", mbps);
    }
}
