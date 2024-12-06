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

import android.icu.text.DecimalFormat;
import android.net.Uri;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.documentfile.provider.DocumentFile;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import se.arctosoft.vault.data.Password;
import se.arctosoft.vault.data.ProgressData;
import se.arctosoft.vault.encryption.Encryption;
import se.arctosoft.vault.interfaces.IOnImportDone;
import se.arctosoft.vault.interfaces.IOnProgress;

public class ImportViewModel extends ViewModel {
    private static final String TAG = "ImportViewModel";

    private final List<DocumentFile> filesToImport = new LinkedList<>();

    private boolean importing, deleteAfterImport, sameDirectory;
    private long totalBytes;
    private String destinationFolderName;
    private Uri currentDirectoryUri, importToUri;
    private DocumentFile destinationDirectory;
    private final AtomicBoolean interrupted = new AtomicBoolean(false);

    private MutableLiveData<ProgressData> progressData;

    private Thread importThread;
    private IOnImportDone onImportDoneBottomSheet, onImportDoneFragment;

    public MutableLiveData<ProgressData> getProgressData() {
        if (progressData == null) {
            progressData = new MutableLiveData<>(null);
        }
        return progressData;
    }

    @NonNull
    public List<DocumentFile> getFilesToImport() {
        return filesToImport;
    }

    public boolean isImporting() {
        return importing;
    }

    public void setImporting(boolean importing) {
        this.importing = importing;
    }

    public void setOnImportDoneBottomSheet(IOnImportDone onImportDone) {
        this.onImportDoneBottomSheet = onImportDone;
    }

    public void setOnImportDoneFragment(IOnImportDone onImportDoneFragment) {
        this.onImportDoneFragment = onImportDoneFragment;
    }

    public void setDestinationDirectory(DocumentFile destinationDirectory) {
        this.destinationDirectory = destinationDirectory;
    }

    public DocumentFile getDestinationDirectory() {
        return destinationDirectory;
    }

    public void setSameDirectory(boolean sameDirectory) {
        this.sameDirectory = sameDirectory;
    }

    public boolean isSameDirectory() {
        return sameDirectory;
    }

    public boolean isDeleteAfterImport() {
        return deleteAfterImport;
    }

    public void setDeleteAfterImport(boolean deleteAfterImport) {
        this.deleteAfterImport = deleteAfterImport;
    }

    public void setTotalBytes(long totalBytes) {
        this.totalBytes = totalBytes;
    }

    public long getTotalBytes() {
        return totalBytes;
    }

    public Uri getImportToUri() {
        return importToUri;
    }

    public void setImportToUri(Uri importToUri) {
        this.importToUri = importToUri;
    }

    public void setCurrentDirectoryUri(Uri currentDirectoryUri) {
        this.currentDirectoryUri = currentDirectoryUri;
    }

    public Uri getCurrentDirectoryUri() {
        return currentDirectoryUri;
    }

    public void setDestinationFolderName(String destinationFolderName) {
        this.destinationFolderName = destinationFolderName;
    }

    public String getDestinationFolderName() {
        return destinationFolderName;
    }

    public void cancelImport() {
        Log.e(TAG, "cancelImport: ");
        interrupted.set(true);
        setImporting(false);
        setImportToUri(null);
        if (importThread != null) {
            importThread.interrupt();
        }
    }

    public void startImport(FragmentActivity activity) {
        Log.e(TAG, "startImport: ");
        if (importThread != null) {
            importThread.interrupt();
        }
        interrupted.set(false);
        importThread = new Thread(() -> {
            Password password = Password.getInstance();
            final DecimalFormat decimalFormat = new DecimalFormat("0.00");
            final String totalMB = decimalFormat.format(totalBytes / 1000000.0);
            final int[] progress = new int[]{1};
            int errors = 0;
            int thumbErrors = 0;
            final double[] bytesDone = new double[]{0};
            final long[] lastPublish = {0};
            final IOnProgress onProgress = progress1 -> {
                if (System.currentTimeMillis() - lastPublish[0] > 20) {
                    lastPublish[0] = System.currentTimeMillis();
                    getProgressData().postValue(new ProgressData(filesToImport.size(), progress[0], (int) Math.round((bytesDone[0] + progress1) / totalBytes * 100.0),
                            decimalFormat.format((bytesDone[0] + progress1) / 1000000.0), totalMB));
                }
            };
            for (DocumentFile file : filesToImport) {
                if (Thread.currentThread().isInterrupted() || interrupted.get()) {
                    if (onImportDoneFragment != null) {
                        onImportDoneFragment.onDone(importToUri, sameDirectory, progress[0] - 1, errors, thumbErrors);
                    }
                    Log.e(TAG, "startImport: interrupted, stop");
                    break;
                }
                Pair<Boolean, Boolean> imported = new Pair<>(false, false);
                try {
                    imported = Encryption.importFileToDirectory(activity, file, destinationDirectory, password.getPassword(), 2, onProgress, interrupted);
                } catch (SecurityException e) {
                    e.printStackTrace();
                }
                if (interrupted.get()) {
                    break;
                }
                progress[0]++;
                bytesDone[0] += file.length();
                if (!imported.first) {
                    //progress[0]--;
                    //runOnUiThread(() -> Toaster.getInstance(GalleryActivity.this).showLong(getString(R.string.gallery_importing_error, file.getName())));
                    errors++;
                } else if (!imported.second) {
                    //runOnUiThread(() -> Toaster.getInstance(GalleryActivity.this).showLong(getString(R.string.gallery_importing_error_no_thumb, file.getName())));
                    thumbErrors++;
                }
                if (deleteAfterImport && imported.first) {
                    file.delete();
                }
            }
            Uri importToUri1 = getImportToUri();
            boolean sameDir = sameDirectory;
            if (onImportDoneBottomSheet != null) {
                onImportDoneBottomSheet.onDone(importToUri1, sameDir, progress[0] - 1, errors, thumbErrors);
            }
            if (onImportDoneFragment != null) {
                onImportDoneFragment.onDone(importToUri1, sameDir, progress[0] - 1, errors, thumbErrors);
            }
            interrupted.set(false);
        });
        importThread.start();
    }

}
