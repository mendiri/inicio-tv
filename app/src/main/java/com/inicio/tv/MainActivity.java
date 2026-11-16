package com.inicio.tv;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** La pantalla de inicio: saludo, reloj, red, tiempo, Home Assistant y apps por categoría. */
public class MainActivity extends Activity {

    static final String ACTION_HOME_PRESSED = "com.inicio.tv.HOME_PRESSED";

    private static final int TEXT = 0xFF2E2A35;
    private static final int TEXT_2 = 0xFF5E5966;
    private static final long WEATHER_EVERY = 30 * 60_000L;
    private static final long HA_EVERY = 60_000L;
    private static final int TILE = 104;
    /** Horóscopo: altura de cada línea (dp). El ritmo de lectura está en config.json. */
    private static final int HORO_LINE = 23;
    private static final int TILE_GAP = 14;
    /** Margen izquierdo de cada fila: menor que el hueco entre apps, para que no asome la anterior. */
    private static final int ROW_PAD = 11;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private SharedPreferences prefs;
    private Config cfg;
    private long cfgStamp = -1;
    private Set<String> lastPackages = new HashSet<>();
    private float density;
    private Typeface light, regular, medium;

    // Cabecera
    private TextView greetingView, settingsButton, customizeButton;
    private AmbientView ambient;
    // Horóscopo: se alterna con el saludo
    private View horoView;
    private TextView horoLabel, horoText;
    private ScrollView horoScroll;
    private Runnable horoReading;
    private final List<Horoscope> horoscopes = new ArrayList<>();
    private String horoscopeDay = "";
    private long horoscopeTriedAt;
    private int greetStep;
    /** Solo mientras Inicio está en pantalla avanza el ciclo del saludo. */
    private boolean resumed;
    /**
     * Turno del ciclo del saludo. Cada reinicio (volver a Inicio, recargar la configuración) lo incrementa, y todo lo
     * que quedara pendiente de un turno anterior se descarta: así nunca hay dos ciclos a la vez acortando las pausas.
     */
    private int greetTurn;
    private WifiView wifi;
    // Tiempo
    private View weatherCardView, haCardView;
    private TextView wTitle, wPlace, wTemp, wDesc, wMinMax, wRain;
    private WeatherIcon wIcon;
    private View wNowPage;
    private LinearLayout wWeekPage;
    private boolean showingWeek;
    private long weatherAt;
    // Home Assistant
    private TextView haTitleView, haHint;
    // La tarjeta de abajo alterna Home Assistant, fútbol de hoy y resultados de ayer
    private TextView cardIcon, cardName;
    private View haPage;
    private FrameLayout cardFrame;
    private long updateCheckedAt;
    /** Páginas de fútbol: las de hoy y las de ayer, de FOOT_ROWS partidos cada una. */
    private final List<View> footPages = new ArrayList<>();
    private final java.util.Map<View, String> pageTitles = new java.util.HashMap<>();
    private final List<View> cardPages = new ArrayList<>();
    private int cardIndex;
    private Football football;
    private long footballAt;
    private LinearLayout haCol1, haCol2;
    private SparkView spark;
    // Apps
    private View cardsView;
    private ScrollView rowsScroll;
    private LinearLayout rowsBox;
    private final List<Row> rows = new ArrayList<>();

    private static final class App {
        String pkg, label;
        Drawable icon;
        Intent intent;
    }

    private static final class Row {
        View container;
        HorizontalScrollView scroller;
        LinearLayout strip;
        int last;
        /** Primera columna visible: la fila se desplaza de columna en columna. */
        int first;
    }

    /** Un cuadro de app; sabe en qué fila y posición está. */
    private final class Tile extends LinearLayout {
        final int row, index;
        String pkg;

        Tile(Context ctx, int row, int index) {
            super(ctx);
            this.row = row;
            this.index = index;
        }
    }

    // ------------------------------------------------------------------ ciclo de vida

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        density = getResources().getDisplayMetrics().density;
        prefs = getSharedPreferences("inicio", MODE_PRIVATE);
        light = font("outfit_w300");
        regular = font("outfit_w400");
        medium = font("outfit_w500");
        setContentView(buildUi());
        loadConfig();
        rebuildRows();

        IntentFilter pkgs = new IntentFilter();
        pkgs.addAction(Intent.ACTION_PACKAGE_ADDED);
        pkgs.addAction(Intent.ACTION_PACKAGE_REMOVED);
        pkgs.addAction(Intent.ACTION_PACKAGE_CHANGED);
        pkgs.addDataScheme("package");
        registerReceiver(packagesChanged, pkgs);
        registerReceiver(homePressed, new IntentFilter(ACTION_HOME_PRESSED));

        FireHomeWatcher.startIfGranted(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (configChangedOnDisk()) {
            loadConfig();
            rebuildRows();
            weatherAt = 0;
        } else if (!installedPackages().equals(lastPackages)) {
            rebuildRows();
        }
        IntentFilter net = new IntentFilter();
        net.addAction(WifiManager.RSSI_CHANGED_ACTION);
        net.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        net.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        registerReceiver(networkChanged, net);
        updateWifi();
        ambient.start();
        ui.post(refresher);
        ui.removeCallbacks(weatherCycle);
        ui.postDelayed(weatherCycle, Math.max(1, cfg.weatherCycleSeconds) * 1000L);
        resumed = true;
        restartGreeting();
        ui.removeCallbacks(cardCycle);
        ui.postDelayed(cardCycle, cfg.cardCycleSeconds * 1000L);
    }

    @Override
    protected void onPause() {
        super.onPause();
        ui.removeCallbacks(refresher);
        ui.removeCallbacks(weatherCycle);
        resumed = false;
        ambient.stop();
        greetTurn++;
        ui.removeCallbacks(cardCycle);
        stopReading();
        try { unregisterReceiver(networkChanged); } catch (IllegalArgumentException ignored) { }
    }

    @Override
    protected void onDestroy() {
        try { unregisterReceiver(packagesChanged); } catch (IllegalArgumentException ignored) { }
        try { unregisterReceiver(homePressed); } catch (IllegalArgumentException ignored) { }
        io.shutdownNow();
        super.onDestroy();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        goHome();
    }

    @Override
    public void onBackPressed() {
        // Es la pantalla de inicio: Atrás no sale, vuelve al principio.
        goHome();
    }

    private void goHome() {
        if (!rows.isEmpty()) {
            for (Row r : rows) {
                r.last = 0;
                r.first = 0;
                r.scroller.scrollTo(0, 0);
            }
            focusRow(0);
        } else {
            settingsButton.requestFocus();
        }
    }

    private final BroadcastReceiver packagesChanged = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            rebuildRows();
        }
    };

    private final BroadcastReceiver homePressed = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            goHome();
        }
    };

    private final BroadcastReceiver networkChanged = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            updateWifi();
        }
    };

    private final Runnable refresher = new Runnable() {
        @Override
        public void run() {
            if (cfg.weatherShow && System.currentTimeMillis() - weatherAt > WEATHER_EVERY) refreshWeather();
            if (cfg.haShow) refreshHa();
            refreshFootball();
            checkForUpdate();
            refreshHoroscopes();
            ui.postDelayed(this, HA_EVERY);
        }
    };

    // ------------------------------------------------------------------ configuración

    private boolean configChangedOnDisk() {
        File f = Config.userFile(this);
        long stamp = f != null && f.isFile() ? f.lastModified() : 0;
        return stamp != cfgStamp;
    }

    private void loadConfig() {
        File f = Config.userFile(this);
        cfgStamp = f != null && f.isFile() ? f.lastModified() : 0;
        cfg = Config.load(this);
        if (Config.lastError != null) {
            // Sin esto, un fallo de formato pasa desapercibido: Inicio usaría la configuración de fábrica.
            Toast.makeText(this, "config.json tiene un error (" + Config.lastError
                    + "). Se usa la configuración de fábrica.", Toast.LENGTH_LONG).show();
        }
        greetingView.setText(cfg.greeting);
        ambient.setSpeed(cfg.backgroundSpeed);
        if (resumed) ambient.start();
        horoscopes.clear();
        horoscopeDay = "";
        horoscopeTriedAt = 0;
        greetStep = 0;
        greetingView.animate().cancel();
        horoView.animate().cancel();
        stopReading();
        greetingView.setAlpha(1f);
        greetingView.setVisibility(View.VISIBLE);
        horoView.setVisibility(View.INVISIBLE);
        if (resumed) restartGreeting();
        haTitleView.setText(cfg.haTitle);
        weatherCardView.setVisibility(cfg.weatherShow ? View.VISIBLE : View.GONE);
        haCardView.setVisibility(cfg.haShow || cfg.footballShow ? View.VISIBLE : View.GONE);
        cardsView.setVisibility(cfg.weatherShow || cfg.haShow || cfg.footballShow ? View.VISIBLE : View.GONE);
        football = null;
        footballAt = 0;
        updateCheckedAt = 0;
        buildFootballPages(null, null);
        resetCardPages();
    }

    // ------------------------------------------------------------------ interfaz

    private int dp(float v) {
        return Math.round(v * density);
    }

    private Typeface font(String name) {
        try {
            return Typeface.createFromAsset(getAssets(), "fonts/" + name + ".ttf");
        } catch (RuntimeException e) {
            return Typeface.DEFAULT;
        }
    }

    private TextView text(float sp, Typeface tf, int color) {
        TextView t = new TextView(this);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTypeface(tf);
        t.setTextColor(color);
        t.setIncludeFontPadding(false);
        return t;
    }

    private GradientDrawable rounded(int color, float radiusDp, int strokeColor, float strokeDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        if (strokeDp > 0) g.setStroke(Math.max(1, dp(strokeDp)), strokeColor);
        return g;
    }

    private View buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setClipChildren(false);
        ambient = new AmbientView(this);
        root.addView(ambient, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(48), dp(26), dp(40), 0);
        page.setClipChildren(false);
        page.setClipToPadding(false);
        root.addView(page, new FrameLayout.LayoutParams(-1, -1));

        // --- Cabecera
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setClipChildren(false);
        page.addView(header, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        header.addView(left, new LinearLayout.LayoutParams(0, -2, 1));
        // El saludo y el horóscopo comparten sitio y se alternan con un fundido. Ocupan toda la altura de la
        // cabecera: el horóscopo muestra tres líneas.
        FrameLayout greetBox = new FrameLayout(this);
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(-1, dp(96));
        glp.rightMargin = dp(28);
        left.addView(greetBox, glp);
        greetingView = text(38, regular, TEXT);
        greetBox.addView(greetingView, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER_VERTICAL));
        // Horóscopo: una etiqueta y una ventana de dos líneas por la que el texto sube a ritmo de lectura.
        LinearLayout horo = new LinearLayout(this);
        horo.setOrientation(LinearLayout.VERTICAL);
        horo.setVisibility(View.INVISIBLE);
        greetBox.addView(horo, new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER_VERTICAL));
        horoView = horo;
        horoLabel = text(12, medium, 0xFFD0705A);
        horoLabel.setLetterSpacing(0.1f);
        horo.addView(horoLabel);
        horoScroll = new ScrollView(this);
        horoScroll.setVerticalScrollBarEnabled(false);
        horoScroll.setFocusable(false);
        horoScroll.setVerticalFadingEdgeEnabled(true);
        horoScroll.setFadingEdgeLength(dp(10));
        LinearLayout.LayoutParams hsl = new LinearLayout.LayoutParams(-1, dp(HORO_LINE * 3));
        hsl.topMargin = dp(6);
        horo.addView(horoScroll, hsl);
        horoText = text(17, regular, TEXT);
        // Altura de línea fija: así la ventana muestra exactamente dos líneas.
        horoText.setLineSpacing(dp(HORO_LINE) - horoText.getPaint().getFontMetricsInt(null), 1f);
        horoScroll.addView(horoText, new FrameLayout.LayoutParams(-1, -2));

        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setGravity(Gravity.END);
        right.setClipChildren(false);
        header.addView(right, new LinearLayout.LayoutParams(-2, -2));

        LinearLayout status = new LinearLayout(this);
        status.setOrientation(LinearLayout.HORIZONTAL);
        status.setGravity(Gravity.CENTER_VERTICAL);
        right.addView(status, new LinearLayout.LayoutParams(-2, -2));
        wifi = new WifiView(this, TEXT);
        status.addView(wifi, new LinearLayout.LayoutParams(dp(20), dp(17)));
        TextClock clock = new TextClock(this);
        clock.setFormat24Hour("HH:mm");
        clock.setFormat12Hour("HH:mm");
        clock.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        clock.setTypeface(regular);
        clock.setTextColor(TEXT);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-2, -2);
        clp.leftMargin = dp(14);
        status.addView(clock, clp);

        settingsButton = text(15, regular, TEXT);
        settingsButton.setText("Configuración");
        settingsButton.setGravity(Gravity.CENTER_VERTICAL);
        settingsButton.setPadding(dp(18), dp(9), dp(14), dp(9));
        GearDrawable gear = new GearDrawable(TEXT, dp(16));
        gear.setBounds(0, 0, dp(16), dp(16));
        settingsButton.setCompoundDrawables(null, null, gear, null);
        settingsButton.setCompoundDrawablePadding(dp(10));
        settingsButton.setBackground(focusable(rounded(0x8CFFFFFF, 22, 0x80FFFFFF, 1),
                rounded(0xF7FFFFFF, 22, 0xFFFFC09A, 2.5f)));
        settingsButton.setFocusable(true);
        settingsButton.setClickable(true);
        settingsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openSettings();
            }
        });
        settingsButton.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                loadConfig();
                rebuildRows();
                weatherAt = 0;
                ui.removeCallbacks(refresher);
                ui.post(refresher);
                Toast.makeText(MainActivity.this, "Configuración recargada", Toast.LENGTH_SHORT).show();
                return true;
            }
        });
        settingsButton.setOnFocusChangeListener(grow(1.06f));

        // Personalizar (categorías, orden, ocultar), a la izquierda de Configuración.
        customizeButton = text(15, regular, TEXT);
        customizeButton.setText("Personalizar");
        customizeButton.setGravity(Gravity.CENTER_VERTICAL);
        customizeButton.setPadding(dp(18), dp(9), dp(14), dp(9));
        SlidersDrawable sliders = new SlidersDrawable(TEXT, dp(16));
        sliders.setBounds(0, 0, dp(16), dp(16));
        customizeButton.setCompoundDrawables(null, null, sliders, null);
        customizeButton.setCompoundDrawablePadding(dp(10));
        customizeButton.setBackground(focusable(rounded(0x8CFFFFFF, 22, 0x80FFFFFF, 1),
                rounded(0xF7FFFFFF, 22, 0xFFFFC09A, 2.5f)));
        customizeButton.setFocusable(true);
        customizeButton.setClickable(true);
        customizeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, EditorActivity.class));
            }
        });
        customizeButton.setOnFocusChangeListener(grow(1.06f));

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setClipChildren(false);
        buttons.addView(customizeButton);
        LinearLayout.LayoutParams sblp = new LinearLayout.LayoutParams(-2, -2);
        sblp.leftMargin = dp(14);
        buttons.addView(settingsButton, sblp);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-2, -2);
        slp.topMargin = dp(16);
        right.addView(buttons, slp);

        // --- Cuerpo: tarjetas a la izquierda, apps a la derecha
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-1, 0, 1);
        blp.topMargin = dp(22);
        page.addView(body, blp);

        LinearLayout cards = new LinearLayout(this);
        cards.setOrientation(LinearLayout.VERTICAL);
        body.addView(cards, new LinearLayout.LayoutParams(dp(290), -1));
        weatherCardView = weatherCard();
        LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(-1, -2);
        wlp.bottomMargin = dp(16);
        cards.addView(weatherCardView, wlp);
        haCardView = haCard();
        cards.addView(haCardView, new LinearLayout.LayoutParams(-1, -2));
        cardsView = cards;

        rowsScroll = new Scrollers.Rows(this);
        rowsScroll.setClipToPadding(false);
        rowsScroll.setVerticalScrollBarEnabled(false);
        rowsScroll.setVerticalFadingEdgeEnabled(true);
        rowsScroll.setFadingEdgeLength(dp(36));
        rowsScroll.setPadding(0, 0, 0, dp(40));
        rowsScroll.setFocusable(false);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(0, -1, 1);
        rlp.leftMargin = dp(12);
        body.addView(rowsScroll, rlp);
        rowsBox = new LinearLayout(this);
        rowsBox.setOrientation(LinearLayout.VERTICAL);
        rowsBox.setClipChildren(false);
        rowsScroll.addView(rowsBox, new FrameLayout.LayoutParams(-1, -2));

        return root;
    }

    private StateListDrawable focusable(Drawable normal, Drawable focused) {
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_focused}, focused);
        s.addState(new int[]{}, normal);
        return s;
    }

    private View.OnFocusChangeListener grow(final float scale) {
        return new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean has) {
                v.animate().scaleX(has ? scale : 1f).scaleY(has ? scale : 1f)
                        .translationZ(has ? dp(10) : 0).setDuration(150).start();
            }
        };
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(rounded(0xA6FFFFFF, 24, 0x99FFFFFF, 1));
        c.setPadding(dp(22), dp(18), dp(20), dp(18));
        return c;
    }

    private View weatherCard() {
        LinearLayout c = card();
        wTitle = text(22, regular, TEXT);
        wTitle.setText("El Tiempo");
        c.addView(wTitle);

        // Dos páginas en el mismo sitio: el tiempo de ahora y los próximos 7 días, que se alternan.
        FrameLayout pages = new FrameLayout(this);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(-1, -2);
        plp.topMargin = dp(10);
        c.addView(pages, plp);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        pages.addView(row, new FrameLayout.LayoutParams(-1, -2));
        wNowPage = row;

        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        row.addView(l, new LinearLayout.LayoutParams(0, -2, 1));
        wPlace = text(16, regular, TEXT);
        l.addView(wPlace);
        wTemp = text(42, regular, TEXT);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-2, -2);
        tlp.topMargin = dp(6);
        l.addView(wTemp, tlp);
        wMinMax = text(12, regular, TEXT_2);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(-2, -2);
        mlp.topMargin = dp(6);
        l.addView(wMinMax, mlp);
        wRain = text(12, regular, TEXT_2);
        wRain.setFontFeatureSettings("tnum");
        LinearLayout.LayoutParams rnl = new LinearLayout.LayoutParams(-2, -2);
        rnl.topMargin = dp(5);
        l.addView(wRain, rnl);

        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.VERTICAL);
        r.setGravity(Gravity.CENTER_HORIZONTAL);
        row.addView(r, new LinearLayout.LayoutParams(-2, -2));
        wIcon = new WeatherIcon(this);
        r.addView(wIcon, new LinearLayout.LayoutParams(dp(78), dp(64)));
        wDesc = text(13, regular, TEXT);
        wDesc.setGravity(Gravity.CENTER);
        wDesc.setMaxWidth(dp(130));
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(-2, -2);
        dlp.topMargin = dp(4);
        r.addView(wDesc, dlp);

        wWeekPage = new LinearLayout(this);
        wWeekPage.setOrientation(LinearLayout.HORIZONTAL);
        wWeekPage.setGravity(Gravity.CENTER_VERTICAL);
        wWeekPage.setVisibility(View.INVISIBLE);
        pages.addView(wWeekPage, new FrameLayout.LayoutParams(-1, -1));

        wPlace.setText(" ");
        wTemp.setText("--°C");
        wDesc.setText("Cargando…");
        return c;
    }

    private void fillWeek(List<Weather.Day> days) {
        wWeekPage.removeAllViews();
        for (Weather.Day d : days) {
            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setGravity(Gravity.CENTER_HORIZONTAL);
            wWeekPage.addView(col, new LinearLayout.LayoutParams(0, -2, 1));
            // Todo centrado sobre el mismo eje: nombre, icono, máxima y mínima.
            TextView name = text(13, regular, TEXT_2);
            name.setText(d.name);
            name.setGravity(Gravity.CENTER_HORIZONTAL);
            col.addView(name, new LinearLayout.LayoutParams(-1, -2));
            WeatherIcon icon = new WeatherIcon(this);
            icon.setType(d.icon());
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(34), dp(32));
            ilp.gravity = Gravity.CENTER_HORIZONTAL;
            ilp.topMargin = dp(6);
            ilp.bottomMargin = dp(6);
            col.addView(icon, ilp);
            TextView max = text(14, medium, TEXT);
            max.setFontFeatureSettings("tnum");
            max.setText(d.max + "°");
            max.setGravity(Gravity.CENTER_HORIZONTAL);
            col.addView(max, new LinearLayout.LayoutParams(-1, -2));
            TextView min = text(12, regular, TEXT_2);
            min.setFontFeatureSettings("tnum");
            min.setText(d.min + "°");
            min.setGravity(Gravity.CENTER_HORIZONTAL);
            LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(-1, -2);
            nlp.topMargin = dp(4);
            col.addView(min, nlp);
        }
    }

    /** Alterna la tarjeta del tiempo entre ahora y la semana, con un fundido. */
    private final Runnable weatherCycle = new Runnable() {
        @Override
        public void run() {
            if (cfg.weatherCycleSeconds <= 0) return;
            if (wWeekPage.getChildCount() > 0 || showingWeek) {
                showingWeek = !showingWeek;
                View in = showingWeek ? wWeekPage : wNowPage;
                final View out = showingWeek ? wNowPage : wWeekPage;
                in.setAlpha(0f);
                in.setVisibility(View.VISIBLE);
                in.animate().alpha(1f).setDuration(450).start();
                out.animate().alpha(0f).setDuration(450).withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        out.setVisibility(View.INVISIBLE);
                    }
                }).start();
                wTitle.setText(showingWeek ? "Próximos días" : "El Tiempo");
            }
            ui.postDelayed(this, cfg.weatherCycleSeconds * 1000L);
        }
    };

    private View haCard() {
        LinearLayout c = card();
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        c.addView(head);
        cardIcon = text(18, regular, TEXT);
        cardIcon.setText("🏠");
        head.addView(cardIcon);
        cardName = text(20, regular, TEXT);
        cardName.setText("Home Assistant");
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(-2, -2);
        nlp.leftMargin = dp(10);
        head.addView(cardName, nlp);

        // Tres páginas en el mismo sitio, que se alternan como las del tiempo.
        FrameLayout pages = new FrameLayout(this);
        pages.setMinimumHeight(dp(112));
        c.addView(pages, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout ha = new LinearLayout(this);
        ha.setOrientation(LinearLayout.VERTICAL);
        pages.addView(ha, new FrameLayout.LayoutParams(-1, -2));
        haPage = ha;

        LinearLayout sub = new LinearLayout(this);
        sub.setOrientation(LinearLayout.HORIZONTAL);
        sub.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams sublp = new LinearLayout.LayoutParams(-1, -2);
        sublp.topMargin = dp(10);
        ha.addView(sub, sublp);
        haTitleView = text(13, regular, TEXT);
        sub.addView(haTitleView, new LinearLayout.LayoutParams(0, -2, 1));
        spark = new SparkView(this);
        spark.setVisibility(View.GONE);
        sub.addView(spark, new LinearLayout.LayoutParams(dp(110), dp(26)));

        LinearLayout cols = new LinearLayout(this);
        cols.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-1, -2);
        clp.topMargin = dp(4);
        ha.addView(cols, clp);
        haCol1 = new LinearLayout(this);
        haCol1.setOrientation(LinearLayout.VERTICAL);
        cols.addView(haCol1, new LinearLayout.LayoutParams(0, -2, 1));
        haCol2 = new LinearLayout(this);
        haCol2.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams c2 = new LinearLayout.LayoutParams(0, -2, 1);
        c2.leftMargin = dp(8);
        cols.addView(haCol2, c2);

        haHint = text(12, regular, TEXT_2);
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(-1, -2);
        hl.topMargin = dp(6);
        ha.addView(haHint, hl);

        cardFrame = pages;
        return c;
    }

    private LinearLayout footPage(String title) {
        LinearLayout p = new LinearLayout(this);
        p.setOrientation(LinearLayout.VERTICAL);
        p.setPadding(0, dp(8), 0, 0);
        p.setVisibility(View.INVISIBLE);
        cardFrame.addView(p, new FrameLayout.LayoutParams(-1, -1));
        footPages.add(p);
        pageTitles.put(p, title);
        return p;
    }

    private static final int FOOT_ROWS = 5;

    /**
     * Rehace las páginas de fútbol: hoy y ayer, de FOOT_ROWS partidos por página ("Fútbol hoy · 1/2"), para que
     * salgan todos en vez de un "+4 más". Sin datos, una página por día con el aviso (cargando o el error).
     */
    private void buildFootballPages(Football f, String problem) {
        for (View v : footPages) {
            v.animate().cancel();
            cardFrame.removeView(v);
            pageTitles.remove(v);
        }
        footPages.clear();
        addDayPages("Fútbol hoy", f == null ? null : f.today, problem, "Hoy no hay partidos de tus ligas.");
        addDayPages("Resultados de ayer", f == null ? null : f.yesterday, problem, "Ayer no hubo partidos de tus ligas.");
    }

    private void addDayPages(String title, List<Football.Match> matches, String problem, String none) {
        if (matches == null || matches.isEmpty()) {
            TextView t = text(13, regular, TEXT_2);
            t.setText(matches == null ? (problem != null ? problem : "Cargando…") : none);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.topMargin = dp(8);
            footPage(title).addView(t, lp);
            return;
        }
        int pagesCount = (matches.size() + FOOT_ROWS - 1) / FOOT_ROWS;
        java.text.SimpleDateFormat hm = new java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault());
        for (int pg = 0; pg < pagesCount; pg++) {
            LinearLayout page = footPage(pagesCount == 1 ? title : title + "  ·  " + (pg + 1) + "/" + pagesCount);
            for (int i = pg * FOOT_ROWS; i < Math.min(matches.size(), (pg + 1) * FOOT_ROWS); i++) {
                page.addView(matchRow(matches.get(i), hm));
            }
        }
    }

    /** Una fila: hora (o minuto en directo), local, marcador, visitante. */
    private View matchRow(Football.Match m, java.text.SimpleDateFormat hm) {
        boolean live = "in".equals(m.state);
        boolean pre = "pre".equals(m.state);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-1, -2);
        rlp.topMargin = dp(6);
        row.setLayoutParams(rlp);

        TextView when = text(12, live ? medium : regular, live ? 0xFFD0705A : TEXT_2);
        when.setFontFeatureSettings("tnum");
        when.setText(pre ? hm.format(new java.util.Date(m.start)) : m.clock);
        when.setSingleLine(true);
        row.addView(when, new LinearLayout.LayoutParams(dp(44), -2));

        TextView home = text(13, m.homeFav ? medium : regular, m.homeFav ? 0xFFB85A44 : TEXT);
        home.setText(m.home);
        home.setSingleLine(true);
        home.setEllipsize(TextUtils.TruncateAt.END);
        home.setGravity(Gravity.END);
        row.addView(home, new LinearLayout.LayoutParams(0, -2, 1));

        TextView score = text(13, medium, live ? 0xFFD0705A : TEXT);
        score.setFontFeatureSettings("tnum");
        score.setGravity(Gravity.CENTER);
        score.setText(pre ? "–" : m.homeScore + " – " + m.awayScore);
        row.addView(score, new LinearLayout.LayoutParams(dp(46), -2));

        TextView away = text(13, m.awayFav ? medium : regular, m.awayFav ? 0xFFB85A44 : TEXT);
        away.setText(m.away);
        away.setSingleLine(true);
        away.setEllipsize(TextUtils.TruncateAt.END);
        row.addView(away, new LinearLayout.LayoutParams(0, -2, 1));
        return row;
    }

    private List<View> wantedCardPages() {
        List<View> pages = new ArrayList<>();
        if (cfg.haShow) pages.add(haPage);
        if (cfg.footballShow) pages.addAll(footPages);
        return pages;
    }

    /** Las páginas que tocan según la configuración, empezando por la primera. */
    private void resetCardPages() {
        cardPages.clear();
        cardPages.addAll(wantedCardPages());
        haPage.animate().cancel();
        haPage.setAlpha(1f);
        haPage.setVisibility(cfg.haShow ? View.INVISIBLE : View.GONE);
        for (View v : footPages) {
            v.animate().cancel();
            v.setAlpha(1f);
            v.setVisibility(View.INVISIBLE);
        }
        cardIndex = 0;
        if (!cardPages.isEmpty()) showCardTitle(cardPages.get(0)).setVisibility(View.VISIBLE);
    }

    /** Tras rehacer las páginas de fútbol: sigue en la que se veía, o en la que ocupe su lugar. */
    private void syncCardPages() {
        View current = cardPages.isEmpty() ? null : cardPages.get(Math.min(cardIndex, cardPages.size() - 1));
        cardPages.clear();
        cardPages.addAll(wantedCardPages());
        if (cardPages.isEmpty()) return;
        int i = cardPages.indexOf(current);
        if (i >= 0) {
            cardIndex = i;
            return;
        }
        cardIndex = Math.min(cardIndex, cardPages.size() - 1);
        View v = cardPages.get(cardIndex);
        v.setAlpha(1f);
        v.setVisibility(View.VISIBLE);
        showCardTitle(v);
    }

    private View showCardTitle(View page) {
        if (page == haPage) {
            cardIcon.setText("🏠");
            cardName.setText("Home Assistant");
        } else {
            cardIcon.setText("⚽");
            String t = pageTitles.get(page);
            cardName.setText(t == null ? "Fútbol" : t);
        }
        return page;
    }

    /** Alterna la tarjeta de abajo entre sus páginas, con un fundido. */
    private final Runnable cardCycle = new Runnable() {
        @Override
        public void run() {
            if (cardPages.size() > 1) {
                final View out = cardPages.get(cardIndex);
                cardIndex = (cardIndex + 1) % cardPages.size();
                View in = cardPages.get(cardIndex);
                in.setAlpha(0f);
                in.setVisibility(View.VISIBLE);
                in.animate().alpha(1f).setDuration(450).start();
                out.animate().alpha(0f).setDuration(450).withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        out.setVisibility(View.INVISIBLE);
                        out.setAlpha(1f);
                    }
                }).start();
                showCardTitle(in);
            }
            ui.postDelayed(this, cfg.cardCycleSeconds * 1000L);
        }
    };

    /** Cada 12 h mira si hay versión nueva en GitHub; si la hay, un punto coral en "Personalizar". */
    private void checkForUpdate() {
        final String repo = cfg.updateRepo;
        if (repo.isEmpty() || System.currentTimeMillis() - updateCheckedAt < 12 * 3600_000L) return;
        updateCheckedAt = System.currentTimeMillis();
        final String mine = Updater.installedVersion(this);
        io.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    final boolean newer = Updater.isNewer(Updater.latest(repo).version, mine);
                    ui.post(new Runnable() {
                        @Override
                        public void run() {
                            showUpdateDot(newer);
                        }
                    });
                } catch (Exception ignored) {
                    // Sin conexión o sin release: se vuelve a mirar en 12 h.
                }
            }
        });
    }

    private void showUpdateDot(boolean on) {
        if (!on) {
            customizeButton.setText("Personalizar");
            return;
        }
        android.text.SpannableStringBuilder sb = new android.text.SpannableStringBuilder("Personalizar  ●");
        sb.setSpan(new android.text.style.ForegroundColorSpan(0xFFD0705A), sb.length() - 1, sb.length(), 0);
        customizeButton.setText(sb);
    }

    private void refreshFootball() {
        if (!cfg.footballShow) return;
        long every = football != null && football.anyLive() ? 60_000L : 15 * 60_000L;
        if (System.currentTimeMillis() - footballAt < every) return;
        footballAt = System.currentTimeMillis();
        final List<String> leagues = new ArrayList<>(cfg.leagues);
        final List<String> favorites = new ArrayList<>(cfg.favoriteTeams);
        io.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    final Football f = Football.fetch(leagues, favorites);
                    ui.post(new Runnable() {
                        @Override
                        public void run() {
                            football = f;
                            buildFootballPages(f, null);
                            syncCardPages();
                        }
                    });
                } catch (final Exception e) {
                    ui.post(new Runnable() {
                        @Override
                        public void run() {
                            if (football != null) return;   // se quedan los últimos datos
                            buildFootballPages(null, "Sin conexión con ESPN: " + why(e));
                            syncCardPages();
                            footballAt = System.currentTimeMillis() - 14 * 60_000L;   // reintento en un minuto
                        }
                    });
                }
            }
        });
    }

    // ------------------------------------------------------------------ apps

    private Set<String> installedPackages() {
        Set<String> s = new HashSet<>();
        PackageManager pm = getPackageManager();
        for (String cat : new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
            for (ResolveInfo ri : pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(cat), 0)) {
                s.add(ri.activityInfo.packageName);
            }
        }
        return s;
    }

    private Map<String, App> loadApps() {
        PackageManager pm = getPackageManager();
        Map<String, App> apps = new LinkedHashMap<>();
        for (String cat : new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
            for (ResolveInfo ri : pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(cat), 0)) {
                String pkg = ri.activityInfo.packageName;
                if (apps.containsKey(pkg) || pkg.equals(getPackageName()) || cfg.hidden.contains(pkg)) continue;
                App a = new App();
                a.pkg = pkg;
                CharSequence label = ri.loadLabel(pm);
                a.label = label == null ? pkg : label.toString();
                a.icon = ri.loadIcon(pm);
                a.intent = pm.getLeanbackLaunchIntentForPackage(pkg);
                if (a.intent == null) a.intent = pm.getLaunchIntentForPackage(pkg);
                if (a.intent == null) continue;
                apps.put(pkg, a);
            }
        }
        return apps;
    }

    private void rebuildRows() {
        lastPackages = installedPackages();
        Map<String, App> apps = loadApps();
        rowsBox.removeAllViews();
        rows.clear();

        Set<String> used = new HashSet<>();
        for (Config.Category cat : cfg.categories) {
            List<App> list = new ArrayList<>();
            for (String pkg : cat.packages) {
                App a = apps.get(pkg);
                if (a != null && used.add(pkg)) list.add(a);
            }
            addRow(cat.name, list);
        }
        if (cfg.showOthers) {
            List<App> rest = new ArrayList<>();
            for (App a : apps.values()) if (!used.contains(a.pkg)) rest.add(a);
            final Collator col = Collator.getInstance();
            Collections.sort(rest, new Comparator<App>() {
                @Override
                public int compare(App x, App y) {
                    return col.compare(x.label, y.label);
                }
            });
            addRow(cfg.othersName, rest);
        }
        rowsScroll.post(new Runnable() {
            @Override
            public void run() {
                rowsScroll.scrollTo(0, 0);
                if (!rows.isEmpty()) focusRow(0); else settingsButton.requestFocus();
            }
        });
    }

    private void addRow(String name, List<App> list) {
        if (list.isEmpty()) return;
        final int rowIndex = rows.size();
        final Row row = new Row();

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setClipChildren(false);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-1, -2);
        blp.bottomMargin = dp(8);
        rowsBox.addView(box, blp);

        TextView title = text(20, regular, TEXT);
        title.setText(name);
        title.setPadding(dp(ROW_PAD), 0, 0, 0);
        box.addView(title);

        HorizontalScrollView hsv = new Scrollers.Row(this);
        hsv.setHorizontalScrollBarEnabled(false);
        hsv.setHorizontalFadingEdgeEnabled(true);
        hsv.setFadingEdgeLength(dp(48));
        hsv.setFocusable(false);
        hsv.setSmoothScrollingEnabled(true);
        box.addView(hsv, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout strip = new LinearLayout(this);
        strip.setOrientation(LinearLayout.HORIZONTAL);
        strip.setClipChildren(false);
        // A la derecha, sitio de sobra: así la última app también queda alineada en su columna.
        strip.setPadding(dp(ROW_PAD), dp(12), dp(700), dp(14));
        // La app ampliada se dibuja sobre el margen: sin esto Android la recorta.
        strip.setClipToPadding(false);
        hsv.addView(strip, new FrameLayout.LayoutParams(-2, -2));

        for (int i = 0; i < list.size(); i++) {
            strip.addView(tile(list.get(i), rowIndex, i, row));
        }
        row.container = box;
        row.scroller = hsv;
        row.strip = strip;
        rows.add(row);
    }

    private View tile(final App app, int rowIndex, int index, final Row row) {
        final Tile t = new Tile(this, rowIndex, index);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setGravity(Gravity.CENTER);
        t.setFocusable(true);
        t.setClickable(true);
        t.setBackground(focusable(rounded(0x99FFFFFF, 20, 0x80FFFFFF, 1),
                rounded(0xF7FFFFFF, 20, 0xFFFFC09A, 3)));
        t.setPadding(dp(8), dp(10), dp(8), dp(8));

        ImageView icon = new ImageView(this);
        icon.setImageDrawable(app.icon);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        t.addView(icon, new LinearLayout.LayoutParams(dp(50), dp(50)));

        TextView label = text(12, regular, TEXT);
        label.setText(app.label);
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        label.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(-1, -2);
        llp.topMargin = dp(10);
        t.addView(label, llp);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(TILE), dp(TILE));
        lp.rightMargin = dp(TILE_GAP);
        t.setLayoutParams(lp);

        t.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean has) {
                v.animate().scaleX(has ? 1.1f : 1f).scaleY(has ? 1.1f : 1f)
                        .translationZ(has ? dp(12) : 0).setDuration(150).start();
                if (has) {
                    row.last = t.index;
                    snap(row, t.index);
                }
            }
        });
        t.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    startActivity(new Intent(app.intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                } catch (RuntimeException e) {
                    Toast.makeText(MainActivity.this, "No se pudo abrir " + app.label, Toast.LENGTH_SHORT).show();
                }
            }
        });
        t.pkg = app.pkg;
        t.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                openAppInfo(app.pkg, app.label);
                return true;
            }
        });
        return t;
    }

    /**
     * La página de la app en los ajustes del Fire TV (la de "Gestionar aplicaciones instaladas"): forzar detención,
     * borrar caché o datos, desinstalar... Se abre con el botón de opciones del mando o manteniendo OK.
     */
    private void openAppInfo(String pkg, String label) {
        Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", pkg, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        ResolveInfo ri = getPackageManager().resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY);
        if (ri == null || ri.activityInfo.name.contains("CTSDummy")) {
            Toast.makeText(this, "El Fire TV no deja abrir la información de " + label + " desde aquí", Toast.LENGTH_LONG).show();
            return;
        }
        try {
            startActivity(i);
        } catch (RuntimeException e) {
            Toast.makeText(this, "No se pudo abrir la información de " + label, Toast.LENGTH_SHORT).show();
        }
    }

    private int step() {
        return dp(TILE + TILE_GAP);
    }

    /** Desplaza la fila de columna en columna para que la app elegida quede a la vista y alineada. */
    private void snap(Row r, int index) {
        int width = r.scroller.getWidth();
        int cols = width <= 0 ? 4 : Math.max(1, (width - dp(ROW_PAD) - dp(24)) / step());
        if (index < r.first) r.first = index;
        else if (index > r.first + cols - 1) r.first = index - cols + 1;
        r.scroller.smoothScrollTo(r.first * step(), 0);
    }

    private void focusRow(int i) {
        if (i < 0 || i >= rows.size()) return;
        Row r = rows.get(i);
        int count = r.strip.getChildCount();
        if (count == 0) return;
        r.strip.getChildAt(Math.min(r.last, count - 1)).requestFocus();
        rowsScroll.smoothScrollTo(0, i == 0 ? 0 : Math.max(0, r.container.getTop() - dp(4)));
    }

    /** Arriba/abajo cambian de categoría (recordando la app de cada fila); izquierda/derecha eligen app. */
    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.KEYCODE_MENU && getCurrentFocus() instanceof Tile) {
            if (e.getAction() == KeyEvent.ACTION_UP && e.getRepeatCount() == 0) {
                Tile t = (Tile) getCurrentFocus();
                TextView label = (TextView) t.getChildAt(1);
                openAppInfo(t.pkg, label.getText().toString());
            }
            return true;
        }
        if (e.getAction() == KeyEvent.ACTION_DOWN) {
            View f = getCurrentFocus();
            int key = e.getKeyCode();
            if (f instanceof Tile) {
                Tile t = (Tile) f;
                switch (key) {
                    case KeyEvent.KEYCODE_DPAD_UP:
                        if (t.row == 0) {
                            settingsButton.requestFocus();
                            rowsScroll.smoothScrollTo(0, 0);
                        } else {
                            focusRow(t.row - 1);
                        }
                        return true;
                    case KeyEvent.KEYCODE_DPAD_DOWN:
                        if (t.row < rows.size() - 1) focusRow(t.row + 1);
                        return true;
                    case KeyEvent.KEYCODE_DPAD_LEFT:
                        if (t.index > 0) rows.get(t.row).strip.getChildAt(t.index - 1).requestFocus();
                        return true;
                    case KeyEvent.KEYCODE_DPAD_RIGHT:
                        LinearLayout strip = rows.get(t.row).strip;
                        if (t.index < strip.getChildCount() - 1) strip.getChildAt(t.index + 1).requestFocus();
                        return true;
                    default:
                        break;
                }
            } else if (f == settingsButton || f == customizeButton) {
                switch (key) {
                    case KeyEvent.KEYCODE_DPAD_DOWN:
                        focusRow(0);
                        return true;
                    case KeyEvent.KEYCODE_DPAD_LEFT:
                        if (f == settingsButton) customizeButton.requestFocus();
                        return true;
                    case KeyEvent.KEYCODE_DPAD_RIGHT:
                        if (f == customizeButton) settingsButton.requestFocus();
                        return true;
                    case KeyEvent.KEYCODE_DPAD_UP:
                        return true;
                    default:
                        break;
                }
            } else if (f == null && isDpad(key)) {
                goHome();
                return true;
            }
        }
        return super.dispatchKeyEvent(e);
    }

    private static boolean isDpad(int key) {
        return key == KeyEvent.KEYCODE_DPAD_UP || key == KeyEvent.KEYCODE_DPAD_DOWN
                || key == KeyEvent.KEYCODE_DPAD_LEFT || key == KeyEvent.KEYCODE_DPAD_RIGHT;
    }

    /**
     * Fire OS resuelve ACTION_SETTINGS a una pantalla vacía de compatibilidad (CTSDummy) que acaba en el inicio de
     * Amazon; sus Ajustes de verdad son una pantalla del propio launcher de Amazon. Igual que hace cleanyourtv.
     */
    static Intent settingsIntent(Context ctx) {
        Intent intent = new Intent(Settings.ACTION_SETTINGS);
        ResolveInfo ri = ctx.getPackageManager().resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY);
        if (ri == null || ri.activityInfo.name.contains("CTSDummy")) {
            intent = new Intent().setClassName("com.amazon.tv.launcher", "com.amazon.tv.launcher.ui.MainSettingsActivity");
        }
        return intent;
    }

    private void openSettings() {
        Intent intent = settingsIntent(this);
        try {
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (RuntimeException e) {
            Toast.makeText(this, "No se pudieron abrir los ajustes", Toast.LENGTH_SHORT).show();
        }
    }

    // ------------------------------------------------------------------ red

    private void updateWifi() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        NetworkInfo ni = cm == null ? null : cm.getActiveNetworkInfo();
        if (ni == null || !ni.isConnected()) {
            wifi.set(false, 0);
        } else if (ni.getType() == ConnectivityManager.TYPE_WIFI) {
            WifiManager wm = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
            int rssi = wm == null ? -50 : wm.getConnectionInfo().getRssi();
            wifi.set(true, WifiManager.calculateSignalLevel(rssi, 5));
        } else {
            wifi.set(true, 4);
        }
    }

    // ------------------------------------------------------------------ tiempo y Home Assistant

    /** Descarga el horóscopo de los signos elegidos, una vez al día. */
    private void refreshHoroscopes() {
        final String today = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ROOT).format(new java.util.Date());
        if (cfg.signs.isEmpty() || today.equals(horoscopeDay)) return;
        // Si la web falla, no se insiste más de una vez cada media hora.
        if (System.currentTimeMillis() - horoscopeTriedAt < WEATHER_EVERY) return;
        horoscopeTriedAt = System.currentTimeMillis();
        final List<Integer> signs = new ArrayList<>(cfg.signs);
        final boolean preferHola = cfg.horoscopeHola;
        io.execute(new Runnable() {
            @Override
            public void run() {
                final List<Horoscope> got = new ArrayList<>();
                for (int sign : signs) {
                    try {
                        got.add(Horoscope.fetch(sign, prefs, preferHola));
                    } catch (Exception ignored) {
                        // Ese signo no sale hoy; el resto sí.
                    }
                }
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        if (got.isEmpty()) return;   // se reintenta en la siguiente actualización
                        horoscopes.clear();
                        horoscopes.addAll(got);
                        // Si alguno aún es el de ayer, se vuelve a pedir dentro de media hora.
                        boolean allFresh = true;
                        for (Horoscope h : got) allFresh &= h.fresh;
                        if (allFresh) horoscopeDay = today;
                    }
                });
            }
        });
    }

    /*
     * Saludo -> horóscopo de cada signo -> saludo..., con un fundido. El saludo dura lo configurado; cada horóscopo,
     * lo que tarde en leerse: una pausa, el texto subiendo línea a línea y otra pausa.
     */

    /** Empieza un turno nuevo del ciclo: vuelve al saludo y programa el primer cambio tras su duración. */
    private void restartGreeting() {
        greetTurn++;
        stopReading();
        greetStep = 0;
        greetingView.animate().cancel();
        horoView.animate().cancel();
        greetingView.setAlpha(1f);
        greetingView.setVisibility(View.VISIBLE);
        horoView.setVisibility(View.INVISIBLE);
        scheduleGreeting(cfg.greetingCycleSeconds * 1000L);
    }

    private void scheduleGreeting(long delay) {
        final int turn = greetTurn;
        ui.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (turn == greetTurn) advanceGreeting(turn);
            }
        }, delay);
    }

    private void advanceGreeting(final int turn) {
        if (horoscopes.isEmpty()) {
            scheduleGreeting(cfg.greetingCycleSeconds * 1000L);
            return;
        }
        int previous = greetStep;
        greetStep = (greetStep + 1) % (horoscopes.size() + 1);
        final View out = previous == 0 ? greetingView : horoView;
        final View in = greetStep == 0 ? greetingView : horoView;
        final int step = greetStep;
        stopReading();
        out.animate().alpha(0f).setDuration(300).withEndAction(new Runnable() {
            @Override
            public void run() {
                if (turn != greetTurn) return;
                out.setVisibility(View.INVISIBLE);
                if (step > 0) {
                    Horoscope h = horoscopes.get(step - 1);
                    horoLabel.setText(h.name.toUpperCase(java.util.Locale.getDefault()) + "  ·  HORÓSCOPO DE HOY");
                    horoText.setText(h.text);
                    horoScroll.scrollTo(0, 0);
                }
                in.setAlpha(0f);
                in.setVisibility(View.VISIBLE);
                in.animate().alpha(1f).setDuration(400).start();
                if (step == 0) {
                    scheduleGreeting(cfg.greetingCycleSeconds * 1000L);
                } else {
                    // Tras medir el texto: cuánto hay que subir y cuánto tarda.
                    horoText.post(new Runnable() {
                        @Override
                        public void run() {
                            if (turn == greetTurn) scheduleGreeting(startReading());
                        }
                    });
                }
            }
        }).start();
    }

    /**
     * Empieza a subir el horóscopo; devuelve cuánto durará en pantalla. Se mueve con el reloj real y no con un
     * animador del sistema: con la escala de animaciones al 0,5x (la limpieza la deja así) iría al doble de velocidad.
     */
    private long startReading() {
        final int distance = horoText.getHeight() - horoScroll.getHeight();
        if (distance <= 0) return Math.max(cfg.greetingCycleSeconds * 1000L, cfg.readStartMs + cfg.readEndMs + 3000);
        final long duration = Math.round((double) distance / dp(HORO_LINE) * cfg.readLineMs);
        final long startAt = android.os.SystemClock.uptimeMillis() + cfg.readStartMs;
        horoReading = new Runnable() {
            @Override
            public void run() {
                long t = android.os.SystemClock.uptimeMillis() - startAt;
                if (t < 0) {
                    ui.postDelayed(this, -t);
                    return;
                }
                horoScroll.scrollTo(0, (int) Math.min(distance, distance * t / duration));
                if (t < duration) ui.postDelayed(this, 16);
            }
        };
        ui.post(horoReading);
        return cfg.readStartMs + duration + cfg.readEndMs;
    }

    private void stopReading() {
        if (horoReading != null) {
            ui.removeCallbacks(horoReading);
            horoReading = null;
        }
    }

    private void refreshWeather() {
        final Config c = cfg;
        io.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    final Weather w = Weather.fetch(c, prefs);
                    ui.post(new Runnable() {
                        @Override
                        public void run() {
                            weatherAt = System.currentTimeMillis();
                            wPlace.setText(w.place);
                            wTemp.setText(w.temperature + "°C");
                            wDesc.setText(w.description());
                            wIcon.setType(w.icon());
                            wMinMax.setText("Máx " + w.max + "°  ·  Mín " + w.min + "°");
                            fillWeek(w.week);
                            wRain.setText(w.rainChance < 0 ? "" : "Prob. de lluvia: " + w.rainChance + " %");
                        }
                    });
                } catch (final Exception e) {
                    ui.post(new Runnable() {
                        @Override
                        public void run() {
                            if (weatherAt == 0) {
                                wPlace.setText(c.city);
                                wDesc.setText("Sin conexión");
                                wIcon.setType(WeatherIcon.CLOUD);
                            }
                        }
                    });
                }
            }
        });
    }

    private void refreshHa() {
        final Config c = cfg;
        if (!c.haConfigured()) {
            haCol1.removeAllViews();
            haCol2.removeAllViews();
            spark.set(null);
            haHint.setVisibility(View.VISIBLE);
            haHint.setText("Añade la dirección y el token de Home Assistant en config.json");
            return;
        }
        io.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    final HomeAssistant h = HomeAssistant.fetch(c);
                    ui.post(new Runnable() {
                        @Override
                        public void run() {
                            showHa(h.lines, h.graph);
                        }
                    });
                } catch (final Exception e) {
                    ui.post(new Runnable() {
                        @Override
                        public void run() {
                            haHint.setVisibility(View.VISIBLE);
                            haHint.setText("Sin conexión con Home Assistant: " + why(e));
                        }
                    });
                }
            }
        });
    }

    /** El motivo de un fallo de Home Assistant, en palabras. */
    private static String why(Exception e) {
        String m = String.valueOf(e.getMessage());
        if (m.contains("HTTP 401") || m.contains("HTTP 403")) return "token incorrecto";
        if (m.contains("HTTP 404")) return "dirección incorrecta (404)";
        if (m.startsWith("HTTP ")) return m;
        if (e instanceof java.net.UnknownHostException) return "no se encuentra la dirección";
        if (e instanceof java.net.ConnectException) return "no responde en esa IP y puerto";
        if (e instanceof java.net.SocketTimeoutException) return "no responde (tiempo agotado)";
        if (e instanceof javax.net.ssl.SSLException) return "problema con el certificado https";
        if (e instanceof java.net.MalformedURLException) return "la url no es válida";
        if (e instanceof org.json.JSONException) return "respuesta inesperada";
        return e.getClass().getSimpleName();
    }

    private void showHa(List<HomeAssistant.Line> lines, List<Float> graph) {
        haHint.setVisibility(View.GONE);
        haCol1.removeAllViews();
        haCol2.removeAllViews();
        // Hasta 3 líneas: una tabla, con el nombre a la izquierda y cada valor en su columna, alineado a la derecha.
        // Con más, dos columnas de texto.
        boolean single = lines.size() <= 3;
        haCol2.setVisibility(single ? View.GONE : View.VISIBLE);
        int half = single ? lines.size() : (lines.size() + 1) / 2;
        for (int i = 0; i < lines.size(); i++) {
            HomeAssistant.Line line = lines.get(i);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.topMargin = dp(single ? 9 : 7);
            if (single) {
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                TextView name = text(14, regular, TEXT_2);
                name.setText(line.label);
                name.setSingleLine(true);
                name.setEllipsize(TextUtils.TruncateAt.END);
                row.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
                boolean wide = line.values.size() == 1;
                for (String v : line.values) {
                    TextView value = text(14, medium, TEXT);
                    value.setFontFeatureSettings("tnum");   // cifras de ancho fijo: columnas alineadas
                    value.setText(v);
                    value.setSingleLine(true);
                    value.setGravity(Gravity.END);
                    LinearLayout.LayoutParams vlp = new LinearLayout.LayoutParams(wide ? -2 : dp(66), -2);
                    vlp.leftMargin = dp(8);
                    row.addView(value, vlp);
                }
                haCol1.addView(row, lp);
            } else {
                StringBuilder sb = new StringBuilder(line.label).append(": ");
                for (int j = 0; j < line.values.size(); j++) {
                    if (j > 0) sb.append("  ·  ");
                    sb.append(line.values.get(j));
                }
                TextView t = text(13, regular, TEXT);
                t.setText(sb);
                t.setSingleLine(true);
                t.setEllipsize(TextUtils.TruncateAt.END);
                (i < half ? haCol1 : haCol2).addView(t, lp);
            }
        }
        spark.set(graph);
    }
}
