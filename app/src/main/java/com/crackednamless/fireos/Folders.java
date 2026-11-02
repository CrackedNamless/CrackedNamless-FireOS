package com.crackednamless.fireos;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Le cartelle della Library: pacchetto -> nome cartella. Una cartella esiste finche' contiene almeno un'app.
final class Folders {

    static final int MAX_NAME = 24;
    private static final String PREFS = "folders";

    private final SharedPreferences prefs;
    private final Map<String, String> assignment = new HashMap<>();

    Folders(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        for (Map.Entry<String, ?> e : prefs.getAll().entrySet()) {
            if (e.getValue() instanceof String) {
                assignment.put(e.getKey(), (String) e.getValue());
            }
        }
    }

    static String clean(String name) {
        if (name == null) {
            return "";
        }
        String t = name.trim().replaceAll("\\s+", " ");
        return t.length() > MAX_NAME ? t.substring(0, MAX_NAME).trim() : t;
    }

    Map<String, String> assignment() {
        return Collections.unmodifiableMap(assignment);
    }

    String folderOf(String pkg) {
        return assignment.get(pkg);
    }

    // Se esiste gia' una cartella con lo stesso nome (senza distinguere maiuscole) usa quella.
    private String canonical(String name) {
        for (String existing : assignment.values()) {
            if (existing.equalsIgnoreCase(name)) {
                return existing;
            }
        }
        return name;
    }

    void put(String pkg, String folder) {
        String name = clean(folder);
        if (name.isEmpty()) {
            assignment.remove(pkg);
            prefs.edit().remove(pkg).apply();
            return;
        }
        name = canonical(name);
        assignment.put(pkg, name);
        prefs.edit().putString(pkg, name).apply();
    }

    void rename(String from, String to) {
        String name = clean(to);
        if (name.isEmpty()) {
            return;
        }
        name = canonical(name);
        SharedPreferences.Editor edit = prefs.edit();
        for (Map.Entry<String, String> e : assignment.entrySet()) {
            if (e.getValue().equalsIgnoreCase(from)) {
                e.setValue(name);
                edit.putString(e.getKey(), name);
            }
        }
        edit.apply();
    }

    void delete(String folder) {
        SharedPreferences.Editor edit = prefs.edit();
        List<String> gone = new ArrayList<>();
        for (Map.Entry<String, String> e : assignment.entrySet()) {
            if (e.getValue().equalsIgnoreCase(folder)) {
                gone.add(e.getKey());
            }
        }
        for (String pkg : gone) {
            assignment.remove(pkg);
            edit.remove(pkg);
        }
        edit.apply();
    }
}
