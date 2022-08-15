package se.arctosoft.vault;

import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import java.util.ArrayList;
import java.util.List;

import se.arctosoft.vault.adapters.GalleryAdapter;
import se.arctosoft.vault.adapters.GalleryFullscreenAdapter;
import se.arctosoft.vault.data.GalleryFile;
import se.arctosoft.vault.databinding.ActivityGalleryDirectoryBinding;
import se.arctosoft.vault.encryption.Password;
import se.arctosoft.vault.utils.FileStuff;
import se.arctosoft.vault.utils.Settings;

public class GalleryDirectoryActivity extends AppCompatActivity {
    private static final String TAG = "GalleryDirectoryActivity";
    private static final Object lock = new Object();
    public static final String EXTRA_DIRECTORY = "d";
    //private static final String SAVED_KEY_POSITION = "p";
    //public static int LAST_POS = 0;

    private ActivityGalleryDirectoryBinding binding;
    private GalleryAdapter galleryAdapter;
    private GalleryFullscreenAdapter galleryFullscreenAdapter;
    private List<GalleryFile> galleryFiles;
    private Settings settings;
    private Uri currentDirectory;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityGalleryDirectoryBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        Bundle extras = getIntent().getExtras();
        currentDirectory = null;
        if (extras != null) {
            currentDirectory = Uri.parse(extras.getString(EXTRA_DIRECTORY));
        }
        if (currentDirectory == null) {
            finish();
            return;
        }

        setSupportActionBar(binding.toolbar);
        ActionBar ab = getSupportActionBar();
        if (ab != null) {
            ab.setDisplayHomeAsUpEnabled(true);
            ab.setTitle(FileStuff.getFilenameFromUri(currentDirectory, false));
        }

        //int pos = LAST_POS;
        //if (savedInstanceState != null) {
        //pos = savedInstanceState.getInt(SAVED_KEY_POSITION, 0);
        //Log.e(TAG, "onRestoreInstanceState: scroll to " + pos);
        //}

        init();
    }

    private void setLoading(boolean loading) {
        binding.cLLoading.cLLoading.setVisibility(loading ? View.VISIBLE : View.GONE);
    }

    private void init() {
        settings = Settings.getInstance(this);
        if (!settings.isUnlocked()) {
            finish();
            return;
        }
        galleryFiles = new ArrayList<>();
        setupRecycler();
        setupViewpager();

        findFilesIn(currentDirectory);
    }

    private void setupRecycler() {
        RecyclerView recyclerView = binding.recyclerView;
        int spanCount = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE ? 6 : 3;
        RecyclerView.LayoutManager layoutManager = new GridLayoutManager(this, spanCount, RecyclerView.VERTICAL, false);
        recyclerView.setLayoutManager(layoutManager);
        galleryAdapter = new GalleryAdapter(this, galleryFiles);
        recyclerView.setAdapter(galleryAdapter);
        galleryAdapter.setOnFileCLicked(pos -> showViewpager(true, pos));
    }

    private void setupViewpager() {
        showViewpager(false, -1);
        galleryFullscreenAdapter = new GalleryFullscreenAdapter(this, galleryFiles, pos -> galleryAdapter.notifyItemRemoved(pos), currentDirectory);
        binding.viewPager.setAdapter(galleryFullscreenAdapter);
        binding.viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                binding.recyclerView.scrollToPosition(position);
            }
        });
    }

    private void showViewpager(boolean show, int pos) {
        if (show) {
            binding.viewPager.setVisibility(View.VISIBLE);
            binding.viewPager.setCurrentItem(pos, false);
        } else {
            binding.viewPager.setVisibility(View.GONE);
            if (pos >= 0) {
                RecyclerView.ViewHolder viewHolder = binding.recyclerView.findViewHolderForAdapterPosition(pos);
                if (viewHolder != null) {
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
            long start = System.currentTimeMillis();
            List<Uri> files = FileStuff.getFilesInFolder(getContentResolver(), directoryUri);
            Log.e(TAG, "onActivityResult: found " + files.size());
            Log.e(TAG, "onActivityResult: took " + (System.currentTimeMillis() - start) + " ms");
            List<GalleryFile> galleryFiles = FileStuff.getEncryptedFilesInFolder(files);

            runOnUiThread(() -> {
                setLoading(false);
                synchronized (lock) {
                    this.galleryFiles.addAll(0, galleryFiles);
                    galleryAdapter.notifyItemRangeInserted(0, galleryFiles.size());
                }
            });
        }).start();
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

    @Override
    public void onBackPressed() {
        if (binding.viewPager.getVisibility() == View.VISIBLE) {
            showViewpager(false, binding.viewPager.getCurrentItem());
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        //savePosition();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        //int pos = savePosition();
        //outState.putInt(SAVED_KEY_POSITION, pos);
        //Log.e(TAG, "onSaveInstanceState: " + pos);
    }

    /*private int savePosition() {
        GridLayoutManager layoutManager = (GridLayoutManager) binding.recyclerView.getLayoutManager();
        return layoutManager.findFirstVisibleItemPosition();
    }*/

    /*@Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        int pos = savedInstanceState.getInt(SAVED_KEY_POSITION, 0);
        binding.recyclerView.postDelayed(() -> binding.recyclerView.scrollToPosition(pos), 100);
        Log.e(TAG, "onRestoreInstanceState: scroll to " + pos);
    }*/
}