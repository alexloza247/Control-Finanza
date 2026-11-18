# geofence-plugin (plugin local)

Recordatorios por ubicación con **geocercas** (Google Play Services
`GeofencingClient`): la app registra comercios con su radio y, cuando sales de
uno tras haber estado dentro un rato, llega una notificación para registrar el
gasto. Funciona aunque la app esté cerrada.

## Cómo entra al APK (sin tocar el workflow)

El workflow genera el proyecto Android con `npx cap add android` + `npx cap
sync` en cada build. Este plugin se declara en el `package.json` raíz como
dependencia local (`"geofence-plugin": "file:./plugins/geofence"`), igual que
`backup-download`: `npm install` lo enlaza y `cap sync` incluye su código
Android y su manifiesto (permisos y receptores) en el build. No hace falta
ningún paso extra en `.github/workflows/build-apk.yml`.

## Qué hace

- `syncPlaces({placesJson})`: registra una geocerca circular por cada comercio
  activo (EXIT, sin expiración, retardo de permanencia de 3 min para no avisar
  al pasar por enfrente) y quita las que ya no están. La lista se guarda en
  SharedPreferences para que `BootReceiver` las vuelva a registrar tras
  reiniciar el teléfono.
- `clearAll()`: quita todas las geocercas y limpia el estado.
- `permStatus()`: devuelve `{fine, background, notifications, playServices}`.
- `requestLocation()`: pide el permiso de ubicación (y el de notificaciones en
  Android 13+) y resuelve con el estado resultante.
- `openSettings()`: abre la pantalla de ajustes de la app (para conceder la
  ubicación en segundo plano, que en Android 11+ solo se otorga desde ahí).

## Permisos (AndroidManifest del plugin)

`ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, `ACCESS_BACKGROUND_LOCATION`,
`RECEIVE_BOOT_COMPLETED`, `POST_NOTIFICATIONS`.

## Flujo del aviso

`GeofenceReceiver` lleva el estado adentro/afuera de cada comercio en
SharedPreferences (`inside_json`). En un EXIT solo avisa si la visita duró al
menos 2 minutos. La notificación abre la app y deja una acción pendiente en
`CapacitorStorage` (clave `finanzas_geofence_pending`, el mismo mecanismo que
usa el widget): la app la lee al arrancar o al volver al frente y abre el
formulario de gasto con la nota prellenada con el nombre del comercio.

## Notas

- El Java no se compila localmente: la primera compilación real es el build de
  GitHub Actions. Revisar con cuidado imports y niveles de API al modificarlo.
- Sin Play Services o sin permisos, nada explota: la app sigue funcionando y
  Configuración muestra el estado.
