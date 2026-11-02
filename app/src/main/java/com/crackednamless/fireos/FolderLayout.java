package com.crackednamless.fireos;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

// Divide le app in cartelle (ordinate per nome) e app libere. Le cartelle vuote non esistono.
final class FolderLayout {

    final Map<String, List<AppEntry>> folders = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    final List<AppEntry> loose = new ArrayList<>();

    static FolderLayout build(List<AppEntry> apps, Map<String, String> assignment) {
        FolderLayout out = new FolderLayout();
        for (AppEntry app : apps) {
            String folder = assignment.get(app.packageName);
            if (folder == null) {
                out.loose.add(app);
            } else {
                out.folders.computeIfAbsent(folder, k -> new ArrayList<>()).add(app);
            }
        }
        return out;
    }
}
