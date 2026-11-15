package com.inicio.tv;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Partidos de hoy y resultados de ayer, del marcador público de ESPN (sin cuenta ni clave):
 *   https://site.api.espn.com/apis/site/v2/sports/soccer/esp.1/scoreboard?dates=AAAAMMDD
 * Ligas: esp.1 (LaLiga), esp.2 (Segunda), uefa.champions, uefa.europa, esp.copa_del_rey, eng.1...
 * "esp.2:Zaragoza" toma de esa liga solo los partidos de ese equipo (varios con "|": "esp.2:Zaragoza|Huesca").
 * Los equipos favoritos van los primeros y resaltados.
 */
final class Football {

    static final class Match {
        long start;
        String home, away, homeScore, awayScore;
        /** "pre" (sin empezar), "in" (en juego) o "post" (terminado), como los da ESPN. */
        String state;
        String clock;
        boolean homeFav, awayFav;

        boolean fav() { return homeFav || awayFav; }
    }

    final List<Match> today = new ArrayList<>();
    final List<Match> yesterday = new ArrayList<>();

    boolean anyLive() {
        for (Match m : today) if ("in".equals(m.state)) return true;
        return false;
    }

    /** "Atlético" ~ "atletico": sin tildes ni mayúsculas, para comparar nombres de equipo. */
    static String plain(String s) {
        return java.text.Normalizer.normalize(s.toLowerCase(Locale.ROOT), java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").trim();
    }

    private static boolean matches(String team, List<String> names) {
        String t = plain(team);
        for (String n : names) if (!n.isEmpty() && t.contains(n)) return true;
        return false;
    }

    static Football fetch(List<String> leagues, List<String> favorites) throws Exception {
        List<String> favs = new ArrayList<>();
        for (String fv : favorites) favs.add(plain(fv));
        Calendar c = Calendar.getInstance();
        String todayKey = day(c);
        c.add(Calendar.DAY_OF_MONTH, -1);
        String yesterdayKey = day(c);
        Football f = new Football();
        Exception last = null;
        int ok = 0;
        // Una consulta por liga y día, en su forma más simple: ESPN responde 400 a un rango de fechas con idioma.
        for (String entry : leagues) {
            // "esp.2:Zaragoza|Huesca" -> liga esp.2, solo esos equipos (que también cuentan como favoritos)
            int colon = entry.indexOf(':');
            String league = colon < 0 ? entry.trim() : entry.substring(0, colon).trim();
            List<String> only = new ArrayList<>();
            if (colon >= 0) for (String t : entry.substring(colon + 1).split("\\|")) if (!plain(t).isEmpty()) only.add(plain(t));
            for (String o : only) if (!favs.contains(o)) favs.add(o);
            for (String date : new String[]{yesterdayKey, todayKey}) {
                try {
                    JSONObject o = new JSONObject(Net.get("https://site.api.espn.com/apis/site/v2/sports/soccer/"
                            + league + "/scoreboard?dates=" + date, null));
                    JSONArray events = o.optJSONArray("events");
                    if (events != null) {
                        for (int i = 0; i < events.length(); i++) {
                            Match m = parse(events.getJSONObject(i));
                            if (m == null) continue;
                            if (!only.isEmpty() && !matches(m.home, only) && !matches(m.away, only)) continue;
                            // Se reparte por el día en hora local: ESPN agrupa los partidos por el día de EE. UU.
                            Calendar mc = Calendar.getInstance();
                            mc.setTimeInMillis(m.start);
                            String key = day(mc);
                            List<Match> into = key.equals(todayKey) ? f.today : key.equals(yesterdayKey) ? f.yesterday : null;
                            if (into != null && !contains(into, m)) into.add(m);
                        }
                    }
                    ok++;
                } catch (Exception e) {
                    last = e;
                }
            }
        }
        if (ok == 0 && last != null) throw last;
        for (List<Match> list : new List[]{f.today, f.yesterday}) {
            for (Object o : list) {
                Match m = (Match) o;
                m.homeFav = matches(m.home, favs);
                m.awayFav = matches(m.away, favs);
            }
        }
        // Primero los de tus equipos; dentro de cada grupo, por hora.
        Comparator<Match> byTime = new Comparator<Match>() {
            @Override
            public int compare(Match a, Match b) {
                if (a.fav() != b.fav()) return a.fav() ? -1 : 1;
                return a.start < b.start ? -1 : a.start > b.start ? 1 : 0;
            }
        };
        Collections.sort(f.today, byTime);
        Collections.sort(f.yesterday, byTime);
        return f;
    }

    private static boolean contains(List<Match> list, Match m) {
        for (Match x : list) if (x.start == m.start && x.home.equals(m.home)) return true;
        return false;
    }

    private static String day(Calendar c) {
        return String.format(Locale.ROOT, "%04d%02d%02d",
                c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
    }

    private static Match parse(JSONObject e) {
        Match m = new Match();
        Date start = date(e.optString("date"));
        if (start == null) return null;
        m.start = start.getTime();
        JSONArray comps = e.optJSONArray("competitions");
        JSONObject comp = comps == null ? null : comps.optJSONObject(0);
        JSONArray teams = comp == null ? null : comp.optJSONArray("competitors");
        if (teams == null) return null;
        for (int i = 0; i < teams.length(); i++) {
            JSONObject t = teams.optJSONObject(i);
            if (t == null) continue;
            JSONObject team = t.optJSONObject("team");
            String name = team == null ? "?" : team.optString("shortDisplayName", team.optString("displayName", "?"));
            String score = t.optString("score", "");
            if ("away".equals(t.optString("homeAway"))) {
                m.away = name;
                m.awayScore = score;
            } else {
                m.home = name;
                m.homeScore = score;
            }
        }
        if (m.home == null || m.away == null) return null;
        JSONObject status = e.optJSONObject("status");
        if (status == null && comp != null) status = comp.optJSONObject("status");
        JSONObject type = status == null ? null : status.optJSONObject("type");
        m.state = type == null ? "pre" : type.optString("state", "pre");
        String detail = type == null ? "" : type.optString("shortDetail", "");
        if ("in".equals(m.state)) {
            m.clock = "HT".equals(detail) ? "Desc." : status.optString("displayClock", detail);
        } else if ("post".equals(m.state)) {
            m.clock = detail.contains("Postponed") || detail.contains("Aplazado") ? "Aplaz." : "Final";
        }
        return m;
    }

    /** "2026-10-30T20:00Z" o "2026-10-30T20:00:00Z", en UTC. */
    private static Date date(String s) {
        for (String p : new String[]{"yyyy-MM-dd'T'HH:mm'Z'", "yyyy-MM-dd'T'HH:mm:ss'Z'"}) {
            SimpleDateFormat f = new SimpleDateFormat(p, Locale.ROOT);
            f.setTimeZone(TimeZone.getTimeZone("UTC"));
            try {
                return f.parse(s);
            } catch (ParseException ignored) {
                // siguiente formato
            }
        }
        return null;
    }
}
