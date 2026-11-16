package com.inicio.tv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.View;

/**
 * El fondo pastel de Inicio, con las manchas de luz flotando y respirando muy despacio (un recorrido completo
 * tarda uno o dos minutos). Va con el reloj real, no con los animadores del sistema, que la limpieza deja al 0,5x.
 * Se redibuja 20 veces por segundo, de sobra para un movimiento tan lento, y solo mientras Inicio está en pantalla.
 */
final class AmbientView extends View {

    private static final long FRAME_MS = 50;

    /** Una mancha: color, centro y radio (fracciones de la pantalla), amplitud y periodo de su movimiento. */
    private static final class Blob {
        final int color;
        final float cx, cy, radius, ax, ay, periodX, periodY, phase;
        RadialGradient shader;

        Blob(int color, float cx, float cy, float radius, float ax, float ay, float periodX, float periodY, float phase) {
            this.color = color;
            this.cx = cx;
            this.cy = cy;
            this.radius = radius;
            this.ax = ax;
            this.ay = ay;
            this.periodX = periodX;
            this.periodY = periodY;
            this.phase = phase;
        }
    }

    // Los mismos colores y sitios que el fondo fijo (Look.background), ahora con movimiento.
    private final Blob[] blobs = {
            new Blob(0xB3FFC9AE, 0.40f, 0.62f, 0.30f, 0.12f, 0.10f, 97, 131, 0.0f),
            new Blob(0x99CDB8F2, 0.66f, 0.40f, 0.28f, 0.14f, 0.12f, 113, 83, 1.7f),
            new Blob(0x80B9D9F6, 0.92f, 0.78f, 0.30f, 0.10f, 0.12f, 89, 149, 3.1f),
            new Blob(0x80F8B9C9, 0.08f, 0.98f, 0.25f, 0.12f, 0.08f, 127, 101, 4.4f),
            new Blob(0x66FFFFFF, 0.12f, 0.08f, 0.30f, 0.10f, 0.08f, 139, 107, 5.2f),
    };

    private final Paint basePaint = new Paint();
    private final Paint blobPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix matrix = new Matrix();
    private final long origin = SystemClock.uptimeMillis();
    private boolean running;
    private float speed = 1f;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            invalidate();
            postDelayed(this, FRAME_MS);
        }
    };

    AmbientView(Context ctx) {
        super(ctx);
    }

    /** speed 1 = ritmo normal; 0 = fondo quieto. */
    void setSpeed(float speed) {
        this.speed = Math.max(0f, speed);
        if (this.speed == 0f) stop();
        invalidate();
    }

    void start() {
        if (running || speed == 0f) return;
        running = true;
        post(tick);
    }

    void stop() {
        running = false;
        removeCallbacks(tick);
    }

    @Override
    protected void onDetachedFromWindow() {
        stop();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        basePaint.setShader(new LinearGradient(0, 0, w, h,
                new int[]{0xFFF8EEE4, 0xFFEFDDEB, 0xFFDDE7F6}, null, Shader.TileMode.CLAMP));
        float size = Math.max(w, h);
        for (Blob b : blobs) {
            // Centrado en el origen: cada fotograma solo se mueve y se escala, sin crear nada nuevo.
            b.shader = new RadialGradient(0, 0, size * b.radius,
                    new int[]{b.color, b.color & 0x00FFFFFF}, null, Shader.TileMode.CLAMP);
        }
    }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        c.drawRect(0, 0, w, h, basePaint);
        double t = (SystemClock.uptimeMillis() - origin) / 1000.0 * speed;
        float size = Math.max(w, h);
        for (Blob b : blobs) {
            if (b.shader == null) continue;
            float x = w * (b.cx + b.ax * (float) Math.sin(2 * Math.PI * t / b.periodX + b.phase));
            float y = h * (b.cy + b.ay * (float) Math.cos(2 * Math.PI * t / b.periodY + b.phase));
            float breathe = 1f + 0.08f * (float) Math.sin(2 * Math.PI * t / (b.periodX * 0.7) + b.phase);
            float r = size * b.radius * breathe;
            matrix.setScale(breathe, breathe);
            matrix.postTranslate(x, y);
            b.shader.setLocalMatrix(matrix);
            blobPaint.setShader(b.shader);
            c.drawCircle(x, y, r, blobPaint);
        }
    }
}
