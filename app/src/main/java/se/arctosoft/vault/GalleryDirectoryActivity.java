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

import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.documentfile.provider.DocumentFile;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;
import androidx.viewpager2.widget.ViewPager2;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import se.arctosoft.vault.adapters.GalleryGridAdapter;
import se.arctosoft.vault.adapters.GalleryPagerAdapter;
import se.arctosoft.vault.data.GalleryFile;
import se.arctosoft.vault.databinding.ActivityGalleryDirectoryBinding;
import se.arctosoft.vault.encryption.Encryption;
import se.arctosoft.vault.encryption.Password;
import se.arctosoft.vault.exception.InvalidPasswordException;
import se.arctosoft.vault.utils.Dialogs;
import se.arctosoft.vault.utils.FileStuff;
import se.arctosoft.vault.utils.Settings;
import se.arctosoft.vault.utils.Toaster;
import se.arctosoft.vault.viewmodel.GalleryDirectoryViewModel;

public class GalleryDirectoryActivity extends AppCompatActivity {
    private static final String TAG = "GalleryDirectoryActivity";
    private static final Object LOCK = new Object();
    public static final String EXTRA_DIRECTORY = "d";
    public static final String EXTRA_NESTED_PATH = "n";
    public static final String EXTRA_IS_ALL = "a";
    private static final int MIN_FILES_FOR_FAST_SCROLL = 60;

    private ActivityGalleryDirectoryBinding binding;
    private GalleryDirectoryViewModel viewModel;

    private GalleryGridAdapter galleryGridAdapter;
    private GalleryPagerAdapter galleryPagerAdapter;
    private Settings settings;
    private Uri currentDirectory;
    private DocumentFile currentDocumentDirectory;
    private String nestedPath;
    private boolean inSelectionMode = false;
    private boolean isExporting = false;
    private boolean isCancelled = false;
    private boolean isAllFolder = false;

    private int foundFiles = 0, foundFolders = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        binding = ActivityGalleryDirectoryBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        Bundle extras = getIntent().getExtras();
        currentDirectory = null;
        currentDocumentDirectory = null;
        if (extras != null) {
            this.nestedPath = extras.getString(EXTRA_NESTED_PATH);
            String dir = extras.getString(EXTRA_DIRECTORY);
            if (dir != null) {
                currentDirectory = Uri.parse(dir);
                currentDocumentDirectory = DocumentFile.fromTreeUri(this, currentDirectory);
                if (!currentDocumentDirectory.getUri().toString().equals(currentDirectory.toString())) {
                    String[] paths = nestedPath.split("/");
                    for (String s : paths) {
                        if (currentDocumentDirectory != null && s != null && !s.isEmpty()) {
                            DocumentFile found = currentDocumentDirectory.findFile(s);
                            if (found != null) {
                                currentDocumentDirectory = found;
                            } else {
                                break;
                            }
                        }
                    }
                }
            }

            isAllFolder = extras.getBoolean(EXTRA_IS_ALL, false);
        }
        if (this.nestedPath == null) {
            this.nestedPath = "";
        }
        if (currentDirectory == null && !isAllFolder) {
            finish();
            return;
        }

        setSupportActionBar(binding.toolbar);
        ActionBar ab = getSupportActionBar();
        if (ab != null) {
            ab.setDisplayHomeAsUpEnabled(true);
            ab.setTitle(isAllFolder ? getString(R.string.gallery_all) : FileStuff.getFilenameFromUri(currentDirectory, false));
        }

        viewModel = new ViewModelProvider(this).get(GalleryDirectoryViewModel.class);

        init();
    }

    private void setLoading(boolean loading) {
        binding.cLLoading.cLLoading.setVisibility(loading ? View.VISIBLE : View.GONE);
        binding.cLLoading.txtProgress.setVisibility(View.GONE);
    }

    private void setLoadingWithProgress(int progress, int failed, int total, int stringId) {
        binding.cLLoading.cLLoading.setVisibility(View.VISIBLE);
        if (total > 0) {
            binding.cLLoading.txtProgress.setText(getString(stringId, progress, total, failed));
            binding.cLLoading.txtProgress.setVisibility(View.VISIBLE);
        } else {
            binding.cLLoading.txtProgress.setVisibility(View.GONE);
        }
    }

    private void setLoadingAllWithProgress() {
        binding.cLLoading.cLLoading.setVisibility(View.VISIBLE);
        binding.cLLoading.txtProgress.setText(getString(R.string.gallery_loading_all_progress, foundFiles, foundFolders));
        binding.cLLoading.txtProgress.setVisibility(View.VISIBLE);
    }

    private void init() {
        settings = Settings.getInstance(this);
        if (settings.isLocked()) {
            finish();
            return;
        }
        DocumentFile documentFile = DocumentFile.fromSingleUri(this, currentDirectory);
        if (isAllFolder || (documentFile != null && documentFile.isDirectory() && documentFile.exists())) {
            setupViewpager();
            setupRecycler();
            setClickListeners();

            if (!viewModel.isInitialised()) {
                if (isAllFolder) {
                    findAllFiles();
                } else {
                    findFilesIn(currentDirectory);
                }
            }
        } else {
            Toaster.getInstance(this).showLong(getString(R.string.directory_does_not_exist));
            finish();
        }
    }

    private void setClickListeners() {
        binding.btnDeleteFiles.setOnClickListener(v -> Dialogs.showConfirmationDialog(this, getString(R.string.dialog_delete_files_title),
                getResources().getQuantityString(R.plurals.dialog_delete_files_message, galleryGridAdapter.getSelectedFiles().size()),
                (dialog, which) -> deleteSelectedFiles()));
    }

    public void onSelectionChanged(int selected) {
        binding.btnDeleteFiles.setText(getString(R.string.gallery_delete_selected_files, selected));
    }

    private void deleteSelectedFiles() {
        setLoading(true);
        new Thread(() -> {
            synchronized (LOCK) {
                List<GalleryFile> selectedFiles = galleryGridAdapter.getSelectedFiles();
                ConcurrentLinkedQueue<GalleryFile> queue = new ConcurrentLinkedQueue<>(selectedFiles);
                List<Integer> positionsDeleted = Collections.synchronizedList(new ArrayList<>());
                AtomicInteger deletedCount = new AtomicInteger(0);
                AtomicInteger failedCount = new AtomicInteger(0);
                final int total = selectedFiles.size();
                final int threadCount;
                if (total < 4) {
                    threadCount = 1;
                } else if (total < 20) {
                    threadCount = 4;
                } else {
                    threadCount = 8;
                }
                runOnUiThread(() -> setLoadingWithProgress(0, 0, 0, R.string.gallery_deleting_progress));
                List<Thread> threads = new ArrayList<>(threadCount);
                for (int i = 0; i < threadCount; i++) {
                    Thread t = new Thread(() -> {
                        GalleryFile f;
                        while (!isCancelled && (f = queue.poll()) != null) {
                            deleteFile(total, f, positionsDeleted, deletedCount, failedCount);
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
                if (isCancelled) {
                    isCancelled = false;
                }
                Collections.sort(positionsDeleted);
                runOnUiThread(() -> {
                    for (int i = positionsDeleted.size() - 1; i >= 0; i--) {
                        viewModel.getGalleryFiles().remove(i);
                        galleryGridAdapter.notifyItemRemoved(i);
                        galleryPagerAdapter.notifyItemRemoved(i);
                    }
                    galleryGridAdapter.onSelectionModeChanged(false);
                    setLoading(false);
                });
            }
        }).start();
    }

    private void deleteFile(int total, GalleryFile file, List<Integer> positionsDeleted, AtomicInteger deletedCount, AtomicInteger failedCount) {
        if (file == null || isCancelled) {
            return;
        }
        boolean deleted = FileStuff.deleteFile(this, file.getUri());
        FileStuff.deleteFile(this, file.getThumbUri());
        FileStuff.deleteFile(this, file.getNoteUri());
        if (deleted) {
            deletedCount.addAndGet(1);
            int i = viewModel.getGalleryFiles().indexOf(file);
            if (i >= 0) {
                positionsDeleted.add(i);
            }
        } else {
            failedCount.addAndGet(1);
        }
        runOnUiThread(() -> setLoadingWithProgress(deletedCount.get() + failedCount.get(), failedCount.get(), total, R.string.gallery_deleting_progress));
    }

    private void setupRecycler() {
        if (viewModel.isInitialised()) {
            binding.recyclerView.setFastScrollEnabled(viewModel.getGalleryFiles().size() > MIN_FILES_FOR_FAST_SCROLL);
        } else {
            binding.recyclerView.setFastScrollEnabled(false);
        }
        int spanCount = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE ? 6 : 3;
        RecyclerView.LayoutManager layoutManager = new StaggeredGridLayoutManager(spanCount, RecyclerView.VERTICAL);
        binding.recyclerView.setLayoutManager(layoutManager);
        galleryGridAdapter = new GalleryGridAdapter(this, viewModel.getGalleryFiles(), settings.showFilenames(), false);
        galleryGridAdapter.setNestedPath(nestedPath);
        galleryGridAdapter.setOnFileDeleted(pos -> galleryPagerAdapter.notifyItemRemoved(pos));
        binding.recyclerView.setAdapter(galleryGridAdapter);
        galleryGridAdapter.setOnFileCLicked(pos -> showViewpager(true, pos, true));
        galleryGridAdapter.setOnSelectionModeChanged(this::onSelectionModeChanged);
    }

    private void onSelectionModeChanged(boolean inSelectionMode) {
        this.inSelectionMode = inSelectionMode;
        if (inSelectionMode) {
            binding.lLSelectionButtons.setVisibility(View.VISIBLE);
        } else {
            binding.lLSelectionButtons.setVisibility(View.GONE);
        }
        invalidateOptionsMenu();
    }

    private void setupViewpager() {
        galleryPagerAdapter = new GalleryPagerAdapter(this, viewModel.getGalleryFiles(), pos -> galleryGridAdapter.notifyItemRemoved(pos), currentDocumentDirectory, isAllFolder, nestedPath);
        binding.viewPager.setAdapter(galleryPagerAdapter);
        //Log.e(TAG, "setupViewpager: " + viewModel.getCurrentPosition() + " " + viewModel.isFullscreen());
        binding.viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                binding.recyclerView.scrollToPosition(position);
                viewModel.setCurrentPosition(position);
            }
        });
        binding.viewPager.postDelayed(() -> {
            binding.viewPager.setCurrentItem(viewModel.getCurrentPosition(), false);
            showViewpager(viewModel.isViewpagerVisible(), viewModel.getCurrentPosition(), false);
        }, 200);
    }

    private void showViewpager(boolean show, int pos, boolean animate) {
        //Log.e(TAG, "showViewpager: " + show + " " + pos);
        viewModel.setViewpagerVisible(show);
        galleryPagerAdapter.showPager(show);
        if (show) {
            binding.viewPager.setVisibility(View.VISIBLE);
            binding.viewPager.setCurrentItem(pos, false);
        } else {
            binding.viewPager.setVisibility(View.GONE);
            if (pos >= 0) {
                RecyclerView.ViewHolder viewHolder = binding.recyclerView.findViewHolderForAdapterPosition(pos);
                if (viewHolder != null && animate) {
                    Animation animation = new AlphaAnimation(0, 1);
                    animation.setDuration(500);
                    animation.setInterpolator(new LinearInterpolator());
                    viewHolder.itemView.startAnimation(animation);
                }
                binding.recyclerView.scrollToPosition(pos);
            }
        }
    }

    private void findFilesIn(Uri directoryUri) {
        setLoading(true);
        new Thread(() -> {
            List<GalleryFile> galleryFiles = FileStuff.getFilesInFolder(this, directoryUri);

            runOnUiThread(() -> {
                setLoading(false);
                if (galleryFiles.size() > MIN_FILES_FOR_FAST_SCROLL) {
                    binding.recyclerView.setFastScrollEnabled(true);
                }
                synchronized (LOCK) {
                    if (viewModel.isInitialised()) {
                        return;
                    }
                    viewModel.setInitialised(galleryFiles);
                    galleryGridAdapter.notifyItemRangeInserted(0, galleryFiles.size());
                    galleryPagerAdapter.notifyItemRangeInserted(0, galleryFiles.size());
                }
            });

            for (int i = 0; i < galleryFiles.size(); i++) {
                GalleryFile g = galleryFiles.get(i);
                if (g.isDirectory()) {
                    int finalI = i;
                    new Thread(() -> {
                        List<GalleryFile> found = FileStuff.getFilesInFolder(this, g.getUri());
                        if (found.isEmpty()) {
                            runOnUiThread(() -> {
                                synchronized (LOCK) {
                                    int i1 = viewModel.getGalleryFiles().indexOf(g);
                                    if (i1 >= 0) {
                                        viewModel.getGalleryFiles().remove(i1);
                                        galleryGridAdapter.notifyItemRemoved(i1);
                                        galleryPagerAdapter.notifyItemRemoved(i1);
                                    }
                                }
                            });
                        } else {
                            g.setFilesInDirectory(found);
                            runOnUiThread(() -> galleryGridAdapter.notifyItemChanged(finalI));
                        }
                    }).start();
                }
            }
        }).start();
    }

    private synchronized void incrementFiles(int amount) {
        foundFiles += amount;
    }

    private synchronized void incrementFolders(int amount) {
        foundFolders += amount;
    }

    private void findAllFiles() {
        Log.e(TAG, "findAllFiles: ");
        foundFiles = 0;
        foundFolders = 0;
        setLoading(true);
        new Thread(() -> {
            List<Uri> directories = settings.getGalleryDirectoriesAsUri(true);

            List<Uri> uriFiles = new ArrayList<>(directories.size());
            for (Uri uri : directories) {
                DocumentFile documentFile = DocumentFile.fromTreeUri(this, uri);
                if (documentFile.canRead()) {
                    uriFiles.add(documentFile.getUri());
                }
            }

            runOnUiThread(this::setLoadingAllWithProgress);

            if (isCancelled) {
                finish();
                return;
            }

            List<GalleryFile> folders = new ArrayList<>();
            List<GalleryFile> files = new LinkedList<>();
            long start = System.currentTimeMillis();
            List<GalleryFile> filesToSearch = new ArrayList<>();
            for (Uri uri : uriFiles) {
                List<GalleryFile> filesInFolder = FileStuff.getFilesInFolder(this, uri);
                for (GalleryFile foundFile : filesInFolder) {
                    if (foundFile.isDirectory()) {
                        Log.e(TAG, "findAllFiles: found " + foundFile.getNameWithPath());
                        boolean add = true;
                        for (GalleryFile addedFile : filesToSearch) {
                            if (foundFile.getNameWithPath().startsWith(addedFile.getNameWithPath() + "/")) {
                                // Do not add e.g. folder Pictures/a/b if folder Pictures/a have already been added as it will be searched by a thread in findAllFilesInFolder().
                                // Prevents showing duplicate files
                                add = false;
                                Log.e(TAG, "findAllFiles: not adding nested " + foundFile.getNameWithPath());
                                break;
                            }
                        }
                        if (add) {
                            filesToSearch.add(foundFile);
                        }
                    } else {
                        filesToSearch.add(foundFile);
                    }
                }
            }
            for (GalleryFile galleryFile : filesToSearch) {
                if (galleryFile.isDirectory()) {
                    folders.add(galleryFile);
                } else {
                    files.add(galleryFile);
                }
            }

            incrementFiles(files.size());

            runOnUiThread(this::setLoadingAllWithProgress);

            List<Thread> threads = new ArrayList<>();
            for (GalleryFile galleryFile : folders) {
                if (galleryFile.isDirectory()) {
                    Thread t = new Thread(() -> {
                        List<GalleryFile> allFilesInFolder = findAllFilesInFolder(galleryFile.getUri());
                        synchronized (LOCK) {
                            files.addAll(allFilesInFolder);
                        }
                    });
                    threads.add(t);
                    t.start();
                }
            }
            for (Thread t : threads) {
                if (isCancelled) {
                    finish();
                    return;
                }
                try {
                    t.join();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
            Log.e(TAG, "findAllFiles: joined, found " + files.size() + ", took " + (System.currentTimeMillis() - start));
            if (isCancelled) {
                finish();
                return;
            }

            files.sort(GalleryFile::compareTo);

            runOnUiThread(() -> {
                setLoading(false);
                if (files.size() > MIN_FILES_FOR_FAST_SCROLL) {
                    binding.recyclerView.setFastScrollEnabled(true);
                }
                if (viewModel.isInitialised()) {
                    return;
                }
                viewModel.setInitialised(files);
                galleryGridAdapter.notifyItemRangeInserted(0, files.size());
                galleryPagerAdapter.notifyItemRangeInserted(0, files.size());
            });
        }).start();
    }

    @NonNull
    private List<GalleryFile> findAllFilesInFolder(Uri uri) {
        Log.e(TAG, "findAllFilesInFolder: find all files in " + uri.getLastPathSegment());
        List<GalleryFile> files = new ArrayList<>();
        if (isFinishing() || isDestroyed() || isCancelled) {
            return files;
        }
        incrementFolders(1);
        List<GalleryFile> filesInFolder = FileStuff.getFilesInFolder(this, uri);
        for (GalleryFile galleryFile : filesInFolder) {
            if (isCancelled) {
                return files;
            }
            if (galleryFile.isDirectory()) {
                runOnUiThread(this::setLoadingAllWithProgress);
                files.addAll(findAllFilesInFolder(galleryFile.getUri()));
            } else {
                files.add(galleryFile);
            }
        }
        incrementFiles(files.size());
        runOnUiThread(this::setLoadingAllWithProgress);
        return files;
    }

    @Override
    protected void onStop() {
        if (galleryPagerAdapter != null) {
            galleryPagerAdapter.pausePlayers();
        }
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        if (galleryPagerAdapter != null) {
            galleryPagerAdapter.releasePlayers();
        }
        super.onDestroy();
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            onBackPressed();
            return true;
        } else if (id == R.id.lock) {
            lock();
            return true;
        } else if (id == R.id.toggle_filename) {
            settings.setShowFilenames(galleryGridAdapter.toggleFilenames());
            return true;
        } else if (id == R.id.select_all) {
            galleryGridAdapter.selectAll();
            return true;
        } else if (id == R.id.export_selected) {
            exportSelected();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void lock() {
        Password.lock(this, settings);
        finishAffinity();
        startActivity(new Intent(this, LaunchActivity.class));
    }

    private void exportSelected() {
        Dialogs.showConfirmationDialog(this, getString(R.string.dialog_export_selected_title), getString(R.string.dialog_export_selected_message, FileStuff.getFilenameWithPathFromUri(currentDirectory)), (dialog, which) -> {
            isExporting = true;
            final List<GalleryFile> galleryFilesCopy = new ArrayList<>(galleryGridAdapter.getSelectedFiles());
            setLoadingWithProgress(0, 0, galleryFilesCopy.size(), R.string.gallery_exporting_progress);
            galleryGridAdapter.onSelectionModeChanged(false);
            new Thread(() -> {
                final int[] exported = {0};
                final int[] failed = {0};
                for (GalleryFile f : galleryFilesCopy) {
                    if (isFinishing() || isDestroyed() || !isExporting) {
                        break;
                    }
                    Encryption.IOnUriResult result = new Encryption.IOnUriResult() {
                        @Override
                        public void onUriResult(Uri outputUri) {
                            exported[0]++;
                        }

                        @Override
                        public void onError(Exception e) {
                            failed[0]++;
                        }

                        @Override
                        public void onInvalidPassword(InvalidPasswordException e) {
                            failed[0]++;
                        }
                    };
                    Encryption.decryptAndExport(this, f.getUri(), currentDocumentDirectory, f, settings.getTempPassword(), result, f.isVideo());
                    runOnUiThread(() -> setLoadingWithProgress(exported[0], failed[0], galleryFilesCopy.size(), R.string.gallery_exporting_progress));
                }
                runOnUiThread(() -> {
                    isExporting = false;
                    setLoading(false);
                    if (failed[0] == 0) {
                        Toaster.getInstance(this).showLong(getString(R.string.gallery_selected_files_exported, exported[0]));
                    } else {
                        Toaster.getInstance(this).showLong(getString(R.string.gallery_selected_files_exported_with_failed, exported[0], failed[0]));
                    }
                });
            }).start();
        });
    }

    @Override
    public boolean onCreateOptionsMenu(@NonNull Menu menu) {
        getMenuInflater().inflate(R.menu.menu_gallery_directory, menu);
        menu.findItem(R.id.toggle_filename).setVisible(!inSelectionMode);
        menu.findItem(R.id.select_all).setVisible(inSelectionMode);
        menu.findItem(R.id.export_selected).setVisible(inSelectionMode);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public void onBackPressed() {
        if (viewModel.isViewpagerVisible()) {
            showViewpager(false, viewModel.getCurrentPosition(), true);
        } else if (isExporting) {
            isExporting = false;
        } else if (binding.cLLoading.cLLoading.getVisibility() == View.VISIBLE) {
            isCancelled = true;
        } else if (inSelectionMode && galleryGridAdapter != null) {
            galleryGridAdapter.onSelectionModeChanged(false);
        } else {
            super.onBackPressed();
        }
    }
}