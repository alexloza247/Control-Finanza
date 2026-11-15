package com.alejandro.backupdownload;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
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
 * Guarda un archivo de texto directo en la carpeta pública de Descargas.
 *
 * En Android 10+ usa MediaStore (MediaStore.Downloads): no pide ningún permiso
 * y el archivo aparece en Descargas con el nombre pedido. En Android 9 o menor
 * escribe directo en la carpeta pública de Descargas (necesita el permiso
 * WRITE_EXTERNAL_STORAGE, declarado en el AndroidManifest del plugin).
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
            call.reject("Falta el contenido del respaldo");
            return;
        }
        try {
            String savedName;
            String uri;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                Uri u = saveWithMediaStore(filename, data);
                uri = u.toString();
                savedName = queryDisplayName(u, filename);
            } else {
                File f = saveWithFileApi(filename, data);
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

    private Uri saveWithMediaStore(String filename, String data) throws Exception {
        ContentResolver resolver = getContext().getContentResolver();
        ContentValues values = new ContentValues();
        values.put(MediaStore.Downloads.DISPLAY_NAME, filename);
        values.put(MediaStore.Downloads.MIME_TYPE, "text/plain");
        values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
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
                os.write(data.getBytes(StandardCharsets.UTF_8));
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

    private File saveWithFileApi(String filename, String data) throws Exception {
        File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        if (dir == null) {
            throw new Exception("No hay carpeta de Descargas");
        }
        if (!dir.exists() && !dir.mkdirs()) {
            throw new Exception("No se pudo crear la carpeta de Descargas");
        }
        File file = new File(dir, filename);
        FileOutputStream fos = new FileOutputStream(file, false);
        try {
            fos.write(data.getBytes(StandardCharsets.UTF_8));
        } finally {
            fos.close();
        }
        return file;
    }
}
