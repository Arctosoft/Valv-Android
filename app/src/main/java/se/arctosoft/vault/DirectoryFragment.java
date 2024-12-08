package se.arctosoft.vault;

import android.animation.Animator;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.documentfile.provider.DocumentFile;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.snackbar.Snackbar;

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
                    Password.lock(activity);
                    activity.finish();
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

        if (galleryViewModel.isRootDir()) {
            setupViewpager();
            setupGrid();
            setClickListeners();

            if (!galleryViewModel.isInitialised()) {
                addRootFolders();
            }
        } else {
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

        initViewModels();
        shareViewModel = new ViewModelProvider(requireActivity()).get(ShareViewModel.class);
        shareViewModel.getHasData().observe(getViewLifecycleOwner(), aBoolean -> {
            Log.e(TAG, "onChanged: " + aBoolean);
            if (aBoolean) {
                checkSharedData();
            }
        });
    }

    @Override
    void showViewpager(boolean show, int pos, boolean animate) {
        binding.layoutFabsAdd.setVisibility(show ? View.GONE : View.VISIBLE);
        super.showViewpager(show, pos, animate);
    }

    private void checkSharedData() {
        Log.e(TAG, "checkSharedData: " + (shareViewModel != null ? shareViewModel.getFilesReceived().size() : ""));
        if (!shareViewModel.getFilesReceived().isEmpty() && importViewModel != null) {
            importViewModel.getFilesToImport().clear();
            importViewModel.getTextToImport().clear();
            importViewModel.getFilesToImport().addAll(shareViewModel.getFilesReceived());
            importViewModel.setCurrentDirectoryUri(galleryViewModel.getCurrentDirectoryUri());
            importViewModel.setFromShare(true);
            shareViewModel.clear();

            BottomSheetImportFragment bottomSheetImportFragment = new BottomSheetImportFragment();
            FragmentManager childFragmentManager = getChildFragmentManager();
            bottomSheetImportFragment.show(childFragmentManager, null);
        }
    }

    boolean expandedFabs = false;

    private void setClickListeners() {
        View[] views = new View[]{binding.fabAddFolder, binding.fabImportMedia, binding.fabAddText};
        boolean rootDir = galleryViewModel.isRootDir();
        binding.fab.setOnClickListener(v -> {
            if (expandedFabs) {
                binding.fab.animate().rotation(0).setDuration(120).start();
                for (View view : views) {
                    //view.animate().alpha(0f).setDuration(120).setListener(getHideOnEndListener(view)).start();
                    view.setAlpha(0f);
                    view.setVisibility(View.GONE);
                }
                expandedFabs = false;
            } else {
                binding.fab.animate().rotation(-90).setDuration(120).start();
                for (int i = 0, viewsLength = views.length; i < viewsLength; i++) {
                    View view = views[i];
                    if (rootDir || view != binding.fabAddFolder) {
                        view.animate().alpha(1f).setDuration(120).setStartDelay(i * 20).setListener(getShowOnStartListener(view)).start();
                    }
                }
                expandedFabs = true;
            }
        });
        binding.fabAddFolder.setOnClickListener(v -> {
            resultLauncherAddFolder.launch(null);
            binding.fab.performClick();
        });
        FragmentActivity context = requireActivity();
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
            String[] mimeTypes = new String[]{"image/*", "video/*"};
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
                //List<GalleryFile> galleryFiles = FileStuff.getFilesInFolder(context, directoryUri);

                if (galleryViewModel.getGalleryFiles().isEmpty()) {
                    addAllFolder();
                }
                synchronized (LOCK) {
                    galleryViewModel.getGalleryFiles().add(0, GalleryFile.asDirectory(directoryUri/*, galleryFiles*/));
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
        //if (viewModel.getFilesToAdd() != null) {
        //    importFiles(viewModel.getFilesToAdd());
        //}
        //if (viewModel.getTextToImport() != null) {
        //    importText(viewModel.getTextToImport());
        //}
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
            if (navController.getPreviousBackStackEntry() == null && !galleryViewModel.getGalleryFiles().isEmpty()) {
                addAllFolder();
            }
            binding.noMedia.setVisibility(directories.isEmpty() ? View.VISIBLE : View.GONE);
            setLoading(false);
        });
        galleryViewModel.setInitialised(true);
    }

    private void addAllFolder() {
        synchronized (LOCK) {
            galleryViewModel.getGalleryFiles().add(0, GalleryFile.asAllFolder(getString(R.string.gallery_all)));
            galleryGridAdapter.notifyItemInserted(0);
        }
        binding.noMedia.setVisibility(View.GONE);
    }

    @Override
    public void onStart() {
        super.onStart();
        checkSharedData();
    }
}