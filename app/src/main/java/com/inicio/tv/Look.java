package com.inicio.tv;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.widget.TextView;

/** El aspecto común de las pantallas de Inicio: colores, fondo pastel, tarjetas y textos. */
final class Look {
    private Look() {}

    static final int TEXT = 0xFF2E2A35;
    static final int TEXT_2 = 0xFF5E5966;
    static final int FOCUS = 0xFFFFC09A;

    static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    static Typeface font(Context c, String name) {
        try {
            return Typeface.createFromAsset(c.getAssets(), "fonts/" + name + ".ttf");
        } catch (RuntimeException e) {
            return Typeface.DEFAULT;
        }
    }

    static TextView text(Context c, float sp, Typeface tf, int color) {
        TextView t = new TextView(c);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTypeface(tf);
        t.setTextColor(color);
        t.setIncludeFontPadding(false);
        return t;
    }

    static GradientDrawable rounded(Context c, int color, float radiusDp, int strokeColor, float strokeDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(c, radiusDp));
        if (strokeDp > 0) g.setStroke(Math.max(1, dp(c, strokeDp)), strokeColor);
        return g;
    }

    static StateListDrawable focusable(Drawable normal, Drawable focused) {
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_focused}, focused);
        s.addState(new int[]{}, normal);
        return s;
    }

    private static GradientDrawable blob(int color, float cx, float cy, float radiusPx) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{color, color & 0x00FFFFFF});
        g.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        g.setGradientCenter(cx, cy);
        g.setGradientRadius(radiusPx);
        return g;
    }

    /** Fondo pastel: un degradado y unas manchas de luz suaves. */
    static Drawable background(Context c) {
        DisplayMetrics m = c.getResources().getDisplayMetrics();
        float w = Math.max(m.widthPixels, m.heightPixels);
        GradientDrawable base = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFFF8EEE4, 0xFFEFDDEB, 0xFFDDE7F6});
        return new LayerDrawable(new Drawable[]{
                base,
                blob(0xB3FFC9AE, 0.40f, 0.62f, w * 0.30f),
                blob(0x99CDB8F2, 0.66f, 0.40f, w * 0.28f),
                blob(0x80B9D9F6, 0.92f, 0.78f, w * 0.30f),
                blob(0x80F8B9C9, 0.08f, 0.98f, w * 0.25f),
                blob(0x66FFFFFF, 0.12f, 0.08f, w * 0.30f),
        });
    }
}
