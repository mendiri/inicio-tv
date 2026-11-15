package com.inicio.tv;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

/** Tres reguladores: el icono del botón Personalizar. */
final class SlidersDrawable extends Drawable {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int size;

    SlidersDrawable(int color, int sizePx) {
        paint.setColor(color);
        paint.setStrokeCap(Paint.Cap.ROUND);
        size = sizePx;
    }

    @Override
    public void draw(Canvas c) {
        Rect b = getBounds();
        float w = b.width(), h = b.height();
        float stroke = h * 0.12f;
        float[] knobs = {0.68f, 0.3f, 0.55f};
        for (int i = 0; i < 3; i++) {
            float y = b.top + h * (0.2f + 0.3f * i);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(stroke);
            c.drawLine(b.left + stroke / 2, y, b.left + w - stroke / 2, y, paint);
            paint.setStyle(Paint.Style.FILL);
            c.drawCircle(b.left + w * knobs[i], y, h * 0.15f, paint);
        }
    }

    @Override public int getIntrinsicWidth() { return size; }
    @Override public int getIntrinsicHeight() { return size; }
    @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
    @Override public void setColorFilter(ColorFilter cf) { paint.setColorFilter(cf); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
