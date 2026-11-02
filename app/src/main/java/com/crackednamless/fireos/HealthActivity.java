package com.crackednamless.fireos;

import android.app.Activity;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Environment;
import android.os.StatFs;
import android.os.SystemClock;
import android.text.TextUtils;
import android.text.format.Formatter;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

// Salute del Fire: memoria, spazio e tempo acceso, pulizia della RAM e gestione delle app che restano attive in background.
// Chiudere un'app usa "am force-stop", bloccarla usa appops RUN_IN_BACKGROUND: entrambi reversibili, nulla viene disinstallato.
public final class HealthActivity extends Activity {

    private static final int REQUEST_VPN = 1;
    private static final int GREEN = 0xFF7CD992;
    private static final int AMBER = 0xFFFFC857;
    private static final int RED = 0xFFFF7A7A;
    private static final int DIM = 0xB3FFFFFF;
    private static final Pattern PACKAGE = Pattern.compile("^[A-Za-z][A-Za-z0-9_.]*$");
    private static final String LIST_BLOCKED = "cmd appops query-op RUN_IN_BACKGROUND ignore";

    // Dal piu' importante al meno importante: serve a scegliere il gruppo da mostrare quando un'app ha piu' processi.
    private static final String[] RANKED_GROUPS = {"Foreground", "Visible", "Perceptible", "Previous", "B Services", "Cached"};
    private static final int FIRST_CLOSABLE = 2;

    private static final class Item {
        final String title;
        final String pkg;
        final String sub;
        final String right;
        final Drawable icon;
        final boolean closable;
        final boolean blockable;
        final boolean blocked;

        Item(String title, String pkg, String sub, String right, Drawable icon, boolean closable, boolean blockable,
             boolean blocked) {
            this.title = title;
            this.pkg = pkg;
            this.sub = sub;
            this.right = right;
            this.icon = icon;
            this.closable = closable;
            this.blockable = blockable;
            this.blocked = blocked;
        }

        static Item section(String title) {
            return new Item(title, null, null, null, null, false, false, false);
        }

        boolean isSection() {
            return pkg == null;
        }
    }

    private static final class Card {
        TextView value;
        TextView sub;
        ProgressBar bar;
    }

    private final List<Item> items = new ArrayList<>();
    private Card memory;
    private Card storage;
    private Card uptime;
    private TextView status;
    private TextView autoNote;
    private TextView autoButton;
    private View freeButton;
    private BaseAdapter adapter;
    private Runnable pending;
    private boolean busy;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(GlassBackground.create(getResources()));
        root.setPadding(dp(64), dp(28), dp(64), dp(20));

        TextView title = text(getString(R.string.health_title), 26, Color.WHITE);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);
        status = text(getString(R.string.health_loading), 14, DIM);
        status.setPadding(0, dp(2), 0, dp(12));
        root.addView(status);

        LinearLayout cards = new LinearLayout(this);
        cards.setOrientation(LinearLayout.HORIZONTAL);
        memory = card(cards, getString(R.string.health_card_memory), true, false);
        storage = card(cards, getString(R.string.health_card_storage), true, false);
        uptime = card(cards, getString(R.string.health_card_uptime), false, true);
        root.addView(cards, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        actions.setPadding(0, dp(14), 0, dp(4));
        freeButton = button(getString(R.string.health_free_now));
        freeButton.setOnClickListener(v -> freeMemory());
        autoButton = button("");
        autoButton.setOnClickListener(v -> {
            MemoryGuard.setEnabled(this, !MemoryGuard.isEnabled(this));
            updateAuto();
        });
        actions.addView(freeButton);
        LinearLayout.LayoutParams gap = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        gap.leftMargin = dp(10);
        actions.addView(autoButton, gap);
        autoNote = text("", 12, 0x80FFFFFF);
        autoNote.setPadding(dp(16), 0, 0, 0);
        actions.addView(autoNote);
        root.addView(actions);

        ListView list = new ListView(this);
        list.setDivider(new ColorDrawable(Color.TRANSPARENT));
        list.setDividerHeight(dp(4));
        list.setVerticalScrollBarEnabled(false);
        list.setSelector(R.drawable.row_bg);
        adapter = new Adapter();
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, position, id) -> showActions(items.get(position)));
        LinearLayout.LayoutParams listParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        listParams.topMargin = dp(8);
        root.addView(list, listParams);
        setContentView(root);
        freeButton.requestFocus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateCards();
        updateAuto();
        load();
    }

    private void updateCards() {
        long total = MemoryGuard.totalMb(this);
        long free = MemoryGuard.availableMb(this);
        int usedPct = total > 0 ? (int) Math.round(100.0 * (total - free) / total) : 0;
        memory.value.setText(getString(R.string.health_mb_free, free));
        memory.sub.setText(getString(R.string.health_of_total, total, usedPct));
        memory.bar.setProgress(usedPct);
        int color = free < MemoryGuard.LOW_MB ? RED : free < 250 ? AMBER : GREEN;
        memory.value.setTextColor(color);
        memory.bar.setProgressTintList(ColorStateList.valueOf(color));

        StatFs fs = new StatFs(Environment.getDataDirectory().getPath());
        long totalBytes = fs.getTotalBytes();
        long freeBytes = fs.getAvailableBytes();
        int storageUsed = totalBytes > 0 ? (int) Math.round(100.0 * (totalBytes - freeBytes) / totalBytes) : 0;
        storage.value.setText(getString(R.string.health_storage_free, Formatter.formatFileSize(this, freeBytes)));
        storage.sub.setText(getString(R.string.health_of_total_size, Formatter.formatFileSize(this, totalBytes),
                storageUsed));
        storage.bar.setProgress(storageUsed);
        int storageColor = storageUsed > 92 ? RED : storageUsed > 85 ? AMBER : GREEN;
        storage.value.setTextColor(storageColor);
        storage.bar.setProgressTintList(ColorStateList.valueOf(storageColor));

        long minutes = SystemClock.elapsedRealtime() / 60_000L;
        long days = minutes / (24 * 60);
        long hours = (minutes / 60) % 24;
        uptime.value.setText(days > 0 ? getString(R.string.health_uptime_days, days, hours)
                : getString(R.string.health_uptime_hours, minutes / 60, minutes % 60));
        uptime.sub.setText(days >= 7 ? R.string.health_uptime_restart : R.string.health_uptime_ok);
        uptime.value.setTextColor(days >= 7 ? AMBER : Color.WHITE);
    }

    private void updateAuto() {
        boolean on = MemoryGuard.isEnabled(this);
        autoButton.setText(getString(R.string.health_auto, getString(on ? R.string.tools_on : R.string.tools_off)));
        autoButton.setTextColor(on ? GREEN : Color.WHITE);
        long last = MemoryGuard.lastTime(this);
        if (!on) {
            autoNote.setText("");
        } else if (last == 0) {
            autoNote.setText(R.string.health_auto_hint);
        } else {
            autoNote.setText(getString(R.string.health_auto_last,
                    android.text.format.DateFormat.getTimeFormat(this).format(new Date(last)),
                    MemoryGuard.lastFreedMb(this)));
        }
    }

    private void load() {
        if (busy) {
            return;
        }
        if (needConsent(this::load)) {
            return;
        }
        busy = true;
        status.setText(R.string.health_loading);
        List<String> commands = new ArrayList<>();
        commands.add("dumpsys meminfo");
        commands.add(LIST_BLOCKED);
        AdbBridge.run(this, commands, new AdbBridge.Listener() {
            @Override
            public void onStatus(String message) {
                if (items.isEmpty()) {
                    status.setText(message);
                }
            }

            @Override
            public void onDone(List<String> outputs, String error) {
                busy = false;
                updateCards();
                if (error != null) {
                    status.setText(error);
                    return;
                }
                bind(MemInfoParser.parse(outputs.get(0)), parseBlocked(outputs.get(1)));
            }
        });
    }

    private static Set<String> parseBlocked(String output) {
        Set<String> blocked = new HashSet<>();
        for (String line : output.split("\n")) {
            String pkg = line.trim();
            if (PACKAGE.matcher(pkg).matches()) {
                blocked.add(pkg);
            }
        }
        return blocked;
    }

    // Solo app con icona non essenziali o installate dall'utente: i servizi di sistema e le app essenziali non si possono bloccare da qui.
    private boolean isManageable(android.content.pm.ApplicationInfo info, Set<String> launchable) {
        if (launchable.contains(info.packageName)) {
            return true;
        }
        return (info.flags & android.content.pm.ApplicationInfo.FLAG_SYSTEM) == 0
                && !Tweaks.isEssential(info.packageName, getPackageName());
    }

    private void bind(List<MemInfoParser.Proc> procs, Set<String> blocked) {
        PackageManager pm = getPackageManager();
        Set<String> hidden = new HashSet<>();
        Map<String, long[]> sums = new LinkedHashMap<>();
        for (MemInfoParser.Proc p : procs) {
            int rank = rankOf(p.group);
            if (rank < 0 || p.packageName.equals(getPackageName())) {
                hidden.add(p.packageName);
                continue;
            }
            long[] s = sums.get(p.packageName);
            if (s == null) {
                s = new long[]{0, rank};
                sums.put(p.packageName, s);
            }
            s[0] += p.pssKb;
            s[1] = Math.min(s[1], rank);
        }

        List<Item> running = new ArrayList<>();
        List<String> order = new ArrayList<>(sums.keySet());
        Collections.sort(order, (a, b) -> Long.compare(sums.get(b)[0], sums.get(a)[0]));
        long closableKb = 0;
        int closableCount = 0;
        Set<String> shown = new HashSet<>();
        Set<String> launchable = new HashSet<>(Tweaks.nonEssentialApps(pm, getPackageName()));
        for (String pkg : order) {
            if (hidden.contains(pkg)) {
                continue;
            }
            try {
                android.content.pm.ApplicationInfo info = pm.getApplicationInfo(pkg, 0);
                if (!isManageable(info, launchable)) {
                    continue;
                }
                long[] s = sums.get(pkg);
                int rank = (int) s[1];
                if (rank < FIRST_CLOSABLE) {
                    continue;
                }
                closableKb += s[0];
                closableCount++;
                String group = getString(groupLabel(rank));
                boolean isBlocked = blocked.contains(pkg);
                running.add(new Item(String.valueOf(pm.getApplicationLabel(info)), pkg,
                        isBlocked ? group + " · " + getString(R.string.health_blocked) : group,
                        getString(R.string.health_mb, s[0] / 1024), pm.getApplicationIcon(info), true, true,
                        isBlocked));
                shown.add(pkg);
            } catch (PackageManager.NameNotFoundException ignored) {
                // Processo che non appartiene a un'app installata.
            }
        }

        List<Item> blockedOnly = new ArrayList<>();
        for (String pkg : blocked) {
            if (shown.contains(pkg)) {
                continue;
            }
            try {
                android.content.pm.ApplicationInfo info = pm.getApplicationInfo(pkg, 0);
                if (!isManageable(info, launchable)) {
                    continue;
                }
                blockedOnly.add(new Item(String.valueOf(pm.getApplicationLabel(info)), pkg, pkg,
                        getString(R.string.health_blocked), pm.getApplicationIcon(info), false, true, true));
            } catch (PackageManager.NameNotFoundException ignored) {
                // Pacchetto non piu' installato.
            }
        }
        Collections.sort(blockedOnly, (a, b) -> a.title.compareToIgnoreCase(b.title));

        items.clear();
        if (!running.isEmpty()) {
            items.add(Item.section(getString(R.string.health_section_running, running.size())));
            items.addAll(running);
        }
        if (!blockedOnly.isEmpty()) {
            items.add(Item.section(getString(R.string.health_section_blocked, blockedOnly.size())));
            items.addAll(blockedOnly);
        }
        adapter.notifyDataSetChanged();
        status.setText(getString(R.string.health_summary, closableCount, closableKb / 1024));
    }

    private static int rankOf(String group) {
        for (int i = 0; i < RANKED_GROUPS.length; i++) {
            if (RANKED_GROUPS[i].equals(group)) {
                return i;
            }
        }
        return -1;
    }

    private static int groupLabel(int rank) {
        switch (rank) {
            case 0:
                return R.string.health_group_foreground;
            case 1:
                return R.string.health_group_visible;
            case 2:
                return R.string.health_group_active;
            case 3:
                return R.string.health_group_previous;
            case 4:
                return R.string.health_group_service;
            default:
                return R.string.health_group_cached;
        }
    }

    private void freeMemory() {
        long before = MemoryGuard.availableMb(this);
        exec(Collections.singletonList("am kill-all"), () -> {
            status.setText(R.string.tools_closing_idle);
            status.postDelayed(() -> {
                updateCards();
                status.setText(getString(R.string.tools_memory_result, before, MemoryGuard.availableMb(this)));
            }, 1500);
        }, false);
    }

    private void showActions(Item item) {
        if (item.isSection()) {
            return;
        }
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackgroundResource(R.drawable.panel_bg);
        box.setPadding(dp(22), dp(18), dp(22), dp(18));
        TextView name = text(item.title, 18, Color.WHITE);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        name.setSingleLine();
        name.setEllipsize(TextUtils.TruncateAt.END);
        box.addView(name);
        TextView hint = text(getString(R.string.health_dialog_hint), 12, 0x99FFFFFF);
        hint.setPadding(0, dp(2), 0, dp(12));
        box.addView(hint);

        View first = null;
        if (item.closable) {
            first = option(box, dialog, getString(R.string.health_close_now), () -> exec(
                    Collections.singletonList("am force-stop " + item.pkg),
                    () -> status.setText(getString(R.string.health_closed, item.title)), true));
        }
        if (item.blockable) {
            String mode = item.blocked ? "allow" : "ignore";
            List<String> commands = new ArrayList<>();
            commands.add("cmd appops set " + item.pkg + " RUN_IN_BACKGROUND " + mode);
            commands.add("cmd appops set " + item.pkg + " RUN_ANY_IN_BACKGROUND " + mode);
            if (!item.blocked) {
                commands.add("am force-stop " + item.pkg);
            }
            View o = option(box, dialog, getString(item.blocked ? R.string.health_allow : R.string.health_block),
                    () -> exec(commands, () -> status.setText(getString(
                            item.blocked ? R.string.health_now_allowed : R.string.health_now_blocked, item.title)),
                            true));
            first = first != null ? first : o;
        }
        View cancel = option(box, dialog, getString(R.string.menu_back), null);
        first = first != null ? first : cancel;

        dialog.setContentView(box, new ViewGroup.LayoutParams(dp(380), ViewGroup.LayoutParams.WRAP_CONTENT));
        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        dialog.show();
        first.requestFocus();
    }

    private View option(LinearLayout box, Dialog dialog, String label, Runnable action) {
        TextView t = text(label, 16, Color.WHITE);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setBackgroundResource(R.drawable.row_bg);
        t.setPadding(dp(16), dp(10), dp(16), dp(10));
        t.setFocusable(true);
        t.setClickable(true);
        t.setOnClickListener(v -> {
            dialog.dismiss();
            if (action != null) {
                action.run();
            }
        });
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        p.topMargin = dp(6);
        box.addView(t, p);
        return t;
    }

    // Esegue i comandi sul tunnel; alla fine chiama done e, se reload e' true, rilegge la lista.
    private void exec(List<String> commands, Runnable done, boolean reload) {
        if (busy) {
            return;
        }
        if (needConsent(() -> exec(commands, done, reload))) {
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
                if (error != null) {
                    status.setText(error);
                    return;
                }
                done.run();
                updateCards();
                if (reload) {
                    load();
                }
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

    private Card card(LinearLayout parent, String label, boolean withBar, boolean last) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackgroundResource(R.drawable.glass_card);
        box.setPadding(dp(18), dp(14), dp(18), dp(14));
        TextView name = text(label, 10, 0x99FFFFFF);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        name.setLetterSpacing(0.12f);
        box.addView(name);
        Card c = new Card();
        c.value = text("", 26, Color.WHITE);
        c.value.setTypeface(Typeface.DEFAULT_BOLD);
        c.value.setPadding(0, dp(2), 0, dp(6));
        box.addView(c.value);
        c.bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        c.bar.setProgressDrawable(getDrawable(R.drawable.progress_bar));
        c.bar.setMax(100);
        c.bar.setVisibility(withBar ? View.VISIBLE : View.INVISIBLE);
        box.addView(c.bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(5)));
        c.sub = text("", 12, DIM);
        c.sub.setPadding(0, dp(6), 0, 0);
        box.addView(c.sub);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        if (!last) {
            p.rightMargin = dp(12);
        }
        parent.addView(box, p);
        return c;
    }

    private TextView button(String label) {
        TextView b = text(label, 14, Color.WHITE);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setBackgroundResource(R.drawable.btn_secondary);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(22), dp(9), dp(22), dp(9));
        b.setFocusable(true);
        b.setClickable(true);
        return b;
    }

    private final class Adapter extends BaseAdapter {
        @Override
        public int getCount() {
            return items.size();
        }

        @Override
        public Object getItem(int position) {
            return items.get(position);
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
            return items.get(position).isSection() ? 0 : 1;
        }

        @Override
        public boolean areAllItemsEnabled() {
            return false;
        }

        @Override
        public boolean isEnabled(int position) {
            return !items.get(position).isSection();
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            Item item = items.get(position);
            if (item.isSection()) {
                TextView header = convertView instanceof TextView ? (TextView) convertView : text("", 10, 0x99FFFFFF);
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
                LinearLayout row = new LinearLayout(HealthActivity.this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(18), dp(8), dp(18), dp(8));
                ImageView icon = new ImageView(HealthActivity.this);
                row.addView(icon, new LinearLayout.LayoutParams(dp(34), dp(34)));
                LinearLayout names = new LinearLayout(HealthActivity.this);
                names.setOrientation(LinearLayout.VERTICAL);
                names.setPadding(dp(14), 0, 0, 0);
                TextView title = text("", 16, Color.WHITE);
                title.setTypeface(Typeface.DEFAULT_BOLD);
                title.setSingleLine();
                title.setEllipsize(TextUtils.TruncateAt.END);
                TextView sub = text("", 12, 0x80FFFFFF);
                sub.setSingleLine();
                sub.setEllipsize(TextUtils.TruncateAt.END);
                names.addView(title);
                names.addView(sub);
                row.addView(names, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                TextView right = text("", 14, DIM);
                right.setTypeface(Typeface.DEFAULT_BOLD);
                right.setPadding(dp(16), 0, 0, 0);
                row.addView(right);
                v = new RowViews(row, icon, title, sub, right);
                row.setTag(v);
            }
            v.icon.setImageDrawable(item.icon);
            v.title.setText(item.title);
            v.sub.setText(item.sub);
            v.right.setText(item.right);
            v.right.setTextColor(item.blocked ? GREEN : DIM);
            return v.root;
        }
    }

    private static final class RowViews {
        final View root;
        final ImageView icon;
        final TextView title;
        final TextView sub;
        final TextView right;

        RowViews(View root, ImageView icon, TextView title, TextView sub, TextView right) {
            this.root = root;
            this.icon = icon;
            this.title = title;
            this.sub = sub;
            this.right = right;
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
