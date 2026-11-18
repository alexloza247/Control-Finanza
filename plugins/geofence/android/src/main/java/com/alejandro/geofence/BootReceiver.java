package com.alejandro.geofence;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingClient;
import com.google.android.gms.location.GeofencingRequest;
import com.google.android.gms.location.LocationServices;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Después de reiniciar el teléfono, las geocercas registradas se pierden:
 * este receptor lee la lista guardada por GeofencePlugin y las vuelve a
 * registrar. Si no hay comercios activos, no hace nada.
 */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) {
            return;
        }
        String action = intent.getAction();
        if (
            !Intent.ACTION_BOOT_COMPLETED.equals(action)
                && !"android.intent.action.QUICKBOOT_POWERON".equals(action)
        ) {
            return;
        }
        try {
            SharedPreferences prefs =
                context.getSharedPreferences(GeofencePlugin.PREFS, Context.MODE_PRIVATE);
            String json = prefs.getString(GeofencePlugin.KEY_PLACES, "[]");
            JSONArray arr = new JSONArray(json);
            if (arr.length() == 0) {
                return;
            }
            List<Geofence> fences = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                float radius = (float) o.optDouble("radius", 100);
                if (radius < 30) radius = 30;
                if (radius > 500) radius = 500;
                Geofence gf =
                    new Geofence.Builder()
                        .setRequestId(o.getString("id"))
                        .setCircularRegion(o.getDouble("lat"), o.getDouble("lng"), radius)
                        .setExpirationDuration(Geofence.NEVER_EXPIRE)
                        .setTransitionTypes(
                            Geofence.GEOFENCE_TRANSITION_ENTER | Geofence.GEOFENCE_TRANSITION_EXIT
                        )
                        .setLoiteringDelay(180000)
                        .setNotificationResponsiveness(60000)
                        .build();
                fences.add(gf);
            }
            GeofencingRequest req =
                new GeofencingRequest.Builder()
                    .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
                    .addGeofences(fences)
                    .build();
            GeofencingClient client = LocationServices.getGeofencingClient(context);
            client.addGeofences(req, GeofencePlugin.geofencePendingIntent(context));
        } catch (Exception ignored) {}
    }
}
