package se.arctosoft.vault.data;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

import se.arctosoft.vault.utils.FileStuff;

public class GalleryFile {
    private static final String TAG = "GalleryFile";
    private final FileType fileType;
    private final String encryptedName, name;
    private final boolean isDirectory;
    private Uri fileUri, thumbUri;
    private List<GalleryFile> filesInDirectory;

    private GalleryFile(@NonNull Uri fileUri, @Nullable Uri thumbUri) {
        this.fileUri = fileUri;
        this.encryptedName = FileStuff.getFilenameFromUri(fileUri, false);
        this.name = encryptedName.split("-", 2)[1];
        this.thumbUri = thumbUri;
        this.isDirectory = false;
        this.fileType = FileType.fromFilename(encryptedName);
    }

    private GalleryFile(@NonNull Uri fileUri, List<GalleryFile> filesInDirectory) {
        this.fileUri = fileUri;
        this.encryptedName = FileStuff.getFilenameFromUri(fileUri, false);
        this.name = encryptedName;
        this.thumbUri = null;
        this.isDirectory = true;
        this.fileType = FileType.fromFilename(encryptedName);
        this.filesInDirectory = filesInDirectory;
    }

    public static GalleryFile asDirectory(Uri fileUri, List<GalleryFile> filesInDirectory) {
        return new GalleryFile(fileUri, filesInDirectory);
    }

    public static GalleryFile asFile(Uri fileUri, @Nullable Uri thumbUri) {
        return new GalleryFile(fileUri, thumbUri);
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
        return filesInDirectory.get(0);
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
}
