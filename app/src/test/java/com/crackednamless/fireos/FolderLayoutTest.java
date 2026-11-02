package com.crackednamless.fireos;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

public class FolderLayoutTest {

    private static AppEntry app(String label, String pkg) {
        return new AppEntry(label, pkg, pkg + ".Main");
    }

    @Test
    public void groupsAppsByFolderAndKeepsTheRestLoose() {
        List<AppEntry> apps = Arrays.asList(app("Alpha", "a"), app("Beta", "b"), app("Gamma", "c"), app("Delta", "d"));
        Map<String, String> assignment = new HashMap<>();
        assignment.put("b", "Streaming");
        assignment.put("d", "streaming");
        assignment.put("c", "Games");

        FolderLayout layout = FolderLayout.build(apps, assignment);

        assertEquals(Arrays.asList("Games", "Streaming"), new ArrayList<>(layout.folders.keySet()).subList(0, 2));
        assertEquals(1, layout.loose.size());
        assertEquals("a", layout.loose.get(0).packageName);
        assertEquals(1, layout.folders.get("Games").size());
    }

    @Test
    public void emptyAssignmentLeavesEverythingLoose() {
        List<AppEntry> apps = Arrays.asList(app("Alpha", "a"), app("Beta", "b"));
        FolderLayout layout = FolderLayout.build(apps, new HashMap<>());
        assertTrue(layout.folders.isEmpty());
        assertEquals(2, layout.loose.size());
    }

    @Test
    public void cleanTrimsCollapsesSpacesAndLimitsLength() {
        assertEquals("My folder", Folders.clean("  My    folder "));
        assertEquals("", Folders.clean(null));
        assertEquals(Folders.MAX_NAME, Folders.clean("abcdefghijklmnopqrstuvwxyz0123456789").length());
    }
}
