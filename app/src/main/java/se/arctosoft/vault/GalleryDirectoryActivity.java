package se.arctosoft.vault;

import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;
import androidx.viewpager2.widget.ViewPager2;

import java.util.ArrayList;
import java.util.List;

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
    private static final Object lock = new Object();
    public static final String EXTRA_DIRECTORY = "d";

    private ActivityGalleryDirectoryBinding binding;
    private GalleryDirectoryViewModel viewModel;

    private GalleryGridAdapter galleryGridAdapter;
    private GalleryPagerAdapter galleryPagerAdapter;
    private Settings settings;
    private Uri currentDirectory;
    private boolean inSelectionMode = false;
    private boolean isExporting = false;
    private boolean isCancelled = false;

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

        viewModel = new ViewModelProvider(this).get(GalleryDirectoryViewModel.class);

        init();
    }

    private void setLoading(boolean loading) {
        binding.cLLoading.cLLoading.setVisibility(loading ? View.VISIBLE : View.GONE);
    }

    private void setLoadingProgress(int exported, int failed, int total) {
        binding.cLLoading.cLLoading.setVisibility(View.VISIBLE);
        if (total > 0) {
            binding.cLLoading.txtImporting.setText(getString(R.string.gallery_exporting_progress, exported, total, failed));
            binding.cLLoading.txtImporting.setVisibility(View.VISIBLE);
        } else {
            binding.cLLoading.txtImporting.setVisibility(View.GONE);
        }
    }

    private void init() {
        settings = Settings.getInstance(this);
        if (settings.isLocked()) {
            finish();
            return;
        }
        setupViewpager();
        setupRecycler();
        setClickListeners();

        if (!viewModel.isInitialised()) {
            findFilesIn(currentDirectory);
        }
    }

    private void setClickListeners() {
        binding.btnDeleteFiles.setOnClickListener(v -> Dialogs.showConfirmationDialog(this, getString(R.string.dialog_delete_files_title),
                getResources().getQuantityString(R.plurals.dialog_delete_files_message, galleryGridAdapter.getSelectedFiles().size()),
                (dialog, which) -> {
                    setLoading(true);
                    new Thread(() -> {
                        synchronized (lock) {
                            for (GalleryFile f : galleryGridAdapter.getSelectedFiles()) {
                                if (isCancelled) {
                                    isCancelled = false;
                                    break;
                                }
                                boolean deleted = FileStuff.deleteFile(this, f.getUri());
                                FileStuff.deleteFile(this, f.getThumbUri());
                                if (deleted) {
                                    int i = viewModel.getGalleryFiles().indexOf(f);
                                    if (i >= 0) {
                                        viewModel.getGalleryFiles().remove(i);
                                        runOnUiThread(() -> {
                                            galleryGridAdapter.notifyItemRemoved(i);
                                            galleryPagerAdapter.notifyItemRemoved(i);
                                        });
                                    }
                                }
                            }
                            runOnUiThread(() -> {
                                galleryGridAdapter.onSelectionModeChanged(false);
                                setLoading(false);
                            });
                        }
                    }).start();
                }));
    }

    private void setupRecycler() {
        RecyclerView recyclerView = binding.recyclerView;
        int spanCount = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE ? 6 : 3;
        RecyclerView.LayoutManager layoutManager = new StaggeredGridLayoutManager(spanCount, RecyclerView.VERTICAL);
        recyclerView.setLayoutManager(layoutManager);
        galleryGridAdapter = new GalleryGridAdapter(this, viewModel.getGalleryFiles(), true, false); // TODO setting to show/hide names
        galleryGridAdapter.setOnFileDeleted(pos -> galleryPagerAdapter.notifyItemRemoved(pos));
        recyclerView.setAdapter(galleryGridAdapter);
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
        galleryPagerAdapter = new GalleryPagerAdapter(this, viewModel.getGalleryFiles(), pos -> galleryGridAdapter.notifyItemRemoved(pos), currentDirectory);
        binding.viewPager.setAdapter(galleryPagerAdapter);
        //Log.e(TAG, "setupViewpager: " + viewModel.getCurrentPosition() + " " + viewModel.isFullscreen());
        binding.viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                binding.recyclerView.scrollToPosition(position);
                viewModel.setCurrentPosition(position);
                galleryPagerAdapter.releaseVideo();
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
            for (int i = 0; i < galleryFiles.size(); i++) {
                GalleryFile g = galleryFiles.get(i);
                if (g.isDirectory()) {
                    int finalI = i;
                    new Thread(() -> {
                        g.setFilesInDirectory(FileStuff.getFilesInFolder(this, g.getUri()));
                        runOnUiThread(() -> galleryGridAdapter.notifyItemChanged(finalI));
                    }).start();
                }
            }

            runOnUiThread(() -> {
                setLoading(false);
                synchronized (lock) {
                    if (viewModel.isInitialised()) {
                        return;
                    }
                    viewModel.setInitialised(galleryFiles);
                    galleryGridAdapter.notifyItemRangeInserted(0, galleryFiles.size());
                    galleryPagerAdapter.notifyItemRangeInserted(0, galleryFiles.size());
                }
            });
        }).start();
    }

    @Override
    protected void onStop() {
        if (galleryPagerAdapter != null) {
            galleryPagerAdapter.pauseVideo();
        }
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        if (galleryPagerAdapter != null) {
            galleryPagerAdapter.releaseVideo();
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
            galleryGridAdapter.toggleFilenames();
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
            setLoadingProgress(0, 0, galleryFilesCopy.size());
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
                    Encryption.decryptAndExport(this, f.getUri(), currentDirectory, settings.getTempPassword(), result, f.isVideo());
                    runOnUiThread(() -> setLoadingProgress(exported[0], failed[0], galleryFilesCopy.size()));
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