package se.arctosoft.vault.data;

import android.net.Uri;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.documentfile.provider.DocumentFile;

import java.util.ArrayList;
import java.util.List;

public class GalleryFile {
    private static final String TAG = "GalleryFile";
    private final DocumentFile documentFile;
    private final FileType fileType;
    private final boolean isDirectory;
    private Uri thumbUri;
    private List<GalleryFile> filesInDirectory;

    private GalleryFile(DocumentFile documentFile, @Nullable Uri thumbUri) {
        this.documentFile = documentFile;
        this.thumbUri = thumbUri;
        this.isDirectory = false;
        this.fileType = FileType.fromDocument(documentFile);
    }

    private GalleryFile(DocumentFile documentFile, List<GalleryFile> filesInDirectory) {
        this.documentFile = documentFile;
        this.thumbUri = null;
        this.isDirectory = true;
        this.fileType = FileType.fromDocument(documentFile);
        this.filesInDirectory = filesInDirectory;
    }

    public static GalleryFile asDirectory(DocumentFile documentFile, List<GalleryFile> filesInDirectory) {
        return new GalleryFile(documentFile, filesInDirectory);
    }

    public static GalleryFile asFile(DocumentFile documentFile, @Nullable Uri thumbUri) {
        return new GalleryFile(documentFile, thumbUri);
    }

    public void setThumbUri(Uri thumbUri) {
        this.thumbUri = thumbUri;
    }

    public String getName() {
        return documentFile.isDirectory() ? documentFile.getName() : documentFile.getName().split("-")[1];
    }

    public String getOriginalName() {
        return documentFile.getName();
    }

    public Uri getUri() {
        return documentFile.getUri();
    }

    @Nullable
    public Uri getThumbUri() {
        return thumbUri;
    }

    public boolean hasThumb() {
        return thumbUri != null;
    }

    public DocumentFile getDocumentFile() {
        return documentFile;
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
