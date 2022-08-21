package se.arctosoft.vault.viewmodel;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;

import java.util.LinkedList;
import java.util.List;

import se.arctosoft.vault.data.GalleryFile;

public class GalleryDirectoryViewModel extends ViewModel {
    private static final String TAG = "GalleryDirectoryViewMod";

    private final List<GalleryFile> galleryFiles = new LinkedList<>();
    private int currentPosition = 0;
    private boolean viewPagerVisible = false;
    private boolean initialised = false;

    public boolean isInitialised() {
        return initialised;
    }

    @NonNull
    public List<GalleryFile> getGalleryFiles() {
        return galleryFiles;
    }

    public void setInitialised(List<GalleryFile> galleryFiles) {
        //Log.e(TAG, "setInitialised: " + galleryFiles.size());
        if (initialised) {
            return;
        }
        this.initialised = true;
        this.galleryFiles.addAll(galleryFiles);
    }

    public int getCurrentPosition() {
        return currentPosition;
    }

    public void setCurrentPosition(int currentPosition) {
        this.currentPosition = currentPosition;
    }

    public boolean isViewpagerVisible() {
        return viewPagerVisible;
    }

    public void setViewpagerVisible(boolean fullscreen) {
        viewPagerVisible = fullscreen;
    }
}
