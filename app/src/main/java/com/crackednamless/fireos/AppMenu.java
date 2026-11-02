package com.crackednamless.fireos;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// Pannello opzioni di un'app o di una cartella (pressione lunga su OK nella Libreria). Solo per contesti Activity.
final class AppMenu {

    interface Actions {
        void move(AppEntry app, String folder);

        void info(AppEntry app);

        void uninstall(AppEntry app);

        void rename(String from, String to);

        void delete(String folder);
    }

    private static final int ROW_DP = 42;
    private static final int ROW_GAP_DP = 6;
    private static final int MAX_ROWS_HEIGHT_DP = 5 * (ROW_DP + ROW_GAP_DP);

    private final Context context;
    private final PackageManager pm;
    private final Actions actions;
    private final Runnable onClosed;
    private final float d;
    private final FrameLayout overlay;
    private final TextView title;
    private final TextView subtitle;
    private final ScrollView scroll;
    private final LinearLayout list;
    private AppEntry app;
    private String currentFolder;
    private List<String> folderNames = new ArrayList<>();

    AppMenu(Context context, ViewGroup root, Actions actions, Runnable onClosed) {
        this.context = context;
        this.pm = context.getPackageManager();
        this.actions = actions;
        this.onClosed = onClosed;
        this.d = context.getResources().getDisplayMetrics().density;

        overlay = new FrameLayout(context);
        overlay.setBackgroundColor(0xCC05070B);
        overlay.setClickable(true);
        overlay.setVisibility(View.GONE);

        LinearLayout panel = new LinearLayout(context);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundResource(R.drawable.panel_bg);
        panel.setPadding(dp(26), dp(20), dp(26), dp(22));
        FrameLayout.LayoutParams panelLp = new FrameLayout.LayoutParams(dp(420), ViewGroup.LayoutParams.WRAP_CONTENT);
        panelLp.gravity = Gravity.CENTER;
        overlay.addView(panel, panelLp);

        title = new TextView(context);
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(20);
        title.setTypeface(null, Typeface.BOLD);
        title.setSingleLine();
        title.setEllipsize(TextUtils.TruncateAt.END);
        panel.addView(title);

        subtitle = new TextView(context);
        subtitle.setTextColor(0xB3FFFFFF);
        subtitle.setTextSize(12);
        subtitle.setSingleLine();
        subtitle.setEllipsize(TextUtils.TruncateAt.END);
        panel.addView(subtitle);

        scroll = new ScrollView(context);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        list = new LinearLayout(context);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        scrollLp.topMargin = dp(14);
        panel.addView(scroll, scrollLp);

        root.addView(overlay, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
    }

    boolean isShown() {
        return overlay.getVisibility() == View.VISIBLE;
    }

    void hide() {
        if (!isShown()) {
            return;
        }
        overlay.setVisibility(View.GONE);
        list.removeAllViews();
        onClosed.run();
    }

    void showApp(AppEntry entry, String folder, List<String> names) {
        app = entry;
        currentFolder = folder;
        folderNames = names;
        pageApp();
        overlay.setVisibility(View.VISIBLE);
        focusFirst();
    }

    void showFolder(String name) {
        app = null;
        currentFolder = name;
        pageFolder();
        overlay.setVisibility(View.VISIBLE);
        focusFirst();
    }

    private void pageApp() {
        title.setText(app.label);
        subtitle.setText(currentFolder == null ? app.packageName
                : context.getString(R.string.menu_folder_of, currentFolder));
        begin();
        row(R.string.menu_move, 0xFFFFFFFF, this::pageMove);
        if (currentFolder != null) {
            row(R.string.menu_remove_from_folder, 0xFFFFFFFF, () -> done(() -> actions.move(app, "")));
        }
        row(R.string.app_info, 0xFFFFFFFF, () -> done(() -> actions.info(app)));
        if (isSystem(app.packageName)) {
            note(R.string.menu_system_app);
        } else {
            row(R.string.menu_uninstall, 0xFFF87171, () -> done(() -> actions.uninstall(app)));
        }
        fit();
    }

    private void pageMove() {
        subtitle.setText(R.string.menu_move_title);
        begin();
        for (String n : folderNames) {
            if (!n.equalsIgnoreCase(currentFolder == null ? "" : currentFolder)) {
                row(n, 0xFFFFFFFF, () -> done(() -> actions.move(app, n)));
            }
        }
        int[] presets = {R.string.preset_streaming, R.string.preset_games, R.string.preset_music,
                R.string.preset_tools, R.string.preset_kids};
        for (int res : presets) {
            String name = context.getString(res);
            if (!containsIgnoreCase(folderNames, name)) {
                row(name, 0xCCFFFFFF, () -> done(() -> actions.move(app, name)));
            }
        }
        row(R.string.menu_new_folder, 0xFF60A5FA, () -> pageName("", name -> done(() -> actions.move(app, name))));
        row(R.string.menu_back, 0x99FFFFFF, this::pageApp);
        fit();
    }

    private void pageFolder() {
        title.setText(currentFolder);
        subtitle.setText(R.string.menu_delete_folder_hint);
        begin();
        row(R.string.menu_rename_folder, 0xFFFFFFFF, () -> pageName(currentFolder,
                name -> done(() -> actions.rename(currentFolder, name))));
        row(R.string.menu_delete_folder, 0xFFF87171, () -> done(() -> actions.delete(currentFolder)));
        row(R.string.close, 0x99FFFFFF, this::hide);
        fit();
    }

    private interface NameDone {
        void accept(String name);
    }

    private void pageName(String initial, NameDone callback) {
        subtitle.setText(R.string.menu_folder_name);
        begin();
        EditText input = new EditText(context);
        input.setText(initial);
        input.setSelection(initial.length());
        input.setSingleLine();
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(Folders.MAX_NAME)});
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        input.setImeOptions(EditorInfo.IME_ACTION_DONE);
        input.setHint(R.string.menu_folder_name);
        input.setHintTextColor(0x66FFFFFF);
        input.setTextColor(0xFFFFFFFF);
        input.setTextSize(14);
        input.setBackgroundResource(R.drawable.row_bg);
        input.setPadding(dp(16), 0, dp(16), 0);
        input.setLayoutParams(rowParams());
        Runnable submit = () -> {
            String name = Folders.clean(input.getText().toString());
            if (!name.isEmpty()) {
                callback.accept(name);
            }
        };
        input.setOnEditorActionListener((v, actionId, event) -> {
            submit.run();
            return true;
        });
        list.addView(input);
        row(app != null ? R.string.menu_create : R.string.menu_rename, 0xFF34D399, submit);
        row(R.string.menu_back, 0x99FFFFFF, () -> {
            if (app != null) {
                pageMove();
            } else {
                pageFolder();
            }
        });
        fit();
        input.requestFocus();
    }

    private void done(Runnable action) {
        action.run();
        hide();
    }

    private void begin() {
        list.removeAllViews();
    }

    private void fit() {
        int rows = list.getChildCount();
        int wanted = rows * dp(ROW_DP + ROW_GAP_DP);
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) scroll.getLayoutParams();
        lp.height = Math.min(wanted, dp(MAX_ROWS_HEIGHT_DP));
        scroll.setLayoutParams(lp);
        scroll.scrollTo(0, 0);
        focusFirst();
    }

    private void focusFirst() {
        View first = list.getChildAt(0);
        if (first != null) {
            first.requestFocus();
        }
    }

    private LinearLayout.LayoutParams rowParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(ROW_DP));
        lp.bottomMargin = dp(ROW_GAP_DP);
        return lp;
    }

    private void row(int textRes, int color, Runnable click) {
        row(context.getString(textRes), color, click);
    }

    private void row(String text, int color, Runnable click) {
        TextView row = new TextView(context);
        row.setText(text);
        row.setTextColor(color);
        row.setTextSize(13);
        row.setTypeface(null, Typeface.BOLD);
        row.setSingleLine();
        row.setEllipsize(TextUtils.TruncateAt.END);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), 0, dp(16), 0);
        row.setBackgroundResource(R.drawable.row_bg);
        row.setFocusable(true);
        row.setClickable(true);
        row.setOnClickListener(v -> click.run());
        row.setLayoutParams(rowParams());
        list.addView(row);
    }

    private void note(int textRes) {
        TextView note = new TextView(context);
        note.setText(textRes);
        note.setTextColor(0x99FFFFFF);
        note.setTextSize(11);
        note.setGravity(Gravity.CENTER_VERTICAL);
        note.setPadding(dp(4), 0, dp(4), 0);
        note.setLayoutParams(rowParams());
        list.addView(note);
    }

    private boolean isSystem(String pkg) {
        try {
            ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
            return (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0
                    && (ai.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0;
        } catch (PackageManager.NameNotFoundException e) {
            return true;
        }
    }

    private static boolean containsIgnoreCase(List<String> names, String value) {
        for (String n : names) {
            if (n.toLowerCase(Locale.ROOT).equals(value.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private int dp(int v) {
        return Math.round(v * d);
    }
}
