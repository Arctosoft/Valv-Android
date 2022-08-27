package se.arctosoft.vault.data;

import android.net.Uri;

import androidx.annotation.NonNull;

import java.util.Objects;

public class StoredDirectory {
    private final String uriString;
    private final Uri uri;
    private final boolean rootDir;

    public StoredDirectory(String uriString, boolean rootDir) {
        this.uriString = uriString;
        this.uri = Uri.parse(uriString);
        this.rootDir = rootDir;
    }

    public String getUriString() {
        return uriString;
    }

    public Uri getUri() {
        return uri;
    }

    public boolean isRootDir() {
        return rootDir;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        StoredDirectory that = (StoredDirectory) o;
        return uri.equals(that.uri);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uri);
    }

    @NonNull
    @Override
    public String toString() {
        return (rootDir ? '1' : '0') + uriString;
    }
}
