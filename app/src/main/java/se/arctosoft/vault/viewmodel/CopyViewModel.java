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

import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.documentfile.provider.DocumentFile;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import se.arctosoft.vault.data.GalleryFile;
import se.arctosoft.vault.data.ProgressData;
import se.arctosoft.vault.interfaces.IOnFileOperationDone;
import se.arctosoft.vault.interfaces.IOnProgress;
import se.arctosoft.vault.utils.FileStuff;

public class CopyViewModel extends ViewModel {
    private static final String TAG = "CopyViewModel";

    private final List<GalleryFile> files = new LinkedList<>();

    private boolean running;
    private long totalBytes;
    private String destinationFolderName;
    final AtomicBoolean interrupted = new AtomicBoolean(false);

    private MutableLiveData<ProgressData> progressData;

    private Thread thread;
    private IOnFileOperationDone onDoneBottomSheet, onDoneFragment;
    private DocumentFile destinationDirectory;
    private Uri currentDirectoryUri, destinationUri;

    public MutableLiveData<ProgressData> getProgressData() {
        if (progressData == null) {
            progressData = new MutableLiveData<>(null);
        }
        return progressData;
    }

    public void setDestinationFolderName(String destinationFolderName) {
        this.destinationFolderName = destinationFolderName;
    }

    public String getDestinationFolderName() {
        return destinationFolderName;
    }

    public void setDestinationDirectory(DocumentFile destinationDirectory) {
        this.destinationDirectory = destinationDirectory;
    }

    public DocumentFile getDestinationDirectory() {
        return destinationDirectory;
    }

    public void setCurrentDirectoryUri(Uri currentDirectoryUri) {
        this.currentDirectoryUri = currentDirectoryUri;
    }

    public Uri getCurrentDirectoryUri() {
        return currentDirectoryUri;
    }

    public void setDestinationUri(Uri destinationUri) {
        this.destinationUri = destinationUri;
    }

    public Uri getDestinationUri() {
        return destinationUri;
    }

    @NonNull
    public List<GalleryFile> getFiles() {
        return files;
    }

    public boolean isRunning() {
        return running;
    }

    public void setRunning(boolean running) {
        this.running = running;
    }

    public void setOnDoneBottomSheet(IOnFileOperationDone onFileOperationDone) {
        this.onDoneBottomSheet = onFileOperationDone;
    }

    public void setOnDoneFragment(IOnFileOperationDone onFileOperationDone) {
        this.onDoneFragment = onFileOperationDone;
    }

    public void setTotalBytes(long totalBytes) {
        this.totalBytes = totalBytes;
    }

    public long getTotalBytes() {
        return totalBytes;
    }

    public void cancel() {
        Log.e(TAG, "cancel: ");
        interrupted.set(true);
        setRunning(false);
        if (thread != null) {
            thread.interrupt();
        }
    }

    public void start(FragmentActivity activity) {
        Log.e(TAG, "start: ");
        if (thread != null) {
            thread.interrupt();
        }
        interrupted.set(false);
        thread = new Thread(() -> {
            final int fileCount = files.size();
            final List<GalleryFile> doneFiles = Collections.synchronizedList(new ArrayList<>(fileCount));
            final long[] lastPublish = {0};
            final IOnProgress onProgress = currentBytesDeleted -> {
                if (System.currentTimeMillis() - lastPublish[0] > 20) {
                    lastPublish[0] = System.currentTimeMillis();
                    getProgressData().postValue(new ProgressData(fileCount, doneFiles.size() + 1, (int) Math.round((doneFiles.size() + 0.0) / fileCount * 100.0), null, null));
                }
            };

            Log.e(TAG, "start: " + destinationUri);
            DocumentFile destinationDocument = DocumentFile.fromTreeUri(activity, destinationUri);
            for (GalleryFile f : files) {
                boolean success = FileStuff.copyTo(activity, f, destinationDocument);
                if (success) {
                    doneFiles.add(f);
                    onProgress.onProgress(doneFiles.size());
                }
            }

            if (onDoneBottomSheet != null) {
                onDoneBottomSheet.onDone(doneFiles);
            }
            if (onDoneFragment != null) {
                onDoneFragment.onDone(doneFiles);
            }
            interrupted.set(false);
        });
        thread.start();
    }

}
