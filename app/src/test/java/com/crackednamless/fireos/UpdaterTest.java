package com.crackednamless.fireos;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class UpdaterTest {

    @Test
    public void newerVersionsAreDetected() {
        assertTrue(Updater.isNewer("0.2.1", "0.2.0"));
        assertTrue(Updater.isNewer("0.10.0", "0.9.5"));
        assertTrue(Updater.isNewer("1.0", "0.9.9"));
        assertTrue(Updater.isNewer("0.2.0.1", "0.2.0"));
        assertFalse(Updater.isNewer("0.2.0", "0.2.0"));
        assertFalse(Updater.isNewer("0.2.0", "0.2"));
        assertFalse(Updater.isNewer("0.1.9", "0.2.0"));
    }

    @Test
    public void notesLoseMarkdownAndAreShortened() {
        String clean = Updater.cleanNotes("### Added\r\n- **Store tab.** Opens the `Appstore`.\r\n\r\n### Fixed\r\n- Power button");
        assertEquals("Added\n• Store tab. Opens the Appstore.\nFixed\n• Power button", clean);
        assertTrue(Updater.cleanNotes(new String(new char[2000]).replace('\0', 'a')).length() <= 501);
    }
}
