package com.crackednamless.fireos;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class AppRepository {

    private static final Comparator<AppEntry> BY_LABEL = (a, b) -> {
        int c = String.CASE_INSENSITIVE_ORDER.compare(a.label, b.label);
        return c != 0 ? c : a.packageName.compareTo(b.packageName);
    };

    private static final Set<String> HIDDEN_PACKAGES = new HashSet<>(java.util.Arrays.asList(
            "com.android.traceur",
            "com.amazon.audiohome"));

    private AppRepository() {
    }

    private static List<AppEntry> cached;

    public static void invalidate() {
        cached = null;
        AppArt.clear();
    }

    public static List<AppEntry> load(PackageManager pm, String ownPackage) {
        if (cached != null) {
            return cached;
        }
        Intent query = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        List<AppEntry> raw = new ArrayList<>();
        for (ResolveInfo info : pm.queryIntentActivities(query, 0)) {
            CharSequence label = info.loadLabel(pm);
            raw.add(new AppEntry(
                    label != null ? label.toString() : info.activityInfo.packageName,
                    info.activityInfo.packageName,
                    info.activityInfo.name));
        }
        cached = filterAndSort(raw, ownPackage);
        return cached;
    }

    static List<AppEntry> filterAndSort(List<AppEntry> raw, String ownPackage) {
        Set<String> seen = new HashSet<>();
        List<AppEntry> out = new ArrayList<>();
        for (AppEntry e : raw) {
            if (e.packageName.equals(ownPackage) || HIDDEN_PACKAGES.contains(e.packageName)) {
                continue;
            }
            if (seen.add(e.packageName + "/" + e.className)) {
                out.add(e);
            }
        }
        out.sort(BY_LABEL);
        return out;
    }
}
