package se.arctosoft.vault.viewmodel;

import androidx.lifecycle.ViewModel;

public class GalleryDirectoryViewModel extends ViewModel {
    private int currentPosition = 0;
    private boolean isFullscreen = false;

    public int getCurrentPosition() {
        return currentPosition;
    }

    public void setCurrentPosition(int currentPosition) {
        this.currentPosition = currentPosition;
    }

    public boolean isFullscreen() {
        return isFullscreen;
    }

    public void setFullscreen(boolean fullscreen) {
        isFullscreen = fullscreen;
    }
}
