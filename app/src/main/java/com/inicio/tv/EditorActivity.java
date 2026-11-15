package com.inicio.tv;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Personalizar: categorías a la izquierda, sus apps a la derecha. Crear, renombrar, ordenar y borrar categorías;
 * mover apps entre categorías, reordenarlas con las flechas, ocultarlas y volver a mostrarlas.
 * Todo se guarda al momento en el config.json del Fire Stick, sin tocar el resto (tiempo, Home Assistant...).
 */
public class EditorActivity extends Activity {

    private static final class Cat {
        String name;
        final List<String> apps = new ArrayList<>();
        Cat(String name) { this.name = name; }
    }

    private static final class App {
        String pkg, label;
        Drawable icon;
    }

    /** Una fila de la columna izquierda: una sección (categoría, Otras, Ocultas) o "Nueva categoría". */
    private static final class SectionRow extends LinearLayout {
        final int section;
        SectionRow(Context c, int section) { super(c); this.section = section; }
    }

    private static final class AppRow extends LinearLayout {
        final String pkg;
        AppRow(Context c, String pkg) { super(c); this.pkg = pkg; }
    }

    private static final int NEW = -1;

    private JSONObject raw;
    private final List<Cat> cats = new ArrayList<>();
    private final List<String> hidden = new ArrayList<>();
    private final Map<String, App> apps = new LinkedHashMap<>();
    private String othersName = "Otras apps";

    private int selected;
    private String moving;
    /** Mientras se redibujan las listas el foco salta solo: no debe cambiar la sección que se ve. */
    private boolean quiet;

    private FrameLayout root;
    private View pageView;
    /** La ventana emergente abierta (con estilo del launcher), o null. */
    private View sheet;
    private View sheetReturn;

    private Typeface regular, medium;
    private LinearLayout leftList, rightList;
    private TextView rightTitle, rightHint;
    private ScrollView rightScroll;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        regular = Look.font(this, "outfit_w400");
        medium = Look.font(this, "outfit_w500");
        load();
        setContentView(buildUi());
        buildLeft();
        select(0);
        focusSection(0);
    }

    // ------------------------------------------------------------------ datos

    private void load() {
        raw = Config.loadRaw(this);
        othersName = raw.optString("nombre_otras", othersName);
        JSONArray cs = raw.optJSONArray("categorias");
        if (cs != null) {
            for (int i = 0; i < cs.length(); i++) {
                JSONObject o = cs.optJSONObject(i);
                if (o == null) continue;
                Cat c = new Cat(o.optString("nombre", "Apps"));
                JSONArray a = o.optJSONArray("apps");
                if (a != null) for (int j = 0; j < a.length(); j++) if (!c.apps.contains(a.optString(j))) c.apps.add(a.optString(j));
                cats.add(c);
            }
        }
        JSONArray h = raw.optJSONArray("ocultar");
        if (h != null) for (int i = 0; i < h.length(); i++) if (!hidden.contains(h.optString(i))) hidden.add(h.optString(i));

        PackageManager pm = getPackageManager();
        for (String cat : new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
            for (ResolveInfo ri : pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(cat), 0)) {
                String pkg = ri.activityInfo.packageName;
                if (apps.containsKey(pkg) || pkg.equals(getPackageName())) continue;
                App a = new App();
                a.pkg = pkg;
                CharSequence l = ri.loadLabel(pm);
                a.label = l == null ? pkg : l.toString();
                a.icon = ri.loadIcon(pm);
                apps.put(pkg, a);
            }
        }
    }

    private void save() {
        try {
            JSONArray cs = new JSONArray();
            for (Cat c : cats) {
                JSONObject o = new JSONObject();
                o.put("nombre", c.name);
                JSONArray a = new JSONArray();
                for (String p : c.apps) a.put(p);
                o.put("apps", a);
                cs.put(o);
            }
            raw.put("categorias", cs);
            JSONArray h = new JSONArray();
            for (String p : hidden) h.put(p);
            raw.put("ocultar", h);
            Config.saveRaw(this, raw);
        } catch (Exception e) {
            showMessage("No se pudo guardar", String.valueOf(e.getMessage()));
        }
    }

    private int othersSection() { return cats.size(); }
    private int hiddenSection() { return cats.size() + 1; }

    private boolean inAnyCategory(String pkg) {
        for (Cat c : cats) if (c.apps.contains(pkg)) return true;
        return false;
    }

    /** Las apps instaladas de una sección, en su orden. */
    private List<App> appsOf(int section) {
        List<App> list = new ArrayList<>();
        if (section < cats.size()) {
            for (String p : cats.get(section).apps) {
                App a = apps.get(p);
                if (a != null && !hidden.contains(p)) list.add(a);
            }
        } else if (section == othersSection()) {
            for (App a : apps.values()) if (!hidden.contains(a.pkg) && !inAnyCategory(a.pkg)) list.add(a);
            final Collator col = Collator.getInstance();
            Collections.sort(list, new Comparator<App>() {
                @Override
                public int compare(App x, App y) {
                    return col.compare(x.label, y.label);
                }
            });
        } else {
            for (String p : hidden) {
                App a = apps.get(p);
                if (a != null) list.add(a);
            }
        }
        return list;
    }

    private String sectionName(int section) {
        if (section < cats.size()) return cats.get(section).name;
        return section == othersSection() ? othersName : "Ocultas";
    }

    // ------------------------------------------------------------------ interfaz

    private int dp(float v) { return Look.dp(this, v); }

    private View buildUi() {
        root = new FrameLayout(this);
        root.setBackground(Look.background(this));

        LinearLayout page = new LinearLayout(this);
        pageView = page;
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(48), dp(26), dp(48), dp(24));
        root.addView(page, new FrameLayout.LayoutParams(-1, -1));

        TextView kicker = Look.text(this, 13, medium, Look.TEXT);
        kicker.setLetterSpacing(0.08f);
        kicker.setText("PERSONALIZAR");
        page.addView(kicker);
        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.BOTTOM);
        LinearLayout.LayoutParams trl = new LinearLayout.LayoutParams(-1, -2);
        trl.topMargin = dp(14);
        page.addView(titleRow, trl);
        TextView title = Look.text(this, 32, regular, Look.TEXT);
        title.setText("Apps y categorías");
        titleRow.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        TextView keys = Look.text(this, 13, regular, Look.TEXT_2);
        keys.setText("OK: opciones   ·   Atrás: volver al inicio");
        titleRow.addView(keys);
        // Actualizaciones desde GitHub
        TextView update = option("Versión " + Updater.installedVersion(this) + "  ·  Buscar actualizaciones", new Runnable() {
            @Override
            public void run() {
                checkUpdate();
            }
        });
        update.setTextSize(14);
        LinearLayout.LayoutParams ulp = new LinearLayout.LayoutParams(-2, -2);
        ulp.leftMargin = dp(22);
        titleRow.addView(update, ulp);

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-1, 0, 1);
        blp.topMargin = dp(20);
        page.addView(body, blp);

        // Izquierda: secciones
        ScrollView leftScroll = new ScrollView(this);
        leftScroll.setVerticalScrollBarEnabled(false);
        leftScroll.setClipToPadding(false);
        leftScroll.setPadding(dp(6), dp(6), dp(6), dp(6));
        body.addView(leftScroll, new LinearLayout.LayoutParams(dp(270), -1));
        leftList = new LinearLayout(this);
        leftList.setOrientation(LinearLayout.VERTICAL);
        leftScroll.addView(leftList, new FrameLayout.LayoutParams(-1, -2));

        // Derecha: tarjeta con las apps de la sección
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Look.rounded(this, 0xA6FFFFFF, 24, 0x99FFFFFF, 1));
        card.setPadding(dp(22), dp(18), dp(22), dp(10));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0, -1, 1);
        clp.leftMargin = dp(22);
        body.addView(card, clp);
        rightTitle = Look.text(this, 22, regular, Look.TEXT);
        card.addView(rightTitle);
        rightHint = Look.text(this, 13, regular, Look.TEXT_2);
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(-1, -2);
        hl.topMargin = dp(8);
        card.addView(rightHint, hl);
        rightScroll = new ScrollView(this);
        rightScroll.setVerticalScrollBarEnabled(false);
        rightScroll.setClipToPadding(false);
        rightScroll.setPadding(dp(4), dp(8), dp(4), dp(8));
        LinearLayout.LayoutParams rsl = new LinearLayout.LayoutParams(-1, 0, 1);
        rsl.topMargin = dp(6);
        card.addView(rightScroll, rsl);
        rightList = new LinearLayout(this);
        rightList.setOrientation(LinearLayout.VERTICAL);
        rightScroll.addView(rightList, new FrameLayout.LayoutParams(-1, -2));
        return root;
    }

    private void styleRow(View v, boolean highlight) {
        int stroke = highlight ? 0xFFE0607A : Look.FOCUS;
        v.setBackground(Look.focusable(
                Look.rounded(this, highlight ? 0xF7FFFFFF : 0x80FFFFFF, 16, highlight ? stroke : 0x66FFFFFF, highlight ? 2.5f : 1),
                Look.rounded(this, 0xF7FFFFFF, 16, stroke, 2.5f)));
    }

    private void buildLeft() {
        quiet = true;
        leftList.removeAllViews();
        int total = cats.size() + 2;
        for (int s = 0; s < total; s++) {
            if (s == othersSection()) {
                View gap = new View(this);
                leftList.addView(gap, new LinearLayout.LayoutParams(-1, dp(10)));
            }
            leftList.addView(sectionRow(s, sectionName(s), String.valueOf(appsOf(s).size())));
        }
        View gap = new View(this);
        leftList.addView(gap, new LinearLayout.LayoutParams(-1, dp(10)));
        leftList.addView(sectionRow(NEW, "+  Nueva categoría", ""));
        quiet = false;
    }

    private View sectionRow(final int section, String name, String count) {
        final SectionRow row = new SectionRow(this, section);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setFocusable(true);
        row.setClickable(true);
        row.setPadding(dp(16), dp(11), dp(16), dp(11));
        styleRow(row, false);
        TextView n = Look.text(this, 16, regular, section >= cats.size() || section == NEW ? Look.TEXT_2 : Look.TEXT);
        n.setText(name);
        n.setSingleLine(true);
        n.setEllipsize(TextUtils.TruncateAt.END);
        row.addView(n, new LinearLayout.LayoutParams(0, -2, 1));
        TextView c = Look.text(this, 14, medium, Look.TEXT_2);
        c.setFontFeatureSettings("tnum");
        c.setText(count);
        row.addView(c);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(8);
        row.setLayoutParams(lp);
        row.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean has) {
                if (has && !quiet && section != NEW && section != selected) select(section);
            }
        });
        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (section == NEW) newCategory();
                else if (section < cats.size()) categoryMenu(section);
                else showMessage(sectionName(section), "Esta sección no se puede renombrar ni borrar; sus apps sí se pueden mover y ocultar.");
            }
        });
        return row;
    }

    private void select(int section) {
        selected = section;
        renderRight();
    }

    private void renderRight() {
        quiet = true;
        rightList.removeAllViews();
        rightTitle.setText(sectionName(selected));
        if (selected < cats.size()) rightHint.setText("OK sobre una app: moverla con las flechas, cambiarla de categoría u ocultarla");
        else if (selected == othersSection()) rightHint.setText("Apps sin categoría, en orden alfabético. OK: añadirla a una categoría u ocultarla");
        else rightHint.setText("No aparecen en el inicio. OK: volver a mostrarla");
        List<App> list = appsOf(selected);
        if (list.isEmpty()) {
            TextView empty = Look.text(this, 14, regular, Look.TEXT_2);
            empty.setText("No hay apps aquí.");
            empty.setPadding(dp(4), dp(14), 0, 0);
            rightList.addView(empty);
        }
        for (App a : list) rightList.addView(appRow(a));
        quiet = false;
    }

    private View appRow(final App a) {
        AppRow row = new AppRow(this, a.pkg);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setFocusable(true);
        row.setClickable(true);
        row.setPadding(dp(12), dp(8), dp(16), dp(8));
        boolean isMoving = a.pkg.equals(moving);
        styleRow(row, isMoving);
        ImageView icon = new ImageView(this);
        icon.setImageDrawable(a.icon);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        row.addView(icon, new LinearLayout.LayoutParams(dp(34), dp(34)));
        TextView label = Look.text(this, 15, regular, Look.TEXT);
        label.setText(a.label);
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(0, -2, 1);
        llp.leftMargin = dp(14);
        row.addView(label, llp);
        TextView side = Look.text(this, 13, isMoving ? medium : regular, isMoving ? 0xFFE0607A : Look.TEXT_2);
        side.setText(isMoving ? "▲ ▼  moviendo · OK para terminar" : a.pkg);
        side.setSingleLine(true);
        row.addView(side);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(6);
        row.setLayoutParams(lp);
        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (moving != null) stopMoving();
                else appMenu(a);
            }
        });
        return row;
    }

    private void focusSection(int section) {
        quiet = true;
        for (int i = 0; i < leftList.getChildCount(); i++) {
            View v = leftList.getChildAt(i);
            if (v instanceof SectionRow && ((SectionRow) v).section == section) {
                v.requestFocus();
                break;
            }
        }
        quiet = false;
    }

    private void focusApp(String pkg) {
        for (int i = 0; i < rightList.getChildCount(); i++) {
            View v = rightList.getChildAt(i);
            if (v instanceof AppRow && ((AppRow) v).pkg.equals(pkg)) {
                v.requestFocus();
                return;
            }
        }
        focusSection(selected);
    }

    /** Tras un cambio: redibuja las dos columnas y deja el foco donde tenga sentido. */
    private void refresh(String focusPkg) {
        int keep = Math.min(selected, cats.size() + 1);
        buildLeft();
        select(keep);
        if (focusPkg != null) focusApp(focusPkg); else focusSection(keep);
    }

    // ------------------------------------------------------------------ ventanas emergentes

    private interface Choice {
        void chosen(int which);
    }

    private interface Answer {
        void done(String text);
    }

    /** Una pastilla de opción, como las filas del launcher. */
    private TextView option(String text, final Runnable action) {
        TextView t = Look.text(this, 16, regular, Look.TEXT);
        t.setText(text);
        t.setFocusable(true);
        t.setClickable(true);
        t.setPadding(dp(18), dp(12), dp(18), dp(12));
        t.setBackground(Look.focusable(Look.rounded(this, 0x80FFFFFF, 16, 0x66FFFFFF, 1),
                Look.rounded(this, 0xF7FFFFFF, 16, Look.FOCUS, 2.5f)));
        t.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                action.run();
            }
        });
        return t;
    }

    /** Abre una ventana con el aspecto del launcher: fondo atenuado y una tarjeta en el centro. */
    private LinearLayout openSheet(String title, String message) {
        closeSheet(false);
        sheetReturn = getCurrentFocus();
        FrameLayout dim = new FrameLayout(this);
        dim.setBackgroundColor(0x592E2A35);
        dim.setClickable(true);
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Look.rounded(this, 0xF2FBF7F6, 26, 0xCCFFFFFF, 1));
        card.setElevation(dp(16));
        card.setPadding(dp(26), dp(22), dp(26), dp(18));
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(dp(420), -2, Gravity.CENTER);
        dim.addView(card, clp);
        TextView t = Look.text(this, 22, regular, Look.TEXT);
        t.setText(title);
        card.addView(t);
        if (message != null) {
            TextView m = Look.text(this, 14, regular, Look.TEXT_2);
            m.setText(message);
            m.setLineSpacing(dp(3), 1f);
            LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(-1, -2);
            mlp.topMargin = dp(10);
            card.addView(m, mlp);
        }
        View gap = new View(this);
        card.addView(gap, new LinearLayout.LayoutParams(-1, dp(14)));
        // Lo de detrás no puede recibir el foco mientras la ventana está abierta.
        ((LinearLayout) pageView).setDescendantFocusability(LinearLayout.FOCUS_BLOCK_DESCENDANTS);
        root.addView(dim, new FrameLayout.LayoutParams(-1, -1));
        dim.setAlpha(0f);
        dim.animate().alpha(1f).setDuration(150).start();
        sheet = dim;
        return card;
    }

    private void addOption(LinearLayout card, String text, Runnable action) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(8);
        card.addView(option(text, action), lp);
    }

    /** Cierra la ventana; con restore, devuelve el foco a donde estaba. */
    private void closeSheet(boolean restore) {
        if (sheet == null) return;
        root.removeView(sheet);
        sheet = null;
        ((LinearLayout) pageView).setDescendantFocusability(LinearLayout.FOCUS_AFTER_DESCENDANTS);
        if (restore) {
            quiet = true;
            if (sheetReturn != null && sheetReturn.isAttachedToWindow()) sheetReturn.requestFocus();
            else focusSection(selected);
            quiet = false;
        }
    }

    private void showMenu(String title, String message, final String[] options, final Choice choice) {
        LinearLayout card = openSheet(title, message);
        View first = null;
        for (int i = 0; i < options.length; i++) {
            final int which = i;
            addOption(card, options[i], new Runnable() {
                @Override
                public void run() {
                    closeSheet(true);
                    choice.chosen(which);
                }
            });
            if (first == null) first = card.getChildAt(card.getChildCount() - 1);
        }
        addOption(card, "Cancelar", new Runnable() {
            @Override
            public void run() {
                closeSheet(true);
            }
        });
        if (first != null) first.requestFocus();
    }

    private void showMessage(String title, String message) {
        LinearLayout card = openSheet(title, message);
        addOption(card, "Entendido", new Runnable() {
            @Override
            public void run() {
                closeSheet(true);
            }
        });
        card.getChildAt(card.getChildCount() - 1).requestFocus();
    }

    /** Pide un texto con el teclado del Fire TV, en una ventana del launcher. */
    private void ask(String title, String initial, final Answer answer) {
        LinearLayout card = openSheet(title, null);
        final EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setImeOptions(EditorInfo.IME_ACTION_DONE);
        input.setTextSize(18);
        input.setTypeface(regular);
        input.setTextColor(Look.TEXT);
        input.setHintTextColor(Look.TEXT_2);
        input.setHint("Nombre");
        input.setPadding(dp(16), dp(12), dp(16), dp(12));
        input.setBackground(Look.focusable(Look.rounded(this, 0xFFFFFFFF, 14, 0x55B9B2C7, 1),
                Look.rounded(this, 0xFFFFFFFF, 14, Look.FOCUS, 2.5f)));
        input.setText(initial);
        input.setSelection(initial.length());
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(-1, -2);
        ilp.bottomMargin = dp(14);
        card.addView(input, ilp);
        final Runnable accept = new Runnable() {
            @Override
            public void run() {
                String t = input.getText().toString().trim();
                hideKeyboard(input);
                closeSheet(true);
                if (!t.isEmpty()) answer.done(t);
            }
        };
        input.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                accept.run();
                return true;
            }
        });
        addOption(card, "Aceptar", accept);
        addOption(card, "Cancelar", new Runnable() {
            @Override
            public void run() {
                hideKeyboard(input);
                closeSheet(true);
            }
        });
        input.requestFocus();
        input.postDelayed(new Runnable() {
            @Override
            public void run() {
                android.view.inputmethod.InputMethodManager imm =
                        (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                if (imm != null) imm.showSoftInput(input, 0);
            }
        }, 200);
    }

    private void hideKeyboard(View v) {
        android.view.inputmethod.InputMethodManager imm =
                (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
    }

    // ------------------------------------------------------------------ actualizaciones

    private final android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());

    private void checkUpdate() {
        final String repo = Config.load(this).updateRepo;
        if (repo.isEmpty()) {
            showMessage("Actualizaciones", "Añade en config.json el repositorio de GitHub de Inicio:\n"
                    + "\"actualizaciones\": { \"repo\": \"usuario/inicio-tv\" }");
            return;
        }
        openSheet("Buscando actualizaciones…", "Consultando " + repo + " en GitHub.");
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final Updater.Release r = Updater.latest(repo);
                    main.post(new Runnable() {
                        @Override
                        public void run() {
                            offerUpdate(r);
                        }
                    });
                } catch (final Exception e) {
                    main.post(new Runnable() {
                        @Override
                        public void run() {
                            showMessage("No se pudo comprobar", String.valueOf(e.getMessage()));
                        }
                    });
                }
            }
        }).start();
    }

    private void offerUpdate(final Updater.Release r) {
        String mine = Updater.installedVersion(this);
        if (!Updater.isNewer(r.version, mine)) {
            showMessage("Inicio está al día", "Tienes la versión " + mine + ", la última publicada.");
            return;
        }
        String notes = r.notes.length() > 400 ? r.notes.substring(0, 400) + "…" : r.notes;
        showMenu("Nueva versión " + r.version, (notes.isEmpty() ? "" : notes + "\n\n") + "Tienes la " + mine + ".",
                new String[]{"Descargar e instalar"}, new Choice() {
                    @Override
                    public void chosen(int which) {
                        downloadAndInstall(r);
                    }
                });
    }

    private void downloadAndInstall(final Updater.Release r) {
        openSheet("Descargando " + r.version + "…", "Después se abrirá el instalador del Fire TV: pulsa Instalar.");
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Updater.download(EditorActivity.this, r);
                    main.post(new Runnable() {
                        @Override
                        public void run() {
                            closeSheet(true);
                            try {
                                Updater.install(EditorActivity.this);
                            } catch (RuntimeException e) {
                                showMessage("No se pudo abrir el instalador", String.valueOf(e.getMessage()));
                            }
                        }
                    });
                } catch (final Exception e) {
                    main.post(new Runnable() {
                        @Override
                        public void run() {
                            showMessage("No se pudo descargar", String.valueOf(e.getMessage()));
                        }
                    });
                }
            }
        }).start();
    }

    // ------------------------------------------------------------------ categorías

    private void categoryMenu(final int i) {
        final Cat c = cats.get(i);
        showMenu(c.name, null, new String[]{"Renombrar", "Subir", "Bajar", "Eliminar"}, new Choice() {
            @Override
            public void chosen(int which) {
                if (which == 0) {
                    ask("Renombrar categoría", c.name, new Answer() {
                        @Override
                        public void done(String name) {
                            c.name = name;
                            save();
                            refresh(null);
                        }
                    });
                } else if (which == 1 && i > 0) {
                    Collections.swap(cats, i, i - 1);
                    save();
                    selected = i - 1;
                    refresh(null);
                } else if (which == 2 && i < cats.size() - 1) {
                    Collections.swap(cats, i, i + 1);
                    save();
                    selected = i + 1;
                    refresh(null);
                } else if (which == 3) {
                    confirmDelete(i);
                }
            }
        });
    }

    private void confirmDelete(final int i) {
        showMenu("Eliminar \"" + cats.get(i).name + "\"", "Sus apps pasarán a " + othersName + ".",
                new String[]{"Eliminar"}, new Choice() {
                    @Override
                    public void chosen(int which) {
                        cats.remove(i);
                        save();
                        selected = Math.max(0, i - 1);
                        refresh(null);
                    }
                });
    }

    private void newCategory() {
        ask("Nueva categoría", "", new Answer() {
            @Override
            public void done(String name) {
                cats.add(new Cat(name));
                save();
                selected = cats.size() - 1;
                refresh(null);
            }
        });
    }

    // ------------------------------------------------------------------ apps

    private void appMenu(final App a) {
        final List<String> options = new ArrayList<>();
        if (selected < cats.size()) {
            options.add("Mover con las flechas");
            options.add("Cambiar de categoría…");
            options.add("Ocultar");
        } else if (selected == othersSection()) {
            options.add("Añadir a una categoría…");
            options.add("Ocultar");
        } else {
            options.add("Mostrar");
        }
        showMenu(a.label, null, options.toArray(new String[0]), new Choice() {
            @Override
            public void chosen(int which) {
                String o = options.get(which);
                if (o.startsWith("Mover")) startMoving(a.pkg);
                else if (o.endsWith("…")) chooseCategory(a);
                else if (o.equals("Ocultar")) hide(a.pkg);
                else unhide(a.pkg);
            }
        });
    }

    private void chooseCategory(final App a) {
        final List<String> names = new ArrayList<>();
        for (Cat c : cats) names.add(c.name);
        names.add(othersName);
        showMenu("Mover \"" + a.label + "\" a", null, names.toArray(new String[0]), new Choice() {
            @Override
            public void chosen(int which) {
                for (Cat c : cats) c.apps.remove(a.pkg);
                if (which < cats.size()) cats.get(which).apps.add(a.pkg);
                save();
                refresh(null);
            }
        });
    }

    private void hide(String pkg) {
        for (Cat c : cats) c.apps.remove(pkg);
        if (!hidden.contains(pkg)) hidden.add(pkg);
        save();
        refresh(null);
    }

    private void unhide(String pkg) {
        hidden.remove(pkg);
        save();
        refresh(null);
    }

    private void startMoving(String pkg) {
        moving = pkg;
        renderRight();
        focusApp(pkg);
    }

    private void stopMoving() {
        String pkg = moving;
        moving = null;
        save();
        renderRight();
        focusApp(pkg);
    }

    /** Intercambia la app con la vecina instalada (arriba o abajo) dentro de su categoría. */
    private void move(int dir) {
        List<String> list = cats.get(selected).apps;
        int i = list.indexOf(moving);
        if (i < 0) return;
        int j = i + dir;
        while (j >= 0 && j < list.size() && (!apps.containsKey(list.get(j)) || hidden.contains(list.get(j)))) j += dir;
        if (j < 0 || j >= list.size()) return;
        Collections.swap(list, i, j);
        renderRight();
        focusApp(moving);
    }

    // ------------------------------------------------------------------ teclas

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        int key = e.getKeyCode();
        if (sheet != null) {
            if (key == KeyEvent.KEYCODE_BACK) {
                if (e.getAction() == KeyEvent.ACTION_UP) closeSheet(true);
                return true;
            }
            return super.dispatchKeyEvent(e);
        }
        if (moving != null) {
            if (e.getAction() != KeyEvent.ACTION_DOWN) return true;
            switch (key) {
                case KeyEvent.KEYCODE_DPAD_UP: move(-1); return true;
                case KeyEvent.KEYCODE_DPAD_DOWN: move(1); return true;
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                case KeyEvent.KEYCODE_BACK:
                case KeyEvent.KEYCODE_DPAD_LEFT:
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    stopMoving();
                    return true;
                default:
                    return true;
            }
        }
        if (e.getAction() == KeyEvent.ACTION_DOWN) {
            View f = getCurrentFocus();
            if (key == KeyEvent.KEYCODE_DPAD_RIGHT && f instanceof SectionRow) {
                for (int i = 0; i < rightList.getChildCount(); i++) {
                    if (rightList.getChildAt(i) instanceof AppRow) {
                        rightList.getChildAt(i).requestFocus();
                        return true;
                    }
                }
                return true;
            }
            if (key == KeyEvent.KEYCODE_DPAD_LEFT && f instanceof AppRow) {
                focusSection(selected);
                return true;
            }
            if (key == KeyEvent.KEYCODE_DPAD_RIGHT && f instanceof AppRow) return true;
        }
        return super.dispatchKeyEvent(e);
    }
}
