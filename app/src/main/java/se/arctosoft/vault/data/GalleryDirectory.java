package se.arctosoft.vault.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

public class GalleryDirectory {
    private final List<GalleryFile> filesInDirectory;
    private String directoryName;

    public GalleryDirectory(@NonNull List<GalleryFile> filesInDirectory, @NonNull String directoryName) {
        this.filesInDirectory = filesInDirectory;
        this.directoryName = directoryName;
    }

    @NonNull
    public List<GalleryFile> getFilesInDirectory() {
        return filesInDirectory;
    }

    @Nullable
    public GalleryFile getFirstFile() {
        if (filesInDirectory.isEmpty()) {
            return null;
        }
        return filesInDirectory.get(0);
    }

    public int getFileCount() {
        return filesInDirectory.size();
    }

    public String getDirectoryName() {
        return directoryName;
    }

    public void setDirectoryName(@NonNull String directoryName) {
        this.directoryName = directoryName;
    }

    /*public void removeFiles(List<AbstractFile> filesToRemove) {
        filesInDirectory.removeAll(filesToRemove);
    }

    public void addFiles(List<AbstractFile> filesToAdd) {
        filesInDirectory.addAll(filesToAdd);
        filesInDirectory.sort((o1, o2) -> Long.compare(o2.getCreatedAt(), o1.getCreatedAt()));
    }*/
}
