package com.crackednamless.fireos;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

public class AppRepositoryTest {

    private static AppEntry app(String label, String pkg) {
        return new AppEntry(label, pkg, pkg + ".Main");
    }

    @Test
    public void excludesOwnPackage() {
        List<AppEntry> out = AppRepository.filterAndSort(
                Arrays.asList(app("Netflix", "com.netflix"), app("Home", "com.me")), "com.me");
        assertEquals(1, out.size());
        assertEquals("com.netflix", out.get(0).packageName);
    }

    @Test
    public void sortsCaseInsensitively() {
        List<AppEntry> out = AppRepository.filterAndSort(
                Arrays.asList(app("youtube", "a.yt"), app("Disney+", "a.d"), app("Netflix", "a.n")), "x");
        assertEquals("Disney+", out.get(0).label);
        assertEquals("Netflix", out.get(1).label);
        assertEquals("youtube", out.get(2).label);
    }

    @Test
    public void removesDuplicateComponents() {
        AppEntry a = app("Netflix", "a.n");
        List<AppEntry> out = AppRepository.filterAndSort(Arrays.asList(a, a), "x");
        assertEquals(1, out.size());
    }

    @Test
    public void hidesSystemNoiseApps() {
        List<AppEntry> out = AppRepository.filterAndSort(
                Arrays.asList(app("Tracciamento", "com.android.traceur"),
                        app("Musica", "com.amazon.audiohome"),
                        app("Netflix", "com.netflix")), "x");
        assertEquals(1, out.size());
        assertEquals("com.netflix", out.get(0).packageName);
    }

    @Test
    public void emptyInputGivesEmptyList() {
        assertTrue(AppRepository.filterAndSort(Collections.<AppEntry>emptyList(), "x").isEmpty());
    }
}
