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

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import se.arctosoft.vault.data.GalleryFile;
import se.arctosoft.vault.data.ProgressData;
import se.arctosoft.vault.interfaces.IOnFileOperationDone;
import se.arctosoft.vault.interfaces.IOnProgress;
import se.arctosoft.vault.utils.FileStuff;

public class DeleteViewModel extends ViewModel {
    private static final String TAG = "DeleteViewModel";

    private final List<GalleryFile> filesToDelete = new LinkedList<>();

    private boolean deleting;
    private long totalBytes;
    final AtomicBoolean interrupted = new AtomicBoolean(false);

    private MutableLiveData<ProgressData> progressData;

    private Thread thread;
    private IOnFileOperationDone onDeleteDoneBottomSheet, onDeleteDoneFragment;

    public MutableLiveData<ProgressData> getProgressData() {
        if (progressData == null) {
            progressData = new MutableLiveData<>(null);
        }
        return progressData;
    }

    @NonNull
    public List<GalleryFile> getFilesToDelete() {
        return filesToDelete;
    }

    public boolean isDeleting() {
        return deleting;
    }

    public void setDeleting(boolean deleting) {
        this.deleting = deleting;
    }

    public void setOnDeleteDoneBottomSheet(IOnFileOperationDone onImportDone) {
        this.onDeleteDoneBottomSheet = onImportDone;
    }

    public void setOnDeleteDoneFragment(IOnFileOperationDone onDeleteDoneFragment) {
        this.onDeleteDoneFragment = onDeleteDoneFragment;
    }

    public void setTotalBytes(long totalBytes) {
        this.totalBytes = totalBytes;
    }

    public long getTotalBytes() {
        return totalBytes;
    }

    public void cancelDelete() {
        Log.e(TAG, "cancelDelete: ");
        interrupted.set(true);
        setDeleting(false);
        if (thread != null) {
            thread.interrupt();
        }
    }

    public void startDelete(FragmentActivity activity) {
        Log.e(TAG, "startDelete: ");
        if (thread != null) {
            thread.interrupt();
        }
        interrupted.set(false);
        thread = new Thread(() -> {
            final List<GalleryFile> deletedFiles = Collections.synchronizedList(new ArrayList<>(filesToDelete.size()));
            final ConcurrentLinkedQueue<GalleryFile> queue = new ConcurrentLinkedQueue<>(filesToDelete);
            final AtomicLong bytesDeleted = new AtomicLong(0);
            final int fileCount = filesToDelete.size();
            final int threadCount = fileCount < 4 ? 1 : 4;

            final long[] lastPublish = {0};
            final IOnProgress onProgress = currentBytesDeleted -> {
                if (System.currentTimeMillis() - lastPublish[0] > 20) {
                    lastPublish[0] = System.currentTimeMillis();
                    getProgressData().postValue(new ProgressData(filesToDelete.size(), deletedFiles.size() + 1, (int) Math.round((bytesDeleted.get() + 0.0) / totalBytes * 100.0), null, null));
                }
            };

            List<Thread> threads = new ArrayList<>(threadCount);
            for (int i = 0; i < threadCount; i++) {
                Thread t = new Thread(() -> {
                    GalleryFile galleryFile;
                    while (!interrupted.get() && (galleryFile = queue.poll()) != null) {
                        boolean deletedFile = FileStuff.deleteFile(activity, galleryFile.getUri());
                        if (deletedFile) {
                            deletedFiles.add(galleryFile);
                            boolean deletedThumb = FileStuff.deleteFile(activity, galleryFile.getThumbUri());
                            boolean deletedNote = FileStuff.deleteFile(activity, galleryFile.getNoteUri());
                            onProgress.onProgress(bytesDeleted.addAndGet(galleryFile.getSize()));
                        }
                    }
                });
                threads.add(t);
                t.start();
            }

            for (Thread thread : threads) {
                try {
                    thread.join();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            if (onDeleteDoneBottomSheet != null) {
                onDeleteDoneBottomSheet.onDone(deletedFiles);
            }
            if (onDeleteDoneFragment != null) {
                onDeleteDoneFragment.onDone(deletedFiles);
            }
            interrupted.set(false);
        });
        thread.start();
    }

}
