package com.crackednamless.fireos;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Legge la sezione "Total PSS by OOM adjustment" di "dumpsys meminfo": ogni processo con la sua memoria e il gruppo di importanza.
final class MemInfoParser {

    static final class Proc {
        final String name;
        final String packageName;
        final long pssKb;
        final String group;

        Proc(String name, String group, long pssKb) {
            this.name = name;
            int colon = name.indexOf(':');
            this.packageName = colon > 0 ? name.substring(0, colon) : name;
            this.group = group;
            this.pssKb = pssKb;
        }
    }

    private static final Pattern PROCESS = Pattern.compile("^\\s+([\\d,]+)K: (\\S+) \\(pid \\d+[^)]*\\)\\s*$");
    private static final Pattern GROUP = Pattern.compile("^\\s+([\\d,]+)K: ([A-Za-z][A-Za-z ]*?)\\s*$");

    private MemInfoParser() {
    }

    static List<Proc> parse(String dump) {
        List<Proc> result = new ArrayList<>();
        if (dump == null) {
            return result;
        }
        boolean inSection = false;
        String group = null;
        for (String line : dump.split("\n")) {
            if (line.startsWith("Total PSS by OOM adjustment")) {
                inSection = true;
                continue;
            }
            if (!inSection) {
                continue;
            }
            if (line.startsWith("Total PSS by category") || line.startsWith("Total RAM")) {
                break;
            }
            Matcher p = PROCESS.matcher(line);
            if (p.matches()) {
                if (group != null) {
                    result.add(new Proc(p.group(2), group, kb(p.group(1))));
                }
                continue;
            }
            Matcher g = GROUP.matcher(line);
            if (g.matches()) {
                group = g.group(2);
            }
        }
        return result;
    }

    private static long kb(String digits) {
        try {
            return Long.parseLong(digits.replace(",", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
