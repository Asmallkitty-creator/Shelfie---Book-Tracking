package com.shelfie.app;

import android.Manifest;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.UriPermission;
import android.database.Cursor;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import androidx.activity.result.ActivityResult;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PermissionState;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.ActivityCallback;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Backup files for Shelfie.
 *  - saveToDownloads: saves into the public Downloads folder (original behaviour).
 *  - pickFolder / getFolder / clearFolder: lets the person choose a folder (Android's
 *    folder picker) that the app keeps permission for.
 *  - saveToFolder / listBackups / readFile / deleteFile: work inside that folder.
 *  - pickBackupFile: file picker that only shows XML / JSON files.
 * Every save is read back from disk and reported with its size and SHA-256 so the
 * page can prove the file on disk is exactly what it sent.
 */
@CapacitorPlugin(
    name = "ShelfieFiles",
    permissions = {
        // Only used on Android 9 and older. Android 10+ needs no storage permission:
        // Downloads is written through MediaStore and a chosen folder is its own grant.
        @Permission(alias = "storage", strings = {Manifest.permission.WRITE_EXTERNAL_STORAGE})
    }
)
public class ShelfieFilesPlugin extends Plugin {

    @PluginMethod
    public void saveToDownloads(PluginCall call) {
        String name = call.getString("name");
        String data = call.getString("data");
        String mime = call.getString("mime", "text/xml");
        if (name == null || data == null || name.trim().isEmpty()) {
            call.reject("Missing file name or data");
            return;
        }
        name = name.replaceAll("[\\\\/:*?\"<>|]", "_");
        try {
            byte[] bytes = data.getBytes(StandardCharsets.UTF_8);
            ContentResolver cr = getContext().getContentResolver();
            String shownName = name;
            String sha;
            long size;

            if (Build.VERSION.SDK_INT >= 29) {
                ContentValues cv = new ContentValues();
                cv.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
                cv.put(MediaStore.MediaColumns.MIME_TYPE, mime);
                cv.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                cv.put(MediaStore.MediaColumns.IS_PENDING, 1);
                Uri uri = cr.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
                if (uri == null) throw new IOException("Could not create the file in Downloads");
                try (OutputStream os = cr.openOutputStream(uri, "w")) {
                    if (os == null) throw new IOException("Could not open the file for writing");
                    os.write(bytes);
                    os.flush();
                }
                ContentValues done = new ContentValues();
                done.put(MediaStore.MediaColumns.IS_PENDING, 0);
                cr.update(uri, done, null, null);

                // Android renames the file if one with the same name exists.
                try (Cursor c = cr.query(uri, new String[]{MediaStore.MediaColumns.DISPLAY_NAME}, null, null, null)) {
                    if (c != null && c.moveToFirst()) shownName = c.getString(0);
                }
                // Read it back from disk.
                try (InputStream in = cr.openInputStream(uri)) {
                    if (in == null) throw new IOException("Could not read the saved file back");
                    long[] n = new long[1];
                    sha = sha256(in, n);
                    size = n[0];
                }
            } else {
                // Android 9 and older: write straight into the Downloads folder.
                if (!hasStorageAccess()) {
                    call.reject("NO_PERMISSION");
                    return;
                }
                File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                if (!dir.exists() && !dir.mkdirs()) throw new IOException("Downloads folder is not available");
                File f = new File(dir, name);
                int i = 1;
                while (f.exists()) {
                    int dot = name.lastIndexOf('.');
                    String base = dot > 0 ? name.substring(0, dot) : name;
                    String ext = dot > 0 ? name.substring(dot) : "";
                    f = new File(dir, base + " (" + (i++) + ")" + ext);
                }
                try (FileOutputStream fo = new FileOutputStream(f)) {
                    fo.write(bytes);
                    fo.flush();
                }
                shownName = f.getName();
                try (InputStream in = new FileInputStream(f)) {
                    long[] n = new long[1];
                    sha = sha256(in, n);
                    size = n[0];
                }
                MediaScannerConnection.scanFile(getContext(), new String[]{f.getAbsolutePath()}, null, null);
            }

            JSObject r = new JSObject();
            r.put("ok", true);
            r.put("name", shownName);
            r.put("size", size);
            r.put("sha256", sha);
            r.put("folder", "Downloads");
            call.resolve(r);
        } catch (Exception e) {
            call.reject(e.getMessage() != null ? e.getMessage() : "Could not save the file");
        }
    }

    // ---------------------------------------------------------------- permission

    private boolean needsRuntimePermission() {
        return Build.VERSION.SDK_INT <= 28;
    }

    private boolean hasStorageAccess() {
        return !needsRuntimePermission() || getPermissionState("storage") == PermissionState.GRANTED;
    }

    /** granted: can save to Downloads now. runtime: Android will show a permission dialog (Android 9 and older). */
    @PluginMethod
    public void checkAccess(PluginCall call) {
        JSObject r = new JSObject();
        r.put("runtime", needsRuntimePermission());
        r.put("granted", hasStorageAccess());
        r.put("sdk", Build.VERSION.SDK_INT);
        call.resolve(r);
    }

    @PluginMethod
    public void requestAccess(PluginCall call) {
        if (hasStorageAccess()) {
            checkAccess(call);
            return;
        }
        requestPermissionForAlias("storage", call, "accessCallback");
    }

    @PermissionCallback
    private void accessCallback(PluginCall call) {
        checkAccess(call);
    }

    // ---------------------------------------------------------------- folder choice

    private static final int RW = Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;

    private SharedPreferences prefs() {
        return getContext().getSharedPreferences("shelfie_files", android.content.Context.MODE_PRIVATE);
    }

    private Uri savedTree() {
        String s = prefs().getString("folderUri", null);
        if (s == null) return null;
        Uri tree = Uri.parse(s);
        try {
            for (UriPermission p : getContext().getContentResolver().getPersistedUriPermissions()) {
                if (p.getUri().equals(tree) && p.isReadPermission() && p.isWritePermission()) return tree;
            }
        } catch (Exception ignored) { }
        return null;
    }

    private Uri treeDoc(Uri tree) {
        return DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree));
    }

    private String displayName(Uri docUri, String fallback) {
        try (Cursor c = getContext().getContentResolver().query(docUri,
                new String[]{DocumentsContract.Document.COLUMN_DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) return c.getString(0);
        } catch (Exception ignored) { }
        return fallback;
    }

    private boolean docExists(Uri docUri) {
        try (Cursor c = getContext().getContentResolver().query(docUri,
                new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID}, null, null, null)) {
            return c != null && c.moveToFirst();
        } catch (Exception e) {
            return false;
        }
    }

    @PluginMethod
    public void pickFolder(PluginCall call) {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        i.addFlags(RW | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        if (Build.VERSION.SDK_INT >= 26) {
            try {
                // start in Documents: Android 11+ will not let an app use the Downloads folder itself
                i.putExtra(DocumentsContract.EXTRA_INITIAL_URI,
                        DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", "primary:Documents"));
            } catch (Exception ignored) { }
        }
        startActivityForResult(call, i, "pickFolderResult");
    }

    @ActivityCallback
    private void pickFolderResult(PluginCall call, ActivityResult result) {
        if (call == null) return;
        JSObject r = new JSObject();
        if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null || result.getData().getData() == null) {
            r.put("cancelled", true);
            call.resolve(r);
            return;
        }
        Uri tree = result.getData().getData();
        try {
            getContext().getContentResolver().takePersistableUriPermission(tree, RW);
        } catch (Exception e) {
            call.reject("Could not keep access to that folder: " + e.getMessage());
            return;
        }
        // let go of the previous folder so permissions do not pile up
        String old = prefs().getString("folderUri", null);
        if (old != null && !old.equals(tree.toString())) {
            try { getContext().getContentResolver().releasePersistableUriPermission(Uri.parse(old), RW); } catch (Exception ignored) { }
        }
        prefs().edit().putString("folderUri", tree.toString()).apply();
        r.put("ok", true);
        r.put("has", true);
        r.put("uri", tree.toString());
        r.put("name", displayName(treeDoc(tree), "Chosen folder"));
        call.resolve(r);
    }

    @PluginMethod
    public void getFolder(PluginCall call) {
        JSObject r = new JSObject();
        Uri tree = savedTree();
        if (tree == null || !docExists(treeDoc(tree))) {
            r.put("has", false);
            r.put("lost", prefs().getString("folderUri", null) != null);
        } else {
            r.put("has", true);
            r.put("uri", tree.toString());
            r.put("name", displayName(treeDoc(tree), "Chosen folder"));
        }
        call.resolve(r);
    }

    @PluginMethod
    public void clearFolder(PluginCall call) {
        String old = prefs().getString("folderUri", null);
        if (old != null) {
            try { getContext().getContentResolver().releasePersistableUriPermission(Uri.parse(old), RW); } catch (Exception ignored) { }
        }
        prefs().edit().remove("folderUri").apply();
        JSObject r = new JSObject();
        r.put("has", false);
        call.resolve(r);
    }

    // ---------------------------------------------------------------- work inside the chosen folder

    @PluginMethod
    public void saveToFolder(PluginCall call) {
        String name = call.getString("name");
        String data = call.getString("data");
        String mime = call.getString("mime", "text/xml");
        if (name == null || data == null || name.trim().isEmpty()) {
            call.reject("Missing file name or data");
            return;
        }
        Uri tree = savedTree();
        if (tree == null || !docExists(treeDoc(tree))) {
            call.reject("NO_FOLDER");
            return;
        }
        name = name.replaceAll("[\\\\/:*?\"<>|]", "_");
        try {
            ContentResolver cr = getContext().getContentResolver();
            Uri doc = DocumentsContract.createDocument(cr, treeDoc(tree), mime, name);
            if (doc == null) throw new IOException("Could not create the file in that folder");
            byte[] bytes = data.getBytes(StandardCharsets.UTF_8);
            try (OutputStream os = cr.openOutputStream(doc, "w")) {
                if (os == null) throw new IOException("Could not open the file for writing");
                os.write(bytes);
                os.flush();
            }
            String sha;
            long size;
            try (InputStream in = cr.openInputStream(doc)) {
                if (in == null) throw new IOException("Could not read the saved file back");
                long[] n = new long[1];
                sha = sha256(in, n);
                size = n[0];
            }
            JSObject r = new JSObject();
            r.put("ok", true);
            r.put("name", displayName(doc, name));
            r.put("size", size);
            r.put("sha256", sha);
            r.put("folder", displayName(treeDoc(tree), "Chosen folder"));
            r.put("uri", doc.toString());
            call.resolve(r);
        } catch (Exception e) {
            call.reject(e.getMessage() != null ? e.getMessage() : "Could not save the file");
        }
    }

    @PluginMethod
    public void listBackups(PluginCall call) {
        Uri tree = savedTree();
        JSObject out = new JSObject();
        JSArray arr = new JSArray();
        if (tree == null || !docExists(treeDoc(tree))) {
            out.put("files", arr);
            out.put("noFolder", true);
            call.resolve(out);
            return;
        }
        try {
            ContentResolver cr = getContext().getContentResolver();
            Uri kids = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree));
            List<JSObject> list = new ArrayList<>();
            try (Cursor c = cr.query(kids, new String[]{
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_SIZE,
                    DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                    DocumentsContract.Document.COLUMN_MIME_TYPE}, null, null, null)) {
                while (c != null && c.moveToNext()) {
                    String nm = c.getString(1);
                    if (nm == null) continue;
                    String low = nm.toLowerCase();
                    if (!low.startsWith("shelfie") || !(low.endsWith(".xml") || low.endsWith(".json"))) continue;
                    JSObject o = new JSObject();
                    o.put("name", nm);
                    o.put("uri", DocumentsContract.buildDocumentUriUsingTree(tree, c.getString(0)).toString());
                    o.put("size", c.isNull(2) ? 0 : c.getLong(2));
                    o.put("modified", c.isNull(3) ? 0 : c.getLong(3));
                    list.add(o);
                }
            }
            Collections.sort(list, (a, b) -> Long.compare(b.optLong("modified"), a.optLong("modified")));
            for (JSObject o : list) arr.put(o);
            out.put("files", arr);
            call.resolve(out);
        } catch (Exception e) {
            call.reject(e.getMessage() != null ? e.getMessage() : "Could not list the backup folder");
        }
    }

    @PluginMethod
    public void readFile(PluginCall call) {
        String uri = call.getString("uri");
        if (uri == null) {
            call.reject("Missing file");
            return;
        }
        try {
            Uri u = Uri.parse(uri);
            JSObject r = new JSObject();
            r.put("name", displayName(u, "backup"));
            r.put("data", readAll(u));
            call.resolve(r);
        } catch (Exception e) {
            call.reject(e.getMessage() != null ? e.getMessage() : "Could not read the file");
        }
    }

    @PluginMethod
    public void deleteFile(PluginCall call) {
        String uri = call.getString("uri");
        if (uri == null) {
            call.reject("Missing file");
            return;
        }
        try {
            Uri u = Uri.parse(uri);
            // only ever delete Shelfie backup files
            String nm = displayName(u, "").toLowerCase();
            if (!nm.startsWith("shelfie")) {
                call.reject("Not a Shelfie backup file");
                return;
            }
            boolean ok = DocumentsContract.deleteDocument(getContext().getContentResolver(), u);
            JSObject r = new JSObject();
            r.put("ok", ok);
            call.resolve(r);
        } catch (Exception e) {
            call.reject(e.getMessage() != null ? e.getMessage() : "Could not delete the file");
        }
    }

    // ---------------------------------------------------------------- restore picker (XML / JSON only)

    @PluginMethod
    public void pickBackupFile(PluginCall call) {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"text/xml", "application/xml", "application/json"});
        Uri tree = savedTree();
        if (tree != null && Build.VERSION.SDK_INT >= 26) {
            i.putExtra(DocumentsContract.EXTRA_INITIAL_URI, treeDoc(tree));
        }
        startActivityForResult(call, i, "pickBackupResult");
    }

    @ActivityCallback
    private void pickBackupResult(PluginCall call, ActivityResult result) {
        if (call == null) return;
        JSObject r = new JSObject();
        if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null || result.getData().getData() == null) {
            r.put("cancelled", true);
            call.resolve(r);
            return;
        }
        try {
            Uri u = result.getData().getData();
            r.put("name", displayName(u, "backup"));
            r.put("data", readAll(u));
            call.resolve(r);
        } catch (Exception e) {
            call.reject(e.getMessage() != null ? e.getMessage() : "Could not read the file");
        }
    }

    private String readAll(Uri u) throws IOException {
        try (InputStream in = getContext().getContentResolver().openInputStream(u)) {
            if (in == null) throw new IOException("Could not open the file");
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            long total = 0;
            while ((n = in.read(buf)) > 0) {
                total += n;
                if (total > 60L * 1024 * 1024) throw new IOException("That file is too large to be a Shelfie backup");
                bo.write(buf, 0, n);
            }
            return new String(bo.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static String sha256(InputStream in, long[] count) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] buf = new byte[8192];
        int n;
        long total = 0;
        while ((n = in.read(buf)) > 0) {
            md.update(buf, 0, n);
            total += n;
        }
        count[0] = total;
        StringBuilder sb = new StringBuilder();
        for (byte b : md.digest()) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
