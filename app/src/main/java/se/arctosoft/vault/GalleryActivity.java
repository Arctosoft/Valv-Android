package se.arctosoft.vault;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.documentfile.provider.DocumentFile;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import se.arctosoft.vault.adapters.GalleryAdapter;
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
    private GalleryAdapter galleryAdapter;
    private List<GalleryFile> galleryDirectories;
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
        galleryDirectories = new ArrayList<>();
        RecyclerView recyclerView = binding.recyclerView;
        RecyclerView.LayoutManager layoutManager = new GridLayoutManager(this, 3, RecyclerView.VERTICAL, false);
        recyclerView.setLayoutManager(layoutManager);
        galleryAdapter = new GalleryAdapter(this, galleryDirectories);
        recyclerView.setAdapter(galleryAdapter);

        findFolders();
    }

    private void findFolders() {
        int size = galleryDirectories.size();
        galleryDirectories.clear();
        galleryAdapter.notifyItemRangeRemoved(0, size);
        List<Uri> directories = settings.getGalleryDirectoriesAsUri();
        Log.e(TAG, "findFolders: found " + directories.size() + " folders");

        binding.btnAddFolder.setOnClickListener(v -> startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), REQUEST_ADD_DIRECTORY));

        List<DocumentFile> documentFiles = new ArrayList<>(directories.size());
        for (Uri uri : directories) {
            Log.e(TAG, "findFolders: " + uri);
            DocumentFile documentFile = DocumentFile.fromTreeUri(this, uri);
            if (documentFile.canRead()) {
                documentFiles.add(documentFile);
            } else {
                Toaster.getInstance(this).showLong("No permission to read " + uri.getLastPathSegment() + ", please add it again");
                settings.removeGalleryDirectory(uri);
            }
        }
        addDirectories(documentFiles);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_ADD_DIRECTORY && resultCode == Activity.RESULT_OK) {
            if (data != null) {
                Uri uri = data.getData();
                getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                DocumentFile pickedDir = DocumentFile.fromTreeUri(this, uri);
                if (settings.addGalleryDirectory(uri)) {
                    addDirectory(pickedDir);
                }
            }
        } else if (requestCode == REQUEST_IMPORT_IMAGES && resultCode == Activity.RESULT_OK) {
            if (data != null) {
                ClipData clipData = data.getClipData();
                List<Uri> uris = FileStuff.uriListFromClipData(clipData);
                Log.e(TAG, "onActivityResult: got " + uris.size());
                if (uris.isEmpty()) {
                    Uri dataUri = data.getData();
                    if (dataUri != null) {
                        uris.add(dataUri);
                    }
                }
                Log.e(TAG, "onActivityResult: got " + uris.size());
                List<DocumentFile> documentFiles = new ArrayList<>();
                for (Uri uri : uris) {
                    DocumentFile pickedFile = DocumentFile.fromSingleUri(this, uri);
                    if (pickedFile != null && pickedFile.getType() != null && pickedFile.getType().startsWith("image/")) {
                        documentFiles.add(pickedFile);
                    }
                }
                Dialogs.showImportGalleryChooseDestinationDialog(this, settings, documentFiles.size(), directory -> {
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
                        runOnUiThread(() -> {
                            Toaster.getInstance(this).showLong("Encrypted and imported " + (documentFiles.size() - finalFailed) + " files");
                            findFolders();
                        });
                    }).start();
                });
            }
        }
    }

    private void addDirectory(DocumentFile directory) {
        long start = System.currentTimeMillis();
        List<Uri> files = FileStuff.getFilesInFolder(getContentResolver(), directory);
        Log.e(TAG, "onActivityResult: found " + files.size());
        Log.e(TAG, "onActivityResult: took " + (System.currentTimeMillis() - start) + " ms");
        List<GalleryFile> galleryFiles = FileStuff.getEncryptedFilesInFolder(this, files);

        synchronized (lock) {
            galleryDirectories.add(0, GalleryFile.asDirectory(directory, galleryFiles));
            galleryAdapter.notifyItemInserted(0);
        }
    }

    private void addDirectories(@NonNull List<DocumentFile> directories) {
        List<GalleryFile> galleryDirectories = new ArrayList<>(directories.size());
        for (DocumentFile directory : directories) {
            long start = System.currentTimeMillis();
            List<Uri> files = FileStuff.getFilesInFolder(getContentResolver(), directory);
            Log.e(TAG, "onActivityResult: found " + files.size() + " total files");
            Log.e(TAG, "onActivityResult: took " + (System.currentTimeMillis() - start) + " ms");
            List<GalleryFile> galleryFiles = FileStuff.getEncryptedFilesInFolder(this, files);
            Log.e(TAG, "addDirectories: found " + galleryFiles.size() + " encrypted files");
            galleryDirectories.add(GalleryFile.asDirectory(directory, galleryFiles));
        }
        synchronized (lock) {
            this.galleryDirectories.addAll(0, galleryDirectories);
            galleryAdapter.notifyItemRangeInserted(0, galleryDirectories.size());
        }
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
        lock();
        super.onDestroy();
    }
}