package com.inicio.tv;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

/** Rueda dentada para el botón de Configuración. */
final class GearDrawable extends Drawable {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int size;

    GearDrawable(int color, int sizePx) {
        paint.setColor(color);
        size = sizePx;
    }

    @Override
    public void draw(Canvas c) {
        Rect b = getBounds();
        float cx = b.exactCenterX(), cy = b.exactCenterY();
        float r = Math.min(b.width(), b.height()) / 2f;
        float stroke = r * 0.22f;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(stroke);
        c.drawCircle(cx, cy, r * 0.58f, paint);
        c.drawCircle(cx, cy, r * 0.18f, paint);
        paint.setStrokeCap(Paint.Cap.ROUND);
        for (int i = 0; i < 8; i++) {
            c.save();
            c.rotate(i * 45f, cx, cy);
            c.drawLine(cx, cy - r * 0.72f, cx, cy - r * 0.92f, paint);
            c.restore();
        }
    }

    @Override public int getIntrinsicWidth() { return size; }
    @Override public int getIntrinsicHeight() { return size; }
    @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
    @Override public void setColorFilter(ColorFilter cf) { paint.setColorFilter(cf); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
