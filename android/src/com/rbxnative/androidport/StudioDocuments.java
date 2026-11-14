package com.rbxnative.androidport;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.Map;

/** SAF documents mirrored as ordinary paths for the original Rust editor.
 * Writes back synchronously when the editor finishes its atomic local save.
 * A revoked or read-only document reports a save error instead of success.
 */
public final class StudioDocuments {
    private StudioDocuments() {}
    private static SharedPreferences mappings(Context context) {
        return context.getSharedPreferences("studio-documents-v1", Context.MODE_PRIVATE);
    }

    public static synchronized String localize(Activity activity, String address, boolean creating) throws Exception {
        Uri uri = Uri.parse(address);
        if (!"content".equals(uri.getScheme())) throw new IOException("Expected an Android document URI");
        ContentResolver resolver = activity.getContentResolver();
        boolean tree = DocumentsContract.isTreeUri(uri);
        Uri document = tree ? DocumentsContract.buildDocumentUriUsingTree(uri, DocumentsContract.getTreeDocumentId(uri)) : uri;
        String name = displayName(resolver, document);
        String identity = StudioServices.hex(MessageDigest.getInstance("SHA-256").digest(address.getBytes(StandardCharsets.UTF_8)));
        File parent = new File(activity.getFilesDir(), "documents/" + identity);
        if (!parent.isDirectory() && !parent.mkdirs()) throw new IOException("Cannot create local document folder");
        File local = new File(parent, safeName(name));
        if (tree) copyTree(resolver, uri, document, local);
        else if (!creating) copyFile(resolver, document, local);
        if (!mappings(activity).edit().putString(local.getCanonicalPath(), address).commit()) {
            throw new IOException("Cannot retain document mapping");
        }
        return local.getCanonicalPath();
    }

    /** Called after each original editor save, with its real local path. */
    public static synchronized void commit(Activity activity, String path) throws Exception {
        File file = new File(path).getCanonicalFile();
        String target = mappings(activity).getString(file.getPath(), null);
        if (target != null && !DocumentsContract.isTreeUri(Uri.parse(target))) {
            write(activity.getContentResolver(), Uri.parse(target), file);
            return;
        }
        // Files created inside a selected project retain their folder grant.
        String bestRoot = null, bestAddress = null;
        for (Map.Entry<String, ?> entry : mappings(activity).getAll().entrySet()) {
            if (!(entry.getValue() instanceof String)) continue;
            String address = (String) entry.getValue();
            if (DocumentsContract.isTreeUri(Uri.parse(address))
                    && file.getPath().startsWith(entry.getKey() + File.separator)
                    && (bestRoot == null || entry.getKey().length() > bestRoot.length())) {
                bestRoot = entry.getKey(); bestAddress = address;
            }
        }
        if (bestRoot == null) return; // An app-private place or recovery file.
        Uri tree = Uri.parse(bestAddress);
        Uri parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree));
        String[] pieces = file.getPath().substring(bestRoot.length() + 1).split("/");
        for (int i = 0; i < pieces.length; i++) {
            boolean directory = i + 1 < pieces.length;
            Uri child = findChild(activity.getContentResolver(), tree, parent, pieces[i]);
            if (child == null) {
                child = DocumentsContract.createDocument(activity.getContentResolver(), parent,
                        directory ? DocumentsContract.Document.MIME_TYPE_DIR : "application/octet-stream", pieces[i]);
                if (child == null) throw new IOException("Document provider could not create the destination");
            }
            parent = child;
        }
        write(activity.getContentResolver(), parent, file);
    }

    private static void write(ContentResolver resolver, Uri target, File local) throws IOException {
        // rwt requests truncate + write; failure propagates to the editor.
        try (InputStream in = new FileInputStream(local); OutputStream out = resolver.openOutputStream(target, "rwt")) {
            if (out == null) throw new IOException("Document provider refused the write");
            copy(in, out);
            out.flush();
        }
    }

    private static String displayName(ContentResolver resolver, Uri uri) throws IOException {
        try (Cursor cursor = resolver.query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) return cursor.getString(0);
        }
        throw new IOException("Document provider did not return a file name");
    }

    private static String safeName(String name) throws IOException {
        if (name == null || name.isEmpty() || name.equals(".") || name.equals("..")
                || name.indexOf('/') >= 0 || name.indexOf('\\') >= 0 || name.indexOf('\0') >= 0) {
            throw new IOException("Document provider returned an invalid file name");
        }
        return name;
    }

    private static Uri findChild(ContentResolver resolver, Uri tree, Uri parent, String name) throws IOException {
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getDocumentId(parent));
        try (Cursor cursor = resolver.query(children, new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME}, null, null, null)) {
            if (cursor == null) throw new IOException("Cannot list document folder");
            while (cursor.moveToNext()) if (name.equals(cursor.getString(1))) {
                return DocumentsContract.buildDocumentUriUsingTree(tree, cursor.getString(0));
            }
        }
        return null;
    }

    private static void copyTree(ContentResolver resolver, Uri tree, Uri document, File local) throws IOException {
        if (!local.isDirectory() && !local.mkdirs()) throw new IOException("Cannot create local project folder");
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getDocumentId(document));
        try (Cursor cursor = resolver.query(children, new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE}, null, null, null)) {
            if (cursor == null) throw new IOException("Cannot list document folder");
            while (cursor.moveToNext()) {
                Uri child = DocumentsContract.buildDocumentUriUsingTree(tree, cursor.getString(0));
                File into = new File(local, safeName(cursor.getString(1)));
                if (DocumentsContract.Document.MIME_TYPE_DIR.equals(cursor.getString(2))) copyTree(resolver, tree, child, into);
                else copyFile(resolver, child, into);
            }
        }
    }

    private static void copyFile(ContentResolver resolver, Uri source, File target) throws IOException {
        File staged = File.createTempFile(".import-", ".tmp", target.getParentFile());
        try {
            try (InputStream in = resolver.openInputStream(source); FileOutputStream out = new FileOutputStream(staged)) {
                if (in == null) throw new IOException("Document provider refused the read");
                copy(in, out);
                out.getFD().sync();
            }
            Files.move(staged.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } finally { staged.delete(); }
    }

    private static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[65536];
        int count;
        while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
    }
}
