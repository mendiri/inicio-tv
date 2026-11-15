package com.inicio.tv;

import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/** El tiempo de Open-Meteo: sin cuenta ni clave. */
final class Weather {

    /** Un día de la previsión. */
    static final class Day {
        String name;
        int code, max, min;

        int icon() {
            return iconFor(code, true);
        }
    }

    final List<Day> week = new ArrayList<>();

    String place;
    int temperature;
    /** Probabilidad máxima de precipitación hoy, en %, o -1 si no hay dato. */
    int rainChance = -1;
    int max;
    int min;
    int code;
    boolean day;

    /** Busca la ciudad (solo la primera vez; se guarda) y pide el tiempo actual. */
    static Weather fetch(Config cfg, SharedPreferences prefs) throws Exception {
        double lat = cfg.latitude, lon = cfg.longitude;
        String place = cfg.city;
        if (Double.isNaN(lat) || Double.isNaN(lon)) {
            String key = "geo:" + cfg.city.toLowerCase(Locale.ROOT);
            String cached = prefs.getString(key, null);
            if (cached != null) {
                String[] p = cached.split(";", 3);
                lat = Double.parseDouble(p[0]);
                lon = Double.parseDouble(p[1]);
                place = p[2];
            } else {
                JSONObject g = new JSONObject(Net.get("https://geocoding-api.open-meteo.com/v1/search?count=1&language=es&format=json&name="
                        + URLEncoder.encode(cfg.city, "UTF-8"), null));
                JSONArray r = g.optJSONArray("results");
                if (r == null || r.length() == 0) throw new Exception("Ciudad no encontrada: " + cfg.city);
                JSONObject first = r.getJSONObject(0);
                lat = first.getDouble("latitude");
                lon = first.getDouble("longitude");
                place = first.optString("name", cfg.city);
                prefs.edit().putString(key, lat + ";" + lon + ";" + place).apply();
            }
        }
        JSONObject f = new JSONObject(Net.get(String.format(Locale.ROOT,
                "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f"
                        + "&current=temperature_2m,weather_code,is_day"
                        + "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max"
                        + "&timezone=auto&forecast_days=8", lat, lon), null));
        JSONObject cur = f.getJSONObject("current");
        JSONObject daily = f.getJSONObject("daily");
        Weather w = new Weather();
        w.place = place;
        w.temperature = (int) Math.round(cur.getDouble("temperature_2m"));
        w.code = cur.optInt("weather_code", 0);
        w.day = cur.optInt("is_day", 1) == 1;
        w.max = (int) Math.round(daily.getJSONArray("temperature_2m_max").getDouble(0));
        w.min = (int) Math.round(daily.getJSONArray("temperature_2m_min").getDouble(0));
        JSONArray rain = daily.optJSONArray("precipitation_probability_max");
        if (rain != null && !rain.isNull(0)) w.rainChance = (int) Math.round(rain.optDouble(0, -1));
        JSONArray days = daily.getJSONArray("time");
        JSONArray codes = daily.getJSONArray("weather_code");
        JSONArray maxs = daily.getJSONArray("temperature_2m_max");
        JSONArray mins = daily.getJSONArray("temperature_2m_min");
        for (int i = 1; i < days.length(); i++) {   // 0 es hoy
            Day d = new Day();
            d.name = dayName(days.getString(i));
            d.code = codes.optInt(i, 0);
            d.max = (int) Math.round(maxs.getDouble(i));
            d.min = (int) Math.round(mins.getDouble(i));
            w.week.add(d);
        }
        return w;
    }

    /** "2026-10-30" -> "Vie" */
    private static String dayName(String isoDate) {
        String[] p = isoDate.split("-");
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(Integer.parseInt(p[0]), Integer.parseInt(p[1]) - 1, Integer.parseInt(p[2]));
        String[] names = {"Dom", "Lun", "Mar", "Mié", "Jue", "Vie", "Sáb"};
        return names[c.get(Calendar.DAY_OF_WEEK) - 1];
    }

    /** Códigos WMO de Open-Meteo. */
    String description() {
        switch (code) {
            case 0: return day ? "Despejado" : "Noche despejada";
            case 1: return "Mayormente despejado";
            case 2: return "Parcialmente nublado";
            case 3: return "Nublado";
            case 45: case 48: return "Niebla";
            case 51: case 53: case 55: return "Llovizna";
            case 56: case 57: return "Llovizna helada";
            case 61: return "Lluvia débil";
            case 63: return "Lluvia";
            case 65: return "Lluvia fuerte";
            case 66: case 67: return "Lluvia helada";
            case 71: case 73: case 75: case 77: return "Nieve";
            case 80: case 81: case 82: return "Chubascos";
            case 85: case 86: return "Chubascos de nieve";
            case 95: return "Tormenta";
            case 96: case 99: return "Tormenta con granizo";
            default: return "";
        }
    }

    int icon() {
        return iconFor(code, day);
    }

    static int iconFor(int code, boolean day) {
        switch (code) {
            case 0: return day ? WeatherIcon.SUN : WeatherIcon.MOON;
            case 1: case 2: return day ? WeatherIcon.PARTLY : WeatherIcon.PARTLY_NIGHT;
            case 3: return WeatherIcon.CLOUD;
            case 45: case 48: return WeatherIcon.FOG;
            case 51: case 53: case 55: case 56: case 57: return WeatherIcon.DRIZZLE;
            case 61: case 63: case 65: case 66: case 67: case 80: case 81: case 82: return WeatherIcon.RAIN;
            case 71: case 73: case 75: case 77: case 85: case 86: return WeatherIcon.SNOW;
            case 95: case 96: case 99: return WeatherIcon.STORM;
            default: return WeatherIcon.CLOUD;
        }
    }

    String emoji() {
        switch (code) {
            case 0: return day ? "☀️" : "🌙";
            case 1: return day ? "🌤️" : "🌙";
            case 2: return day ? "⛅" : "☁️";
            case 3: return "☁️";
            case 45: case 48: return "🌫️";
            case 51: case 53: case 55: case 56: case 57: return "🌦️";
            case 61: case 63: case 65: case 66: case 67: case 80: case 81: case 82: return "🌧️";
            case 71: case 73: case 75: case 77: case 85: case 86: return "❄️";
            case 95: case 96: case 99: return "⛈️";
            default: return "🌡️";
        }
    }
}
