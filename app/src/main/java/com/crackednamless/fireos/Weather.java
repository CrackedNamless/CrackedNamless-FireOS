package com.crackednamless.fireos;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

public final class Weather {

    public enum Kind { CLEAR, PARTLY, CLOUD, FOG, RAIN, SNOW, STORM }

    public static final class Data {
        public final String city;
        public final int temp;
        public final int code;
        public final boolean day;

        public Data(String city, int temp, int code, boolean day) {
            this.city = city;
            this.temp = temp;
            this.code = code;
            this.day = day;
        }
    }

    public interface Callback {
        void onWeather(Data data);
    }

    static final double DEFAULT_LAT = 41.89;
    static final double DEFAULT_LON = 12.48;
    private static final long TTL_MS = 30 * 60 * 1000L;
    private static final String PREFS = "weather";

    private Weather() {
    }

    public static Data parse(String json, String city) throws JSONException {
        JSONObject cur = new JSONObject(json).getJSONObject("current");
        return new Data(city, (int) Math.round(cur.getDouble("temperature_2m")),
                cur.getInt("weather_code"), cur.optInt("is_day", 1) == 1);
    }

    public static Kind kind(int code) {
        if (code == 0 || code == 1) {
            return Kind.CLEAR;
        }
        if (code == 2) {
            return Kind.PARTLY;
        }
        if (code == 3) {
            return Kind.CLOUD;
        }
        if (code == 45 || code == 48) {
            return Kind.FOG;
        }
        if ((code >= 51 && code <= 67) || (code >= 80 && code <= 82)) {
            return Kind.RAIN;
        }
        if ((code >= 71 && code <= 77) || code == 85 || code == 86) {
            return Kind.SNOW;
        }
        if (code >= 95) {
            return Kind.STORM;
        }
        return Kind.CLOUD;
    }

    public static int description(int code, boolean day) {
        switch (kind(code)) {
            case CLEAR:
                return code == 0 ? (day ? R.string.weather_sunny : R.string.weather_clear) : R.string.weather_mostly_clear;
            case PARTLY:
                return R.string.weather_partly_cloudy;
            case FOG:
                return R.string.weather_fog;
            case RAIN:
                return code >= 80 ? R.string.weather_showers : R.string.weather_rain;
            case SNOW:
                return R.string.weather_snow;
            case STORM:
                return R.string.weather_thunderstorm;
            default:
                return R.string.weather_cloudy;
        }
    }

    public static int icon(Data d) {
        switch (kind(d.code)) {
            case CLEAR:
                return d.day ? R.drawable.ic_w_sun : R.drawable.ic_w_moon;
            case PARTLY:
                return R.drawable.ic_w_partly;
            case FOG:
            case CLOUD:
                return R.drawable.ic_w_cloud;
            case RAIN:
                return R.drawable.ic_w_rain;
            case SNOW:
                return R.drawable.ic_w_snow;
            default:
                return R.drawable.ic_w_storm;
        }
    }

    public static String temperature(Data d) {
        return String.format(Locale.getDefault(), "%d°", d.temp);
    }

    public static void load(Context context, Callback callback) {
        Context app = context.getApplicationContext();
        SharedPreferences prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String city = prefs.getString("city", app.getString(R.string.weather_default_city));
        Handler main = new Handler(Looper.getMainLooper());

        String cached = prefs.getString("json", null);
        long at = prefs.getLong("at", 0);
        Data cachedData = null;
        if (cached != null) {
            try {
                cachedData = parse(cached, city);
                callback.onWeather(cachedData);
            } catch (JSONException ignored) {
            }
        }
        if (cachedData != null && System.currentTimeMillis() - at < TTL_MS) {
            return;
        }

        double lat = Double.longBitsToDouble(prefs.getLong("lat", Double.doubleToLongBits(DEFAULT_LAT)));
        double lon = Double.longBitsToDouble(prefs.getLong("lon", Double.doubleToLongBits(DEFAULT_LON)));
        new Thread(() -> {
            try {
                String body = download(String.format(Locale.US,
                        "https://api.open-meteo.com/v1/forecast?latitude=%.3f&longitude=%.3f"
                                + "&current=temperature_2m,weather_code,is_day&timezone=auto", lat, lon));
                Data data = parse(body, city);
                prefs.edit().putString("json", body).putLong("at", System.currentTimeMillis()).apply();
                main.post(() -> callback.onWeather(data));
            } catch (Exception ignored) {
            }
        }, "weather").start();
    }

    private static String download(String address) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(address).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(8000);
        try (InputStream in = c.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[2048];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            return out.toString("UTF-8");
        } finally {
            c.disconnect();
        }
    }
}
