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
        return new WidgetFactory(getApplicationContext());
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
            reload();
        }

        @Override
        public void onDataSetChanged() {
            reload();
        }

        @Override
        public void onDestroy() {
            items.clear();
        }

        @Override
        public int getCount() {
            return items.size();
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
            if (position < 0 || position >= items.size()) return null;
            return items.get(position).views;
        }

        @Override
        public RemoteViews getLoadingView() {
            return null;
        }

        private void reload() {
            items.clear();
            int tab = ctx.getSharedPreferences(FinanzasWidgetProvider.WIDGET_PREFS, Context.MODE_PRIVATE)
                    .getInt(FinanzasWidgetProvider.KEY_TAB, FinanzasWidgetProvider.TAB_SALDO);
            JSONObject snap = FinanzasWidgetProvider.readSnapshot(ctx);
            FinanzasWidgetProvider.Theme theme = FinanzasWidgetProvider.resolveTheme(ctx, snap);
            boolean themed = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S;
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
                    FinanzasWidgetProvider.fmtMoney(snap.optDouble("avail", 0)));
            String detail = "Deuda: " + FinanzasWidgetProvider.fmtMoney(snap.optDouble("debt", 0))
                    + "\nMes: +" + FinanzasWidgetProvider.fmtMoney(snap.optDouble("monthInc", 0))
                    + "  −" + FinanzasWidgetProvider.fmtMoney(snap.optDouble("monthExp", 0));
            row.setTextViewText(R.id.row_saldo_detail, detail);
            if (themed) {
                row.setTextColor(R.id.row_saldo_amount, theme.ink);
                row.setTextColor(R.id.row_saldo_detail, theme.muted);
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
                JSONObject b = bills.optJSONObject(i);
                if (b == null) continue;
                RemoteViews row = new RemoteViews(pkg, R.layout.widget_row_bill);
                String label = b.optString("name", "") + " · "
                        + FinanzasWidgetProvider.daysLabel(b.optInt("days", 0));
                row.setTextViewText(R.id.row_bill_name, label);
                row.setTextViewText(R.id.row_bill_amt,
                        FinanzasWidgetProvider.fmtMoney(b.optDouble("amt", 0)));
                if (themed) {
                    FinanzasWidgetProvider.tintBackground(row, R.id.row_bill_root, theme.surface);
                    row.setTextColor(R.id.row_bill_name, theme.ink);
                    row.setTextColor(R.id.row_bill_amt, theme.ink);
                }
                row.setOnClickFillInIntent(R.id.row_bill_root,
                        fillIntent(FinanzasWidgetProvider.DEEP_LINK + "paybill?id="
                                + Uri.encode(b.optString("id", ""))));
                items.add(new Item(TYPE_BILL, row));
                shown++;
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

            org.json.JSONArray presets = snap.optJSONArray("presets");
            java.util.List<JSONObject> mine = new java.util.ArrayList<>();
            if (presets != null) {
                for (int i = 0; i < presets.length(); i++) {
                    JSONObject p = presets.optJSONObject(i);
                    if (p != null && type.equals(p.optString("type"))) mine.add(p);
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
                    String txt = sign + " " + p.optString("name", "");
                    if (p.optDouble("amt", 0) > 0) {
                        txt += " · " + FinanzasWidgetProvider.fmtMoney(p.optDouble("amt", 0));
                    }
                    chips.setTextViewText(chipIds[i], txt);
                    chips.setViewVisibility(chipIds[i], View.VISIBLE);
                    chips.setOnClickFillInIntent(chipIds[i],
                            fillIntent(FinanzasWidgetProvider.DEEP_LINK + "usepre?id="
                                    + Uri.encode(p.optString("id", ""))));
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
            if (themed) {
                row.setTextColor(R.id.row_note_text, theme.muted);
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
