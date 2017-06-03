package ch.bailu.aat.util.fs.foc;

import android.annotation.TargetApi;
import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.DocumentsContract;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import ch.bailu.aat.util.ui.AppLog;
import ch.bailu.simpleio.foc.Foc;

/**
 *
 * Android document uri
 *
 * content://[authority]/[tree|document]/[document ID]
 * [scheme]://[authority]/[uri type]/[document ID]/[document type]/[document ID]

 *
 * uri type: uri
 *
 */

@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class FocContent extends Foc {
    public final static String TREE= "tree";
    public final static String DOCUMENT="document";
    public final static String UNKNOWN="unknown";

    private String type;
    private DocumentData data = null;

    private final ContentResolver resolver;
    private final LazyUris uris;




    // called from parent
    private FocContent(ContentResolver r, LazyUris u, DocumentData d) {
        this (r, u, d.type);
        data = d;
    }


    // called from child
    private FocContent(ContentResolver r, LazyUris u, String t) {
        resolver = r;
        uris = u;
        type = t;
    }


    // called from factory
    public FocContent(ContentResolver r, Uri per, DocumentId id , String t) {
        type = t;
        resolver = r;

        uris = new LazyUris(per, id);
    }







    @Override
    public boolean move(Foc dest) throws IOException, SecurityException {
        boolean ok = false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
                dest instanceof FocContent) {
            FocContent target = (FocContent) dest;

            ok = target.uris.hasParent()
                    && uris.hasParent()
                    && (DocumentsContract.renameDocument(resolver, uris.getDocument(), target.getName()) != null)
                    && (DocumentsContract.moveDocument(resolver, uris.getDocument(), uris.getParent(), target.uris.getParent()) != null);


        }
        return ok || super.move(dest);

    }

    @Override
    public boolean remove() throws IOException, SecurityException {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return DocumentsContract.deleteDocument(resolver, uris.getDocument());
        }
        return false;
    }

    @Override
    public boolean mkdir() {
        return uris.hasParent() && DocumentsContract.createDocument(
                resolver,
                uris.getParent(),
                DocumentsContract.Document.MIME_TYPE_DIR,
                getName()) != null;
    }

    @Override
    public Foc parent() {
        if (uris.hasParent()) return new FocContent(resolver, uris.parent(), TREE);
        return null;
    }


    @Override
    public Foc child(String name) {
        return new FocContent(resolver, uris.child(uris.getDocumentId().child(name)), UNKNOWN);
    }






    @Override
    public void foreach(Execute exec) {
        if (isFile()) return;

        Cursor cursor = null;
        try {
            cursor = resolver.query(uris.getChild(),null, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                type = TREE;

                do {
                    DocumentData data = new DocumentData(cursor);
                    AppLog.d(this, data.mimeType);
                    exec.execute(new FocContent(resolver, uris.child(new DocumentId(data.documentId)), data));

                } while (cursor.moveToNext());
            }
        } catch(Exception e) {
            AppLog.d(this, e.toString());
        }

        if (cursor != null) cursor.close();
    }


    @Override
    public void foreachFile(final Execute e) {
        foreach(new Execute() {
            @Override
            public void execute(Foc child) {
                if (child.isFile()) e.execute(child);
            }
        });
    }

    @Override
    public void foreachDir(final Execute e) {
        foreach(new Execute() {
            @Override
            public void execute(Foc child) {
                if (child.isDir()) e.execute(child);
            }
        });

    }

    @Override
    public boolean isDir() {
        if (type == UNKNOWN) querySelf();
        return type == TREE;
    }

    @Override
    public boolean isFile() {
        if (type == UNKNOWN) querySelf();
        return type == DOCUMENT;
    }

    @Override
    public boolean exists() {
        querySelf();
        return type != UNKNOWN;
    }

    @Override
    public boolean canRead() {
        querySelf();
        return exists();
    }

    @Override
    public boolean canWrite() {
        querySelf();
        return (data.flags & DocumentsContract.Document.FLAG_SUPPORTS_WRITE) == data.flags;
    }

    @Override
    public long length() {
        if (isDir()) return 0;

        querySelf();
        return data.size;
    }


    @Override
    public String getPath() {
        return uris.getDocument().toString();
    }


    @Override
    public long lastModified() {
        querySelf();
        return data.lastModified;
    }


    @Override
    public InputStream openR() throws IOException, SecurityException {
        return resolver.openInputStream(uris.getDocument());
    }

    @Override
    public OutputStream openW() throws IOException, SecurityException {
        return resolver.openOutputStream(uris.getDocument());
    }


    private void querySelf() {
        AppLog.d(this, "querySelf() " + uris.getDocumentId());

        if (data != null) return;


        Cursor cursor = null;
        try {
            cursor = resolver.query(uris.getDocument(),null, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                data = new DocumentData(cursor);

                type = data.type;
            } else {
                if (cursor ==  null) AppLog.d(this, "null cursor");
                AppLog.d(this, uris.getDocument().toString());

                data = new DocumentData(uris.getDocumentId().toString());
            }

        } catch(Exception e) {
            AppLog.d(this, e.toString());
            data = new DocumentData(uris.getDocumentId().toString());
        }
        Foc.close(cursor);
    }


    @Override
    public String getName() {
        return uris.getDocumentId().getName();
    }

    @Override
    public String getPathName() {
        return uris.getDocumentId().toString();
    }


}