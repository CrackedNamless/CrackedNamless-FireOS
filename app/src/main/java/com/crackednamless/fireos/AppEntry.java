package com.crackednamless.fireos;

public final class AppEntry {
    public final String label;
    public final String packageName;
    public final String className;

    public AppEntry(String label, String packageName, String className) {
        this.label = label;
        this.packageName = packageName;
        this.className = className;
    }
}
