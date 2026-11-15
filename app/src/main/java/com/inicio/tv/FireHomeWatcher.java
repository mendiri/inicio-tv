package com.inicio.tv;

import android.app.ActivityManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * Botón Home en Fire OS 6. Adaptado de FireHomeWatcher de cleanyourtv (fork de Mohamedkrs, licencia MIT).
 *
 * Fire OS 6 no deja cambiar el launcher por defecto ni desactivar el de Amazon. Cada pulsación de Home deja en el
 * registro del sistema "ActivityManager: START ... cat=[android.intent.category.HOME] ... cmp=com.amazon.tv.launcher/
 * ... from uid 1000". Este servicio espera esa línea (lectura bloqueante, sin sondeo) y trae Inicio al frente.
 * Solo arranca con READ_LOGS concedido por adb: pm grant com.inicio.tv android.permission.READ_LOGS
 */
public class FireHomeWatcher extends Service {

    private static final String TAG = "InicioHome";
    private static final String HOME_CATEGORY = "cat=[android.intent.category.HOME]";
    private static final String AMAZON_HOME = "cmp=com.amazon.tv.launcher/";
    private static final String FROM_SYSTEM = "from uid 1000 ";

    private Thread thread;

    static void startIfGranted(Context ctx) {
        if (ctx.checkSelfPermission(android.Manifest.permission.READ_LOGS) != PackageManager.PERMISSION_GRANTED) return;
        ctx.startService(new Intent(ctx, FireHomeWatcher.class));
    }

    /** Tras reiniciar: el vigilante, y el launcher encima del de Amazon. */
    public static class BootReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context ctx, Intent intent) {
            boolean logs = ctx.checkSelfPermission(android.Manifest.permission.READ_LOGS) == PackageManager.PERMISSION_GRANTED;
            // Solo si Inicio hace de inicio: con el vigilante (Fire OS 6) o con HomeKeyService (Fire OS 7).
            if (!logs && !HomeKeyService.isEnabled(ctx)) return;
            startIfGranted(ctx);
            ctx.startActivity(new Intent(ctx, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (thread == null || !thread.isAlive()) {
            thread = new Thread(new Runnable() {
                @Override
                public void run() {
                    watch();
                }
            }, TAG);
            thread.start();
        }
        return START_STICKY;
    }

    private void watch() {
        while (!Thread.currentThread().isInterrupted()) {
            Process p = null;
            try {
                // -T 1: empieza por la línea más nueva, para no reaccionar a un Home antiguo.
                p = Runtime.getRuntime().exec(new String[]{"logcat", "-T", "1", "-v", "brief", "ActivityManager:I", "*:S"});
                BufferedReader in = new BufferedReader(new InputStreamReader(p.getInputStream()));
                String line;
                while ((line = in.readLine()) != null) {
                    if (line.contains(HOME_CATEGORY) && line.contains(AMAZON_HOME) && line.contains(FROM_SYSTEM)) {
                        bringBack();
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "logcat se detuvo", e);
            } finally {
                if (p != null) p.destroy();
            }
            SystemClock.sleep(2000);
        }
    }

    /**
     * Android 7 retrasa 5 s los arranques de apps en segundo plano tras pulsar Home. Mover al frente la tarea que
     * ya existe (AppTask) no sufre ese retraso; arrancarla de nuevo es el plan B.
     */
    private void bringBack() {
        ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        for (ActivityManager.AppTask task : am.getAppTasks()) {
            try {
                task.moveToFront();
                sendBroadcast(new Intent(MainActivity.ACTION_HOME_PRESSED).setPackage(getPackageName()));
                return;
            } catch (RuntimeException e) {
                Log.w(TAG, "moveToFront falló", e);
            }
        }
        startActivity(new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION));
    }

    @Override
    public void onDestroy() {
        if (thread != null) thread.interrupt();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
