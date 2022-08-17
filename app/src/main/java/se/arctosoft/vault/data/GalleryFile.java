package se.arctosoft.vault.data;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import se.arctosoft.vault.utils.FileStuff;

public class GalleryFile implements Comparable<GalleryFile> {
    private static final String TAG = "GalleryFile";
    private final FileType fileType;
    private final String encryptedName, name;
    private final boolean isDirectory;
    private final Uri fileUri;
    private final long lastModified;
    private Uri thumbUri, decryptedCacheUri;
    private List<GalleryFile> filesInDirectory;

    private GalleryFile(@NonNull CursorFile file, @Nullable CursorFile thumb) {
        this.fileUri = file.getUri();
        this.encryptedName = file.getName();
        this.name = encryptedName.split("-", 2)[1];
        this.thumbUri = thumb == null ? null : thumb.getUri();
        this.decryptedCacheUri = null;
        this.lastModified = file.getLastModified();
        this.isDirectory = false;
        this.fileType = FileType.fromMimeType(file.getMimeType());
    }

    private GalleryFile(@NonNull Uri fileUri, List<GalleryFile> filesInDirectory) {
        this.fileUri = fileUri;
        this.encryptedName = FileStuff.getFilenameFromUri(fileUri, false);
        this.name = encryptedName;
        this.thumbUri = null;
        this.decryptedCacheUri = null;
        this.lastModified = System.currentTimeMillis();
        this.isDirectory = true;
        this.fileType = FileType.fromFilename(encryptedName);
        this.filesInDirectory = filesInDirectory;
    }

    private GalleryFile(@NonNull CursorFile file, List<GalleryFile> filesInDirectory) {
        this.fileUri = file.getUri();
        this.encryptedName = file.getName();
        this.name = encryptedName;
        this.thumbUri = null;
        this.decryptedCacheUri = null;
        this.lastModified = System.currentTimeMillis();
        this.isDirectory = true;
        this.fileType = FileType.DIRECTORY;
        this.filesInDirectory = filesInDirectory;
    }

    public static GalleryFile asDirectory(Uri fileUri, List<GalleryFile> filesInDirectory) {
        return new GalleryFile(fileUri, filesInDirectory);
    }

    public static GalleryFile asDirectory(CursorFile fileUri, List<GalleryFile> filesInDirectory) {
        return new GalleryFile(fileUri, filesInDirectory);
    }

    public static GalleryFile asFile(CursorFile fileUri, @Nullable CursorFile thumbUri) {
        return new GalleryFile(fileUri, thumbUri);
    }

    public void setDecryptedCacheUri(Uri decryptedCacheUri) {
        this.decryptedCacheUri = decryptedCacheUri;
    }

    public long getLastModified() {
        return lastModified;
    }

    @Nullable
    public Uri getDecryptedCacheUri() {
        return decryptedCacheUri;
    }

    public void setThumbUri(Uri thumbUri) {
        this.thumbUri = thumbUri;
    }

    public String getNameWithPath() {
        return FileStuff.getFilenameWithPathFromUri(fileUri);
    }

    public String getName() {
        return name;
    }

    public String getEncryptedName() {
        return encryptedName;
    }

    public Uri getUri() {
        return fileUri;
    }

    @Nullable
    public Uri getThumbUri() {
        return thumbUri;
    }

    public boolean hasThumb() {
        return thumbUri != null;
    }

    public FileType getFileType() {
        return fileType;
    }

    public boolean isDirectory() {
        return isDirectory;
    }

    @Nullable
    public List<GalleryFile> getFilesInDirectory() {
        return filesInDirectory;
    }

    @Nullable
    public GalleryFile getFirstFile() {
        if (filesInDirectory == null || filesInDirectory.isEmpty()) {
            return null;
        }
        for (GalleryFile g : filesInDirectory) {
            if (!g.isDirectory()) {
                return g;
            }
        }
        return null;
    }

    public int getFileCount() {
        return filesInDirectory == null ? 0 : filesInDirectory.size();
    }

    public void setFilesInDirectory(List<GalleryFile> filesInDirectory) {
        if (this.filesInDirectory != null) {
            this.filesInDirectory.clear();
        } else {
            this.filesInDirectory = new ArrayList<>(filesInDirectory.size());
        }
        this.filesInDirectory.addAll(filesInDirectory);
    }

    @Override
    public int compareTo(GalleryFile o) {
        return Long.compare(o.lastModified, this.lastModified);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GalleryFile that = (GalleryFile) o;
        return fileUri.equals(that.fileUri);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fileUri);
    }
}
