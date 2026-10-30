package com.crackednamless.fireos;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class WeatherTest {

    @Test
    public void mapsCodesToKinds() {
        assertEquals(Weather.Kind.CLEAR, Weather.kind(0));
        assertEquals(Weather.Kind.PARTLY, Weather.kind(2));
        assertEquals(Weather.Kind.RAIN, Weather.kind(61));
        assertEquals(Weather.Kind.RAIN, Weather.kind(81));
        assertEquals(Weather.Kind.SNOW, Weather.kind(73));
        assertEquals(Weather.Kind.STORM, Weather.kind(95));
        assertEquals(Weather.Kind.FOG, Weather.kind(45));
    }

    @Test
    public void describesInItalian() {
        assertEquals("Soleggiato", Weather.describe(0, true));
        assertEquals("Sereno", Weather.describe(0, false));
        assertEquals("Temporale", Weather.describe(99, true));
    }
}
