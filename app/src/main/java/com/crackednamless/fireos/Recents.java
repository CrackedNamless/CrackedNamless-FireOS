package com.crackednamless.fireos;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

public final class Recents {

    public static final class Item {
        public final String packageName;
        public final String className;
        public final long time;

        Item(String packageName, String className, long time) {
            this.packageName = packageName;
            this.className = className;
            this.time = time;
        }
    }

    private static final String PREFS = "recents";
    private static final String KEY = "list";
    private static final int MAX = 12;

    private Recents() {
    }

    public static List<Item> load(Context context) {
        return parse(prefs(context).getString(KEY, ""));
    }

    public static void record(Context context, AppEntry entry, long now) {
        List<Item> items = parse(prefs(context).getString(KEY, ""));
        prefs(context).edit().putString(KEY, serialize(push(items, entry, now))).apply();
    }

    static List<Item> push(List<Item> items, AppEntry entry, long now) {
        List<Item> out = new ArrayList<>();
        out.add(new Item(entry.packageName, entry.className, now));
        for (Item i : items) {
            if (!i.packageName.equals(entry.packageName) && out.size() < MAX) {
                out.add(i);
            }
        }
        return out;
    }

    static List<Item> parse(String raw) {
        List<Item> out = new ArrayList<>();
        for (String line : raw.split("\n")) {
            String[] f = line.split("\t");
            if (f.length != 3) {
                continue;
            }
            try {
                out.add(new Item(f[0], f[1], Long.parseLong(f[2])));
            } catch (NumberFormatException ignored) {
                // Riga corrotta: si salta.
            }
        }
        return out;
    }

    static String serialize(List<Item> items) {
        StringBuilder sb = new StringBuilder();
        for (Item i : items) {
            sb.append(i.packageName).append('\t').append(i.className).append('\t').append(i.time).append('\n');
        }
        return sb.toString();
    }

    public static String ago(long time, long now) {
        long minutes = Math.max(0, now - time) / 60000L;
        if (minutes < 1) {
            return "adesso";
        }
        if (minutes < 60) {
            return minutes + " min fa";
        }
        long hours = minutes / 60;
        if (hours < 24) {
            return hours + (hours == 1 ? " ora fa" : " ore fa");
        }
        long days = hours / 24;
        return days == 1 ? "ieri" : days + " giorni fa";
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
