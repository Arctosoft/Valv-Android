/*
 * Valv-Android
 * Copyright (C) 2024 Arctosoft AB
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see https://www.gnu.org/licenses/.
 */

package se.arctosoft.vault.viewmodel;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.documentfile.provider.DocumentFile;
import androidx.lifecycle.ViewModel;

import java.util.LinkedList;
import java.util.List;

import se.arctosoft.vault.data.GalleryFile;
import se.arctosoft.vault.interfaces.IOnAdapterItemChanged;

public class GalleryViewModel extends ViewModel {
    private static final String TAG = "GalleryDirectoryViewMod";

    private final List<GalleryFile> galleryFiles = new LinkedList<>();
    private final List<GalleryFile> hiddenFiles = new LinkedList<>();

    private DocumentFile currentDocumentDirectory;
    private Uri currentDirectoryUri;
    private String directory, nestedPath = "";
    private int currentPosition = 0;
    private boolean viewPagerVisible = false;
    private boolean initialised = false;
    private boolean inSelectionMode = false;
    private boolean isRootDir = false;
    private boolean isAllFolder = false;
    private IOnAdapterItemChanged onAdapterItemChanged;
    private Uri clickedDirectoryUri;

    public boolean isInitialised() {
        return initialised;
    }

    public void setInitialised(boolean initialised) {
        this.initialised = initialised;
    }

    public void setOnAdapterItemChanged(IOnAdapterItemChanged onAdapterItemChanged) {
        this.onAdapterItemChanged = onAdapterItemChanged;
    }

    public IOnAdapterItemChanged getOnAdapterItemChanged() {
        return onAdapterItemChanged;
    }

    @NonNull
    public List<GalleryFile> getGalleryFiles() {
        return galleryFiles;
    }

    public void addGalleryFiles(List<GalleryFile> galleryFiles) {
        this.galleryFiles.addAll(galleryFiles);
    }

    @NonNull
    public List<GalleryFile> getHiddenFiles() {
        return hiddenFiles;
    }

    public void setDirectory(String directory, Context context) {
        this.directory = directory;
        if (directory != null) {
            currentDirectoryUri = Uri.parse(directory);
            currentDocumentDirectory = DocumentFile.fromTreeUri(context, currentDirectoryUri);
            if (!currentDocumentDirectory.getUri().toString().equals(currentDirectoryUri.toString())) {
                String[] paths = nestedPath.split("/");
                for (String s : paths) {
                    if (currentDocumentDirectory != null && s != null && !s.isEmpty()) {
                        DocumentFile found = currentDocumentDirectory.findFile(s);
                        if (found != null) {
                            currentDocumentDirectory = found;
                        }
                    }
                }
            }
        } else {
            currentDirectoryUri = null;
            currentDocumentDirectory = null;
        }
        Log.e(TAG, "currentDocumentDirectory: " + currentDocumentDirectory);
    }

    public void setRootDir(boolean rootDir) {
        isRootDir = rootDir;
    }

    public boolean isRootDir() {
        return isRootDir;
    }

    public String getDirectory() {
        return directory;
    }

    public void setNestedPath(String nestedPath) {
        this.nestedPath = nestedPath;
    }

    public String getNestedPath() {
        return nestedPath;
    }

    public void setClickedDirectoryUri(Uri clickedDirectoryUri) {
        this.clickedDirectoryUri = clickedDirectoryUri;
    }

    public Uri getClickedDirectoryUri() {
        return clickedDirectoryUri;
    }

    public void setCurrentDirectoryUri(Uri currentDirectoryUri) {
        this.currentDirectoryUri = currentDirectoryUri;
    }

    public Uri getCurrentDirectoryUri() {
        return currentDirectoryUri;
    }

    public void setCurrentDocumentDirectory(DocumentFile currentDocumentDirectory) {
        this.currentDocumentDirectory = currentDocumentDirectory;
    }

    public DocumentFile getCurrentDocumentDirectory() {
        return currentDocumentDirectory;
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

    public boolean isInSelectionMode() {
        return inSelectionMode;
    }

    public void setInSelectionMode(boolean inSelectionMode) {
        this.inSelectionMode = inSelectionMode;
    }

    public void setAllFolder(boolean allFolder) {
        isAllFolder = allFolder;
    }

    public boolean isAllFolder() {
        return isAllFolder;
    }
}
