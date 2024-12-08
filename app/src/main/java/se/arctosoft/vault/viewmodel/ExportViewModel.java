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
import se.arctosoft.vault.data.Password;
import se.arctosoft.vault.data.ProgressData;
import se.arctosoft.vault.encryption.Encryption;
import se.arctosoft.vault.exception.InvalidPasswordException;
import se.arctosoft.vault.interfaces.IOnFileOperationDone;
import se.arctosoft.vault.interfaces.IOnProgress;

public class ExportViewModel extends ViewModel {
    private static final String TAG = "ExportViewModel";

    private final List<GalleryFile> filesToExport = new LinkedList<>();

    private boolean running;
    private long totalBytes;
    final AtomicBoolean interrupted = new AtomicBoolean(false);

    private MutableLiveData<ProgressData> progressData;

    private Thread thread;
    private IOnFileOperationDone onDoneBottomSheet, onDoneFragment;
    private DocumentFile currentDocumentDirectory;

    public MutableLiveData<ProgressData> getProgressData() {
        if (progressData == null) {
            progressData = new MutableLiveData<>(null);
        }
        return progressData;
    }

    public void setCurrentDocumentDirectory(DocumentFile currentDocumentDirectory) {
        this.currentDocumentDirectory = currentDocumentDirectory;
    }

    public DocumentFile getCurrentDocumentDirectory() {
        return currentDocumentDirectory;
    }

    @NonNull
    public List<GalleryFile> getFilesToExport() {
        return filesToExport;
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
            Password password = Password.getInstance();
            final int fileCount = filesToExport.size();
            final List<GalleryFile> doneFiles = Collections.synchronizedList(new ArrayList<>(fileCount));
            final long[] lastPublish = {0};
            final IOnProgress onProgress = currentBytesDeleted -> {
                if (System.currentTimeMillis() - lastPublish[0] > 20) {
                    lastPublish[0] = System.currentTimeMillis();
                    getProgressData().postValue(new ProgressData(fileCount, doneFiles.size() + 1, (int) Math.round((doneFiles.size() + 0.0) / fileCount * 100.0), null, null));
                }
            };
            for (GalleryFile f : filesToExport) {
                Encryption.IOnUriResult result = new Encryption.IOnUriResult() {
                    @Override
                    public void onUriResult(Uri outputUri) {
                        doneFiles.add(f);
                        onProgress.onProgress(doneFiles.size());
                    }

                    @Override
                    public void onError(Exception e) {
                    }

                    @Override
                    public void onInvalidPassword(InvalidPasswordException e) {
                    }
                };
                Encryption.decryptAndExport(activity, f.getUri(), currentDocumentDirectory, f, f.isVideo(), f.getVersion(), password.getPassword(), result);
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
