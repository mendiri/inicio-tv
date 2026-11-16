package com.inicio.tv;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * config.json: el del usuario en /sdcard/Android/data/com.inicio.tv/files/config.json (subido con adb), o el que
 * viene dentro del APK (assets/config.json). Un archivo que no se puede leer cae al de dentro del APK.
 */
final class Config {

    static final class Category {
        final String name;
        final List<String> packages = new ArrayList<>();
        Category(String name) { this.name = name; }
    }

    /** Una línea de la tarjeta: una etiqueta y una o varias entidades ("19.3°C · 61 %"). */
    static final class HaEntity {
        final String label;
        final List<String> entities = new ArrayList<>();
        HaEntity(String label) { this.label = label; }
    }

    String greeting = "¡Hola!";
    /** Signos del horóscopo que se alternan con el saludo (índices de Horoscope.SLUGS). */
    final List<Integer> signs = new ArrayList<>();
    int greetingCycleSeconds = 12;
    /** Fuente principal del horóscopo: false La Vanguardia, true ¡HOLA!. */
    boolean horoscopeHola;
    /** Lectura del horóscopo, en milisegundos reales. */
    long readStartMs = 3000, readLineMs = 3000, readEndMs = 1500;
    boolean weatherShow = true;
    int weatherCycleSeconds = 8;
    String city = "Zaragoza";
    double latitude = Double.NaN;
    double longitude = Double.NaN;

    /** Repositorio de GitHub con las versiones de Inicio; "actualizaciones": {"repo": ""} lo desactiva. */
    String updateRepo = "mendiri/inicio-tv";

    /** Fondo con las manchas de color moviéndose; velocidad 1 = normal, 0 = quieto. */
    float backgroundSpeed = 1f;

    boolean footballShow = false;
    final List<String> leagues = new ArrayList<>();
    final List<String> favoriteTeams = new ArrayList<>();
    int cardCycleSeconds = 10;

    boolean haShow = true;
    String haUrl = "";
    String haToken = "";
    String haTitle = "Mi Casa";
    boolean haCountLights = true;
    String haGraph = "";
    final List<HaEntity> haEntities = new ArrayList<>();

    final List<Category> categories = new ArrayList<>();
    final Set<String> hidden = new HashSet<>();
    boolean showOthers = true;
    String othersName = "Otras apps";

    /** Dónde el usuario deja su config.json. */
    static File userFile(Context ctx) {
        File dir = ctx.getExternalFilesDir(null);
        return dir == null ? null : new File(dir, "config.json");
    }

    /** Por qué no se pudo leer el config.json del usuario en la última carga, o null si se leyó bien. */
    static String lastError;

    static Config load(Context ctx) {
        lastError = null;
        File f = userFile(ctx);
        if (f != null && f.isFile()) {
            String text = null;
            try (InputStream in = new FileInputStream(f)) {
                text = read(in);
                return parse(text);
            } catch (Exception e) {
                Log.w("Inicio", "config.json del usuario no válido; uso el del APK", e);
                lastError = describe(e, text);
            }
        }
        try (InputStream in = ctx.getAssets().open("config.json")) {
            return parse(read(in));
        } catch (Exception e) {
            Log.w("Inicio", "config.json del APK no válido", e);
            return new Config();
        }
    }

    /** El config.json en uso, tal cual (el del usuario, o el del APK), para editarlo. */
    static JSONObject loadRaw(Context ctx) {
        File f = userFile(ctx);
        if (f != null && f.isFile()) {
            try (InputStream in = new FileInputStream(f)) {
                return new JSONObject(read(in));
            } catch (Exception e) {
                Log.w("Inicio", "config.json del usuario no válido", e);
            }
        }
        try (InputStream in = ctx.getAssets().open("config.json")) {
            return new JSONObject(read(in));
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    /** Guarda el config.json del usuario (el mismo que sube config.bat). */
    static void saveRaw(Context ctx, JSONObject o) throws Exception {
        File f = userFile(ctx);
        if (f == null) throw new java.io.IOException("Sin almacenamiento");
        File dir = f.getParentFile();
        if (dir != null && !dir.isDirectory() && !dir.mkdirs()) throw new java.io.IOException("No se pudo crear " + dir);
        // org.json escribe "\/": se quita para que la url siga siendo legible al editarla en el PC.
        String json = o.toString(2).replace("\\/", "/");
        File tmp = new File(f.getPath() + ".tmp");
        try (java.io.OutputStream out = new java.io.FileOutputStream(tmp)) {
            out.write(json.getBytes("UTF-8"));
        }
        if (!tmp.renameTo(f)) throw new java.io.IOException("No se pudo guardar " + f);
    }

    /** "línea 12: Expected ',' or '}'..." a partir del error de org.json, sin volcar el archivo entero. */
    private static String describe(Exception e, String text) {
        String m = String.valueOf(e.getMessage());
        int of = m.indexOf(" of ");
        if (of > 0) m = m.substring(0, of);
        java.util.regex.Matcher at = java.util.regex.Pattern.compile("at character (\\d+)").matcher(m);
        if (text != null && at.find()) {
            int pos = Math.min(Integer.parseInt(at.group(1)), text.length());
            int line = 1;
            for (int i = 0; i < pos; i++) if (text.charAt(i) == '\n') line++;
            return "línea " + line + ": " + m.substring(0, at.start()).trim();
        }
        return m;
    }

    private static String read(InputStream in) throws java.io.IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        String s = out.toString("UTF-8");
        // Un BOM al principio (Bloc de notas) rompe org.json.
        return s.startsWith("﻿") ? s.substring(1) : s;
    }

    private static Config parse(String json) throws org.json.JSONException {
        JSONObject o = new JSONObject(json);
        Config c = new Config();
        c.greeting = o.optString("saludo", c.greeting);
        JSONObject h = o.optJSONObject("horoscopo");
        if (h != null && h.optBoolean("mostrar", true)) {
            JSONArray ss = h.optJSONArray("signos");
            if (ss != null) {
                for (int i = 0; i < ss.length(); i++) {
                    int idx = Horoscope.index(ss.optString(i));
                    if (idx >= 0 && !c.signs.contains(idx)) c.signs.add(idx);
                }
            }
            c.greetingCycleSeconds = Math.max(4, h.optInt("segundos", c.greetingCycleSeconds));
            c.horoscopeHola = "hola".equalsIgnoreCase(h.optString("fuente", "lavanguardia").trim());
            c.readStartMs = Math.round(1000 * h.optDouble("pausa_inicial", c.readStartMs / 1000.0));
            c.readLineMs = Math.max(500, Math.round(1000 * h.optDouble("segundos_por_linea", c.readLineMs / 1000.0)));
            c.readEndMs = Math.round(1000 * h.optDouble("pausa_final", c.readEndMs / 1000.0));
        }

        JSONObject w = o.optJSONObject("tiempo");
        if (w != null) {
            c.weatherShow = w.optBoolean("mostrar", true);
            c.weatherCycleSeconds = w.optInt("alternar_segundos", c.weatherCycleSeconds);
            c.city = w.optString("ciudad", c.city);
            c.latitude = w.optDouble("latitud", Double.NaN);
            c.longitude = w.optDouble("longitud", Double.NaN);
        }

        JSONObject ha = o.optJSONObject("home_assistant");
        if (ha != null) {
            c.haShow = ha.optBoolean("mostrar", true);
            c.haUrl = trimSlash(ha.optString("url", ""));
            c.haToken = ha.optString("token", "");
            c.haTitle = ha.optString("titulo", c.haTitle);
            c.haCountLights = ha.optBoolean("contar_luces", true);
            c.haGraph = ha.optString("grafica", "");
            JSONArray es = ha.optJSONArray("entidades");
            if (es != null) {
                for (int i = 0; i < es.length(); i++) {
                    JSONObject e = es.optJSONObject(i);
                    if (e == null) continue;
                    // "entidad" admite un texto o una lista: ["sensor.temperatura", "sensor.humedad"]
                    List<String> ids = new ArrayList<>();
                    JSONArray many = e.optJSONArray("entidad");
                    if (many != null) {
                        for (int j = 0; j < many.length(); j++) if (!many.optString(j).isEmpty()) ids.add(many.optString(j));
                    } else if (!e.optString("entidad", "").isEmpty()) {
                        ids.add(e.optString("entidad"));
                    }
                    if (ids.isEmpty()) continue;
                    HaEntity line = new HaEntity(e.optString("nombre", ids.get(0)));
                    line.entities.addAll(ids);
                    c.haEntities.add(line);
                }
            }
        }

        JSONObject up = o.optJSONObject("actualizaciones");
        if (up != null) c.updateRepo = up.optString("repo", c.updateRepo).trim();

        JSONObject bg = o.optJSONObject("fondo");
        if (bg != null) {
            c.backgroundSpeed = bg.optBoolean("animado", true) ? (float) bg.optDouble("velocidad", 1.0) : 0f;
        }

        JSONObject fb = o.optJSONObject("futbol");
        if (fb != null) {
            c.footballShow = fb.optBoolean("mostrar", true);
            JSONArray ls = fb.optJSONArray("ligas");
            if (ls != null) for (int i = 0; i < ls.length(); i++) if (!ls.optString(i).isEmpty()) c.leagues.add(ls.optString(i));
            c.cardCycleSeconds = Math.max(3, fb.optInt("alternar_segundos", c.cardCycleSeconds));
            JSONArray fv = fb.optJSONArray("favoritos");
            if (fv != null) for (int i = 0; i < fv.length(); i++) if (!fv.optString(i).isEmpty()) c.favoriteTeams.add(fv.optString(i));
        }
        if (c.leagues.isEmpty()) {
            c.leagues.add("esp.1");
            c.leagues.add("uefa.champions");
        }

        JSONArray cats = o.optJSONArray("categorias");
        if (cats != null) {
            for (int i = 0; i < cats.length(); i++) {
                JSONObject cat = cats.optJSONObject(i);
                if (cat == null) continue;
                Category k = new Category(cat.optString("nombre", "Apps"));
                JSONArray apps = cat.optJSONArray("apps");
                if (apps != null) for (int j = 0; j < apps.length(); j++) k.packages.add(apps.optString(j));
                c.categories.add(k);
            }
        }
        JSONArray hide = o.optJSONArray("ocultar");
        if (hide != null) for (int i = 0; i < hide.length(); i++) c.hidden.add(hide.optString(i));
        c.showOthers = o.optBoolean("mostrar_otras", true);
        c.othersName = o.optString("nombre_otras", c.othersName);
        return c;
    }

    private static String trimSlash(String s) {
        s = s.trim();
        while (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        return s;
    }

    boolean haConfigured() {
        return !haUrl.isEmpty() && !haToken.isEmpty() && !haToken.startsWith("PEGA_AQUI");
    }
}
