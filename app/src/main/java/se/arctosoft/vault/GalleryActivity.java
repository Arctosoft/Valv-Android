package se.arctosoft.vault;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.icu.text.DecimalFormat;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.util.Pair;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
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
import se.arctosoft.vault.utils.Dialogs;
import se.arctosoft.vault.utils.FileStuff;
import se.arctosoft.vault.utils.Settings;
import se.arctosoft.vault.utils.Toaster;
import se.arctosoft.vault.viewmodel.GalleryViewModel;

public class GalleryActivity extends AppCompatActivity {
    private static final String TAG = "GalleryActivity";
    private static final int REQUEST_ADD_DIRECTORY = 1;
    private static final int REQUEST_IMPORT_IMAGES = 3;
    private static final int REQUEST_IMPORT_VIDEOS = 4;

    private static final Object lock = new Object();

    private GalleryViewModel viewModel;
    private ActivityGalleryBinding binding;
    private GalleryGridAdapter galleryGridAdapter;
    private List<GalleryFile> galleryFiles;
    private Settings settings;
    private boolean cancelTask = false;
    private boolean inSelectionMode = false;
    private Snackbar snackBarBackPressed;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        binding = ActivityGalleryBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);
        ActionBar ab = getSupportActionBar();
        if (ab != null) {
            ab.setDisplayHomeAsUpEnabled(true);
            ab.setTitle(R.string.gallery_title);
        }

        init();
    }

    private void init() {
        viewModel = new ViewModelProvider(this).get(GalleryViewModel.class);
        settings = Settings.getInstance(this);
        if (settings.isLocked()) {
            finish();
            return;
        }
        galleryFiles = new ArrayList<>();
        RecyclerView recyclerView = binding.recyclerView;
        int spanCount = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE ? 6 : 3;
        RecyclerView.LayoutManager layoutManager = new GridLayoutManager(this, spanCount, RecyclerView.VERTICAL, false);
        recyclerView.setLayoutManager(layoutManager);
        galleryGridAdapter = new GalleryGridAdapter(this, galleryFiles, true, true);
        recyclerView.setAdapter(galleryGridAdapter);
        galleryGridAdapter.setOnSelectionModeChanged(this::onSelectionModeChanged);

        setClickListeners();

        findFolders();
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
        binding.btnAddFolder.setOnClickListener(v -> startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), REQUEST_ADD_DIRECTORY));
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
            FileStuff.pickImageFiles(this, REQUEST_IMPORT_IMAGES);
            showImportOverlay(false);
        });
        binding.btnImportVideos.setOnClickListener(v -> {
            FileStuff.pickVideoFiles(this, REQUEST_IMPORT_VIDEOS);
            showImportOverlay(false);
        });
        binding.importChooseOverlay.setOnClickListener(v -> showImportOverlay(false));
    }

    private void showImportOverlay(boolean show) {
        binding.cLImportChoose.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void setLoading(boolean loading) {
        binding.cLLoading.cLLoading.setVisibility(loading ? View.VISIBLE : View.GONE);
        binding.cLLoading.txtProgress.setVisibility(View.GONE);
    }

    private void setLoadingProgress(int progress, int total, String doneMB, String totalMB) {
        binding.cLLoading.cLLoading.setVisibility(View.VISIBLE);
        if (total > 0) {
            binding.cLLoading.txtProgress.setText(getString(R.string.gallery_importing_progress, progress, total, doneMB, totalMB));
            binding.cLLoading.txtProgress.setVisibility(View.VISIBLE);
        } else {
            binding.cLLoading.txtProgress.setVisibility(View.GONE);
        }
    }

    private void findFolders() {
        setLoading(true);
        new Thread(() -> {
            runOnUiThread(() -> {
                synchronized (lock) {
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

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_ADD_DIRECTORY) {
            if (resultCode == Activity.RESULT_OK) {
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
                }
            } else if (resultCode == Activity.RESULT_CANCELED) {
                viewModel.setFilesToAdd(null);
            }
        } else if ((requestCode == REQUEST_IMPORT_IMAGES || requestCode == REQUEST_IMPORT_VIDEOS) && resultCode == Activity.RESULT_OK) {
            if (data != null) {
                List<DocumentFile> documentFiles = FileStuff.getDocumentsFromDirectoryResult(this, data);
                if (!documentFiles.isEmpty()) {
                    importFiles(documentFiles);
                }
            }
        }
    }

    private void importFiles(List<DocumentFile> documentFiles) {
        Dialogs.showImportGalleryChooseDestinationDialog(this, settings, new Dialogs.IOnDirectorySelected() {
            @Override
            public void onDirectorySelected(@NonNull DocumentFile directory) {
                importToDirectory(documentFiles, directory);
            }

            @Override
            public void onOtherDirectory() {
                viewModel.setFilesToAdd(documentFiles);
                binding.btnAddFolder.performClick();
            }
        });
    }

    private void importToDirectory(@NonNull List<DocumentFile> documentFiles, @NonNull DocumentFile directory) {
        new Thread(() -> {
            double totalSize = 0;
            for (DocumentFile file : documentFiles) {
                totalSize += (file.length() / 1000000.0);
            }
            final DecimalFormat decimalFormat = new DecimalFormat("0.00");
            final String totalMB = decimalFormat.format(totalSize);
            final int[] progress = new int[]{1};
            final double[] bytesDone = new double[]{0};
            runOnUiThread(() -> setLoadingProgress(progress[0], documentFiles.size(), "0", totalMB));
            for (DocumentFile file : documentFiles) {
                if (cancelTask) {
                    cancelTask = false;
                    break;
                }
                Pair<Boolean, Boolean> imported = new Pair<>(false, false);
                try {
                    imported = Encryption.importFileToDirectory(GalleryActivity.this, file, directory, settings);
                } catch (SecurityException e) {
                    e.printStackTrace();
                }
                progress[0]++;
                bytesDone[0] += file.length();
                runOnUiThread(() -> setLoadingProgress(progress[0], documentFiles.size(), decimalFormat.format(bytesDone[0] / 1000000.0), totalMB));
                if (!imported.first) {
                    runOnUiThread(() -> Toaster.getInstance(GalleryActivity.this).showLong(getString(R.string.gallery_importing_error, file.getName())));
                } else if (!imported.second) {
                    runOnUiThread(() -> Toaster.getInstance(GalleryActivity.this).showLong(getString(R.string.gallery_importing_error_no_thumb, file.getName())));
                }
            }
            runOnUiThread(() -> {
                Toaster.getInstance(GalleryActivity.this).showLong(getString(R.string.gallery_importing_done, progress[0] - 1));
                setLoading(false);
            });
            settings.addGalleryDirectory(directory.getUri(), null);
            synchronized (lock) {
                for (int i = 0; i < GalleryActivity.this.galleryFiles.size(); i++) {
                    GalleryFile g = GalleryActivity.this.galleryFiles.get(i);
                    if (g.getUri().equals(directory.getUri())) {
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

        synchronized (lock) {
            this.galleryFiles.add(0, GalleryFile.asDirectory(directoryUri, galleryFiles));
            galleryGridAdapter.notifyItemInserted(0);
        }
    }

    private void addDirectories(@NonNull List<Uri> directories) {
        for (int i = 0; i < directories.size(); i++) {
            Uri uri = directories.get(i);
            GalleryFile galleryFile = GalleryFile.asDirectory(uri, null);
            runOnUiThread(() -> {
                synchronized (lock) {
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
        runOnUiThread(() -> setLoading(false));
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
            Dialogs.showTextDialog(this, getString(R.string.dialog_about_title), getString(R.string.dialog_about_message, BuildConfig.BUILD_TYPE, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE));
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
            super.onBackPressed();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_gallery, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "onDestroy: ");
        //lock();
        super.onDestroy();
    }
}