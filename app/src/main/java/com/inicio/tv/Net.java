package com.inicio.tv;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** Peticiones HTTP sencillas. Siempre fuera del hilo principal. */
final class Net {
    private Net() {}

    static String get(String url, String bearer) throws IOException {
        return get(url, bearer, "application/json", "Inicio-TV/1.0");
    }

    /** Una página web: con el Accept y el User-Agent de un navegador. */
    static String page(String url) throws IOException {
        return get(url, null, "text/html", "Mozilla/5.0 (Linux; Android 7.1.2; AFTMM) Inicio-TV/1.0");
    }

    private static String get(String url, String bearer, String accept, String agent) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(10000);
        c.setRequestProperty("Accept", accept);
        c.setRequestProperty("User-Agent", agent);
        if (bearer != null) c.setRequestProperty("Authorization", "Bearer " + bearer);
        try {
            int code = c.getResponseCode();
            if (code / 100 != 2) throw new IOException("HTTP " + code);
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                return out.toString("UTF-8");
            }
        } finally {
            c.disconnect();
        }
    }

    /** Descarga un archivo (por ejemplo un APK) a disco; sigue las redirecciones de GitHub. */
    static void download(String url, java.io.File to) throws IOException {
        for (int hops = 0; hops < 5; hops++) {
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(10000);
            c.setReadTimeout(30000);
            c.setInstanceFollowRedirects(false);
            c.setRequestProperty("User-Agent", "Inicio-TV/1.0");
            c.setRequestProperty("Accept", "application/octet-stream");
            try {
                int code = c.getResponseCode();
                if (code / 100 == 3) {
                    url = new URL(new URL(url), c.getHeaderField("Location")).toString();
                    continue;
                }
                if (code / 100 != 2) throw new IOException("HTTP " + code);
                java.io.File tmp = new java.io.File(to.getPath() + ".part");
                try (InputStream in = c.getInputStream(); java.io.OutputStream out = new java.io.FileOutputStream(tmp)) {
                    byte[] buf = new byte[16384];
                    int n;
                    while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                }
                if (!tmp.renameTo(to)) throw new IOException("No se pudo guardar la descarga");
                return;
            } finally {
                c.disconnect();
            }
        }
        throw new IOException("Demasiadas redirecciones");
    }
}
