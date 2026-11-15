package com.inicio.tv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/** Gráfica pequeña de línea (consumo, temperatura...) para la tarjeta de Home Assistant. */
final class SparkView extends View {

    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private List<Float> values = new ArrayList<>();

    SparkView(Context ctx) {
        super(ctx);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeCap(Paint.Cap.ROUND);
        line.setStrokeJoin(Paint.Join.ROUND);
    }

    void set(List<Float> v) {
        values = v == null ? new ArrayList<Float>() : v;
        setVisibility(values.size() >= 2 ? VISIBLE : GONE);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        if (values.size() < 2) return;
        float w = getWidth(), h = getHeight();
        float pad = h * 0.1f;
        float min = Float.MAX_VALUE, max = -Float.MAX_VALUE;
        for (float v : values) { min = Math.min(min, v); max = Math.max(max, v); }
        if (max - min < 1e-3f) { max += 1; min -= 1; }
        line.setStrokeWidth(h / 14f);
        line.setShader(new LinearGradient(0, 0, w, 0, 0xFFF0A07A, 0xFFE0607A, Shader.TileMode.CLAMP));
        path.reset();
        int n = values.size();
        for (int i = 0; i < n; i++) {
            float x = pad + (w - 2 * pad) * i / (n - 1);
            float y = h - pad - (h - 2 * pad) * (values.get(i) - min) / (max - min);
            if (i == 0) path.moveTo(x, y); else path.lineTo(x, y);
        }
        c.drawPath(path, line);
    }
}
