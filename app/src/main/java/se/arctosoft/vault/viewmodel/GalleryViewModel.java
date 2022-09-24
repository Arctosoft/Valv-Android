package se.arctosoft.vault.viewmodel;

import androidx.annotation.Nullable;
import androidx.documentfile.provider.DocumentFile;
import androidx.lifecycle.ViewModel;

import java.util.List;

public class GalleryViewModel extends ViewModel {
    private List<DocumentFile> filesToAdd;

    public void setFilesToAdd(@Nullable List<DocumentFile> filesToAdd) {
        this.filesToAdd = filesToAdd;
    }

    @Nullable
    public List<DocumentFile> getFilesToAdd() {
        return filesToAdd;
    }
}
