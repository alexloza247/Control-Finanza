# backup-download (plugin local)

Guarda un archivo directo en la carpeta **Descargas** del teléfono, dentro de
la subcarpeta exclusiva **Control de Finanzas** (para no saturar Descargas con
archivos sueltos), sin abrir el menú de compartir. Lo usa el botón de respaldo
de Control de Finanzas (`.txt`) y el botón **Descargar** del visor de la foto
del ticket (imagen JPEG/PNG en base64).

## Por qué existe

`@capacitor/filesystem` (v7 y v8) no tiene carpeta de Descargas y sus carpetas
públicas (`Documents`, `ExternalStorage`) están bloqueadas por el almacenamiento
con alcance (scoped storage) en Android 11+: escribir ahí con `writeFile` falla.
La vía correcta en Android moderno es MediaStore, y ningún plugin oficial de
Capacitor la expone para archivos de texto o imágenes, así que este plugin lo
hace directo.

## Cómo funciona

- **Android 10+:** inserta el archivo con `MediaStore.Downloads` (nombre, tipo
  MIME y ruta `Download/Control de Finanzas/`). No pide ningún permiso y el
  archivo aparece ahí con el nombre pedido (si ya existe uno igual, Android le
  agrega un sufijo y el plugin devuelve el nombre real guardado).
- **Android 9 o menor:** crea la subcarpeta con `mkdirs()` dentro de la carpeta
  pública de Descargas y escribe ahí (permiso `WRITE_EXTERNAL_STORAGE`,
  declarado aquí con `maxSdkVersion 28`).
- Si algo falla, el método `save` rechaza la promesa y la app cae de vuelta al
  menú de compartir; nunca se dice que se guardó cuando no se guardó.

## Uso (desde `index.html`, sin importar nada)

```js
// Respaldo (texto)
window.Capacitor.Plugins.BackupDownload.save({
  filename: 'respaldo-finanzas.txt',
  data: JSON.stringify(S),
  folder: 'Control de Finanzas',
}).then(function (res) {
  // res.uri, res.name (nombre real guardado en Descargas/Control de Finanzas)
});

// Foto del ticket (imagen en base64 + su MIME)
window.Capacitor.Plugins.BackupDownload.save({
  filename: 'ticket-2026-11-15.jpg',
  data: base64SinPrefijo,
  base64: true,
  mime: 'image/jpeg',
  folder: 'Control de Finanzas',
});
```

`mime` es `text/plain` por defecto y `folder` es opcional (sin carpeta, el
archivo queda directo en Descargas).

## Instalación

Es una dependencia local del `package.json` raíz
(`"backup-download": "file:./plugins/backup-download"`). El workflow de
GitHub Actions hace `npm install` y `npx cap sync`, y `cap sync` registra el
plugin en el proyecto Android automáticamente. No hay que tocar el workflow.
