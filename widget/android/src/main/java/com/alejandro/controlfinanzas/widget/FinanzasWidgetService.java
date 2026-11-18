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
import android.view.View;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import com.alejandro.controlfinanzas.R;

import org.json.JSONObject;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Servicio de vistas remotas del widget: el contenido se desliza
 * arriba/abajo (ListView). Un ScrollView normal no desplaza dentro de un
 * widget; una vista de coleccion (ListView) con RemoteViewsService si.
 *
 * La fila de pestanas queda fija arriba (la dibuja el provider) y debajo va
 * la lista con el contenido de la pestana activa: boton "Registrar
 * movimiento", atajos, pagos, saldo o aviso. Cada elemento de la lista lleva
 * su propio enlace (fill-in intent) y el provider pone la plantilla
 * (setPendingIntentTemplate): al picar se abre la app con el formulario
 * prellenado, igual que antes. Nada se registra en segundo plano.
 *
 * La fabrica lee la pestana activa, el resumen del puente y el tema con los
 * mismos helpers del provider (FinanzasWidgetProvider.*), asi que los datos
 * y colores son los mismos.
 */
public class FinanzasWidgetService extends RemoteViewsService {

    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {
        try {
            return new WidgetFactory(getApplicationContext());
        } catch (Exception e) {
            // Ultimo recurso: una fabrica vacia que igual devuelve un aviso
            // (getCount nunca es 0 y getViewAt nunca devuelve null sin
            // intentar el aviso), para no dejar "Cargando..." eterno.
            return new WidgetFactory(getApplicationContext());
        }
    }

    static final int TYPE_SALDO = 0;
    static final int TYPE_BILL = 1;
    static final int TYPE_ACTION = 2;
    static final int TYPE_CHIPS = 3;
    static final int TYPE_NOTE = 4;

    static class WidgetFactory implements RemoteViewsFactory {
        private final Context ctx;
        private final java.util.List<Item> items = new java.util.ArrayList<>();

        WidgetFactory(Context context) {
            ctx = context.getApplicationContext();
        }

        static final class Item {
            final int type;
            final RemoteViews views;

            Item(int type, RemoteViews views) {
                this.type = type;
                this.views = views;
            }
        }

        @Override
        public void onCreate() {
            try {
                reload();
            } catch (Exception e) {
                ensureFallback();
            }
        }

        @Override
        public void onDataSetChanged() {
            try {
                reload();
            } catch (Exception e) {
                ensureFallback();
            }
        }

        @Override
        public void onDestroy() {
            try {
                items.clear();
            } catch (Exception ignored) {
            }
        }

        @Override
        public int getCount() {
            // NUNCA 0: con 0 el ListView del widget se queda en
            // "Cargando..." para siempre. Si algo fallo, hay 1 aviso.
            return items.isEmpty() ? 1 : items.size();
        }

        @Override
        public int getViewTypeCount() {
            return 5;
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public boolean hasStableIds() {
            return true;
        }

        @Override
        public RemoteViews getViewAt(int position) {
            try {
                if (position < 0 || position >= items.size()) {
                    return fallbackViews();
                }
                RemoteViews v = items.get(position).views;
                return v != null ? v : fallbackViews();
            } catch (Exception e) {
                return fallbackViews();
            }
        }

        @Override
        public RemoteViews getLoadingView() {
            // Vista de carga real (no null): si la fabrica tarda, se ve el
            // aviso en vez de "Cargando..." sin fin.
            return fallbackViews();
        }

        private RemoteViews fallbackViews() {
            try {
                String pkg = ctx.getPackageName();
                return noteViews(pkg, null, false, 999,
                        "Abre la app para cargar tus datos", FinanzasWidgetProvider.DEEP_LINK + "home");
            } catch (Exception e) {
                try {
                    return new RemoteViews(ctx.getPackageName(), R.layout.widget_row_note);
                } catch (Exception e2) {
                    return null;
                }
            }
        }

        private void ensureFallback() {
            if (!items.isEmpty()) return;
            RemoteViews v = fallbackViews();
            if (v != null) items.add(new Item(TYPE_NOTE, v));
        }

        private static double safeDouble(JSONObject o, String key) {
            try {
                return o == null ? 0 : o.optDouble(key, 0);
            } catch (Exception e) {
                return 0;
            }
        }

        private static int safeInt(JSONObject o, String key) {
            try {
                return o == null ? 0 : o.optInt(key, 0);
            } catch (Exception e) {
                return 0;
            }
        }

        private static String safeString(JSONObject o, String key) {
            try {
                return o == null ? "" : o.optString(key, "");
            } catch (Exception e) {
                return "";
            }
        }

        private void reload() {
            try {
                items.clear();
            } catch (Exception ignored) {
            }
            try {
                doReload();
            } catch (Exception e) {
                // Nunca dejar el widget en "Cargando...": si algo falla al armar
                // el contenido, mostrar el aviso en vez de dejar la lista vacia.
                try {
                    items.clear();
                } catch (Exception ignored) {
                }
                ensureFallback();
            }
            ensureFallback();
        }

        private void doReload() {
            int tab = FinanzasWidgetProvider.TAB_SALDO;
            try {
                tab = ctx.getSharedPreferences(FinanzasWidgetProvider.WIDGET_PREFS, Context.MODE_PRIVATE)
                        .getInt(FinanzasWidgetProvider.KEY_TAB, FinanzasWidgetProvider.TAB_SALDO);
            } catch (Exception ignored) {
            }
            JSONObject snap = null;
            try {
                snap = FinanzasWidgetProvider.readSnapshot(ctx);
            } catch (Exception ignored) {
            }
            FinanzasWidgetProvider.Theme theme;
            try {
                theme = FinanzasWidgetProvider.resolveTheme(ctx, snap);
            } catch (Exception e) {
                theme = null;
            }
            boolean themed = theme != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S;
            String pkg = ctx.getPackageName();
            if (snap == null) {
                items.add(new Item(TYPE_NOTE, noteViews(pkg, theme, themed, 900,
                        "Abre la app para cargar tus datos", FinanzasWidgetProvider.DEEP_LINK + "home")));
                return;
            }
            if (tab == FinanzasWidgetProvider.TAB_SALDO) {
                items.add(new Item(TYPE_SALDO, saldoViews(pkg, snap, theme, themed)));
            } else if (tab == FinanzasWidgetProvider.TAB_BILLS) {
                buildBills(pkg, snap, theme, themed);
            } else {
                String type = tab == FinanzasWidgetProvider.TAB_INCOME ? "income"
                        : tab == FinanzasWidgetProvider.TAB_EXPENSE ? "expense" : "transfer";
                buildTypeTab(pkg, snap, type, theme, themed);
            }
        }

        private RemoteViews saldoViews(String pkg, JSONObject snap,
                                       FinanzasWidgetProvider.Theme theme, boolean themed) {
            RemoteViews row = new RemoteViews(pkg, R.layout.widget_row_saldo);
            row.setTextViewText(R.id.row_saldo_amount,
                    FinanzasWidgetProvider.fmtMoney(safeDouble(snap, "avail")));
            row.setTextViewText(R.id.row_saldo_debt,
                    FinanzasWidgetProvider.fmtMoney(safeDouble(snap, "debt")));
            row.setTextViewText(R.id.row_saldo_free,
                    FinanzasWidgetProvider.fmtMoney(safeDouble(snap, "free")));
            row.setTextViewText(R.id.row_saldo_inc,
                    "+" + FinanzasWidgetProvider.fmtMoney(safeDouble(snap, "monthInc")));
            row.setTextViewText(R.id.row_saldo_exp,
                    "−" + FinanzasWidgetProvider.fmtMoney(safeDouble(snap, "monthExp")));
            if (themed && theme != null) {
                try {
                    row.setTextColor(R.id.row_saldo_amount, theme.ink);
                    row.setTextColor(R.id.row_saldo_debt_label, theme.muted);
                    row.setTextColor(R.id.row_saldo_debt, theme.ink);
                    row.setTextColor(R.id.row_saldo_free_label, theme.muted);
                    row.setTextColor(R.id.row_saldo_free, theme.ink);
                    row.setTextColor(R.id.row_saldo_month_label, theme.muted);
                    row.setTextColor(R.id.row_saldo_inc, FinanzasWidgetProvider.GREEN);
                    row.setTextColor(R.id.row_saldo_exp, FinanzasWidgetProvider.RED);
                    row.setInt(R.id.row_saldo_divider, "setBackgroundColor", theme.divider);
                } catch (Exception ignored) {
                }
            }
            return row;
        }

        private void buildBills(String pkg, JSONObject snap,
                                FinanzasWidgetProvider.Theme theme, boolean themed) {
            org.json.JSONArray bills = snap.optJSONArray("bills");
            if (bills == null || bills.length() == 0) {
                items.add(new Item(TYPE_NOTE, noteViews(pkg, theme, themed, 901,
                        "Sin pagos próximos", FinanzasWidgetProvider.DEEP_LINK + "home")));
                return;
            }
            int shown = 0;
            for (int i = 0; i < bills.length() && shown < FinanzasWidgetProvider.MAX_BILLS; i++) {
                try {
                    JSONObject b = bills.optJSONObject(i);
                    if (b == null) continue;
                    RemoteViews row = new RemoteViews(pkg, R.layout.widget_row_bill);
                    row.setTextViewText(R.id.row_bill_name, safeString(b, "name"));
                    row.setTextViewText(R.id.row_bill_amt,
                            FinanzasWidgetProvider.fmtMoney(safeDouble(b, "amt")));
                    // "vence hoy" / "vencido hace..." en su propia segunda
                    // linea, para que el nombre y el monto no se corten.
                    row.setTextViewText(R.id.row_bill_due,
                            FinanzasWidgetProvider.daysLabel(safeInt(b, "days")));
                    if (themed && theme != null) {
                        try {
                            FinanzasWidgetProvider.tintBackground(row, R.id.row_bill_root, theme.surface);
                            row.setTextColor(R.id.row_bill_name, theme.ink);
                            row.setTextColor(R.id.row_bill_amt, theme.ink);
                            row.setTextColor(R.id.row_bill_due, theme.muted);
                        } catch (Exception ignored) {
                        }
                    }
                    row.setOnClickFillInIntent(R.id.row_bill_root,
                            fillIntent(FinanzasWidgetProvider.DEEP_LINK + "paybill?id="
                                    + Uri.encode(safeString(b, "id"))));
                    items.add(new Item(TYPE_BILL, row));
                    shown++;
                } catch (Exception ignored) {
                }
            }
            if (bills.length() > FinanzasWidgetProvider.MAX_BILLS) {
                items.add(new Item(TYPE_NOTE, noteViews(pkg, theme, themed, 902,
                        "+" + (bills.length() - FinanzasWidgetProvider.MAX_BILLS) + " más en la app",
                        FinanzasWidgetProvider.DEEP_LINK + "home")));
            }
        }

        private void buildTypeTab(String pkg, JSONObject snap, String type,
                                  FinanzasWidgetProvider.Theme theme, boolean themed) {
            int code = "income".equals(type) ? 0 : "expense".equals(type) ? 1 : 2;
            RemoteViews action = new RemoteViews(pkg, R.layout.widget_row_action);
            action.setTextViewText(R.id.row_action_btn, "＋ Registrar movimiento");
            if (themed) {
                FinanzasWidgetProvider.tintBackground(action, R.id.row_action_btn, theme.primary);
            }
            action.setOnClickFillInIntent(R.id.row_action_btn,
                    fillIntent(FinanzasWidgetProvider.DEEP_LINK + "newtx?type=" + type));
            items.add(new Item(TYPE_ACTION, action));

            org.json.JSONArray presets = null;
            try {
                presets = snap.optJSONArray("presets");
            } catch (Exception ignored) {
            }
            java.util.List<JSONObject> mine = new java.util.ArrayList<>();
            if (presets != null) {
                for (int i = 0; i < presets.length(); i++) {
                    try {
                        JSONObject p = presets.optJSONObject(i);
                        if (p != null && type.equals(safeString(p, "type"))) mine.add(p);
                    } catch (Exception ignored) {
                    }
                }
            }
            if (mine.isEmpty()) {
                items.add(new Item(TYPE_NOTE, noteViews(pkg, theme, themed, 910 + code,
                        "Aún no tienes atajos · toca para crear uno",
                        FinanzasWidgetProvider.DEEP_LINK + "newpre?type=" + type)));
                return;
            }
            RemoteViews chips = new RemoteViews(pkg, R.layout.widget_row_chips);
            int[] chipIds = {R.id.chip_a, R.id.chip_b, R.id.chip_c};
            String sign = "income".equals(type) ? "+"
                    : "expense".equals(type) ? "−" : "⇄";
            for (int i = 0; i < chipIds.length; i++) {
                if (i < mine.size() && i < FinanzasWidgetProvider.MAX_CHIPS) {
                    if (mine.size() > FinanzasWidgetProvider.MAX_CHIPS
                            && i == FinanzasWidgetProvider.MAX_CHIPS - 1) {
                        chips.setTextViewText(chipIds[i],
                                "+" + (mine.size() - (FinanzasWidgetProvider.MAX_CHIPS - 1)) + " más");
                        chips.setViewVisibility(chipIds[i], View.VISIBLE);
                        chips.setOnClickFillInIntent(chipIds[i],
                                fillIntent(FinanzasWidgetProvider.DEEP_LINK + "home"));
                        if (themed) FinanzasWidgetProvider.themeChip(chips, chipIds[i], theme);
                        continue;
                    }
                    JSONObject p = mine.get(i);
                    // Nombre en la primera linea y monto en la segunda
                    // (salto explicito, no un corte al azar): con el
                    // lineSpacingExtra del chip las dos lineas ya no se
                    // ven empalmadas.
                    String txt = sign + " " + safeString(p, "name");
                    if (safeDouble(p, "amt") > 0) {
                        txt += "\n" + FinanzasWidgetProvider.fmtMoney(safeDouble(p, "amt"));
                    }
                    chips.setTextViewText(chipIds[i], txt);
                    chips.setViewVisibility(chipIds[i], View.VISIBLE);
                    chips.setOnClickFillInIntent(chipIds[i],
                            fillIntent(FinanzasWidgetProvider.DEEP_LINK + "usepre?id="
                                    + Uri.encode(safeString(p, "id"))));
                    if (themed) FinanzasWidgetProvider.themeChip(chips, chipIds[i], theme);
                } else if (i == mine.size() && mine.size() < FinanzasWidgetProvider.MAX_CHIPS) {
                    chips.setTextViewText(chipIds[i], "＋ Nuevo");
                    chips.setViewVisibility(chipIds[i], View.VISIBLE);
                    chips.setOnClickFillInIntent(chipIds[i],
                            fillIntent(FinanzasWidgetProvider.DEEP_LINK + "newpre?type=" + type));
                    if (themed) FinanzasWidgetProvider.themeChip(chips, chipIds[i], theme);
                } else {
                    chips.setViewVisibility(chipIds[i], View.GONE);
                }
            }
            items.add(new Item(TYPE_CHIPS, chips));
        }

        private RemoteViews noteViews(String pkg, FinanzasWidgetProvider.Theme theme, boolean themed,
                                      int code, String text, String url) {
            RemoteViews row = new RemoteViews(pkg, R.layout.widget_row_note);
            row.setTextViewText(R.id.row_note_text, text);
            if (themed && theme != null) {
                try {
                    row.setTextColor(R.id.row_note_text, theme.muted);
                } catch (Exception ignored) {
                }
            }
            row.setOnClickFillInIntent(R.id.row_note_root, fillIntent(url));
            return row;
        }

        /** Enlace que se combina con la plantilla del provider al picar.
         *  Va SOLO como extra (sin accion ni datos): el fill-in nunca pisa
         *  la accion de la plantilla (ACTION_WIDGET_LINK) y el provider
         *  lee la URL de este extra. */
        private Intent fillIntent(String url) {
            Intent i = new Intent();
            i.putExtra(FinanzasWidgetProvider.EXTRA_LINK_URL, url);
            return i;
        }
    }
}
