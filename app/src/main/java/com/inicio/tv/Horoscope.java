package com.inicio.tv;

import android.content.SharedPreferences;
import android.text.Html;

import java.text.Normalizer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * El horóscopo del día, leído de la página de cada signo. Se guarda por día: una descarga por signo y día.
 *  - La Vanguardia (principal por defecto): .../horoscopo/signos-zodiaco-aries/horoscopo-diario, primer párrafo de
 *    <div class="text-block">. A veces repite el mismo texto varios días: si hoy trae el de un día anterior, se
 *    usa el de ¡HOLA!.
 *  - ¡HOLA!: https://www.hola.com/horoscopo/aries/ — en "body-content", un <h3> "Aries hoy 30 de octubre" y el
 *    texto del día en el <p> siguiente. Se publica de madrugada.
 * Con "fuente": "hola" en config.json el orden se invierte.
 */
final class Horoscope {

    static final String[] SLUGS = {"aries", "tauro", "geminis", "cancer", "leo", "virgo", "libra", "escorpio",
            "sagitario", "capricornio", "acuario", "piscis"};
    static final String[] NAMES = {"Aries", "Tauro", "Géminis", "Cáncer", "Leo", "Virgo", "Libra", "Escorpio",
            "Sagitario", "Capricornio", "Acuario", "Piscis"};

    final String name;
    final String text;
    /** false si la web aún tiene el del día anterior (¡HOLA! publica de madrugada): hay que volver a pedirlo. */
    final boolean fresh;

    private Horoscope(String name, String text, boolean fresh) {
        this.name = name;
        this.text = text;
        this.fresh = fresh;
    }

    private static final String[] MONTHS = {"enero", "febrero", "marzo", "abril", "mayo", "junio", "julio",
            "agosto", "septiembre", "octubre", "noviembre", "diciembre"};

    /** "Aries hoy 30 de octubre" es de hoy. */
    private static boolean isToday(String heading) {
        java.util.Calendar c = java.util.Calendar.getInstance();
        String today = c.get(java.util.Calendar.DAY_OF_MONTH) + " de " + MONTHS[c.get(java.util.Calendar.MONTH)];
        return heading.toLowerCase(Locale.ROOT).contains(today);
    }

    /** "Géminis", "geminis" o "GEMINIS" -> índice del signo, o -1. */
    static int index(String sign) {
        String s = Normalizer.normalize(sign.trim().toLowerCase(Locale.ROOT), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        for (int i = 0; i < SLUGS.length; i++) if (SLUGS[i].equals(s)) return i;
        return -1;
    }

    /** {encabezado ("Aries hoy 30 de octubre"), texto del día} */
    private static String[] fromHola(int sign) throws Exception {
        String html = Net.page("https://www.hola.com/horoscopo/" + SLUGS[sign] + "/");
        int body = html.indexOf("body-content");
        int h3 = body < 0 ? -1 : html.indexOf("<h3", body);
        int h3End = h3 < 0 ? -1 : html.indexOf("</h3>", h3);
        if (h3End < 0) throw new Exception("La página del horóscopo ha cambiado");
        @SuppressWarnings("deprecation")
        String heading = Html.fromHtml(html.substring(html.indexOf('>', h3) + 1, h3End)).toString();
        return new String[]{heading, paragraphAfter(html, h3End)};
    }

    private static String fromVanguardia(int sign) throws Exception {
        String html = Net.page("https://www.lavanguardia.com/horoscopo/signos-zodiaco-" + SLUGS[sign] + "/horoscopo-diario");
        return paragraphAfter(html, html.indexOf("class=\"text-block\""));
    }

    /** El texto del primer <p> a partir de from, sin etiquetas ni espacios de más. */
    private static String paragraphAfter(String html, int from) throws Exception {
        int p = from < 0 ? -1 : html.indexOf("<p", from);
        int start = p < 0 ? -1 : html.indexOf('>', p) + 1;
        int end = start <= 0 ? -1 : html.indexOf("</p>", start);
        if (end < 0) throw new Exception("La página del horóscopo ha cambiado");
        @SuppressWarnings("deprecation")
        String text = Html.fromHtml(html.substring(start, end)).toString().replaceAll("\\s+", " ").trim();
        if (text.length() < 20) throw new Exception("Horóscopo vacío");
        return text;
    }

    /** El texto de La Vanguardia, o null si es el mismo que ya traía un día anterior (no lo han actualizado). */
    private static String vanguardiaIfNew(int sign, SharedPreferences prefs, String today) throws Exception {
        String text = fromVanguardia(sign);
        String key = "lv_visto:" + SLUGS[sign];
        String seen = prefs.getString(key, null);   // "fecha en que apareció este texto|texto"
        if (seen != null && seen.endsWith("|" + text) && !seen.startsWith(today + "|")) return null;
        if (seen == null || !seen.endsWith("|" + text)) prefs.edit().putString(key, today + "|" + text).apply();
        return text;
    }

    static Horoscope fetch(int sign, SharedPreferences prefs, boolean preferHola) throws Exception {
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(new Date());
        String key = "horoscopo2:" + SLUGS[sign];
        String cached = prefs.getString(key, null);
        if (cached != null && cached.startsWith(today + "|")) {
            return new Horoscope(NAMES[sign], cached.substring(today.length() + 1), true);
        }
        String text = null;
        boolean fresh = true;
        if (!preferHola) {
            try {
                text = vanguardiaIfNew(sign, prefs, today);
            } catch (Exception ignored) {
                // se prueba con ¡HOLA!
            }
        }
        if (text == null) {
            try {
                String[] hola = fromHola(sign);
                text = hola[1];
                fresh = isToday(hola[0]);
            } catch (Exception e) {
                // ¡HOLA! no responde: La Vanguardia, aunque repita texto.
                text = fromVanguardia(sign);
            }
        }
        if (fresh) prefs.edit().putString(key, today + "|" + text).apply();
        return new Horoscope(NAMES[sign], text, fresh);
    }
}
