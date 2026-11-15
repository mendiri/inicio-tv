package com.inicio.tv;

import android.content.Context;
import android.graphics.Rect;
import android.widget.HorizontalScrollView;
import android.widget.ScrollView;

/** Contenedores que no se desplazan solos al cambiar el foco: MainActivity decide cuánto, por columnas y filas. */
final class Scrollers {
    private Scrollers() {}

    static final class Row extends HorizontalScrollView {
        Row(Context ctx) { super(ctx); }

        @Override
        protected int computeScrollDeltaToGetChildRectOnScreen(Rect rect) { return 0; }

        // Solo se difumina el borde derecho (la app que asoma); el izquierdo cortaría la primera columna.
        @Override
        protected float getLeftFadingEdgeStrength() { return 0f; }
    }

    static final class Rows extends ScrollView {
        Rows(Context ctx) { super(ctx); }

        @Override
        protected int computeScrollDeltaToGetChildRectOnScreen(Rect rect) { return 0; }

        @Override
        protected float getTopFadingEdgeStrength() { return 0f; }
    }
}
