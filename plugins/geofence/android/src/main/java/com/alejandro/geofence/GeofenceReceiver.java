package com.alejandro.geofence;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingEvent;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Recibe las transiciones de las geocercas (ENTER/EXIT) aunque la app esté
 * cerrada.
 *
 * Lleva el estado adentro/afuera de cada comercio en SharedPreferences
 * (clave inside_json: {id: marcaDeTiempoDeEntrada}). En un EXIT solo avisa si
 * la visita duró al menos MIN_DWELL_MS (así pasar por enfrente de la tienda en
 * la calle no genera avisos). El aviso abre la app y deja una acción pendiente
 * en CapacitorStorage (misma clave que lee el widget), para que la app abra el
 * formulario de gasto con la nota prellenada con el nombre del comercio.
 */
public class GeofenceReceiver extends BroadcastReceiver {

    private static final String CHANNEL = "comercios";
    private static final long MIN_DWELL_MS = 120000; // 2 minutos dentro = visita real
    private static final String PENDING_KEY = "finanzas_geofence_pending";
    private static final String CAP_PREFS = "CapacitorStorage";

    @Override
    public void onReceive(Context context, Intent intent) {
        GeofencingEvent event = GeofencingEvent.fromIntent(intent);
        if (event == null || event.hasError()) {
            return;
        }
        int type = event.getGeofenceTransition();
        List<Geofence> fences = event.getTriggeringGeofences();
        if (fences == null) {
            return;
        }
        SharedPreferences prefs =
            context.getSharedPreferences(GeofencePlugin.PREFS, Context.MODE_PRIVATE);
        JSONObject inside;
        try {
            inside = new JSONObject(prefs.getString(GeofencePlugin.KEY_INSIDE, "{}"));
        } catch (Exception e) {
            inside = new JSONObject();
        }
        long now = System.currentTimeMillis();
        for (Geofence gf : fences) {
            String id = gf.getRequestId();
            if (type == Geofence.GEOFENCE_TRANSITION_ENTER) {
                inside.put(id, now);
            } else if (type == Geofence.GEOFENCE_TRANSITION_EXIT) {
                long enteredAt = inside.optLong(id, 0);
                inside.remove(id);
                if (enteredAt > 0 && now - enteredAt >= MIN_DWELL_MS) {
                    String name = placeName(prefs, id);
                    notifyExit(context, id, name);
                }
            }
        }
        prefs.edit().putString(GeofencePlugin.KEY_INSIDE, inside.toString()).apply();
    }

    private String placeName(SharedPreferences prefs, String id) {
        try {
            String json = prefs.getString(GeofencePlugin.KEY_PLACES, "[]");
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                if (id.equals(o.optString("id"))) {
                    return o.optString("name", "un comercio");
                }
            }
        } catch (Exception ignored) {}
        return "un comercio";
    }

    private void notifyExit(Context context, String id, String name) {
        NotificationManager nm =
            (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch =
                new NotificationChannel(
                    CHANNEL, "Recordatorios por ubicación", NotificationManager.IMPORTANCE_DEFAULT
                );
            ch.setDescription("Avisos al salir de un comercio registrado");
            nm.createNotificationChannel(ch);
        }
        // Acción pendiente para la app: al abrirse (o volver al frente) lee esta
        // clave de CapacitorStorage y abre el formulario de gasto.
        try {
            JSONObject pending = new JSONObject();
            pending.put("place", name);
            pending.put("ts", System.currentTimeMillis());
            context
                .getSharedPreferences(CAP_PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(PENDING_KEY, pending.toString())
                .apply();
        } catch (Exception ignored) {}
        Intent launch = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
        if (launch == null) {
            return;
        }
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int pflags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pflags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent pi = PendingIntent.getActivity(context, 0, launch, pflags);
        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            b = new Notification.Builder(context, CHANNEL);
        } else {
            b = new Notification.Builder(context);
        }
        b.setSmallIcon(context.getApplicationInfo().icon)
            .setContentTitle("Saliste de " + name)
            .setContentText("¿Quieres registrar un gasto? Toca para abrir el formulario.")
            .setAutoCancel(true)
            .setContentIntent(pi);
        int notifId = 2000 + Math.abs(id.hashCode() % 1000);
        nm.notify(notifId, b.build());
    }
}
