package com.alejandro.controlfinanzas.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.widget.RemoteViews;

import com.alejandro.controlfinanzas.R;

import org.json.JSONObject;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Widget de inicio 5x2 de Control de Finanzas.
 *
 * 5 pestanas solo con iconos (sin texto): Saldo (cartera), Proximos pagos
 * (calendario), Ingreso (flecha entrando), Gasto (flecha saliendo) y
 * Transferencia (flechas de intercambio). Las pestanas no son tabs reales:
 * cada toque envia un broadcast que vuelve a dibujar el widget con el
 * contenido de esa pestana.
 *
 * El contenido se desliza arriba/abajo: es una ListView alimentada por
 * FinanzasWidgetService (RemoteViewsService), porque un ScrollView normal
 * no desplaza dentro de un widget. La fila de pestanas queda fija arriba y
 * debajo va la lista (boton "Registrar movimiento", atajos, pagos, saldo o
 * aviso). Cada elemento de la lista lleva su propio enlace (fill-in intent)
 * y aqui se pone la plantilla (setPendingIntentTemplate).
 *
 * Los datos vienen del puente de la app: index.html escribe un resumen JSON
 * en @capacitor/preferences (SharedPreferences "CapacitorStorage", clave
 * "finanzas_widget_snapshot") cada vez que guarda. El widget lo lee desde
 * aqui; no puede leer el localStorage del WebView.
 *
 * El toque en el widget ABRE LA APP con el formulario prellenado (enlace
 * controlfinanzas://widget/...): no registra nada en segundo plano. Al picar
 * un atajo se abre el form de movimiento con los datos de ese atajo; al picar
 * un pago proximo se abre su "Revisar y pagar"; "Registrar movimiento" abre
 * el form nuevo del tipo de la pestana.
 *
 * El widget se actualiza al tocar una pestana y cada 30 min
 * (updatePeriodMillis). Si la app guardo datos nuevos, el widget los muestra
 * en la siguiente actualizacion.
 *
 * Sigue los colores del tema de la app (snapshot.theme): fondo, acento del
 * boton y de la pestana activa, iconos y textos; en modo oscuro usa
 * superficies oscuras. Cada pago proximo va en su propio recuadro, como
 * los atajos. El tinte de fondos usa setBackgroundTintList (RemoteViews,
 * API 31+); en versiones viejas se mantiene el aspecto fijo de siempre.
 */
public class FinanzasWidgetProvider extends AppWidgetProvider {

    static final String ACTION_TAB = "com.alejandro.controlfinanzas.widget.ACTION_TAB";
    static final String EXTRA_TAB = "tab";

    static final int TAB_SALDO = 0;
    static final int TAB_BILLS = 1;
    static final int TAB_INCOME = 2;
    static final int TAB_EXPENSE = 3;
    static final int TAB_TRANSFER = 4;

    static final String WIDGET_PREFS = "FinanzasWidgetPrefs";
    static final String KEY_TAB = "tab";
    static final String CAP_PREFS = "CapacitorStorage";
    static final String KEY_SNAPSHOT = "finanzas_widget_snapshot";

    static final String DEEP_LINK = "controlfinanzas://widget/";

    // Tema de la app (index.html: DEFAULT_THEME y el modo oscuro del CSS).
    // Si el snapshot no trae tema (version vieja de la app), se usan estos.
    private static final int THEME_PRIMARY = 0xFF1F7A5C;
    private static final int THEME_SECONDARY = 0xFF2A9D8F;
    private static final int THEME_BG_LIGHT = 0xFFF2F5F4;
    private static final int LIGHT_INK = 0xFF14211F;
    private static final int LIGHT_MUTED = 0xFF5E6E6B;
    private static final int DARK_BG = 0xFF0E1514;
    private static final int DARK_SURFACE = 0xFF17211F;
    private static final int DARK_INK = 0xFFE8F0EE;
    private static final int DARK_MUTED = 0xFF93A4A0;

    static final int MAX_BILLS = 2;
    static final int MAX_CHIPS = 3;

    private static final int[] TAB_BUTTONS = {
            R.id.widget_tab_saldo,
            R.id.widget_tab_bills,
            R.id.widget_tab_income,
            R.id.widget_tab_expense,
            R.id.widget_tab_transfer
    };

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int id : appWidgetIds) {
            appWidgetManager.updateAppWidget(id, buildViews(context, id));
            appWidgetManager.notifyAppWidgetViewDataChanged(id, R.id.widget_content);
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (ACTION_TAB.equals(intent.getAction())) {
            int tab = intent.getIntExtra(EXTRA_TAB, TAB_SALDO);
            context.getSharedPreferences(WIDGET_PREFS, Context.MODE_PRIVATE)
                    .edit().putInt(KEY_TAB, tab).apply();
            AppWidgetManager mgr = AppWidgetManager.getInstance(context);
            ComponentName cn = new ComponentName(context, FinanzasWidgetProvider.class);
            for (int id : mgr.getAppWidgetIds(cn)) {
                mgr.updateAppWidget(id, buildViews(context, id));
                mgr.notifyAppWidgetViewDataChanged(id, R.id.widget_content);
            }
            return;
        }
        super.onReceive(context, intent);
    }

    private RemoteViews buildViews(Context context, int appWidgetId) {
        String pkg = context.getPackageName();
        int tab = context.getSharedPreferences(WIDGET_PREFS, Context.MODE_PRIVATE)
                .getInt(KEY_TAB, TAB_SALDO);
        JSONObject snap = readSnapshot(context);
        Theme theme = resolveTheme(context, snap);
        // El tinte de fondos (setBackgroundTintList) solo existe en
        // RemoteViews desde API 31. En versiones viejas se mantiene el
        // aspecto fijo de siempre (sin tema), para no romper nada.
        boolean themed = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S;
        RemoteViews views = new RemoteViews(pkg, R.layout.widget_finanzas);
        if (themed) {
            tintBackground(views, R.id.widget_root, theme.bg);
        }
        for (int i = 0; i < TAB_BUTTONS.length; i++) {
            views.setInt(TAB_BUTTONS[i], "setBackgroundResource",
                    i == tab ? R.drawable.widget_tab_selected : 0);
            if (themed) {
                views.setInt(TAB_BUTTONS[i], "setColorFilter",
                        i == tab ? theme.primary : theme.muted);
                if (i == tab) {
                    tintBackground(views, TAB_BUTTONS[i], theme.tabSelected);
                }
            }
            views.setOnClickPendingIntent(TAB_BUTTONS[i], tabIntent(context, i));
        }
        // Contenido desplazable: la lista la alimenta FinanzasWidgetService.
        // El intent lleva un data unico por widget para que el sistema no
        // reutilice la misma fabrica entre instancias.
        Intent svc = new Intent(context, FinanzasWidgetService.class);
        svc.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        svc.setData(Uri.parse(svc.toUri(Intent.URI_INTENT_SCHEME)));
        views.setRemoteAdapter(R.id.widget_content, svc);
        // Plantilla de toque para los elementos de la lista: cada uno pone
        // su enlace con fill-in intent y se abre la app con ese deep link.
        // La plantilla es EXPLICITA a MainActivity (ComponentName con el
        // paquete de la app): con la plantilla implicita de antes (solo
        // ACTION_VIEW, sin componente) el sistema resolvia el enlace por
        // intent-filter y en el telefono no encontraba la app, asi que
        // buscaba otra app para abrirlo. Al fijar el componente, el toque
        // siempre abre esta app; el fill-in solo aporta la URI
        // controlfinanzas://widget/... con el formulario a abrir. El
        // intent-filter del enlace sigue en el manifiesto (lo agrega
        // install-widget.py) por si algo dispara la URI desde fuera.
        Intent tpl = new Intent(Intent.ACTION_VIEW);
        tpl.setComponent(new ComponentName(pkg, pkg + ".MainActivity"));
        tpl.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent tplPi = PendingIntent.getActivity(context, 900, tpl,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setPendingIntentTemplate(R.id.widget_content, tplPi);
        return views;
    }

    /** Un atajo (chip): fondo de superficie del tema y texto principal. */
    static void themeChip(RemoteViews chips, int chipId, Theme theme) {
        tintBackground(chips, chipId, theme.surface);
        chips.setTextColor(chipId, theme.ink);
    }

    /**
     * Colores del widget segun el tema de la app (snapshot.theme).
     * En modo oscuro el fondo y las superficies son oscuros y el texto
     * claro; el acento es el color principal elegido por el usuario. En
     * modo Automatico se sigue el modo oscuro del sistema (nativo), para
     * que el widget cambie aunque la app no se haya abierto.
     */
    static final class Theme {
        final int bg, surface, ink, muted, primary, secondary, tabSelected;

        Theme(int bg, int surface, int ink, int muted, int primary, int secondary, int tabSelected) {
            this.bg = bg;
            this.surface = surface;
            this.ink = ink;
            this.muted = muted;
            this.primary = primary;
            this.secondary = secondary;
            this.tabSelected = tabSelected;
        }
    }

    static Theme resolveTheme(Context context, JSONObject snap) {
        JSONObject t = snap == null ? null : snap.optJSONObject("theme");
        int primary = parseColor(t == null ? null : t.optString("primary", null), THEME_PRIMARY);
        int secondary = parseColor(t == null ? null : t.optString("secondary", null), THEME_SECONDARY);
        int bgCustom = parseColor(t == null ? null : t.optString("background", null), THEME_BG_LIGHT);
        String mode = t == null ? "auto" : t.optString("mode", "auto");
        boolean dark;
        if ("dark".equals(mode)) {
            dark = true;
        } else if ("light".equals(mode)) {
            dark = false;
        } else {
            dark = isNightMode(context);
        }
        int bg = dark ? DARK_BG : bgCustom;
        int surface = dark ? DARK_SURFACE : blend(bgCustom, primary, 0.10f);
        int ink = dark ? DARK_INK : LIGHT_INK;
        int muted = dark ? DARK_MUTED : LIGHT_MUTED;
        int tabSelected = dark ? blend(DARK_SURFACE, primary, 0.35f)
                : blend(bgCustom, primary, 0.18f);
        return new Theme(bg, surface, ink, muted, primary, secondary, tabSelected);
    }

    private static boolean isNightMode(Context context) {
        int night = context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        return night == Configuration.UI_MODE_NIGHT_YES;
    }

    private static int parseColor(String hex, int fallback) {
        if (hex == null) return fallback;
        try {
            return Color.parseColor(hex);
        } catch (Exception e) {
            return fallback;
        }
    }

    /** Mezcla base con over en proporcion ratio (0..1). */
    private static int blend(int base, int over, float ratio) {
        float r = 1f - ratio;
        return Color.argb(0xFF,
                Math.round(Color.red(base) * r + Color.red(over) * ratio),
                Math.round(Color.green(base) * r + Color.green(over) * ratio),
                Math.round(Color.blue(base) * r + Color.blue(over) * ratio));
    }

    /**
     * Tine el fondo redondeado de una vista sin perder sus esquinas.
     * Solo se llama cuando themed (API 31+): setColorStateList no existe
     * en RemoteViews en versiones viejas.
     */
    static void tintBackground(RemoteViews views, int viewId, int color) {
        views.setColorStateList(viewId, "setBackgroundTintList", ColorStateList.valueOf(color));
    }

    static JSONObject readSnapshot(Context context) {
        try {
            SharedPreferences prefs = context.getSharedPreferences(CAP_PREFS, Context.MODE_PRIVATE);
            String raw = prefs.getString(KEY_SNAPSHOT, null);
            if (raw == null) return null;
            return new JSONObject(raw);
        } catch (Exception e) {
            return null;
        }
    }

    private PendingIntent tabIntent(Context context, int tab) {
        Intent i = new Intent(context, FinanzasWidgetProvider.class);
        i.setAction(ACTION_TAB);
        i.putExtra(EXTRA_TAB, tab);
        return PendingIntent.getBroadcast(context, 100 + tab, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /** Igual que money() en la app: $1,234.56, negativos como -$1.00. */
    static String fmtMoney(double v) {
        boolean neg = v < 0;
        DecimalFormat df = new DecimalFormat("#,##0.00",
                DecimalFormatSymbols.getInstance(Locale.US));
        return (neg ? "-$" : "$") + df.format(Math.abs(v));
    }

    /** Igual que billLabel()/dueLabel() en la app. */
    static String daysLabel(int days) {
        if (days == 0) return "vence hoy";
        if (days == 1) return "vence mañana";
        if (days < 0) return "vencido hace " + (-days) + (days == -1 ? " día" : " días");
        return "vence en " + days + " días";
    }
}
