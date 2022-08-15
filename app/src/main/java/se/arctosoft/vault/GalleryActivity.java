package se.arctosoft.vault;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.documentfile.provider.DocumentFile;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import se.arctosoft.vault.adapters.GalleryGridAdapter;
import se.arctosoft.vault.data.GalleryFile;
import se.arctosoft.vault.databinding.ActivityGalleryBinding;
import se.arctosoft.vault.encryption.Encryption;
import se.arctosoft.vault.encryption.Password;
import se.arctosoft.vault.utils.Dialogs;
import se.arctosoft.vault.utils.FileStuff;
import se.arctosoft.vault.utils.Settings;
import se.arctosoft.vault.utils.Toaster;

public class GalleryActivity extends AppCompatActivity {
    private static final String TAG = "GalleryActivity";
    private static final int REQUEST_ADD_DIRECTORY = 1;
    private static final int REQUEST_IMPORT_IMAGES = 3;

    private static final Object lock = new Object();

    private ActivityGalleryBinding binding;
    private GalleryGridAdapter galleryGridAdapter;
    private List<GalleryFile> galleryFiles;
    private Settings settings;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
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
        settings = Settings.getInstance(this);
        if (!settings.isUnlocked()) {
            finish();
            return;
        }
        galleryFiles = new ArrayList<>();
        RecyclerView recyclerView = binding.recyclerView;
        int spanCount = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE ? 6 : 3;
        RecyclerView.LayoutManager layoutManager = new GridLayoutManager(this, spanCount, RecyclerView.VERTICAL, false);
        recyclerView.setLayoutManager(layoutManager);
        galleryGridAdapter = new GalleryGridAdapter(this, galleryFiles, null);
        recyclerView.setAdapter(galleryGridAdapter);

        setClickListeners();

        findFolders();
    }

    private void setClickListeners() {
        binding.btnAddFolder.setOnClickListener(v -> startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), REQUEST_ADD_DIRECTORY));
        binding.btnImportFiles.setOnClickListener(v -> FileStuff.pickImageFiles(this, REQUEST_IMPORT_IMAGES));
    }

    private void setLoading(boolean loading) {
        binding.cLLoading.cLLoading.setVisibility(loading ? View.VISIBLE : View.GONE);
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
            List<Uri> directories = settings.getGalleryDirectoriesAsUri();
            //Log.e(TAG, "findFolders: found " + directories.size() + " folders");

            List<Uri> uriFiles = new ArrayList<>(directories.size());
            for (Uri uri : directories) {
                //Log.e(TAG, "findFolders: " + uri);
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
        if (requestCode == REQUEST_ADD_DIRECTORY && resultCode == Activity.RESULT_OK) {
            if (data != null) {
                Uri uri = data.getData();
                getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                DocumentFile documentFile = DocumentFile.fromTreeUri(this, uri);
                if (settings.addGalleryDirectory(documentFile.getUri())) {
                    addDirectory(documentFile.getUri());
                }
            }
        } else if (requestCode == REQUEST_IMPORT_IMAGES && resultCode == Activity.RESULT_OK) {
            if (data != null) {
                List<DocumentFile> documentFiles = FileStuff.getDocumentsFromDirectoryResult(this, data);
                if (!documentFiles.isEmpty()) {
                    Dialogs.showImportGalleryChooseDestinationDialog(this, settings, directory -> {
                        setLoading(true);
                        new Thread(() -> {
                            int failed = 0;
                            for (DocumentFile file : documentFiles) {
                                boolean imported = Encryption.importImageFileToDirectory(this, file, directory, settings);
                                if (!imported) {
                                    failed++;
                                    Toaster.getInstance(this).showLong("Failed to import " + file.getName());
                                }
                            }
                            int finalFailed = failed;
                            runOnUiThread(() -> Toaster.getInstance(this).showLong("Encrypted and imported " + (documentFiles.size() - finalFailed) + " files"));
                            findFolders();
                        }).start();
                    });
                }
            }
        }
    }

    private void addDirectory(Uri directoryUri) {
        long start = System.currentTimeMillis();
        List<Uri> files = FileStuff.getFilesInFolder(getContentResolver(), directoryUri);
        //Log.e(TAG, "onActivityResult: found " + files.size());
        //Log.e(TAG, "onActivityResult: took " + (System.currentTimeMillis() - start) + " ms");
        List<GalleryFile> galleryFiles = FileStuff.getEncryptedFilesInFolder(files);

        synchronized (lock) {
            this.galleryFiles.add(0, GalleryFile.asDirectory(directoryUri, galleryFiles));
            galleryGridAdapter.notifyItemInserted(0);
        }
    }

    private void addDirectories(@NonNull List<Uri> directories) {
        List<GalleryFile> galleryDirectories = new ArrayList<>(directories.size());
        for (Uri directory : directories) {
            long start = System.currentTimeMillis();
            List<Uri> files = FileStuff.getFilesInFolder(getContentResolver(), directory);
            //Log.e(TAG, "onActivityResult: found " + files.size() + " total files");
            //Log.e(TAG, "onActivityResult: took " + (System.currentTimeMillis() - start) + " ms");
            List<GalleryFile> galleryFiles = FileStuff.getEncryptedFilesInFolder(files);
            //Log.e(TAG, "addDirectories: found " + galleryFiles.size() + " encrypted files");
            galleryDirectories.add(GalleryFile.asDirectory(directory, galleryFiles));
        }
        runOnUiThread(() -> {
            setLoading(false);
            synchronized (lock) {
                this.galleryFiles.addAll(0, galleryDirectories);
                galleryGridAdapter.notifyItemRangeInserted(0, galleryDirectories.size());
            }
        });
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            onBackPressed();
            return true;
        } else if (id == R.id.import_files) {
            FileStuff.pickImageFiles(this, REQUEST_IMPORT_IMAGES);
            return true;
        } else if (id == R.id.edit_included_folders) {

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
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_gallery, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    protected void onDestroy() {
        Log.e(TAG, "onDestroy: ");
        //lock();
        super.onDestroy();
    }
}