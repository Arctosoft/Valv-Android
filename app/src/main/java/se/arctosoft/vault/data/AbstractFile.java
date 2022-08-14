package se.arctosoft.vault.data;

import android.net.Uri;

import androidx.annotation.NonNull;

import java.util.Objects;

public abstract class AbstractFile {
    private final Uri fileUri;
    private final FileType fileType;
    private long createdAt;

    protected AbstractFile(Uri fileUri, FileType fileType, long createdAt) {
        this.fileUri = fileUri;
        this.fileType = fileType;
        this.createdAt = createdAt;
    }

    @NonNull
    public Uri getFileUri() {
        return fileUri;
    }

    public FileType getFileType() {
        return fileType;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AbstractFile that = (AbstractFile) o;
        return fileUri.equals(that.fileUri) && fileType == that.fileType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(fileUri, fileType);
    }

    @NonNull
    @Override
    public String toString() {
        return "AbstractFile{" +
                "fileUri=" + fileUri +
                ", fileType=" + fileType +
                ", createdAt=" + createdAt +
                '}';
    }
}
