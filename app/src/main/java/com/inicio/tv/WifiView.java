package com.inicio.tv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/** Icono de Wi-Fi dibujado: 0 a 4 barras, o tachado sin red. */
final class WifiView extends View {

    private final Paint on = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint off = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval = new RectF();
    private int level = 4;
    private boolean connected = true;

    WifiView(Context ctx, int color) {
        super(ctx);
        on.setColor(color);
        on.setStyle(Paint.Style.STROKE);
        on.setStrokeCap(Paint.Cap.ROUND);
        off.set(on);
        off.setAlpha(70);
    }

    void set(boolean connected, int level) {
        if (this.connected == connected && this.level == level) return;
        this.connected = connected;
        this.level = level;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        float stroke = w / 10f;
        on.setStrokeWidth(stroke);
        off.setStrokeWidth(stroke);
        float cx = w / 2f, cy = h * 0.88f;
        // Punto central
        Paint dot = connected && level >= 1 ? on : off;
        dot.setStyle(Paint.Style.FILL);
        c.drawCircle(cx, cy - stroke * 0.2f, stroke * 0.9f, dot);
        dot.setStyle(Paint.Style.STROKE);
        // Tres arcos
        for (int i = 1; i <= 3; i++) {
            float r = (h * 0.78f) * i / 3f;
            oval.set(cx - r, cy - r, cx + r, cy + r);
            Paint p = connected && level >= i + 1 ? on : off;
            c.drawArc(oval, 225, 90, false, p);
        }
        if (!connected) {
            c.drawLine(w * 0.15f, h * 0.15f, w * 0.85f, h * 0.9f, on);
        }
    }
}
