# Widget de inicio 5x2 — Control de Finanzas

Widget de pantalla principal de 5x2 con 5 pestañas **solo con iconos** (sin textos):

| Pestaña | Icono | Contenido |
|---|---|---|
| Saldo | cartera | El recuadro de saldo de la app: dinero disponible, deuda en tarjetas, ingresos y gastos del mes |
| Próximos pagos | calendario | Los próximos pagos (Pagos próximos) con fecha y monto; al picar uno se abre su "Revisar y pagar" |
| Ingreso | flecha entrando | Botón "Registrar movimiento" + atajos de ingreso |
| Gasto | flecha saliendo | Botón "Registrar movimiento" + atajos de gasto (p. ej. "Pasaje camión · $7.00") |
| Transferencia | flechas de intercambio | Botón "Registrar movimiento" + atajos de transferencia |

Al picar un atajo se abre la app con el formulario de movimiento prellenado
con ese atajo, y ahí se confirma el registro. El widget **no registra nada en
segundo plano**: el toque siempre abre la app. Lo mismo para "Registrar
movimiento" (abre el form nuevo del tipo de la pestaña) y para crear un atajo
nuevo ("＋ Nuevo" en la pestaña, o el aviso cuando aún no hay atajos).

**Tamaños (ajuste 2026-11-11):** pestañas de 52dp e iconos grandes, saldo a
34sp, líneas de pagos a 16sp en filas de 46dp de alto, botón de registro de 46dp (en línea con los
demás elementos) y atajos a 14sp con máximo 2 chips por fila (el tercero pasa
a una segunda fila), para que el texto se lea completo sin cortarse. El
detalle del saldo va en dos líneas (Deuda en una, Mes en otra) para que nada
se trunque.

**Desplazamiento (2026-11-11):** el contenido se desliza arriba/abajo: la
fila de pestañas queda fija arriba y debajo va una lista (ListView) con el
contenido de la pestaña. Un ScrollView no desplaza dentro de un widget, por
eso la lista la alimenta `FinanzasWidgetService` (RemoteViewsService +
RemoteViewsFactory); cada elemento lleva su propio enlace y el provider pone
la plantilla de toque. Así el "+ Nuevo" y lo que no quepa en el alto del
widget se alcanza deslizando.

**Toque (2026-11-11):** el toque en un elemento de la lista es un broadcast
al provider (`ACTION_WIDGET_LINK`): el fill-in solo aporta el enlace como
extra, sin acción ni datos, para no pisar la acción de la plantilla. Al
recibirlo, el provider guarda el enlace como acción pendiente en
`CapacitorStorage` (`finanzas_widget_pending_action`) y abre MainActivity
con el deep link. La app lo atiende por tres vías: acción pendiente (la lee
al arrancar y al volver al frente, y la borra al atenderla), URL de
lanzamiento (`getLaunchUrl`, con reintentos si viene vacía) y evento
`appUrlOpen`; un mismo enlace no se atiende dos veces seguidas. Así el
formulario se abre aunque Capacitor no entregue la URL del intent (antes
la app se abría pero no pasaba nada).

**Pagos en recuadro (2026-11-11):** cada pago próximo va dentro de su propio
recuadro redondeado (mismo fondo que los atajos), para que no se pierda con
el fondo del widget.

## Tema: el widget sigue los colores de la app

El resumen del puente incluye `theme:{primary, secondary, background, nav,
mode, dark}` (los colores personalizados de Configuración y el modo
Claro / Oscuro / Automático, con `dark` calculado igual que en la app).
El provider lo lee y aplica:

- **Fondo del widget:** el color de fondo elegido (en modo oscuro, `#0E1514`).
- **Botón "Registrar movimiento":** el color principal.
- **Pestaña activa:** un tinte claro del color principal (oscuro: principal
  sobre la superficie oscura); su icono va en color principal, los demás en
  tono apagado.
- **Recuadros de pagos y atajos:** superficie del tema (claro: fondo
  mezclado con un poco de principal; oscuro: `#17211F`).
- **Textos:** principal oscuro en claro (`#14211F`), claro en oscuro
  (`#E8F0EE`); detalles en tono apagado.
- **Modo Automático:** el widget sigue el modo oscuro del sistema
  (nativo), así que cambia aunque la app no se haya abierto.

Si el resumen no trae tema (versión vieja de la app), se usan los colores
por defecto de la app (principal `#1F7A5C`, secundario `#2A9D8F`, fondo
`#F2F5F4`). El tinte de fondos usa `setBackgroundTintList` de RemoteViews,
disponible desde API 31: en Android más viejo el widget mantiene el aspecto
fijo de siempre (sin tema), sin romperse. Nota: al teñir un fondo redondeado
se conserva su forma pero su borde fino de 1dp toma el mismo color y deja de
verse; la forma redondeada no cambia.

## Cómo funciona el puente de datos

El widget nativo no puede leer el `localStorage` del WebView. Por eso la app
escribe un resumen en `@capacitor/preferences` cada vez que guarda datos
(`save()`) y una vez al arrancar:

- Clave: `finanzas_widget_snapshot`
- Almacén nativo: `SharedPreferences` `"CapacitorStorage"` (el que usa el
  plugin Preferences; si el plugin cambia de archivo, hay que actualizar la
  constante `CAP_PREFS` en `FinanzasWidgetProvider.java`).
- Contenido JSON: `{updated, avail, debt, monthInc, monthExp, theme:{primary,
  secondary, background, nav, mode, dark}, presets:[{id, type, name, amt}],
  bills:[{id, name, amt, next, days, freq}]}` (máximo 5 próximos pagos,
  ordenados por fecha).

El widget se actualiza al tocar una pestaña y cada 30 min
(`updatePeriodMillis`). Si la app guardó datos nuevos, aparecen en la
siguiente actualización.

## Enlaces (deep links)

Esquema `controlfinanzas://widget/...`, atendido en `index.html`
(`registerWidgetLinks`, con `App.addListener('appUrlOpen')` y
`App.getLaunchUrl()` para el lanzamiento en frío):

- `usepre?id=` → form de movimiento prellenado con ese atajo
- `newtx?type=income|expense|transfer` → form nuevo de ese tipo
- `paybill?id=` → "Revisar y pagar" de ese pago próximo
- `newpre?type=` → form de atajo nuevo de ese tipo
- `home` → abrir la app en Resumen

## Archivos

```
widget/
  README.md                  (este archivo)
  scripts/install-widget.py  (copia el código nativo y parchea el manifiesto)
  android/src/main/
    java/com/alejandro/controlfinanzas/widget/FinanzasWidgetProvider.java
    java/com/alejandro/controlfinanzas/widget/FinanzasWidgetService.java
      (RemoteViewsService: alimenta la lista desplazable del contenido)
    res/xml/finanzas_widget_info.xml
    res/layout/widget_finanzas.xml        (fila de pestañas + contenido)
    res/layout/widget_row_saldo.xml
    res/layout/widget_row_bill.xml
    res/layout/widget_row_action.xml      (botón Registrar movimiento)
    res/layout/widget_row_chips.xml       (atajos, máx. 3 por pestaña)
    res/layout/widget_row_note.xml
    res/drawable/widget_bg.xml, widget_tab_selected.xml, widget_btn.xml,
      widget_chip_bg.xml
    res/drawable/ic_wallet.xml, ic_calendar.xml, ic_arrow_down.xml,
      ic_arrow_up.xml, ic_swap.xml
    res/values/widget_strings.xml
```

## Build (paso nuevo en el workflow)

El repo no tiene la carpeta `android/` commiteada: el workflow la genera con
`npx cap add android` + `npx cap sync`. Después de ese paso hay que correr:

```yaml
- name: Install Finance Widget (native)
  run: python3 widget/scripts/install-widget.py
```

Ese paso está en la copia del workflow en
`~/workspace/control-finanzas-workflow-widget/build-apk.yml`, que Alejandro
debe subir a mano a `.github/workflows/build-apk.yml` (la integración no puede
subir archivos de workflow). El script es idempotente y no duplica el receiver,
el servicio ni el intent-filter si ya existen.

## Límites conocidos (probar en el teléfono)

- El widget solo se probó en compilación lógica local (XML, puente de datos y
  enlaces); **la compilación Gradle y el comportamiento en el teléfono aún no
  están verificados**: hay que instalar el APK del build de Actions y agregar
  el widget desde el launcher.
- El widget muestra máximo 2 pagos próximos y 3 atajos por pestaña; el resto
  se ve en la app ("+N más").
- Si la app nunca se abrió después de instalar, el widget pide abrir la app
  para cargar los datos (el resumen se escribe al arrancar y al guardar).
- Los datos del widget pueden tardar hasta 30 min en refrescarse solos; tocar
  una pestaña lo actualiza al momento.
