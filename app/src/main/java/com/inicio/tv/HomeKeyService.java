package com.inicio.tv;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/**
 * Botón Home en Fire OS 7, donde los servicios de accesibilidad sí funcionan (en Fire OS 6.7 no, y allí se usa
 * FireHomeWatcher). Recibe la tecla antes que el sistema y la consume: Amazon nunca llega a abrir su inicio, así que
 * tampoco hay los 5 s de espera de Android 9 tras pulsar Home. Mantenerla pulsada abre los ajustes del Fire TV.
 *
 * Se activa por adb:
 *   settings put secure enabled_accessibility_services com.inicio.tv/.HomeKeyService
 *   settings put secure accessibility_enabled 1
 */
public class HomeKeyService extends AccessibilityService {

    private static final long LONG_PRESS_MS = 700;
    private long downAt;
    private boolean longDone;

    static boolean isEnabled(Context ctx) {
        String s = Settings.Secure.getString(ctx.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return s != null && s.contains(ctx.getPackageName() + "/");
    }

    @Override
    protected boolean onKeyEvent(KeyEvent e) {
        if (e.getKeyCode() != KeyEvent.KEYCODE_HOME) return false;
        if (e.getAction() == KeyEvent.ACTION_DOWN) {
            if (e.getRepeatCount() == 0) {
                downAt = e.getEventTime();
                longDone = false;
            } else if (!longDone && e.getEventTime() - downAt >= LONG_PRESS_MS) {
                longDone = true;
                try {
                    startActivity(MainActivity.settingsIntent(this).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                } catch (RuntimeException ignored) {
                    // Sin ajustes que abrir: no se hace nada.
                }
            }
            return true;
        }
        if (e.getAction() == KeyEvent.ACTION_UP && !longDone) {
            startActivity(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NO_ANIMATION));
        }
        return true;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Solo interesan las teclas.
    }

    @Override
    public void onInterrupt() {
    }
}
