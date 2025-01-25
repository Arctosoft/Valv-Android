/*
 * Valv-Android
 * Copyright (c) 2024 Arctosoft AB.
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
 * You should have received a copy of the GNU General Public License along with this program.  If not, see https://www.gnu.org/licenses/.
 */

package se.arctosoft.vault;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.MenuProvider;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavBackStackEntry;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;
import androidx.viewpager2.widget.ViewPager2;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import se.arctosoft.vault.adapters.GalleryGridAdapter;
import se.arctosoft.vault.adapters.GalleryPagerAdapter;
import se.arctosoft.vault.data.FileType;
import se.arctosoft.vault.data.GalleryFile;
import se.arctosoft.vault.data.Password;
import se.arctosoft.vault.databinding.FragmentDirectoryBinding;
import se.arctosoft.vault.utils.Dialogs;
import se.arctosoft.vault.utils.FileStuff;
import se.arctosoft.vault.utils.Settings;
import se.arctosoft.vault.utils.Toaster;
import se.arctosoft.vault.viewmodel.CopyViewModel;
import se.arctosoft.vault.viewmodel.DeleteViewModel;
import se.arctosoft.vault.viewmodel.ExportViewModel;
import se.arctosoft.vault.viewmodel.GalleryViewModel;
import se.arctosoft.vault.viewmodel.ImportViewModel;
import se.arctosoft.vault.viewmodel.MoveViewModel;
import se.arctosoft.vault.viewmodel.PasswordViewModel;

public abstract class DirectoryBaseFragment extends Fragment implements MenuProvider {
    private static final String TAG = "DirectoryBaseFragment";

    static final Object LOCK = new Object();
    static final int MIN_FILES_FOR_FAST_SCROLL = 60;
    static final int ORDER_BY_NEWEST = 0;
    static final int ORDER_BY_OLDEST = 1;
    static final int ORDER_BY_LARGEST = 2;
    static final int ORDER_BY_SMALLEST = 3;
    static final int ORDER_BY_RANDOM = 4;
    static final int FILTER_ALL = 0;
    static final int FILTER_IMAGES = FileType.IMAGE_V2.type;
    static final int FILTER_GIFS = FileType.GIF_V2.type;
    static final int FILTER_VIDEOS = FileType.VIDEO_V2.type;
    static final int FILTER_TEXTS = FileType.TEXT_V2.type;

    NavController navController;
    FragmentDirectoryBinding binding;
    PasswordViewModel passwordViewModel;
    GalleryViewModel galleryViewModel;
    ImportViewModel importViewModel;
    DeleteViewModel deleteViewModel;
    ExportViewModel exportViewModel;
    CopyViewModel copyViewModel;
    MoveViewModel moveViewModel;

    GalleryGridAdapter galleryGridAdapter;
    GalleryPagerAdapter galleryPagerAdapter;
    Settings settings;

    int orderBy = ORDER_BY_NEWEST;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        Log.e(TAG, "onCreate: ");
        super.onCreate(savedInstanceState);

        navController = NavHostFragment.findNavController(this);

        NavBackStackEntry navBackStackEntry = navController.getCurrentBackStackEntry();
        SavedStateHandle savedStateHandle = navBackStackEntry.getSavedStateHandle();
        savedStateHandle.getLiveData(PasswordFragment.LOGIN_SUCCESSFUL).observe(navBackStackEntry, o -> {
            Boolean success = (Boolean) o;
            if (!success) {
                Password.lock(getContext());
                FragmentActivity activity = getActivity();
                if (activity != null) {
                    activity.finish();
                }
            }
        });
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        Log.e(TAG, "onCreateView: ");
        binding = FragmentDirectoryBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        passwordViewModel = new ViewModelProvider(requireActivity()).get(PasswordViewModel.class);
        galleryViewModel = new ViewModelProvider(this).get(GalleryViewModel.class);
        importViewModel = new ViewModelProvider(this).get(ImportViewModel.class);
        deleteViewModel = new ViewModelProvider(this).get(DeleteViewModel.class);
        exportViewModel = new ViewModelProvider(this).get(ExportViewModel.class);
        copyViewModel = new ViewModelProvider(this).get(CopyViewModel.class);
        moveViewModel = new ViewModelProvider(this).get(MoveViewModel.class);
        navController = NavHostFragment.findNavController(this);
        Log.e(TAG, "onViewCreated: locked? " + passwordViewModel.isLocked());
        if (passwordViewModel.isLocked()) {
            Password.lock(requireActivity());
            navController.navigate(R.id.password);
        } else {
            init();
        }

        setPadding();
    }

    private void setPadding() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.layoutFabsAdd, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(bars.left, 0, bars.right, bars.bottom);
            return WindowInsetsCompat.CONSUMED;
        });
        ViewCompat.setOnApplyWindowInsetsListener(binding.layoutFabsRemoveFolders, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(bars.left, 0, bars.right, bars.bottom);
            return WindowInsetsCompat.CONSUMED;
        });
        View.OnAttachStateChangeListener onAttachStateChangeListener = new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(@NonNull View view) {
                view.requestApplyInsets();
            }

            @Override
            public void onViewDetachedFromWindow(@NonNull View view) {

            }
        };
        binding.layoutFabsAdd.addOnAttachStateChangeListener(onAttachStateChangeListener);
        binding.layoutFabsRemoveFolders.addOnAttachStateChangeListener(onAttachStateChangeListener);
    }

    public abstract void init();

    boolean initActionBar(boolean isAllFolder) {
        ActionBar ab = ((AppCompatActivity) requireActivity()).getSupportActionBar();

        if (ab != null) {
            ab.setDisplayHomeAsUpEnabled(true);
            ab.setTitle(isAllFolder ? getString(R.string.gallery_all) : FileStuff.getFilenameFromUri(galleryViewModel.getCurrentDirectoryUri(), false));
            return true;
        }
        return false;
    }

    void initViewModels() {
        importViewModel.setOnImportDoneFragment((destinationUri, sameDirectory, importedCount, failedCount, thumbErrorCount) -> {
            Log.e(TAG, "setOnImportDoneFragment: " + destinationUri + ", " + sameDirectory + ", " + importedCount + ", " + failedCount + ", " + thumbErrorCount);

            FragmentActivity activity = getActivity();
            if (activity == null || activity.isDestroyed()) {
                return;
            }
            activity.runOnUiThread(() -> {
                Toaster.getInstance(activity).showLong(getString(R.string.gallery_selected_files_imported, importedCount));

                if (galleryViewModel.isRootDir() && galleryViewModel.getGalleryFiles().isEmpty()) {
                    settings.addGalleryDirectory(destinationUri, true, null);
                    addRootFolders();
                } else if (sameDirectory || (destinationUri != null && galleryViewModel.getCurrentDirectoryUri() != null && destinationUri.toString().equals(galleryViewModel.getCurrentDirectoryUri().toString()))) { // files added to current directory
                    synchronized (LOCK) {
                        int size = galleryViewModel.getGalleryFiles().size();
                        galleryViewModel.getGalleryFiles().clear();
                        galleryViewModel.getHiddenFiles().clear();
                        galleryGridAdapter.notifyItemRangeRemoved(0, size);
                        galleryPagerAdapter.notifyItemRangeRemoved(0, size);
                        galleryViewModel.setInitialised(false);
                        findFilesIn(galleryViewModel.getCurrentDirectoryUri());
                    }
                } else {
                    synchronized (LOCK) {
                        for (int i = 0; i < galleryViewModel.getGalleryFiles().size(); i++) {
                            GalleryFile g = galleryViewModel.getGalleryFiles().get(i);
                            if (g.isDirectory() && g.getUri() != null && g.getUri().equals(destinationUri)) {
                                g.resetFilesInDirectory();
                                galleryGridAdapter.notifyItemChanged(i);
                                break;
                            }
                        }
                    }
                }
            });
        });

        deleteViewModel.setOnDeleteDoneFragment(deletedFiles -> {
            Log.e(TAG, "setOnDeleteDoneFragment:  deleted " + deletedFiles.size());
            FragmentActivity activity = getActivity();
            if (activity == null || activity.isDestroyed()) {
                return;
            }
            activity.runOnUiThread(() -> {
                Toaster.getInstance(activity).showLong(getString(R.string.gallery_selected_files_deleted, deletedFiles.size()));
                synchronized (LOCK) {
                    List<GalleryFile> galleryFiles = galleryViewModel.getGalleryFiles();
                    for (int i = galleryFiles.size() - 1; i >= 0; i--) {
                        GalleryFile f = galleryFiles.get(i);
                        for (GalleryFile deleted : deletedFiles) {
                            if (f.equals(deleted)) {
                                galleryFiles.remove(i);
                                galleryGridAdapter.notifyItemRemoved(i);
                                galleryPagerAdapter.notifyItemRemoved(i);
                            }
                        }
                    }
                    galleryGridAdapter.onSelectionModeChanged(false);
                }
            });
        });

        exportViewModel.setOnDoneFragment(processedFiles -> {
            Log.e(TAG, "setOnExportDoneFragment: exported " + processedFiles.size());
            FragmentActivity activity = getActivity();
            if (activity == null || activity.isDestroyed()) {
                return;
            }
            activity.runOnUiThread(() -> {
                galleryGridAdapter.onSelectionModeChanged(false);
                Toaster.getInstance(activity).showLong(getString(R.string.gallery_selected_files_exported, processedFiles.size()));
            });
        });

        copyViewModel.setOnDoneFragment(processedFiles -> {
            Log.e(TAG, "setOnDoneFragment: copied " + processedFiles.size());
            FragmentActivity activity = getActivity();
            if (activity == null || activity.isDestroyed()) {
                return;
            }
            activity.runOnUiThread(() -> {
                galleryGridAdapter.onSelectionModeChanged(false);
                Toaster.getInstance(activity).showLong(getString(R.string.gallery_selected_files_copied, processedFiles.size()));
            });
        });

        moveViewModel.setOnDoneFragment(processedFiles -> {
            Log.e(TAG, "setOnDoneFragment: moved " + processedFiles.size());
            FragmentActivity activity = getActivity();
            if (activity == null || activity.isDestroyed()) {
                return;
            }
            activity.runOnUiThread(() -> {
                Toaster.getInstance(activity).showLong(getString(R.string.gallery_selected_files_moved, processedFiles.size()));
                if (galleryViewModel.isAllFolder()) {
                    return;
                }
                synchronized (LOCK) {
                    List<GalleryFile> galleryFiles = galleryViewModel.getGalleryFiles();
                    for (int i = galleryFiles.size() - 1; i >= 0; i--) {
                        GalleryFile f = galleryFiles.get(i);
                        for (GalleryFile moved : processedFiles) {
                            if (f.equals(moved)) {
                                galleryFiles.remove(i);
                                galleryGridAdapter.notifyItemRemoved(i);
                                galleryPagerAdapter.notifyItemRemoved(i);
                            }
                        }
                    }
                    galleryGridAdapter.onSelectionModeChanged(false);
                }
            });
        });
    }

    abstract void addRootFolders();

    void findFilesIn(Uri directoryUri) {
        Log.e(TAG, "findFilesIn: " + directoryUri);
        setLoading(true);
        new Thread(() -> {
            FragmentActivity activity = getActivity();
            if (activity == null || !isSafe()) {
                Log.e(TAG, "findFilesIn: not safe, return");
                return;
            }
            List<GalleryFile> galleryFiles = FileStuff.getFilesInFolder(activity, directoryUri);

            activity.runOnUiThread(() -> {
                setLoading(false);
                synchronized (LOCK) {
                    if (galleryViewModel.isInitialised()) {
                        return;
                    }
                    galleryViewModel.addGalleryFiles(galleryFiles);
                    galleryViewModel.setInitialised(true);
                    galleryGridAdapter.notifyItemRangeInserted(0, galleryFiles.size());
                    galleryPagerAdapter.notifyItemRangeInserted(0, galleryFiles.size());
                    initFastScroll();
                }
            });
        }).start();
    }

    void setupGrid() {
        initFastScroll();
        int spanCount = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE ? 6 : 3;
        RecyclerView.LayoutManager layoutManager = new StaggeredGridLayoutManager(spanCount, RecyclerView.VERTICAL);
        binding.recyclerView.setLayoutManager(layoutManager);
        galleryGridAdapter = new GalleryGridAdapter(requireActivity(), galleryViewModel.getGalleryFiles(), settings.showFilenames(), galleryViewModel.isRootDir(), galleryViewModel);
        galleryGridAdapter.setNestedPath(galleryViewModel.getNestedPath());
        galleryGridAdapter.setOnFileDeleted(pos -> galleryPagerAdapter.notifyItemRemoved(pos));
        binding.recyclerView.setAdapter(galleryGridAdapter);
        galleryGridAdapter.setOnFileCLicked(pos -> showViewpager(true, pos, true));
        galleryGridAdapter.setOnSelectionModeChanged(this::onSelectionModeChanged);
    }

    private void initFastScroll() {
        if (galleryViewModel.isInitialised()) {
            binding.recyclerView.setFastScrollEnabled(galleryViewModel.getGalleryFiles().size() > MIN_FILES_FOR_FAST_SCROLL);
        } else {
            binding.recyclerView.setFastScrollEnabled(false);
        }
    }

    abstract void onSelectionModeChanged(boolean inSelectionMode);

    void setupViewpager() {
        galleryPagerAdapter = new GalleryPagerAdapter(requireActivity(), galleryViewModel.getGalleryFiles(), pos -> galleryGridAdapter.notifyItemRemoved(pos), galleryViewModel.getCurrentDocumentDirectory(),
                galleryViewModel.isAllFolder(), galleryViewModel.getNestedPath(), galleryViewModel);
        binding.viewPager.setAdapter(galleryPagerAdapter);
        //Log.e(TAG, "setupViewpager: " + viewModel.getCurrentPosition() + " " + viewModel.isFullscreen());
        binding.viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                binding.recyclerView.scrollToPosition(position);
                galleryViewModel.setCurrentPosition(position);
            }
        });
        binding.viewPager.postDelayed(() -> {
            binding.viewPager.setCurrentItem(galleryViewModel.getCurrentPosition(), false);
            showViewpager(galleryViewModel.isViewpagerVisible(), galleryViewModel.getCurrentPosition(), false);
        }, 200);
    }

    void setLoading(boolean loading) {
        binding.cLLoading.cLLoading.setVisibility(loading ? View.VISIBLE : View.GONE);
        binding.cLLoading.txtProgress.setVisibility(View.GONE);
    }

    void showViewpager(boolean show, int pos, boolean animate) {
        //Log.e(TAG, "showViewpager: " + show + " " + pos);
        galleryViewModel.setViewpagerVisible(show);
        galleryPagerAdapter.showPager(show);
        FragmentActivity activity = getActivity();
        if (activity == null || activity.isDestroyed()) {
            return;
        }
        ActionBar ab = ((AppCompatActivity) activity).getSupportActionBar();

        if (show) {
            if (ab != null) {
                ab.hide();
            }
            binding.viewPager.setVisibility(View.VISIBLE);
            binding.viewPager.setCurrentItem(pos, false);
        } else {
            if (ab != null) {
                ab.show();
            }
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

    boolean isSafe() {
        return !(isRemoving() || getActivity() == null || isDetached() || !isAdded() || getView() == null);
    }

    @SuppressLint("NotifyDataSetChanged")
    void orderBy(int order) {
        this.orderBy = order;
        new Thread(() -> {
            synchronized (LOCK) {
                List<GalleryFile> galleryFiles = galleryViewModel.getGalleryFiles();
                if (order == ORDER_BY_NEWEST) {
                    galleryFiles.sort((o1, o2) -> {
                        if (o1.getLastModified() > o2.getLastModified()) {
                            return -1;
                        } else if (o1.getLastModified() < o2.getLastModified()) {
                            return 1;
                        }
                        return 0;
                    });
                } else if (order == ORDER_BY_OLDEST) {
                    galleryFiles.sort((o1, o2) -> {
                        if (o1.getLastModified() > o2.getLastModified()) {
                            return 1;
                        } else if (o1.getLastModified() < o2.getLastModified()) {
                            return -1;
                        }
                        return 0;
                    });
                } else if (order == ORDER_BY_LARGEST) {
                    galleryFiles.sort((o1, o2) -> {
                        if (o1.getSize() > o2.getSize()) {
                            return -1;
                        } else if (o1.getSize() < o2.getSize()) {
                            return 1;
                        }
                        return 0;
                    });
                } else if (order == ORDER_BY_SMALLEST) {
                    galleryFiles.sort((o1, o2) -> {
                        if (o1.getSize() > o2.getSize()) {
                            return 1;
                        } else if (o1.getSize() < o2.getSize()) {
                            return -1;
                        }
                        return 0;
                    });
                } else {
                    Collections.shuffle(galleryFiles);
                }
                requireActivity().runOnUiThread(() -> {
                    synchronized (LOCK) {
                        galleryGridAdapter.notifyDataSetChanged();
                        galleryPagerAdapter.notifyDataSetChanged();
                    }
                });
            }
        }).start();
    }

    @SuppressLint("NotifyDataSetChanged")
    void filterBy(int filter) {
        new Thread(() -> {
            synchronized (LOCK) {
                List<GalleryFile> hiddenFiles = galleryViewModel.getHiddenFiles();
                List<GalleryFile> galleryFiles = galleryViewModel.getGalleryFiles();
                if (!hiddenFiles.isEmpty()) {
                    galleryViewModel.getGalleryFiles().addAll(hiddenFiles);
                    hiddenFiles.clear();
                }
                if (filter != FILTER_ALL) {
                    Iterator<GalleryFile> it = galleryFiles.iterator();
                    while (it.hasNext()) {
                        GalleryFile f = it.next();
                        if (!f.isDirectory() && f.getFileType().type != filter) {
                            it.remove();
                            hiddenFiles.add(f);
                        }
                    }
                    requireActivity().runOnUiThread(() -> {
                        galleryGridAdapter.notifyDataSetChanged();
                        galleryPagerAdapter.notifyDataSetChanged();
                    });
                }
                orderBy(this.orderBy);
            }
        }).start();
    }

    @Override
    public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {
        menu.clear();
        if (galleryViewModel.isInSelectionMode()) {
            if (galleryViewModel.isRootDir()) {
                menuInflater.inflate(R.menu.menu_main_selection_root, menu);
            } else {
                menuInflater.inflate(R.menu.menu_main_selection_dir, menu);
            }
        } else {
            if (galleryViewModel.isRootDir()) {
                menuInflater.inflate(R.menu.menu_root, menu);
            } else {
                menuInflater.inflate(R.menu.menu_dir, menu);
            }
        }
    }

    @Override
    public boolean onMenuItemSelected(@NonNull MenuItem menuItem) {
        int id = menuItem.getItemId();

        if (id == R.id.action_settings) {
            navController.navigate(R.id.action_directory_to_settings);
            return true;
        } else if (id == R.id.lock) {
            Password.lock(getContext());
            FragmentActivity activity = getActivity();
            if (activity != null) {
                activity.finish();
                if (!settings.exitOnLock()) {
                    startActivity(new Intent(requireContext(), MainActivity.class));
                }
            }
            return true;
        } else if (id == R.id.order_by_newest_first) {
            orderBy(ORDER_BY_NEWEST);
            return true;
        } else if (id == R.id.order_by_oldest_first) {
            orderBy(ORDER_BY_OLDEST);
            return true;
        } else if (id == R.id.order_by_largest_first) {
            orderBy(ORDER_BY_LARGEST);
            return true;
        } else if (id == R.id.order_by_smallest_first) {
            orderBy(ORDER_BY_SMALLEST);
            return true;
        } else if (id == R.id.order_by_random) {
            orderBy(ORDER_BY_RANDOM);
            return true;
        } else if (id == R.id.filter_all) {
            filterBy(FILTER_ALL);
            return true;
        } else if (id == R.id.filter_images) {
            filterBy(FILTER_IMAGES);
            return true;
        } else if (id == R.id.filter_gifs) {
            filterBy(FILTER_GIFS);
            return true;
        } else if (id == R.id.filter_videos) {
            filterBy(FILTER_VIDEOS);
            return true;
        } else if (id == R.id.filter_text) {
            filterBy(FILTER_TEXTS);
            return true;
        } else if (id == R.id.toggle_filename) {
            settings.setShowFilenames(galleryGridAdapter.toggleFilenames());
            return true;
        } else if (id == R.id.select_all) {
            galleryGridAdapter.selectAll();
            return true;
        } else if (id == R.id.export_selected) {
            exportViewModel.getFilesToExport().clear();
            exportViewModel.getFilesToExport().addAll(galleryGridAdapter.getSelectedFiles());
            exportViewModel.setCurrentDocumentDirectory(galleryViewModel.getCurrentDocumentDirectory());

            BottomSheetExportFragment bottomSheetDeleteFragment = new BottomSheetExportFragment();
            FragmentManager childFragmentManager = getChildFragmentManager();
            bottomSheetDeleteFragment.show(childFragmentManager, null);
            return true;
        } else if (id == R.id.copy_selected) {
            copyViewModel.getFiles().clear();
            copyViewModel.getFiles().addAll(galleryGridAdapter.getSelectedFiles());
            copyViewModel.setCurrentDirectoryUri(galleryViewModel.getCurrentDirectoryUri());

            BottomSheetCopyFragment bottomSheetCopyFragment = new BottomSheetCopyFragment();
            FragmentManager childFragmentManager = getChildFragmentManager();
            bottomSheetCopyFragment.show(childFragmentManager, null);
            return true;
        } else if (id == R.id.move_selected) {
            moveViewModel.getFiles().clear();
            moveViewModel.getFiles().addAll(galleryGridAdapter.getSelectedFiles());
            moveViewModel.setCurrentDirectoryUri(galleryViewModel.getCurrentDirectoryUri());

            BottomSheetMoveFragment bottomSheetMoveFragment = new BottomSheetMoveFragment();
            FragmentManager childFragmentManager = getChildFragmentManager();
            bottomSheetMoveFragment.show(childFragmentManager, null);
            return true;
        } else if (id == R.id.about) {
            Dialogs.showAboutDialog(requireContext());
            return true;
        }

        return false;
    }

    @Override
    public void onStart() {
        super.onStart();
        requireActivity().addMenuProvider(this, getViewLifecycleOwner());
    }

    @Override
    public void onStop() {
        if (galleryPagerAdapter != null) {
            galleryPagerAdapter.pausePlayers();
        }
        requireActivity().removeMenuProvider(this);
        if (galleryViewModel != null && !galleryViewModel.isViewpagerVisible()) {
            StaggeredGridLayoutManager layoutManager = (StaggeredGridLayoutManager) binding.recyclerView.getLayoutManager();
            if (layoutManager != null) {
                int[] positions = layoutManager.findFirstVisibleItemPositions(null);
                int min = -1;
                for (int position : positions) {
                    if (min == -1 || position < min) {
                        min = position;
                    }
                }
                if (min >= 0) {
                    galleryViewModel.setCurrentPosition(min);
                }
            }
        }
        super.onStop();
    }

    @Override
    public void onDestroy() {
        if (galleryPagerAdapter != null) {
            galleryPagerAdapter.releasePlayers();
        }
        super.onDestroy();
    }

}
