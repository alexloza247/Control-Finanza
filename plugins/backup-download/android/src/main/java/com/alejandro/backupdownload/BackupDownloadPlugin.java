package com.alejandro.backupdownload;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Guarda un archivo directo en la carpeta pública de Descargas, dentro de una
 * subcarpeta exclusiva de la app ("Control de Finanzas") para no saturar
 * Descargas con los archivos sueltos.
 *
 * Sirve para el respaldo (.txt, texto) y para las fotos de los tickets
 * (imagen JPEG/PNG en base64): el llamador pasa el tipo MIME y si el
 * contenido viene en base64.
 *
 * En Android 10+ usa MediaStore (MediaStore.Downloads) con RELATIVE_PATH
 * "Download/Control de Finanzas/": no pide ningún permiso y el archivo
 * aparece ahí con el nombre pedido. En Android 9 o menor crea la subcarpeta
 * con mkdirs() dentro de la carpeta pública de Descargas (necesita el
 * permiso WRITE_EXTERNAL_STORAGE, declarado en el AndroidManifest del
 * plugin).
 *
 * Si algo falla, el método rechaza la promesa y la app cae de vuelta al menú
 * de compartir; nunca se dice que se guardó cuando no se guardó.
 */
@CapacitorPlugin(name = "BackupDownload")
public class BackupDownloadPlugin extends Plugin {

    @PluginMethod
    public void save(PluginCall call) {
        String filename = call.getString("filename", "respaldo-finanzas.txt");
        String data = call.getString("data");
        if (data == null) {
            call.reject("Falta el contenido del archivo");
            return;
        }
        String mime = call.getString("mime", "text/plain");
        if (mime == null || mime.isEmpty()) {
            mime = "text/plain";
        }
        boolean base64 = Boolean.TRUE.equals(call.getBoolean("base64", false));
        String folder = sanitizeFolder(call.getString("folder", ""));
        byte[] bytes;
        try {
            bytes =
                base64
                    ? Base64.decode(data, Base64.DEFAULT)
                    : data.getBytes(StandardCharsets.UTF_8);
        } catch (Exception e) {
            call.reject("No se pudo leer el contenido del archivo");
            return;
        }
        try {
            String savedName;
            String uri;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                Uri u = saveWithMediaStore(filename, bytes, mime, folder);
                uri = u.toString();
                savedName = queryDisplayName(u, filename);
            } else {
                File f = saveWithFileApi(filename, bytes, folder);
                uri = Uri.fromFile(f).toString();
                savedName = f.getName();
            }
            JSObject ret = new JSObject();
            ret.put("uri", uri);
            ret.put("name", savedName);
            call.resolve(ret);
        } catch (Exception e) {
            call.reject("No se pudo guardar en Descargas: " + e.getMessage());
        }
    }

    /** La carpeta es una ruta relativa dentro de Descargas, sin ".." ni barras de más. */
    private String sanitizeFolder(String folder) {
        if (folder == null) {
            return "";
        }
        String[] parts = folder.trim().split("/");
        StringBuilder out = new StringBuilder();
        for (String p : parts) {
            String s = p.trim();
            if (s.isEmpty() || s.equals(".") || s.equals("..")) {
                continue;
            }
            if (out.length() > 0) {
                out.append('/');
            }
            out.append(s);
        }
        return out.toString();
    }

    private Uri saveWithMediaStore(String filename, byte[] bytes, String mime, String folder)
        throws Exception {
        ContentResolver resolver = getContext().getContentResolver();
        ContentValues values = new ContentValues();
        values.put(MediaStore.Downloads.DISPLAY_NAME, filename);
        values.put(MediaStore.Downloads.MIME_TYPE, mime);
        String path = Environment.DIRECTORY_DOWNLOADS;
        if (!folder.isEmpty()) {
            path += "/" + folder;
        }
        values.put(MediaStore.Downloads.RELATIVE_PATH, path);
        Uri uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
        if (uri == null) {
            throw new Exception("MediaStore no devolvió una ubicación");
        }
        try {
            OutputStream os = resolver.openOutputStream(uri);
            if (os == null) {
                throw new Exception("No se pudo abrir el archivo en Descargas");
            }
            try {
                os.write(bytes);
            } finally {
                os.close();
            }
        } catch (Exception e) {
            try {
                resolver.delete(uri, null, null);
            } catch (Exception ignored) {
            }
            throw e;
        }
        return uri;
    }

    private String queryDisplayName(Uri uri, String fallback) {
        Cursor c = null;
        try {
            c =
                getContext()
                    .getContentResolver()
                    .query(uri, new String[] { MediaStore.Downloads.DISPLAY_NAME }, null, null, null);
            if (c != null && c.moveToFirst()) {
                String name = c.getString(0);
                if (name != null && !name.isEmpty()) {
                    return name;
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (c != null) {
                c.close();
            }
        }
        return fallback;
    }

    private File saveWithFileApi(String filename, byte[] bytes, String folder) throws Exception {
        File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        if (dir == null) {
            throw new Exception("No hay carpeta de Descargas");
        }
        if (!folder.isEmpty()) {
            dir = new File(dir, folder);
        }
        if (!dir.exists() && !dir.mkdirs()) {
            throw new Exception("No se pudo crear la carpeta de Descargas");
        }
        File file = new File(dir, filename);
        FileOutputStream fos = new FileOutputStream(file, false);
        try {
            fos.write(bytes);
        } finally {
            fos.close();
        }
        return file;
    }
}
