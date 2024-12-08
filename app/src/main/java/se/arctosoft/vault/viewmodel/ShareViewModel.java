package se.arctosoft.vault.viewmodel;

import androidx.annotation.NonNull;
import androidx.documentfile.provider.DocumentFile;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.ArrayList;
import java.util.List;

public class ShareViewModel extends ViewModel {
    private static final String TAG = "ShareViewModel";

    private MutableLiveData<Boolean> hasData;
    private final List<DocumentFile> filesReceived = new ArrayList<>();

    public void setHasData(boolean hasData) {
        if (this.hasData == null) {
            this.hasData = new MutableLiveData<>(hasData);
        } else {
            this.hasData.setValue(hasData);
        }
    }

    @NonNull
    public MutableLiveData<Boolean> getHasData() {
        if (this.hasData == null) {
            this.hasData = new MutableLiveData<>(false);
        }
        return hasData;
    }

    public List<DocumentFile> getFilesReceived() {
        return filesReceived;
    }

    public void clear() {
        setHasData(false);
        filesReceived.clear();
    }
}
