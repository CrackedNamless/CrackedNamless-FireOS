package com.crackednamless.fireos;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class SystemLogWatcherTest {

    @Test
    public void powerKeyLinesAreRecognised() {
        assertEquals(SystemLogWatcher.POWER,
                SystemLogWatcher.classify("I/WindowManager( 538): Received Key name: POWER, code: 26, action: 0"));
        assertEquals(SystemLogWatcher.POWER,
                SystemLogWatcher.classify("D/InputDispatcher( 538): action=ACTION_DOWN, keyCode=KEYCODE_POWER"));
    }

    @Test
    public void homeStartIsRecognised() {
        assertEquals(SystemLogWatcher.HOME,
                SystemLogWatcher.classify("I/ActivityManager( 538): START u0 {act=android.intent.action.MAIN cat=[android.intent.category.HOME] flg=0x10200000 cmp=com.amazon.tv.launcher/.ui.HomeActivity_vNext} from uid 1000"));
    }

    @Test
    public void logcatErrorsAreNotAKeyPress() {
        assertEquals(SystemLogWatcher.NONE, SystemLogWatcher.classify("read: unexpected EOF!"));
        assertEquals(SystemLogWatcher.NONE, SystemLogWatcher.classify("logcat: Unexpected EOF!"));
        assertEquals(SystemLogWatcher.NONE, SystemLogWatcher.classify(""));
        assertEquals(SystemLogWatcher.NONE,
                SystemLogWatcher.classify("I/ActivityManager( 538): cat=[android.intent.category.HOME] from uid 1000"));
    }
}
