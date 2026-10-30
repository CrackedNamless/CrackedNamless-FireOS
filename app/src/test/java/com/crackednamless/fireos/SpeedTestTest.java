package com.crackednamless.fireos;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class SpeedTestTest {

    @Test
    public void rateConvertsBytesAndNanosToMbps() {
        assertEquals(80.0, SpeedTest.rate(10_000_000L, 1_000_000_000L), 0.001);
        assertEquals(0.0, SpeedTest.rate(100L, 0L), 0.0);
    }
}
