package com.inicio.tv;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;

/**
 * Actualizaciones desde un repositorio público de GitHub ("repo" en config.json, por defecto "mendiri/inicio-tv").
 * Primero mira update.json en la rama main:
 *   {"version": "1.27", "apk": "dist/Inicio.apk", "notas": "..."}
 * y, si no está, la última Release (etiqueta "v1.27" con un APK adjunto). Si la versión es mayor que la instalada,
 * se descarga el APK y se abre el instalador del sistema. Tiene que estar firmado con la misma clave.
 */
final class Updater {

    static final class Release {
        String version;
        String apkUrl;
        String notes;
    }

    private Updater() {}

    static String installedVersion(Context ctx) {
        try {
            return ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "0";
        }
    }

    static Release latest(String repo) throws Exception {
        try {
            String raw = "https://raw.githubusercontent.com/" + repo + "/main/";
            JSONObject u = new JSONObject(Net.get(raw + "update.json", null));
            Release r = new Release();
            r.version = u.getString("version").replaceFirst("^[vV]", "");
            String apk = u.optString("apk", "dist/Inicio.apk");
            r.apkUrl = apk.startsWith("http") ? apk : raw + apk;
            r.notes = u.optString("notas", "").trim();
            return r;
        } catch (Exception noUpdateJson) {
            return fromReleases(repo);
        }
    }

    private static Release fromReleases(String repo) throws Exception {
        JSONObject o = new JSONObject(Net.get("https://api.github.com/repos/" + repo + "/releases/latest", null));
        Release r = new Release();
        r.version = o.optString("tag_name", "").replaceFirst("^[vV]", "");
        r.notes = o.optString("body", "").trim();
        JSONArray assets = o.optJSONArray("assets");
        if (assets != null) {
            for (int i = 0; i < assets.length(); i++) {
                JSONObject a = assets.getJSONObject(i);
                if (a.optString("name").toLowerCase(java.util.Locale.ROOT).endsWith(".apk")) {
                    r.apkUrl = a.optString("browser_download_url");
                    break;
                }
            }
        }
        if (r.version.isEmpty() || r.apkUrl == null) throw new Exception("La última release no tiene un APK");
        return r;
    }

    /** "1.26" > "1.25.3"? Compara número a número. */
    static boolean isNewer(String remote, String local) {
        String[] a = remote.split("\\."), b = local.split("\\.");
        for (int i = 0; i < Math.max(a.length, b.length); i++) {
            int x = i < a.length ? num(a[i]) : 0;
            int y = i < b.length ? num(b[i]) : 0;
            if (x != y) return x > y;
        }
        return false;
    }

    private static int num(String s) {
        try {
            return Integer.parseInt(s.replaceAll("[^0-9]", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    static File download(Context ctx, Release r) throws Exception {
        File dir = new File(ctx.getCacheDir(), "updates");
        if (!dir.isDirectory() && !dir.mkdirs()) throw new Exception("Sin espacio para la descarga");
        File apk = new File(dir, ApkProvider.FILE);
        Net.download(r.apkUrl, apk);
        return apk;
    }

    /** Abre el instalador del sistema con el APK descargado. */
    static void install(Context ctx) {
        Intent i = new Intent(Intent.ACTION_INSTALL_PACKAGE)
                .setData(Uri.parse("content://" + ApkProvider.AUTHORITY + "/" + ApkProvider.FILE))
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        ctx.startActivity(i);
    }
}
