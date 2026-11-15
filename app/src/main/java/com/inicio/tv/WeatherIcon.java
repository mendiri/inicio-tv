package com.inicio.tv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;

/**
 * Iconos del tiempo dibujados, en tonos pastel. Sustituyen a los emojis: Android 7 no tiene todos
 * (los que faltan salen como un cuadrado).
 */
final class WeatherIcon extends View {

    static final int SUN = 0, MOON = 1, PARTLY = 2, PARTLY_NIGHT = 3, CLOUD = 4, FOG = 5,
            DRIZZLE = 6, RAIN = 7, SNOW = 8, STORM = 9;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private int type = CLOUD;

    WeatherIcon(Context ctx) {
        super(ctx);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeCap(Paint.Cap.ROUND);
        setLayerType(LAYER_TYPE_SOFTWARE, null);   // Path.op y las sombras suaves, igual en todos los Fire TV
    }

    void setType(int t) {
        if (type != t) {
            type = t;
            invalidate();
        }
    }

    @Override
    protected void onDraw(Canvas c) {
        float s = Math.min(getWidth(), getHeight());
        c.save();
        c.translate((getWidth() - s) / 2f, (getHeight() - s) / 2f);
        switch (type) {
            case SUN: sun(c, s * 0.5f, s * 0.5f, s * 0.24f); break;
            case MOON: moon(c, s * 0.5f, s * 0.5f, s * 0.28f); break;
            case PARTLY: sun(c, s * 0.62f, s * 0.36f, s * 0.17f); cloud(c, s, 0.05f, false); break;
            case PARTLY_NIGHT: moon(c, s * 0.64f, s * 0.34f, s * 0.19f); cloud(c, s, 0.05f, false); break;
            case CLOUD: cloud(c, s, 0f, false); break;
            case FOG: fog(c, s); break;
            case DRIZZLE: cloud(c, s, -0.08f, false); drops(c, s, 2); break;
            case RAIN: cloud(c, s, -0.08f, true); drops(c, s, 3); break;
            case SNOW: cloud(c, s, -0.08f, false); flakes(c, s); break;
            case STORM: cloud(c, s, -0.08f, true); bolt(c, s); break;
            default: cloud(c, s, 0f, false);
        }
        c.restore();
    }

    private void sun(Canvas c, float cx, float cy, float r) {
        line.setColor(0xFFF6B04A);
        line.setStrokeWidth(r * 0.22f);
        for (int i = 0; i < 8; i++) {
            double a = Math.PI / 4 * i;
            float x1 = cx + (float) Math.cos(a) * r * 1.35f, y1 = cy + (float) Math.sin(a) * r * 1.35f;
            float x2 = cx + (float) Math.cos(a) * r * 1.7f, y2 = cy + (float) Math.sin(a) * r * 1.7f;
            c.drawLine(x1, y1, x2, y2, line);
        }
        fill.setShader(new RadialGradient(cx - r * 0.3f, cy - r * 0.3f, r * 1.4f, 0xFFFFE08A, 0xFFF5A23C, Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, r, fill);
        fill.setShader(null);
    }

    private void moon(Canvas c, float cx, float cy, float r) {
        Path outer = new Path();
        outer.addCircle(cx, cy, r, Path.Direction.CW);
        Path bite = new Path();
        bite.addCircle(cx + r * 0.55f, cy - r * 0.35f, r * 0.85f, Path.Direction.CW);
        outer.op(bite, Path.Op.DIFFERENCE);
        fill.setShader(new LinearGradient(cx - r, cy - r, cx + r, cy + r, 0xFFFFE7A3, 0xFFF2B85C, Shader.TileMode.CLAMP));
        c.drawPath(outer, fill);
        fill.setShader(null);
    }

    /** Nube: tres círculos y una base redondeada. dy la sube (negativo) para dejar sitio a la lluvia. */
    private void cloud(Canvas c, float s, float dy, boolean dark) {
        float y = s * (0.58f + dy);
        path.reset();
        path.addCircle(s * 0.36f, y - s * 0.02f, s * 0.15f, Path.Direction.CW);
        path.addCircle(s * 0.53f, y - s * 0.11f, s * 0.20f, Path.Direction.CW);
        path.addCircle(s * 0.70f, y + s * 0.0f, s * 0.13f, Path.Direction.CW);
        path.addRoundRect(s * 0.2f, y - s * 0.02f, s * 0.84f, y + s * 0.15f, s * 0.08f, s * 0.08f, Path.Direction.CW);
        int top = dark ? 0xFFC9C6D8 : 0xFFF4F7FD;
        int bottom = dark ? 0xFF9C98B4 : 0xFFC4D9F2;
        fill.setShader(new LinearGradient(0, y - s * 0.3f, 0, y + s * 0.15f, top, bottom, Shader.TileMode.CLAMP));
        fill.setShadowLayer(s * 0.04f, 0, s * 0.02f, 0x332E2A35);
        c.drawPath(path, fill);
        fill.clearShadowLayer();
        fill.setShader(null);
    }

    private void drops(Canvas c, float s, int n) {
        line.setColor(0xFF6FA8E8);
        line.setStrokeWidth(s * 0.055f);
        for (int i = 0; i < n; i++) {
            float x = s * (n == 2 ? 0.42f + 0.18f * i : 0.36f + 0.15f * i);
            float y = s * 0.74f;
            c.drawLine(x, y, x - s * 0.04f, y + s * (n == 2 ? 0.08f : 0.13f), line);
        }
    }

    private void flakes(Canvas c, float s) {
        fill.setColor(0xFF8DB8EC);
        for (int i = 0; i < 3; i++) {
            c.drawCircle(s * (0.37f + 0.14f * i), s * (i == 1 ? 0.84f : 0.78f), s * 0.035f, fill);
        }
    }

    private void bolt(Canvas c, float s) {
        path.reset();
        path.moveTo(s * 0.54f, s * 0.64f);
        path.lineTo(s * 0.43f, s * 0.81f);
        path.lineTo(s * 0.52f, s * 0.81f);
        path.lineTo(s * 0.46f, s * 0.95f);
        path.lineTo(s * 0.63f, s * 0.75f);
        path.lineTo(s * 0.54f, s * 0.75f);
        path.lineTo(s * 0.60f, s * 0.64f);
        path.close();
        fill.setColor(0xFFF6B04A);
        c.drawPath(path, fill);
    }

    private void fog(Canvas c, float s) {
        line.setColor(0xFFB3B9CC);
        line.setStrokeWidth(s * 0.07f);
        c.drawLine(s * 0.22f, s * 0.36f, s * 0.70f, s * 0.36f, line);
        c.drawLine(s * 0.30f, s * 0.52f, s * 0.80f, s * 0.52f, line);
        c.drawLine(s * 0.20f, s * 0.68f, s * 0.64f, s * 0.68f, line);
    }
}
