package com.crackednamless.fireos;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

public class MemInfoParserTest {

    private static final String DUMP = "Applications Memory Usage (in Kilobytes):\n"
            + "Total PSS by process:\n"
            + "    85,383K: system (pid 1180 / activities)\n"
            + "    41,200K: com.netflix.ninja (pid 4410 / activities)\n"
            + "\n"
            + "Total PSS by OOM adjustment:\n"
            + "    55,112K: Native\n"
            + "                 9,876K: /system/bin/mediaserver (pid 612)\n"
            + "    85,383K: System\n"
            + "                85,383K: system (pid 1180)\n"
            + "    90,004K: Foreground\n"
            + "                90,004K: com.netflix.ninja (pid 4410)\n"
            + "    32,500K: Cached\n"
            + "                20,100K: com.amazon.tv.launcher:service (pid 2201)\n"
            + "                12,400K: com.google.android.youtube.tv (pid 3302)\n"
            + "\n"
            + "Total PSS by category:\n"
            + "    1,000K: Dalvik\n"
            + "\n"
            + "Total RAM: 922,268K (status normal)\n";

    @Test
    public void readsProcessesWithTheirGroup() {
        List<MemInfoParser.Proc> procs = MemInfoParser.parse(DUMP);
        assertEquals(5, procs.size());
        assertEquals("Native", procs.get(0).group);
        assertEquals("/system/bin/mediaserver", procs.get(0).name);
        assertEquals(9876, procs.get(0).pssKb);
        assertEquals("Foreground", procs.get(2).group);
        assertEquals("com.netflix.ninja", procs.get(2).packageName);
        assertEquals(90004, procs.get(2).pssKb);
    }

    @Test
    public void packageNameDropsTheProcessSuffix() {
        List<MemInfoParser.Proc> procs = MemInfoParser.parse(DUMP);
        assertEquals("Cached", procs.get(3).group);
        assertEquals("com.amazon.tv.launcher", procs.get(3).packageName);
        assertEquals("com.amazon.tv.launcher:service", procs.get(3).name);
    }

    @Test
    public void ignoresTextOutsideTheOomSection() {
        assertTrue(MemInfoParser.parse("nothing here").isEmpty());
        assertTrue(MemInfoParser.parse(null).isEmpty());
    }
}
