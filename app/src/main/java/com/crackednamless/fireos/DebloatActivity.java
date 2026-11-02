package com.crackednamless.fireos;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// Menu strumenti: azioni in blocco (servizi Amazon, prestazioni, memoria) e interruttore per ogni servizio.
// Tutto reversibile: "pm disable-user" lascia APK e dati, le impostazioni tornano ai valori di prima.
public final class DebloatActivity extends Activity {

    private static final String PRIME_TOGGLE = "if cmd appops get com.amazon.avod RUN_IN_BACKGROUND | grep -q ignore; "
            + "then M=allow; else M=ignore; fi; "
            + "for p in com.amazon.avod com.amazon.firebat; do "
            + "cmd appops set $p RUN_IN_BACKGROUND $M; cmd appops set $p RUN_ANY_IN_BACKGROUND $M; "
            + "if [ $M = ignore ]; then am force-stop $p; fi; done; echo $M";
    private static final int REQUEST_VPN = 1;
    private static final int GREEN = 0xFF7CD992;
    private static final int DIM = 0xB3FFFFFF;

    private interface Summary {
        String of(List<String> outputs);
    }

    private static final class Row {
        final String title;
        final String sub;
        final String state;
        final int stateColor;
        final Runnable action;

        Row(String title, String sub, String state, int stateColor, Runnable action) {
            this.title = title;
            this.sub = sub;
            this.state = state;
            this.stateColor = stateColor;
            this.action = action;
        }

        static Row section(String title) {
            return new Row(title, null, null, 0, null);
        }

        boolean isSection() {
            return action == null;
        }
    }

    private static final class Service {
        final String pkg;
        final String label;
        final boolean disabled;

        Service(String pkg, String label, boolean disabled) {
            this.pkg = pkg;
            this.label = label;
            this.disabled = disabled;
        }
    }

    private final List<Row> rows = new ArrayList<>();
    private TextView status;
    private BaseAdapter adapter;
    private Runnable pending;
    private boolean busy;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF0B0E14);
        root.setPadding(dp(64), dp(28), dp(64), dp(20));

        TextView title = text(getString(R.string.tools_title), 26, Color.WHITE);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);
        status = text(getString(R.string.tools_intro), 14, DIM);
        status.setPadding(0, dp(2), 0, dp(12));
        root.addView(status);

        ListView list = new ListView(this);
        list.setDivider(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        list.setDividerHeight(dp(4));
        list.setVerticalScrollBarEnabled(false);
        list.setSelector(R.drawable.row_bg);
        adapter = new Adapter();
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, position, id) -> rows.get(position).action.run());
        root.addView(list, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        reload();
    }

    private void reload() {
        List<Service> services = new ArrayList<>();
        PackageManager pm = getPackageManager();
        for (String pkg : Tweaks.PACKAGES) {
            try {
                ApplicationInfo info = pm.getApplicationInfo(pkg, PackageManager.GET_DISABLED_COMPONENTS);
                int state = pm.getApplicationEnabledSetting(pkg);
                boolean disabled = state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                        || state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER;
                services.add(new Service(pkg, String.valueOf(pm.getApplicationLabel(info)), disabled));
            } catch (PackageManager.NameNotFoundException ignored) {
                // Pacchetto non presente su questo Stick.
            }
        }
        Collections.sort(services, (a, b) -> a.label.compareToIgnoreCase(b.label));

        List<String> toDisable = new ArrayList<>();
        List<String> toEnable = new ArrayList<>();
        for (Service s : services) {
            (s.disabled ? toEnable : toDisable).add(s.pkg);
        }

        rows.clear();
        boolean fast = AdbBridge.isFastHome(this);
        Resources res = getResources();
        String on = getString(R.string.tools_on);
        String off = getString(R.string.tools_off);
        rows.add(Row.section(getString(R.string.tools_section_speed)));
        rows.add(new Row(getString(R.string.health_title), getString(R.string.health_tools_sub), "", DIM,
                () -> startActivity(new Intent(this, HealthActivity.class))));
        rows.add(new Row(getString(R.string.tools_fast_home), getString(R.string.tools_fast_home_sub),
                fast ? on : off, fast ? GREEN : DIM, this::toggleFast));
        boolean perf = performanceOn();
        rows.add(new Row(getString(R.string.tools_performance), getString(R.string.tools_performance_sub),
                perf ? on : off, perf ? GREEN : DIM, () -> setPerformance(!perf)));
        rows.add(new Row(getString(R.string.tools_free_memory), getString(R.string.tools_free_memory_sub),
                "", DIM, this::freeMemory));

        rows.add(Row.section(getString(R.string.tools_section_amazon)));
        rows.add(new Row(getString(R.string.tools_disable_all), getString(R.string.tools_disable_all_sub),
                toDisable.isEmpty() ? getString(R.string.tools_already_done)
                        : res.getQuantityString(R.plurals.tools_count_active, toDisable.size(), toDisable.size()),
                toDisable.isEmpty() ? GREEN : DIM,
                () -> bulk(toDisable, "pm disable-user --user 0 ", R.plurals.tools_done_disabled)));
        rows.add(new Row(getString(R.string.tools_enable_all), getString(R.string.tools_enable_all_sub),
                toEnable.isEmpty() ? getString(R.string.tools_none_to_enable)
                        : res.getQuantityString(R.plurals.tools_count_disabled, toEnable.size(), toEnable.size()),
                DIM, () -> bulk(toEnable, "pm enable --user 0 ", R.plurals.tools_done_enabled)));
        rows.add(new Row(getString(R.string.tools_prime), getString(R.string.tools_prime_sub),
                getString(R.string.tools_tap_to_change), DIM,
                () -> exec(Collections.singletonList(PRIME_TOGGLE), outputs -> getString(
                        "ignore".equals(outputs.get(0).trim()) ? R.string.tools_prime_blocked
                                : R.string.tools_prime_allowed))));

        if (!services.isEmpty()) {
            rows.add(Row.section(getString(R.string.tools_section_services, services.size())));
        }
        for (Service s : services) {
            rows.add(new Row(s.label, s.pkg,
                    getString(s.disabled ? R.string.tools_service_disabled : R.string.tools_service_active),
                    s.disabled ? GREEN : DIM,
                    () -> exec(Collections.singletonList(
                            (s.disabled ? "pm enable --user 0 " : "pm disable-user --user 0 ") + s.pkg),
                            outputs -> getString(s.disabled ? R.string.tools_service_now_active
                                    : R.string.tools_service_now_disabled, s.label))));
        }
        adapter.notifyDataSetChanged();
    }

    private boolean performanceOn() {
        return "0".equals(scale("window_animation_scale")) && "0".equals(scale("transition_animation_scale"));
    }

    private String scale(String key) {
        String value = Settings.Global.getString(getContentResolver(), key);
        if (value == null) {
            return null;
        }
        try {
            return Float.parseFloat(value) == 0f ? "0" : value;
        } catch (NumberFormatException e) {
            return value;
        }
    }

    private void setPerformance(boolean on) {
        exec(Tweaks.performanceCommands(on), outputs -> getString(on ? R.string.tools_performance_on : R.string.tools_performance_off));
    }

    private void bulk(List<String> packages, String command, int donePlural) {
        if (packages.isEmpty()) {
            status.setText(R.string.tools_nothing_to_do);
            return;
        }
        List<String> commands = new ArrayList<>();
        for (String pkg : packages) {
            commands.add(command + pkg);
        }
        exec(commands, outputs -> getResources().getQuantityString(donePlural, packages.size(), packages.size()));
    }

    private void freeMemory() {
        long before = availableMb();
        exec(Collections.singletonList("am kill-all"), outputs -> {
            status.postDelayed(() -> status.setText(getString(R.string.tools_memory_result, before, availableMb())),
                    1500);
            return getString(R.string.tools_closing_idle);
        });
    }

    private long availableMb() {
        ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
        ((ActivityManager) getSystemService(Context.ACTIVITY_SERVICE)).getMemoryInfo(info);
        return info.availMem / (1024 * 1024);
    }

    // Esegue i comandi sul tunnel e riporta l'esito nella riga di stato.
    private void exec(List<String> commands, Summary summary) {
        if (busy) {
            return;
        }
        if (needConsent(() -> exec(commands, summary))) {
            return;
        }
        busy = true;
        AdbBridge.run(this, commands, new AdbBridge.Listener() {
            @Override
            public void onStatus(String message) {
                status.setText(message);
            }

            @Override
            public void onDone(List<String> outputs, String error) {
                busy = false;
                status.setText(error != null ? error : summary.of(outputs));
                reload();
            }
        });
    }

    private void toggleFast() {
        if (busy) {
            return;
        }
        if (needConsent(this::toggleFast)) {
            return;
        }
        busy = true;
        AdbBridge.setFastHome(this, !AdbBridge.isFastHome(this), new AdbBridge.Listener() {
            @Override
            public void onStatus(String message) {
                status.setText(message);
            }

            @Override
            public void onDone(List<String> outputs, String error) {
                busy = false;
                status.setText(error != null ? error : getString(R.string.tools_done));
                reload();
            }
        });
    }

    // Se serve il consenso VPN lo chiede e, una volta dato, rilancia l'azione. Restituisce true se ha dovuto chiederlo.
    private boolean needConsent(Runnable retry) {
        Intent consent = AdbBridge.vpnConsent(this);
        if (consent == null) {
            return false;
        }
        pending = retry;
        try {
            startActivityForResult(consent, REQUEST_VPN);
        } catch (ActivityNotFoundException e) {
            status.setText(getString(R.string.tools_vpn_dialog_missing, getPackageName()));
        }
        return true;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_VPN) {
            Runnable retry = pending;
            pending = null;
            if (resultCode == RESULT_OK && retry != null) {
                retry.run();
            } else {
                status.setText(R.string.err_vpn_consent_denied);
            }
        }
    }

    private final class Adapter extends BaseAdapter {
        @Override
        public int getCount() {
            return rows.size();
        }

        @Override
        public Object getItem(int position) {
            return rows.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public int getViewTypeCount() {
            return 2;
        }

        @Override
        public int getItemViewType(int position) {
            return rows.get(position).isSection() ? 0 : 1;
        }

        @Override
        public boolean areAllItemsEnabled() {
            return false;
        }

        @Override
        public boolean isEnabled(int position) {
            return !rows.get(position).isSection();
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            Row item = rows.get(position);
            if (item.isSection()) {
                TextView header = convertView instanceof TextView ? (TextView) convertView
                        : text("", 10, 0x99FFFFFF);
                header.setText(item.title);
                header.setTypeface(Typeface.DEFAULT_BOLD);
                header.setLetterSpacing(0.12f);
                header.setPadding(dp(4), position == 0 ? dp(2) : dp(18), 0, dp(6));
                return header;
            }
            RowViews v;
            if (convertView != null && convertView.getTag() instanceof RowViews) {
                v = (RowViews) convertView.getTag();
            } else {
                LinearLayout row = new LinearLayout(DebloatActivity.this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(18), dp(9), dp(18), dp(9));
                LinearLayout names = new LinearLayout(DebloatActivity.this);
                names.setOrientation(LinearLayout.VERTICAL);
                TextView title = text("", 17, Color.WHITE);
                title.setTypeface(Typeface.DEFAULT_BOLD);
                title.setSingleLine();
                title.setEllipsize(android.text.TextUtils.TruncateAt.END);
                TextView sub = text("", 12, 0x80FFFFFF);
                sub.setSingleLine();
                sub.setEllipsize(android.text.TextUtils.TruncateAt.END);
                names.addView(title);
                names.addView(sub);
                row.addView(names, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                TextView state = text("", 14, DIM);
                state.setTypeface(Typeface.DEFAULT_BOLD);
                state.setPadding(dp(16), 0, 0, 0);
                row.addView(state);
                v = new RowViews(row, title, sub, state);
                row.setTag(v);
            }
            v.title.setText(item.title);
            v.sub.setText(item.sub);
            v.sub.setVisibility(item.sub == null || item.sub.isEmpty() ? View.GONE : View.VISIBLE);
            v.state.setText(item.state);
            v.state.setTextColor(item.stateColor);
            return v.root;
        }
    }

    private static final class RowViews {
        final View root;
        final TextView title;
        final TextView sub;
        final TextView state;

        RowViews(View root, TextView title, TextView sub, TextView state) {
            this.root = root;
            this.title = title;
            this.sub = sub;
            this.state = state;
        }
    }

    private TextView text(String value, int sp, int color) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(sp);
        t.setTextColor(color);
        return t;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
