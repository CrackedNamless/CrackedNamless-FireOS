package com.crackednamless.fireos;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;

public class TweaksTest {

    @Test
    public void coreSystemAndOwnAppsAreEssential() {
        String self = "com.crackednamless.fireos";
        assertTrue(Tweaks.isEssential(self, self));
        assertTrue(Tweaks.isEssential("com.amazon.tv.launcher", self));
        assertTrue(Tweaks.isEssential("com.amazon.venezia", self));
        assertTrue(Tweaks.isEssential("com.amazon.ftv.profilepicker", self));
        assertTrue(Tweaks.isEssential("com.amazon.tv.settings.v2", self));
        assertTrue(Tweaks.isEssential("com.amazon.vizzini.ftvcds", self));
        assertTrue(Tweaks.isEssential("com.android.providers.downloads.ui", self));
    }

    @Test
    public void streamingAndOtherAppsAreNotEssential() {
        String self = "com.crackednamless.fireos";
        assertFalse(Tweaks.isEssential("com.netflix.ninja", self));
        assertFalse(Tweaks.isEssential("tv.streamingcommunity", self));
        assertFalse(Tweaks.isEssential("com.amazon.cloud9", self));
        assertFalse(Tweaks.isEssential("com.amazon.firebat", self));
    }

    @Test
    public void blockCommandBlocksEveryPackage() {
        String command = Tweaks.autoStartBlockCommand(Arrays.asList("a.b", "c.d"));
        assertTrue(command.startsWith("for p in a.b c.d; do "));
        assertTrue(command.contains("RUN_IN_BACKGROUND ignore"));
        assertTrue(command.contains("RUN_ANY_IN_BACKGROUND ignore"));
        assertEquals(1, command.split("am force-stop").length - 1);
    }
}
