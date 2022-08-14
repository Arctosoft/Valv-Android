package se.arctosoft.vault;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.documentfile.provider.DocumentFile;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import se.arctosoft.vault.adapters.GalleryAdapter;
import se.arctosoft.vault.data.GalleryFile;
import se.arctosoft.vault.databinding.ActivityGalleryDirectoryBinding;
import se.arctosoft.vault.encryption.Password;
import se.arctosoft.vault.utils.FileStuff;
import se.arctosoft.vault.utils.Settings;

public class GalleryDirectoryActivity extends AppCompatActivity {
    private static final String TAG = "GalleryDirectoryActivity";
    private static final Object lock = new Object();
    public static final String EXTRA_DIRECTORY = "d";

    private ActivityGalleryDirectoryBinding binding;
    private GalleryAdapter galleryAdapter;
    private List<GalleryFile> directoryFiles;
    private Settings settings;
    private DocumentFile currentDirectory;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityGalleryDirectoryBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        Bundle extras = getIntent().getExtras();
        Uri uri = null;
        if (extras != null) {
            uri = Uri.parse(extras.getString(EXTRA_DIRECTORY));
        }
        if (uri == null) {
            finish();
            return;
        }

        currentDirectory = DocumentFile.fromSingleUri(this, uri);

        setSupportActionBar(binding.toolbar);
        ActionBar ab = getSupportActionBar();
        if (ab != null) {
            ab.setDisplayHomeAsUpEnabled(true);
            ab.setTitle(currentDirectory.getName());
        }

        init();
    }

    private void init() {
        settings = Settings.getInstance(this);
        if (!settings.isUnlocked()) {
            finish();
            return;
        }
        directoryFiles = new ArrayList<>();
        RecyclerView recyclerView = binding.recyclerView;
        RecyclerView.LayoutManager layoutManager = new GridLayoutManager(this, 3, RecyclerView.VERTICAL, false);
        recyclerView.setLayoutManager(layoutManager);
        galleryAdapter = new GalleryAdapter(this, directoryFiles);
        recyclerView.setAdapter(galleryAdapter);

        findFilesIn(currentDirectory);
    }

    private void findFilesIn(DocumentFile directory) {
        long start = System.currentTimeMillis();
        List<Uri> files = FileStuff.getFilesInFolder(getContentResolver(), directory);
        Log.e(TAG, "onActivityResult: found " + files.size());
        Log.e(TAG, "onActivityResult: took " + (System.currentTimeMillis() - start) + " ms");
        List<GalleryFile> galleryFiles = FileStuff.getEncryptedFilesInFolder(this, files);

        synchronized (lock) {
            directoryFiles.addAll(0, galleryFiles);
            galleryAdapter.notifyItemRangeInserted(0, galleryFiles.size());
        }
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
        }
        return super.onOptionsItemSelected(item);
    }

    private void lock() {
        Password.lock(this, settings);
        finishAffinity();
        startActivity(new Intent(this, LaunchActivity.class));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_gallery_directory, menu);
        return super.onCreateOptionsMenu(menu);
    }
}