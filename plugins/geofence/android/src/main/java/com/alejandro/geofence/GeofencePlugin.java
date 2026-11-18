package com.alejandro.geofence;

import android.Manifest;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import androidx.core.content.ContextCompat;
import com.getcapacitor.JSObject;
import com.getcapacitor.PermissionState;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingClient;
import com.google.android.gms.location.GeofencingRequest;
import com.google.android.gms.location.LocationServices;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Recordatorios por ubicación con geocercas (Google Play Services).
 *
 * La app web manda la lista de comercios activos (JSON) y este plugin registra
 * una geocerca circular por cada uno (EXIT, con retardo de permanencia para no
 * avisar al pasar por enfrente). Cuando el usuario sale de un comercio tras
 * haber estado dentro un rato, GeofenceReceiver publica la notificación.
 *
 * La lista se guarda en SharedPreferences ("geofence_data") para que
 * BootReceiver vuelva a registrar las geocercas después de reiniciar el
 * teléfono. Si Play Services o los permisos faltan, nada explota: los métodos
 * resuelven con el estado y la app lo muestra en Configuración.
 */
@CapacitorPlugin(
    name = "Geofence",
    permissions = {
        @Permission(
            alias = "location",
            strings = {
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            }
        ),
        @Permission(alias = "notifications", strings = {Manifest.permission.POST_NOTIFICATIONS})
    }
)
public class GeofencePlugin extends Plugin {

    public static final String PREFS = "geofence_data";
    public static final String KEY_PLACES = "places_json";
    public static final String KEY_INSIDE = "inside_json";

    /** PendingIntent compartido por el plugin y BootReceiver (mismo código e intent = mismo PendingIntent). */
    public static PendingIntent geofencePendingIntent(Context ctx) {
        Intent intent = new Intent(ctx, GeofenceReceiver.class);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return PendingIntent.getBroadcast(ctx, 0, intent, flags);
    }

    private GeofencingClient client() {
        return LocationServices.getGeofencingClient(getContext());
    }

    private JSObject permStatusObject() {
        Context ctx = getContext();
        boolean fine =
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        boolean bg = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            bg =
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                    == PackageManager.PERMISSION_GRANTED;
        }
        boolean notif = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notif =
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS)
                    == PackageManager.PERMISSION_GRANTED;
        }
        boolean play =
            GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(ctx)
                == ConnectionResult.SUCCESS;
        JSObject r = new JSObject();
        r.put("fine", fine);
        r.put("background", bg);
        r.put("notifications", notif);
        r.put("playServices", play);
        return r;
    }

    @PluginMethod
    public void permStatus(PluginCall call) {
        call.resolve(permStatusObject());
    }

    @PluginMethod
    public void requestLocation(PluginCall call) {
        boolean fineGranted = getPermissionState("location") == PermissionState.GRANTED;
        boolean notifOk =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || getPermissionState("notifications") == PermissionState.GRANTED;
        if (fineGranted && notifOk) {
            call.resolve(permStatusObject());
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissionForAliases(
                new String[] { "location", "notifications" },
                call,
                "locationPermsCallback"
            );
        } else {
            requestPermissionForAlias("location", call, "locationPermsCallback");
        }
    }

    @PermissionCallback
    public void locationPermsCallback(PluginCall call) {
        call.resolve(permStatusObject());
    }

    @PluginMethod
    public void openSettings(PluginCall call) {
        try {
            Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            i.setData(Uri.parse("package:" + getContext().getPackageName()));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(i);
            call.resolve();
        } catch (Exception e) {
            call.reject("No se pudieron abrir los ajustes");
        }
    }

    @PluginMethod
    public void syncPlaces(PluginCall call) {
        String json = call.getString("placesJson", "[]");
        try {
            JSONArray arr = new JSONArray(json);
            List<Geofence> fences = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                String id = o.getString("id");
                double lat = o.getDouble("lat");
                double lng = o.getDouble("lng");
                float radius = (float) o.optDouble("radius", 100);
                if (radius < 30) radius = 30;
                if (radius > 500) radius = 500;
                Geofence gf =
                    new Geofence.Builder()
                        .setRequestId(id)
                        .setCircularRegion(lat, lng, radius)
                        .setExpirationDuration(Geofence.NEVER_EXPIRE)
                        .setTransitionTypes(
                            Geofence.GEOFENCE_TRANSITION_ENTER | Geofence.GEOFENCE_TRANSITION_EXIT
                        )
                        .setLoiteringDelay(180000)
                        .setNotificationResponsiveness(60000)
                        .build();
                fences.add(gf);
            }
            // Persistir para BootReceiver antes de tocar las geocercas.
            getContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_PLACES, json)
                .apply();
            final List<Geofence> toAdd = fences;
            // Quitar las anteriores y luego registrar las nuevas (encadenado
            // para que el remove no borre las recién agregadas).
            client()
                .removeGeofences(geofencePendingIntent(getContext()))
                .addOnCompleteListener(task -> {
                    if (toAdd.isEmpty()) {
                        call.resolve();
                        return;
                    }
                    GeofencingRequest req =
                        new GeofencingRequest.Builder()
                            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
                            .addGeofences(toAdd)
                            .build();
                    client()
                        .addGeofences(req, geofencePendingIntent(getContext()))
                        .addOnSuccessListener(a -> call.resolve())
                        .addOnFailureListener(e ->
                            call.reject("No se pudieron activar las geocercas: " + e.getMessage())
                        );
                });
        } catch (Exception e) {
            call.reject("Error al sincronizar geocercas: " + e.getMessage());
        }
    }

    @PluginMethod
    public void clearAll(PluginCall call) {
        try {
            getContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_PLACES, "[]")
                .putString(KEY_INSIDE, "{}")
                .apply();
            client()
                .removeGeofences(geofencePendingIntent(getContext()))
                .addOnCompleteListener(task -> call.resolve());
        } catch (Exception e) {
            call.reject("No se pudieron quitar las geocercas: " + e.getMessage());
        }
    }
}
