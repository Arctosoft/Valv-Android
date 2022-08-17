package se.arctosoft.vault.data;

import android.net.Uri;
import android.provider.DocumentsContract;

public class CursorFile implements Comparable<CursorFile> {
    private final String name;
    private final Uri uri;
    private final long lastModified;
    private final String mimeType;
    private final boolean isDirectory;
    private String unencryptedName;

    public CursorFile(String name, Uri uri, long lastModified, String mimeType) {
        this.name = name;
        this.uri = uri;
        this.lastModified = lastModified;
        this.mimeType = mimeType;
        this.isDirectory = DocumentsContract.Document.MIME_TYPE_DIR.equals(mimeType);
    }

    public void setUnencryptedName(String unencryptedName) {
        this.unencryptedName = unencryptedName;
    }

    public String getUnencryptedName() {
        return unencryptedName;
    }

    public boolean isDirectory() {
        return isDirectory;
    }

    public String getName() {
        return name;
    }

    public Uri getUri() {
        return uri;
    }

    public long getLastModified() {
        return lastModified;
    }

    public String getMimeType() {
        return mimeType;
    }

    @Override
    public int compareTo(CursorFile o) {
        if (o.isDirectory) {
            return 1;
        }
        return Long.compare(o.lastModified, this.lastModified);
    }
}
