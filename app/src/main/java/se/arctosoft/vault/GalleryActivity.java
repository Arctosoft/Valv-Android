/*
 * Valv-Android
 * Copyright (C) 2023 Arctosoft AB
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

package se.arctosoft.vault;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.icu.text.DecimalFormat;
import android.net.Uri;
import android.os.Bundle;
import android.util.Pair;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;

import androidx.activity.result.ActivityResult;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.documentfile.provider.DocumentFile;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.List;

import se.arctosoft.vault.adapters.GalleryGridAdapter;
import se.arctosoft.vault.data.GalleryFile;
import se.arctosoft.vault.databinding.ActivityGalleryBinding;
import se.arctosoft.vault.encryption.Encryption;
import se.arctosoft.vault.encryption.Password;
import se.arctosoft.vault.interfaces.IOnDirectoryAdded;
import se.arctosoft.vault.interfaces.IOnProgress;
import se.arctosoft.vault.utils.Dialogs;
import se.arctosoft.vault.utils.FileStuff;
import se.arctosoft.vault.utils.Settings;
import se.arctosoft.vault.utils.Toaster;
import se.arctosoft.vault.viewmodel.GalleryViewModel;

public class GalleryActivity extends BaseActivity {
    private static final String TAG = "GalleryActivity";

    private static final Object LOCK = new Object();

    private GalleryViewModel viewModel;
    private ActivityGalleryBinding binding;
    private GalleryGridAdapter galleryGridAdapter;
    private List<GalleryFile> galleryFiles;
    private Settings settings;
    private boolean cancelTask = false;
    private boolean inSelectionMode = false;
    private boolean isWaitingForUnlock = false;
    private Snackbar snackBarBackPressed;
    private Intent shareIntent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        binding = ActivityGalleryBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);
        ActionBar ab = getSupportActionBar();
        if (ab != null) {
            ab.setDisplayHomeAsUpEnabled(false);
            ab.setTitle(R.string.gallery_title);
        }
        init();

        Intent intent = getIntent();
        String action = intent.getAction();
        String type = intent.getType();

        if ((Intent.ACTION_SEND.equals(action) || Intent.ACTION_SEND_MULTIPLE.equals(action)) && type != null) {
            if (settings.isLocked()) {
                this.shareIntent = intent;
                this.isWaitingForUnlock = true;
                Toaster.getInstance(this).showShort(getString(R.string.gallery_share_locked));
                startActivity(new Intent(this, LaunchActivity.class)
                        .putExtra(LaunchActivity.EXTRA_ONLY_UNLOCK, true));
            } else {
                handleShareIntent(intent, action, type);
            }
        } else {
            if (settings.isLocked()) {
                finish();
                return;
            }
            setClickListeners();
            findFolders();
        }
    }

    private void init() {
        viewModel = new ViewModelProvider(this).get(GalleryViewModel.class);
        settings = Settings.getInstance(this);

        galleryFiles = new ArrayList<>();
        RecyclerView recyclerView = binding.recyclerView;
        int spanCount = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE ? 6 : 3;
        RecyclerView.LayoutManager layoutManager = new GridLayoutManager(this, spanCount, RecyclerView.VERTICAL, false);
        recyclerView.setLayoutManager(layoutManager);
        galleryGridAdapter = new GalleryGridAdapter(this, galleryFiles, true, true);
        recyclerView.setAdapter(galleryGridAdapter);
        galleryGridAdapter.setOnSelectionModeChanged(this::onSelectionModeChanged);
    }

    private void handleShareIntent(Intent intent, String action, String type) {
        setClickListeners();
        findFolders();
        if (Intent.ACTION_SEND.equals(action)) {
            if (type.startsWith("image/") || type.startsWith("video/")) {
                handleSendSingle(intent);
            }
        } else if (Intent.ACTION_SEND_MULTIPLE.equals(action)) {
            if (type.startsWith("image/") || type.startsWith("video/") || type.equals("*/*")) {
                handleSendMultiple(intent);
            }
        }
    }

    private void handleSendSingle(Intent intent) {
        Uri uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
        if (uri != null) {
            List<Uri> list = new ArrayList<>(1);
            list.add(uri);
            List<DocumentFile> documentFiles = FileStuff.getDocumentsFromShareIntent(this, list);
            if (!documentFiles.isEmpty()) {
                importFiles(documentFiles);
            }
        }
    }

    private void handleSendMultiple(Intent intent) {
        ArrayList<Uri> uris = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
        if (uris != null) {
            List<DocumentFile> documentFiles = FileStuff.getDocumentsFromShareIntent(this, uris);
            if (!documentFiles.isEmpty()) {
                importFiles(documentFiles);
            }
        }
    }


    private void onSelectionModeChanged(boolean inSelectionMode) {
        this.inSelectionMode = inSelectionMode;
        if (inSelectionMode) {
            binding.lLButtons.setVisibility(View.GONE);
            binding.lLSelectionButtons.setVisibility(View.VISIBLE);
        } else {
            binding.lLSelectionButtons.setVisibility(View.GONE);
            binding.lLButtons.setVisibility(View.VISIBLE);
        }
    }

    private void setClickListeners() {
        binding.btnAddFolder.setOnClickListener(v -> activityLauncher.launch(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), this::addFolder));
        binding.btnImportFiles.setOnClickListener(v -> showImportOverlay(true));
        binding.btnRemoveFolder.setOnClickListener(v -> Dialogs.showConfirmationDialog(this, getString(R.string.dialog_remove_folder_title),
                getResources().getQuantityString(R.plurals.dialog_remove_folder_message, galleryGridAdapter.getSelectedFiles().size()),
                (dialog, which) -> {
                    for (GalleryFile f : galleryGridAdapter.getSelectedFiles()) {
                        settings.removeGalleryDirectory(f.getUri());
                        try {
                            getContentResolver().releasePersistableUriPermission(f.getUri(), Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                        } catch (SecurityException e) {
                            e.printStackTrace();
                        }
                        int i = galleryFiles.indexOf(f);
                        if (i >= 0) {
                            galleryFiles.remove(i);
                            galleryGridAdapter.notifyItemRemoved(i);
                        }
                    }
                    galleryGridAdapter.onSelectionModeChanged(false);
                }));
        binding.btnImportImages.setOnClickListener(v -> {
            FileStuff.pickImageFiles(activityLauncher, result -> onImportImagesOrVideos(result.getData()));
            showImportOverlay(false);
        });
        binding.btnImportVideos.setOnClickListener(v -> {
            FileStuff.pickVideoFiles(activityLauncher, result -> onImportImagesOrVideos(result.getData()));
            showImportOverlay(false);
        });
        binding.btnImportTextWrite.setOnClickListener(v -> {
            Dialogs.showImportTextDialog(this, null, false, text -> {
                if (text != null && !text.isBlank()) {
                    importText(text);
                }
            });
            showImportOverlay(false);
        });
        binding.importChooseOverlay.setOnClickListener(v -> showImportOverlay(false));
    }

    private void importText(@NonNull String text) {
        Dialogs.showImportTextChooseDestinationDialog(this, settings, new Dialogs.IOnDirectorySelected() {
            @Override
            public void onDirectorySelected(@NonNull DocumentFile directory, boolean deleteOriginal) {
                DocumentFile createdFile = Encryption.importTextToDirectory(GalleryActivity.this, text, null, directory, settings);
                if (createdFile != null) {
                    Toaster.getInstance(GalleryActivity.this).showLong(getString(R.string.gallery_importing_done, 1));
                } else {
                    Toaster.getInstance(GalleryActivity.this).showLong(getString(R.string.gallery_importing_error));
                }
            }

            @Override
            public void onOtherDirectory() {
                viewModel.setTextToImport(text);
                binding.btnAddFolder.performClick();
            }
        });

    }

    private void onImportImagesOrVideos(@Nullable Intent data) {
        if (data != null) {
            List<DocumentFile> documentFiles = FileStuff.getDocumentsFromDirectoryResult(this, data);
            if (!documentFiles.isEmpty()) {
                importFiles(documentFiles);
            }
        }
    }

    private void addFolder(ActivityResult result) {
        if (result.getResultCode() == Activity.RESULT_OK) {
            Intent data = result.getData();
            if (data != null) {
                Uri uri = data.getData();
                DocumentFile documentFile = DocumentFile.fromTreeUri(this, uri);
                getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                settings.addGalleryDirectory(documentFile.getUri(), new IOnDirectoryAdded() {
                    @Override
                    public void onAddedAsRoot() {
                        Toaster.getInstance(GalleryActivity.this).showLong(getString(R.string.gallery_added_folder, FileStuff.getFilenameWithPathFromUri(uri)));
                        addDirectory(documentFile.getUri());
                    }

                    @Override
                    public void onAddedAsChildOf(@NonNull Uri parentUri) {
                        Toaster.getInstance(GalleryActivity.this).showLong(getString(R.string.gallery_added_folder_child, FileStuff.getFilenameWithPathFromUri(uri), FileStuff.getFilenameWithPathFromUri(parentUri)));
                    }

                    @Override
                    public void onAlreadyExists(boolean isRootDir) {
                        Toaster.getInstance(GalleryActivity.this).showLong(getString(R.string.gallery_added_folder_duplicate, FileStuff.getFilenameWithPathFromUri(uri)));
                        if (isRootDir) {
                            findFolders();
                        }
                    }
                });
                if (viewModel.getFilesToAdd() != null) {
                    importFiles(viewModel.getFilesToAdd());
                }
                if (viewModel.getTextToImport() != null) {
                    importText(viewModel.getTextToImport());
                }
            }
        } else if (result.getResultCode() == Activity.RESULT_CANCELED) {
            viewModel.setFilesToAdd(null);
        }
    }

    private void showImportOverlay(boolean show) {
        binding.cLImportChoose.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void setLoading(boolean loading) {
        binding.cLLoading.cLLoading.setVisibility(loading ? View.VISIBLE : View.GONE);
        binding.cLLoading.txtProgress.setVisibility(View.GONE);
    }

    private void setLoadingProgress(int progress, int total, String doneMB, String totalMB, int percentageDone) {
        binding.cLLoading.cLLoading.setVisibility(View.VISIBLE);
        if (total > 0) {
            binding.cLLoading.txtProgress.setText(getString(R.string.gallery_importing_progress, progress, total, doneMB, totalMB, percentageDone));
            binding.cLLoading.txtProgress.setVisibility(View.VISIBLE);
        } else {
            binding.cLLoading.txtProgress.setVisibility(View.GONE);
        }
    }

    private void findFolders() {
        setLoading(true);
        new Thread(() -> {
            runOnUiThread(() -> {
                synchronized (LOCK) {
                    int size = galleryFiles.size();
                    galleryFiles.clear();
                    galleryGridAdapter.notifyItemRangeRemoved(0, size);
                }
            });
            List<Uri> directories = settings.getGalleryDirectoriesAsUri(true);

            List<Uri> uriFiles = new ArrayList<>(directories.size());
            for (Uri uri : directories) {
                DocumentFile documentFile = DocumentFile.fromTreeUri(this, uri);
                if (documentFile.canRead()) {
                    uriFiles.add(documentFile.getUri());
                } else {
                    runOnUiThread(() -> Toaster.getInstance(this).showLong("No permission to read " + uri.getLastPathSegment() + ", please add it again"));
                    settings.removeGalleryDirectory(uri);
                }
            }
            addDirectories(uriFiles);
        }).start();
    }

    private void importFiles(List<DocumentFile> documentFiles) {
        Dialogs.showImportGalleryChooseDestinationDialog(this, settings, documentFiles.size(), new Dialogs.IOnDirectorySelected() {
            @Override
            public void onDirectorySelected(@NonNull DocumentFile directory, boolean deleteOriginal) {
                importToDirectory(documentFiles, directory, deleteOriginal);
            }

            @Override
            public void onOtherDirectory() {
                viewModel.setFilesToAdd(documentFiles);
                binding.btnAddFolder.performClick();
            }
        });
    }

    private void importToDirectory(@NonNull List<DocumentFile> documentFiles, @NonNull DocumentFile directory, boolean deleteOriginal) {
        new Thread(() -> {
            double totalBytes = 0;
            for (DocumentFile file : documentFiles) {
                totalBytes += file.length();
            }
            final DecimalFormat decimalFormat = new DecimalFormat("0.00");
            final String totalMB = decimalFormat.format(totalBytes / 1000000.0);
            final int[] progress = new int[]{1};
            final double[] bytesDone = new double[]{0};
            final long[] lastPublish = {0};
            double finalTotalSize = totalBytes;
            final IOnProgress onProgress = progress1 -> {
                if (System.currentTimeMillis() - lastPublish[0] > 20) {
                    lastPublish[0] = System.currentTimeMillis();
                    runOnUiThread(() -> setLoadingProgress(progress[0], documentFiles.size(), decimalFormat.format((bytesDone[0] + progress1) / 1000000.0), totalMB,
                            (int) Math.round((bytesDone[0] + progress1) / finalTotalSize * 100.0)));
                }
            };
            for (DocumentFile file : documentFiles) {
                if (cancelTask) {
                    cancelTask = false;
                    break;
                }
                Pair<Boolean, Boolean> imported = new Pair<>(false, false);
                try {
                    imported = Encryption.importFileToDirectory(GalleryActivity.this, file, directory, settings, onProgress);
                } catch (SecurityException e) {
                    e.printStackTrace();
                }
                progress[0]++;
                bytesDone[0] += file.length();
                if (!imported.first) {
                    progress[0]--;
                    runOnUiThread(() -> Toaster.getInstance(GalleryActivity.this).showLong(getString(R.string.gallery_importing_error, file.getName())));
                } else if (!imported.second) {
                    runOnUiThread(() -> Toaster.getInstance(GalleryActivity.this).showLong(getString(R.string.gallery_importing_error_no_thumb, file.getName())));
                }
                if (deleteOriginal && imported.first) {
                    file.delete();
                }
            }
            runOnUiThread(() -> {
                Toaster.getInstance(GalleryActivity.this).showLong(getString(R.string.gallery_importing_done, progress[0] - 1));
                setLoading(false);
            });
            settings.addGalleryDirectory(directory.getUri(), null);
            synchronized (LOCK) {
                for (int i = 0; i < GalleryActivity.this.galleryFiles.size(); i++) {
                    GalleryFile g = GalleryActivity.this.galleryFiles.get(i);
                    if (g.getUri() != null && g.getUri().equals(directory.getUri())) {
                        List<GalleryFile> galleryFiles = FileStuff.getFilesInFolder(GalleryActivity.this, directory.getUri());
                        g.setFilesInDirectory(galleryFiles);
                        int finalI = i;
                        GalleryFile removed = GalleryActivity.this.galleryFiles.remove(finalI);
                        GalleryActivity.this.galleryFiles.add(0, removed);
                        runOnUiThread(() -> {
                            galleryGridAdapter.notifyItemMoved(finalI, 0);
                            galleryGridAdapter.notifyItemChanged(0);
                        });
                        break;
                    }
                }
            }
        }).start();
    }

    private void addDirectory(Uri directoryUri) {
        List<GalleryFile> galleryFiles = FileStuff.getFilesInFolder(this, directoryUri);

        synchronized (LOCK) {
            this.galleryFiles.add(0, GalleryFile.asDirectory(directoryUri, galleryFiles));
            galleryGridAdapter.notifyItemInserted(0);
        }
    }

    private void refreshDirectory(GalleryFile dir) {
        List<GalleryFile> found = FileStuff.getFilesInFolder(this, dir.getUri());
        int pos = galleryFiles.indexOf(dir);
        if (pos >= 0) {
            dir.setFilesInDirectory(found);
            galleryGridAdapter.notifyItemChanged(pos);
        }
    }

    private void addDirectories(@NonNull List<Uri> directories) {
        for (int i = 0; i < directories.size(); i++) {
            Uri uri = directories.get(i);
            GalleryFile galleryFile = GalleryFile.asDirectory(uri, null);
            runOnUiThread(() -> {
                synchronized (LOCK) {
                    this.galleryFiles.add(galleryFile);
                    galleryGridAdapter.notifyItemInserted(this.galleryFiles.size() - 1);
                }
            });
            new Thread(() -> {
                List<GalleryFile> galleryFiles = FileStuff.getFilesInFolder(this, uri);
                galleryFile.setFilesInDirectory(galleryFiles);
                runOnUiThread(() -> galleryGridAdapter.notifyItemChanged(this.galleryFiles.indexOf(galleryFile)));
            }).start();
        }
        runOnUiThread(() -> {
            if (!this.galleryFiles.isEmpty()) {
                synchronized (LOCK) {
                    this.galleryFiles.add(0, GalleryFile.asAllFolder(getString(R.string.gallery_all)));
                    galleryGridAdapter.notifyItemInserted(0);
                }
            }
            setLoading(false);
        });
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            onBackPressed();
            return true;
        } else if (id == R.id.edit_included_folders) {
            Dialogs.showEditIncludedFolders(this, settings, selectedToRemove -> {
                settings.removeGalleryDirectories(selectedToRemove);
                Toaster.getInstance(this).showLong(getResources().getQuantityString(R.plurals.edit_included_removed, selectedToRemove.size(), selectedToRemove.size()));
                findFolders();
            });
        } else if (id == R.id.about) {
            Dialogs.showAboutDialog(this);
        } else if (id == R.id.lock) {
            lock();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void lock() {
        Password.lock(this, settings);
        finish();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (isWaitingForUnlock && settings != null && !settings.isLocked() && shareIntent != null) {
            handleShareIntent(shareIntent, shareIntent.getAction(), shareIntent.getType());
            isWaitingForUnlock = false;
            shareIntent = null;
        } else if (!isWaitingForUnlock && (settings == null || settings.isLocked())) {
            finishAffinity();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (galleryFiles != null && galleryGridAdapter != null && !galleryFiles.isEmpty()) {
            synchronized (LOCK) {
                GridLayoutManager lm = (GridLayoutManager) binding.recyclerView.getLayoutManager();
                if (lm != null) {
                    int firstVisiblePosition = lm.findFirstVisibleItemPosition();
                    int lastVisibleItemPosition = lm.findLastVisibleItemPosition();
                    if (firstVisiblePosition != RecyclerView.NO_POSITION && lastVisibleItemPosition != RecyclerView.NO_POSITION) {
                        for (int i = firstVisiblePosition; i <= lastVisibleItemPosition; i++) {
                            if (i >= 0 && i < galleryFiles.size()) {
                                GalleryFile galleryFile = galleryFiles.get(i);
                                if (!galleryFile.isAllFolder() && galleryFile.isDirectory()) {
                                    refreshDirectory(galleryFile);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (binding.cLLoading.cLLoading.getVisibility() == View.VISIBLE) {
            cancelTask = true;
        } else if (binding.cLImportChoose.getVisibility() == View.VISIBLE) {
            showImportOverlay(false);
        } else if (inSelectionMode && galleryGridAdapter != null) {
            galleryGridAdapter.onSelectionModeChanged(false);
        } else if (snackBarBackPressed == null || !snackBarBackPressed.isShownOrQueued()) {
            snackBarBackPressed = Snackbar.make(binding.lLButtons, getString(R.string.main_press_back_again_to_exit), 2000);
            snackBarBackPressed.setAnchorView(binding.lLButtons);
            snackBarBackPressed.show();
        } else {
            if (isTaskRoot()) {
                Password.lock(this, settings);
            }
            super.onBackPressed();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_gallery, menu);
        return super.onCreateOptionsMenu(menu);
    }
}