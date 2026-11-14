package com.rbxnative.androidport;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract.Document;
import android.provider.DocumentsContract.Root;
import android.provider.DocumentsProvider;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

/** Private fixture provider: exercises real ContentResolver / SAF operations. */
public final class ServiceTestDocuments extends DocumentsProvider {
    @Override public boolean onCreate() { return true; }
    private File file(String id) throws FileNotFoundException {
        try {
            File root = new File(getContext().getFilesDir(), "service-fixtures").getCanonicalFile();
            File file = id.equals("root") ? root : new File(root, id.substring("root/".length())).getCanonicalFile();
            if (!id.equals("root") && (!id.startsWith("root/") || !file.getPath().startsWith(root.getPath() + "/"))) throw new IOException("Invalid fixture ID");
            return file;
        } catch (IOException | RuntimeException e) { throw new FileNotFoundException("Invalid fixture document"); }
    }
    private void add(MatrixCursor cursor, String id, File file) {
        MatrixCursor.RowBuilder row = cursor.newRow();
        for (String column : cursor.getColumnNames()) {
            switch (column) {
                case Document.COLUMN_DOCUMENT_ID: row.add(id); break;
                case Document.COLUMN_DISPLAY_NAME: row.add(file.getName()); break;
                case Document.COLUMN_MIME_TYPE: row.add(file.isDirectory() ? Document.MIME_TYPE_DIR : "application/octet-stream"); break;
                case Document.COLUMN_SIZE: row.add(file.length()); break;
                case Document.COLUMN_FLAGS: row.add(file.isDirectory() ? Document.FLAG_DIR_SUPPORTS_CREATE : file.getName().equals("readonly.rbxl") ? 0 : Document.FLAG_SUPPORTS_WRITE); break;
                default: row.add(null);
            }
        }
    }
    private static String[] columns(String[] requested) {
        return requested != null ? requested : new String[]{Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE, Document.COLUMN_SIZE, Document.COLUMN_FLAGS};
    }
    @Override public Cursor queryRoots(String[] projection) {
        MatrixCursor cursor = new MatrixCursor(new String[]{Root.COLUMN_ROOT_ID, Root.COLUMN_DOCUMENT_ID, Root.COLUMN_TITLE, Root.COLUMN_FLAGS});
        cursor.addRow(new Object[]{"fixtures", "root", "Private service fixtures", Root.FLAG_SUPPORTS_CREATE | Root.FLAG_SUPPORTS_IS_CHILD});
        return cursor;
    }
    @Override public Cursor queryDocument(String id, String[] projection) throws FileNotFoundException {
        File file = file(id);
        if (!file.exists()) throw new FileNotFoundException("Fixture missing");
        MatrixCursor cursor = new MatrixCursor(columns(projection));
        add(cursor, id, file); return cursor;
    }
    @Override public Cursor queryChildDocuments(String parent, String[] projection, String sort) throws FileNotFoundException {
        MatrixCursor cursor = new MatrixCursor(columns(projection));
        File[] children = file(parent).listFiles();
        if (children == null) throw new FileNotFoundException("Fixture directory missing");
        for (File child : children) add(cursor, parent + "/" + child.getName(), child);
        return cursor;
    }
    @Override public ParcelFileDescriptor openDocument(String id, String mode, CancellationSignal signal) throws FileNotFoundException {
        File file = file(id);
        if (mode.contains("w") && file.getName().equals("readonly.rbxl")) throw new FileNotFoundException("Read-only fixture");
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.parseMode(mode));
    }
    @Override public String createDocument(String parent, String mime, String name) throws FileNotFoundException {
        if (name.contains("/") || name.equals("..") || name.equals(".")) throw new FileNotFoundException("Invalid fixture name");
        String id = parent + "/" + name;
        File destination = file(id);
        try {
            boolean created = Document.MIME_TYPE_DIR.equals(mime) ? destination.mkdir() : destination.createNewFile();
            if (!created) throw new IOException("Fixture creation failed");
            return id;
        } catch (IOException e) { throw new FileNotFoundException(e.getMessage()); }
    }
    @Override public boolean isChildDocument(String parent, String document) { return document.startsWith(parent + "/"); }
}
