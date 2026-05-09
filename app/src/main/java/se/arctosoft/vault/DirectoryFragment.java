package se.arctosoft.vault;

import android.animation.Animator;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.util.Log;
import android.view.HapticFeedbackConstants;
import android.view.ScaleGestureDetector;
import android.view.View;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.documentfile.provider.DocumentFile;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.snackbar.Snackbar;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;

import se.arctosoft.vault.data.GalleryFile;
import se.arctosoft.vault.data.Password;
import se.arctosoft.vault.interfaces.IOnDirectoryAdded;
import se.arctosoft.vault.utils.Dialogs;
import se.arctosoft.vault.utils.FileStuff;
import se.arctosoft.vault.utils.Settings;
import se.arctosoft.vault.utils.Toaster;
import se.arctosoft.vault.viewmodel.ShareViewModel;

public class DirectoryFragment extends DirectoryBaseFragment {
    private static final String TAG = "DirectoryFragment";

    public static final String ARGUMENT_DIRECTORY = "directory";
    public static final String ARGUMENT_NESTED_PATH = "nestedPath";

    private Snackbar snackBarBackPressed;
    private ShareViewModel shareViewModel;
    private BottomNavigationView bottomNavigationView;

    // --- NEW: Variables for the Breathing Grid ---
    private ScaleGestureDetector scaleGestureDetector;
    private float scaleFactor = 1.0f;
    private static final int MIN_COLUMNS = 2;
    private static final int MAX_COLUMNS = 6;

    private final ActivityResultLauncher<Uri> resultLauncherAddFolder = registerForActivityResult(new ActivityResultContracts.OpenDocumentTree(), uri -> {
        if (uri != null) {
            addFolder(uri, true);
        }
    });

    private final ActivityResultLauncher<String[]> resultLauncherOpenDocuments = registerForActivityResult(new ActivityResultContracts.OpenMultipleDocuments(), uris -> {
        if (uris != null && !uris.isEmpty()) {
            Log.e(TAG, "onActivityResult: " + uris.size());
            Context context = getContext();
            if (context == null || !isSafe()) {
                return;
            }
            List<DocumentFile> documents = FileStuff.getDocumentsFromDirectoryResult(context, uris);
            if (!documents.isEmpty() && importViewModel != null) {
                importViewModel.getFilesToImport().clear();
                importViewModel.getTextToImport().clear();
                importViewModel.getFilesToImport().addAll(documents);
                importViewModel.setCurrentDirectoryUri(galleryViewModel.getCurrentDirectoryUri());
                importViewModel.setCurrentDocumentDirectory(galleryViewModel.getCurrentDocumentDirectory());

                BottomSheetImportFragment bottomSheetImportFragment = new BottomSheetImportFragment();
                FragmentManager childFragmentManager = getChildFragmentManager();
                bottomSheetImportFragment.show(childFragmentManager, null);
            }
        }
    });

    public void init() {
        Context context = requireContext();
        settings = Settings.getInstance(context);

        OnBackPressedCallback callback = new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (galleryViewModel.isViewpagerVisible()) {
                    showViewpager(false, galleryViewModel.getCurrentPosition(), false);
                } else if (galleryViewModel.isInSelectionMode()) {
                    galleryGridAdapter.onSelectionModeChanged(false);
                } else if (galleryViewModel.isRootDir() && (snackBarBackPressed == null || !snackBarBackPressed.isShownOrQueued())) {
                    snackBarBackPressed = Snackbar.make(binding.fab, getString(R.string.main_press_back_again_to_exit), 2000);
                    snackBarBackPressed.setAnchorView(binding.fab);
                    snackBarBackPressed.show();
                } else if (!navController.popBackStack()) {
                    FragmentActivity activity = requireActivity();
                    Password.lock(activity, galleryViewModel.isEmptyRootDir());
                    activity.finish();
                    if (!settings.exitOnLock()) {
                        startActivity(new Intent(context, MainActivity.class));
                    }
                }
            }
        };
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), callback);

        Bundle arguments = getArguments();
        if (arguments != null) {
            galleryViewModel.setNestedPath(arguments.getString(ARGUMENT_NESTED_PATH, ""));
            galleryViewModel.setDirectory(arguments.getString(ARGUMENT_DIRECTORY), context);
        }
        galleryViewModel.setAllFolder(false);
        Log.e(TAG, "init: directory: " + galleryViewModel.getDirectory());
        Log.e(TAG, "init: nested path: " + galleryViewModel.getNestedPath());
        if (galleryViewModel.getCurrentDirectoryUri() != null) {
            galleryViewModel.setRootDir(false);
            if (!initActionBar(false)) { // getSupportActionBar() is null directly after orientation change
                binding.recyclerView.post(() -> initActionBar(false));
            }
        } else {
            galleryViewModel.setRootDir(true);
        }

        galleryViewModel.setOnAdapterItemChanged(pos -> {
            galleryPagerAdapter.notifyItemChanged(pos);
            galleryGridAdapter.notifyItemChanged(pos);
        });

        // Initialize Bottom Navigation
        bottomNavigationView = binding.getRoot().findViewById(R.id.bottom_navigation);

        if (galleryViewModel.isRootDir()) {
            setupViewpager();
            setupGrid();
            setClickListeners();
            setupBottomNavigation();

            if (!galleryViewModel.isInitialised()) {
                addRootFolders();
            }
        } else {
            // Hide bottom navigation if we are inside a specific folder
            if (bottomNavigationView != null) {
                bottomNavigationView.setVisibility(View.GONE);
            }

            DocumentFile documentFile = DocumentFile.fromSingleUri(context, galleryViewModel.getCurrentDirectoryUri());
            if (documentFile != null && documentFile.isDirectory() && documentFile.exists()) {
                setupViewpager();
                setupGrid();
                setClickListeners();

                if (!galleryViewModel.isInitialised()) {
                    findFilesIn(galleryViewModel.getCurrentDirectoryUri());
                }
            } else {
                Toaster.getInstance(context).showLong(getString(R.string.directory_does_not_exist));
                navController.popBackStack();
                return;
            }
        }

        // --- NEW: THE "BREATHING" GRID (PINCH TO ZOOM COLUMNS) ---
        scaleGestureDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(@NonNull ScaleGestureDetector detector) {
                scaleFactor *= detector.getScaleFactor();

                if (binding.recyclerView.getLayoutManager() instanceof GridLayoutManager) {
                    GridLayoutManager layoutManager = (GridLayoutManager) binding.recyclerView.getLayoutManager();
                    int currentSpans = layoutManager.getSpanCount();

                    // Pinching Out (Zooming In) -> Fewer Columns
                    if (scaleFactor > 1.25f && currentSpans > MIN_COLUMNS) {
                        layoutManager.setSpanCount(currentSpans - 1);
                        scaleFactor = 1.0f; // Reset threshold
                        binding.recyclerView.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                        galleryGridAdapter.notifyItemRangeChanged(0, galleryGridAdapter.getItemCount());
                        return true;
                    }
                    // Pinching In (Zooming Out) -> More Columns
                    else if (scaleFactor < 0.75f && currentSpans < MAX_COLUMNS) {
                        layoutManager.setSpanCount(currentSpans + 1);
                        scaleFactor = 1.0f; // Reset threshold
                        binding.recyclerView.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                        galleryGridAdapter.notifyItemRangeChanged(0, galleryGridAdapter.getItemCount());
                        return true;
                    }
                }
                return false;
            }
        });

        // Intercept touches on the RecyclerView to feed the detector
        binding.recyclerView.setOnTouchListener((v, event) -> {
            scaleGestureDetector.onTouchEvent(event);
            return false; // Return false so normal scrolling still works perfectly
        });
        // -----------------------------

        initViewModels();
        shareViewModel = new ViewModelProvider(requireActivity()).get(ShareViewModel.class);
        shareViewModel.getHasData().observe(getViewLifecycleOwner(), aBoolean -> {
            if (aBoolean) {
                checkSharedData();
            }
        });
    }

    private void setupBottomNavigation() {
        if (bottomNavigationView == null) return;

        bottomNavigationView.setVisibility(View.VISIBLE);
        // Ensure "Albums" is selected when on this root screen
        bottomNavigationView.setSelectedItemId(R.id.nav_albums);

        bottomNavigationView.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_all_files) {
                // Navigate to the DirectoryAllFragment when "All Files" is clicked
                navController.navigate(R.id.action_directory_to_directoryAll);
                return true;
            } else if (id == R.id.nav_albums) {
                // Do nothing, we are already on the albums tab
                return true;
            }
            return false;
        });
    }

    @Override
    void showViewpager(boolean show, int pos, boolean animate) {
        if (binding.layoutFabsAdd != null) binding.layoutFabsAdd.setVisibility(show ? View.GONE : View.VISIBLE);
        View bottomNav = binding.getRoot().findViewById(R.id.bottom_navigation);
        if (bottomNav != null) bottomNav.setVisibility(show ? View.GONE : View.VISIBLE);

        if (show) {
            binding.viewPager.setCurrentItem(pos, false);
            binding.viewPager.setAlpha(0f);
            binding.viewPager.setScaleX(0.95f);
            binding.viewPager.setScaleY(0.95f);
            binding.viewPager.setVisibility(View.VISIBLE);
            galleryPagerAdapter.triggerActiveVideo(pos);

            binding.viewPager.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(250)
                    .setInterpolator(new androidx.interpolator.view.animation.FastOutSlowInInterpolator())
                    .start();
        } else {
            binding.viewPager.animate()
                    .alpha(0f)
                    .scaleX(0.95f)
                    .scaleY(0.95f)
                    .setDuration(200)
                    .setInterpolator(new androidx.interpolator.view.animation.FastOutSlowInInterpolator())
                    .withEndAction(() -> {
                        binding.viewPager.setVisibility(View.GONE);
                        binding.viewPager.setScaleX(1f);
                        binding.viewPager.setScaleY(1f);
                        binding.viewPager.setAlpha(1f);
                    })
                    .start();
        }

        super.showViewpager(show, pos, false);
    }

    private void checkSharedData() {
        if (!shareViewModel.getFilesReceived().isEmpty() && importViewModel != null) {
            importViewModel.getFilesToImport().clear();
            importViewModel.getTextToImport().clear();
            importViewModel.getFilesToImport().addAll(shareViewModel.getFilesReceived());
            importViewModel.setCurrentDirectoryUri(galleryViewModel.getCurrentDirectoryUri());
            importViewModel.setCurrentDocumentDirectory(galleryViewModel.getCurrentDocumentDirectory());
            importViewModel.setFromShare(true);
            shareViewModel.clear();

            BottomSheetImportFragment bottomSheetImportFragment = new BottomSheetImportFragment();
            FragmentManager childFragmentManager = getChildFragmentManager();
            bottomSheetImportFragment.show(childFragmentManager, null);
        }
    }

    boolean expandedFabs = false;

    private void setClickListeners() {
        View[] views = new View[]{galleryViewModel.isRootDir() ? binding.fabAddFolder : binding.fabCreateFolder, binding.fabImportMedia, binding.fabAddText};
        binding.fab.setOnClickListener(v -> {
            if (expandedFabs) {
                binding.fab.animate().rotation(0).setDuration(120).start();
                for (View view : views) {
                    view.setAlpha(0f);
                    view.setVisibility(View.GONE);
                }
                expandedFabs = false;
            } else {
                binding.fab.animate().rotation(-90).setDuration(120).start();
                for (int i = 0, viewsLength = views.length; i < viewsLength; i++) {
                    View view = views[i];
                    view.animate().alpha(1f).setDuration(120).setStartDelay(i * 20).setListener(getShowOnStartListener(view)).start();
                }
                expandedFabs = true;
            }
        });
        binding.fabAddFolder.setOnClickListener(v -> {
            resultLauncherAddFolder.launch(null);
            binding.fab.performClick();
        });
        FragmentActivity context = requireActivity();
        binding.fabCreateFolder.setOnClickListener(v -> {
            Dialogs.showCreateFolderDialog(context, text -> {
                if (text != null && !text.isBlank()) {
                    try {
                        Uri newFolderUri = DocumentsContract.createDocument(
                                context.getContentResolver(),
                                galleryViewModel.getCurrentDirectoryUri(),
                                DocumentsContract.Document.MIME_TYPE_DIR,
                                text
                        );
                        synchronized (LOCK) {
                            galleryViewModel.getGalleryFiles().add(0, GalleryFile.asDirectory(newFolderUri));
                            galleryGridAdapter.notifyItemInserted(0);
                            galleryPagerAdapter.notifyItemInserted(0);
                            binding.recyclerView.scrollToPosition(0);
                        }
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    }
                }
            });
            binding.fab.performClick();
        });
        binding.fabRemoveFolders.setOnClickListener(v -> {
            if (galleryViewModel.isRootDir()) {
                onRemoveFolderClicked(context);
            } else {
                deleteViewModel.getFilesToDelete().clear();
                deleteViewModel.getFilesToDelete().addAll(galleryGridAdapter.getSelectedFiles());

                BottomSheetDeleteFragment bottomSheetDeleteFragment = new BottomSheetDeleteFragment();
                FragmentManager childFragmentManager = getChildFragmentManager();
                bottomSheetDeleteFragment.show(childFragmentManager, null);
            }
        });
        binding.fabImportMedia.setOnClickListener(v -> {
            String[] mimeTypes = new String[]{"image/*", "video/*", "audio/*"};
            resultLauncherOpenDocuments.launch(mimeTypes);
            binding.fab.performClick();
        });
        binding.fabAddText.setOnClickListener(v -> {
            Dialogs.showImportTextDialog(context, null, false, text -> {
                GalleryFile tempText = GalleryFile.asTempText(text);
                importViewModel.getFilesToImport().clear();
                importViewModel.getTextToImport().clear();
                importViewModel.getTextToImport().add(tempText);
                importViewModel.setCurrentDirectoryUri(galleryViewModel.getCurrentDirectoryUri());
                importViewModel.setCurrentDocumentDirectory(galleryViewModel.getCurrentDocumentDirectory());

                BottomSheetImportFragment bottomSheetImportFragment = new BottomSheetImportFragment();
                FragmentManager childFragmentManager = getChildFragmentManager();
                bottomSheetImportFragment.show(childFragmentManager, null);
            });
            binding.fab.performClick();
        });
        binding.fabsContainer.setOnClickListener(v -> {
            if (expandedFabs) {
                binding.fab.performClick();
            }
        });
    }

    private void onRemoveFolderClicked(Context context) {
        Dialogs.showConfirmationDialog(context, getString(R.string.dialog_remove_folder_title),
                getResources().getQuantityString(R.plurals.dialog_remove_folder_message, galleryGridAdapter.getSelectedFiles().size()),
                (dialog, which) -> {
                    for (GalleryFile f : galleryGridAdapter.getSelectedFiles()) {
                        FragmentActivity activity = requireActivity();
                        settings.removeGalleryDirectory(f.getUri());
                        Log.e(TAG, "onRemoveFolderClicked: remove " + f.getUri());
                        try {
                            activity.getContentResolver().releasePersistableUriPermission(f.getUri(), Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                        } catch (SecurityException e) {
                            e.printStackTrace();
                        }
                        try {
                            activity.getContentResolver().releasePersistableUriPermission(Uri.parse(f.getUri().toString().split("/document/")[0]), Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                        } catch (SecurityException e) {
                            e.printStackTrace();
                        }
                        int i = galleryViewModel.getGalleryFiles().indexOf(f);
                        if (i >= 0) {
                            galleryViewModel.getGalleryFiles().remove(i);
                            galleryGridAdapter.notifyItemRemoved(i);
                        }
                    }
                    galleryGridAdapter.onSelectionModeChanged(false);
                });
    }

    private Animator.AnimatorListener getShowOnStartListener(View view) {
        return new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(@NonNull Animator animation) {
                view.setVisibility(View.VISIBLE);
            }

            @Override
            public void onAnimationEnd(@NonNull Animator animation) {

            }

            @Override
            public void onAnimationCancel(@NonNull Animator animation) {

            }

            @Override
            public void onAnimationRepeat(@NonNull Animator animation) {

            }
        };
    }

    @Override
    void onSelectionModeChanged(boolean inSelectionMode) {
        galleryViewModel.setInSelectionMode(inSelectionMode);
        if (inSelectionMode) {
            binding.layoutFabsAdd.setVisibility(View.GONE);
            binding.layoutFabsRemoveFolders.setVisibility(View.VISIBLE);
        } else {
            binding.layoutFabsAdd.setVisibility(View.VISIBLE);
            binding.layoutFabsRemoveFolders.setVisibility(View.GONE);
        }

        // Hide bottom navigation during selection mode to prevent weird UX
        if (bottomNavigationView != null && galleryViewModel.isRootDir()) {
            bottomNavigationView.setVisibility(inSelectionMode ? View.GONE : View.VISIBLE);
        }

        requireActivity().invalidateOptionsMenu();
    }

    private void addFolder(Uri uri, boolean asRootDir) {
        Context context = getContext();
        if (context == null) {
            return;
        }
        DocumentFile documentFile = DocumentFile.fromTreeUri(context, uri);
        context.getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        settings.addGalleryDirectory(documentFile.getUri(), asRootDir, new IOnDirectoryAdded() {
            @Override
            public void onAddedAsRoot() {
                Toaster.getInstance(context).showLong(getString(R.string.gallery_added_folder, FileStuff.getFilenameWithPathFromUri(uri)));
                Uri directoryUri = documentFile.getUri();

                synchronized (LOCK) {
                    galleryViewModel.getGalleryFiles().add(0, GalleryFile.asDirectory(directoryUri));
                    galleryGridAdapter.notifyItemInserted(0);
                }
            }

            @Override
            public void onAdded() {
                Toaster.getInstance(context).showLong(getString(R.string.gallery_added_folder, FileStuff.getFilenameWithPathFromUri(uri)));
            }

            @Override
            public void onAlreadyExists() {
                Toaster.getInstance(context).showLong(getString(R.string.gallery_added_folder, FileStuff.getFilenameWithPathFromUri(uri)));
                if (asRootDir) {
                    addRootFolders();
                }
            }
        });
    }

    @Override
    void addRootFolders() {
        setLoading(true);
        synchronized (LOCK) {
            int size = galleryViewModel.getGalleryFiles().size();
            galleryViewModel.getGalleryFiles().clear();
            galleryGridAdapter.notifyItemRangeRemoved(0, size);
        }
        new Thread(() -> {
            FragmentActivity activity = requireActivity();
            List<Uri> directories = settings.getGalleryDirectoriesAsUri(true);

            List<Uri> uriFiles = new ArrayList<>(directories.size());
            for (Uri uri : directories) {
                DocumentFile documentFile = DocumentFile.fromTreeUri(activity, uri);
                if (documentFile.canRead()) {
                    uriFiles.add(documentFile.getUri());
                } else {
                    activity.runOnUiThread(() -> Toaster.getInstance(activity).showLong(getString(R.string.gallery_find_files_no_permission, uri.getLastPathSegment())));
                    settings.removeGalleryDirectory(uri);
                }
            }
            addFoundRootDirectories(uriFiles, activity);
        }).start();
    }

    private void addFoundRootDirectories(@NonNull List<Uri> directories, FragmentActivity activity) {
        for (int i = 0; i < directories.size(); i++) {
            Uri uri = directories.get(i);
            GalleryFile galleryFile = GalleryFile.asDirectory(uri);
            activity.runOnUiThread(() -> {
                synchronized (LOCK) {
                    galleryViewModel.getGalleryFiles().add(galleryFile);
                    galleryGridAdapter.notifyItemInserted(galleryViewModel.getGalleryFiles().size() - 1);
                }
            });
        }
        activity.runOnUiThread(() -> {
            binding.noMedia.setVisibility(directories.isEmpty() ? View.VISIBLE : View.GONE);
            setLoading(false);
        });
        galleryViewModel.setInitialised(true);
    }

    @Override
    public void onStart() {
        super.onStart();
        checkSharedData();
    }
}