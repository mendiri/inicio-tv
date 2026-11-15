package com.inicio.tv;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/** Lee estados de Home Assistant por su API REST, con un token de larga duración. */
final class HomeAssistant {

    /** Una línea de la tarjeta: etiqueta y sus valores (cada uno irá en su columna). */
    static final class Line {
        final String label;
        final List<String> values = new ArrayList<>();
        Line(String label) { this.label = label; }
    }

    final List<Line> lines = new ArrayList<>();
    /** Valores de la gráfica (últimas horas), o vacía. */
    final List<Float> graph = new ArrayList<>();

    static HomeAssistant fetch(Config cfg) throws Exception {
        HomeAssistant h = new HomeAssistant();
        if (cfg.haCountLights) {
            JSONArray all = new JSONArray(Net.get(cfg.haUrl + "/api/states", cfg.haToken));
            int on = 0;
            for (int i = 0; i < all.length(); i++) {
                JSONObject s = all.getJSONObject(i);
                if (s.optString("entity_id").startsWith("light.") && "on".equals(s.optString("state"))) on++;
            }
            Line l = new Line("Luces");
            l.values.add(on == 1 ? "1 encendida" : on + " encendidas");
            h.lines.add(l);
        }
        for (Config.HaEntity e : cfg.haEntities) {
            Line l = new Line(e.label);
            for (String id : e.entities) l.values.add(state(cfg, id));
            h.lines.add(l);
        }
        if (!cfg.haGraph.isEmpty()) {
            try {
                SimpleDateFormat iso = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ROOT);
                iso.setTimeZone(TimeZone.getTimeZone("UTC"));
                String start = iso.format(new Date(System.currentTimeMillis() - 6L * 3600_000L));
                JSONArray hist = new JSONArray(Net.get(cfg.haUrl + "/api/history/period/" + start
                        + "?minimal_response&no_attributes&filter_entity_id=" + URLEncoder.encode(cfg.haGraph, "UTF-8"), cfg.haToken));
                if (hist.length() > 0) {
                    JSONArray pts = hist.getJSONArray(0);
                    for (int i = 0; i < pts.length(); i++) {
                        try {
                            h.graph.add(Float.parseFloat(pts.getJSONObject(i).optString("state")));
                        } catch (NumberFormatException ignored) {
                            // "unavailable" y similares
                        }
                    }
                }
            } catch (Exception ignored) {
                // Sin gráfica: la tarjeta sigue con sus líneas.
            }
        }
        return h;
    }

    private static String state(Config cfg, String id) {
        try {
            JSONObject s = new JSONObject(Net.get(cfg.haUrl + "/api/states/" + id, cfg.haToken));
            String unit = s.optJSONObject("attributes") == null ? ""
                    : s.optJSONObject("attributes").optString("unit_of_measurement", "");
            return translate(s.optString("state")) + (unit.isEmpty() ? "" : unit.startsWith("°") ? unit : " " + unit);
        } catch (Exception ex) {
            return "—";
        }
    }

    private static String translate(String state) {
        switch (state) {
            case "on": return "Encendido";
            case "off": return "Apagado";
            case "open": return "Abierta";
            case "closed": return "Cerrada";
            case "locked": return "Cerrada";
            case "unlocked": return "Abierta";
            case "home": return "En casa";
            case "not_home": return "Fuera";
            case "armed_away": case "armed_home": case "armed_night": case "armed_vacation": case "armed_custom_bypass":
                return "Armada";
            case "disarmed": return "Desarmada";
            case "triggered": return "¡Disparada!";
            case "unavailable": return "No disponible";
            case "unknown": return "—";
            case "playing": return "Reproduciendo";
            case "paused": return "En pausa";
            case "idle": return "Inactivo";
            case "heat": return "Calor";
            case "cool": return "Frío";
            case "auto": return "Auto";
            default:
                // Números con muchos decimales: 21.4567 -> 21.5
                try {
                    double d = Double.parseDouble(state);
                    if (d == Math.rint(d)) return String.valueOf((long) d);
                    return String.format(Locale.getDefault(), "%.1f", d);
                } catch (NumberFormatException e) {
                    return state;
                }
        }
    }
}
