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
    public void picksDescriptionResource() {
        assertEquals(R.string.weather_sunny, Weather.description(0, true));
        assertEquals(R.string.weather_clear, Weather.description(0, false));
        assertEquals(R.string.weather_thunderstorm, Weather.description(99, true));
    }
}
