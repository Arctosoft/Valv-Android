package se.arctosoft.vault;

import android.animation.Animator;
import android.annotation.SuppressLint;
import android.content.Context;
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

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.MenuProvider;
import androidx.documentfile.provider.DocumentFile;
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

import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import se.arctosoft.vault.adapters.GalleryGridAdapter;
import se.arctosoft.vault.adapters.GalleryPagerAdapter;
import se.arctosoft.vault.data.FileType;
import se.arctosoft.vault.data.GalleryFile;
import se.arctosoft.vault.data.Password;
import se.arctosoft.vault.databinding.FragmentDirectoryBinding;
import se.arctosoft.vault.interfaces.IOnDirectoryAdded;
import se.arctosoft.vault.utils.Dialogs;
import se.arctosoft.vault.utils.FileStuff;
import se.arctosoft.vault.utils.Settings;
import se.arctosoft.vault.utils.Toaster;
import se.arctosoft.vault.viewmodel.CopyViewModel;
import se.arctosoft.vault.viewmodel.DeleteViewModel;
import se.arctosoft.vault.viewmodel.ExportViewModel;
import se.arctosoft.vault.viewmodel.GalleryViewModel;
import se.arctosoft.vault.viewmodel.ImportViewModel;
import se.arctosoft.vault.viewmodel.PasswordViewModel;

public class DirectoryFragment extends Fragment implements MenuProvider {
    private static final String TAG = "DirectoryFragment";

    private static final Object LOCK = new Object();
    private static final int MIN_FILES_FOR_FAST_SCROLL = 60;
    private static final int ORDER_BY_NEWEST = 0;
    private static final int ORDER_BY_OLDEST = 1;
    private static final int ORDER_BY_LARGEST = 2;
    private static final int ORDER_BY_SMALLEST = 3;
    private static final int ORDER_BY_RANDOM = 4;
    private static final int FILTER_ALL = 0;
    private static final int FILTER_IMAGES = FileType.IMAGE_V2.type;
    private static final int FILTER_GIFS = FileType.GIF_V2.type;
    private static final int FILTER_VIDEOS = FileType.VIDEO_V2.type;
    private static final int FILTER_TEXTS = FileType.TEXT_V2.type;

    public static final String ARGUMENT_DIRECTORY = "directory";
    public static final String ARGUMENT_NESTED_PATH = "nestedPath";
    public static final String ARGUMENT_IS_ALL = "all";

    private NavController navController;
    private FragmentDirectoryBinding binding;
    private PasswordViewModel passwordViewModel;
    private GalleryViewModel galleryViewModel;
    private ImportViewModel importViewModel;
    private DeleteViewModel deleteViewModel;
    private ExportViewModel exportViewModel;
    private CopyViewModel copyViewModel;

    private GalleryGridAdapter galleryGridAdapter;
    private GalleryPagerAdapter galleryPagerAdapter;
    private Settings settings;
    private Snackbar snackBarBackPressed;

    private int foundFiles = 0, foundFolders = 0, orderBy = ORDER_BY_NEWEST;
    private boolean isCancelled = false;

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
                importViewModel.getFilesToImport().addAll(documents);
                importViewModel.setCurrentDirectoryUri(galleryViewModel.getCurrentDirectoryUri());

                BottomSheetImportFragment bottomSheetImportFragment = new BottomSheetImportFragment();
                FragmentManager childFragmentManager = getChildFragmentManager();
                bottomSheetImportFragment.show(childFragmentManager, null);
            }
        }
    });

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
                //int startDestination = navController.getGraph().getStartDestinationId();
                //NavOptions navOptions = new NavOptions.Builder().setPopUpTo(startDestination, true).build();
                //navController.navigate(startDestination, null, navOptions);
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

    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        passwordViewModel = new ViewModelProvider(requireActivity()).get(PasswordViewModel.class);
        galleryViewModel = new ViewModelProvider(this).get(GalleryViewModel.class);
        importViewModel = new ViewModelProvider(this).get(ImportViewModel.class);
        deleteViewModel = new ViewModelProvider(this).get(DeleteViewModel.class);
        exportViewModel = new ViewModelProvider(this).get(ExportViewModel.class);
        copyViewModel = new ViewModelProvider(this).get(CopyViewModel.class);
        //binding.buttonFirst.setOnClickListener(v -> NavHostFragment.findNavController(DirectoryFragment.this).navigate(R.id.action_FirstFragment_to_SecondFragment));
        navController = NavHostFragment.findNavController(this);
        Log.e(TAG, "onViewCreated: locked? " + passwordViewModel.isLocked());
        if (passwordViewModel.isLocked()) {
            Password.lock(requireActivity());
            navController.navigate(R.id.password);
        } else {
            init();
        }
    }

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
        Log.e(TAG, "init: handleOnBackPressed add? " + requireActivity().getOnBackPressedDispatcher().hasEnabledCallbacks());

        Bundle arguments = getArguments();
        if (arguments != null) {
            galleryViewModel.setNestedPath(arguments.getString(ARGUMENT_NESTED_PATH, ""));
            galleryViewModel.setDirectory(arguments.getString(ARGUMENT_DIRECTORY), context);
            galleryViewModel.setAllFolder(arguments.getBoolean(ARGUMENT_IS_ALL, false));
        }
        Log.e(TAG, "init: directory: " + galleryViewModel.getDirectory());
        Log.e(TAG, "init: nested path: " + galleryViewModel.getNestedPath());
        boolean isAllFolder = galleryViewModel.isAllFolder();
        if (galleryViewModel.getCurrentDirectoryUri() != null) {
            galleryViewModel.setRootDir(false);
            ActionBar ab = ((AppCompatActivity) requireActivity()).getSupportActionBar();

            if (ab != null) {
                ab.setDisplayHomeAsUpEnabled(true);
                ab.setTitle(isAllFolder ? getString(R.string.gallery_all) : FileStuff.getFilenameFromUri(galleryViewModel.getCurrentDirectoryUri(), false));
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

            Log.e(TAG, "init: " + galleryViewModel.isInitialised());
            if (!galleryViewModel.isInitialised()) {
                addRootFolders();
            }
        } else {
            DocumentFile documentFile = DocumentFile.fromSingleUri(context, galleryViewModel.getCurrentDirectoryUri());
            if (isAllFolder || (documentFile != null && documentFile.isDirectory() && documentFile.exists())) {
                Log.e(TAG, "init: find");
                setupViewpager();
                setupGrid();
                setClickListeners();

                if (!galleryViewModel.isInitialised()) {
                    Log.e(TAG, "init: not initialised");
                    if (isAllFolder) {
                        findAllFiles();
                    } else {
                        findFilesIn(galleryViewModel.getCurrentDirectoryUri());
                    }
                }
            } else {
                Toaster.getInstance(context).showLong(getString(R.string.directory_does_not_exist));
                navController.popBackStack();
                return;
            }
        }

        importViewModel.setOnImportDoneFragment((destinationUri, sameDirectory, importedCount, failedCount, thumbErrorCount) -> {
            Log.e(TAG, "setOnImportDoneFragment: " + destinationUri + ", " + sameDirectory + ", " + importedCount + ", " + failedCount + ", " + thumbErrorCount);

            FragmentActivity activity = getActivity();
            if (activity == null || activity.isDestroyed()) {
                return;
            }
            activity.runOnUiThread(() -> Toaster.getInstance(activity).showLong(getString(R.string.gallery_selected_files_imported, importedCount)));
            if (sameDirectory || (destinationUri != null && galleryViewModel.getCurrentDirectoryUri() != null
                    && destinationUri.toString().equals(galleryViewModel.getCurrentDirectoryUri().toString()))) { // files added to current directory
                activity.runOnUiThread(() -> {
                    synchronized (LOCK) {
                        int size = galleryViewModel.getGalleryFiles().size();
                        galleryViewModel.getGalleryFiles().clear();
                        galleryViewModel.getHiddenFiles().clear();
                        galleryGridAdapter.notifyItemRangeRemoved(0, size);
                        galleryPagerAdapter.notifyItemRangeRemoved(0, size);
                        galleryViewModel.setInitialised(false);
                        findFilesIn(galleryViewModel.getCurrentDirectoryUri());
                    }
                });
            } else {
                synchronized (LOCK) {
                    for (int i = 0; i < galleryViewModel.getGalleryFiles().size(); i++) {
                        GalleryFile g = galleryViewModel.getGalleryFiles().get(i);
                        if (g.isDirectory() && g.getUri() != null && g.getUri().equals(destinationUri)) {
                            //List<GalleryFile> galleryFiles = FileStuff.getFilesInFolder(activity, destinationUri);
                            //g.setFilesInDirectory(galleryFiles);
                            g.resetFilesInDirectory();
                            int finalI = i;
                            activity.runOnUiThread(() -> galleryGridAdapter.notifyItemChanged(finalI));
                            break;
                        }
                    }
                }
            }
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
    }

    private void setupGrid() {
        if (galleryViewModel.isInitialised()) {
            binding.recyclerView.setFastScrollEnabled(galleryViewModel.getGalleryFiles().size() > MIN_FILES_FOR_FAST_SCROLL);
        } else {
            binding.recyclerView.setFastScrollEnabled(false);
        }
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

    private void setupViewpager() {
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

    private void showViewpager(boolean show, int pos, boolean animate) {
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
            if (binding.fabImportMedia.getVisibility() == View.VISIBLE) {
                binding.fab.performClick();
            }
            binding.fab.setVisibility(View.GONE);
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
            binding.fab.setVisibility(View.VISIBLE);
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
                        try {
                            activity.getContentResolver().releasePersistableUriPermission(f.getUri(), Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
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

    private void onSelectionModeChanged(boolean inSelectionMode) {
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

    private void addRootFolders() {
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

    private void setLoading(boolean loading) {
        binding.cLLoading.cLLoading.setVisibility(loading ? View.VISIBLE : View.GONE);
        binding.cLLoading.txtProgress.setVisibility(View.GONE);
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
                synchronized (LOCK) {
                    galleryViewModel.getGalleryFiles().add(0, GalleryFile.asAllFolder(getString(R.string.gallery_all)));
                    galleryGridAdapter.notifyItemInserted(0);
                }
            }
            setLoading(false);
        });
        galleryViewModel.setInitialised(true);
    }

    private void findAllFiles() {
        Log.e(TAG, "findAllFiles: ");
        foundFiles = 0;
        foundFolders = 0;
        setLoading(true);
        new Thread(() -> {
            List<Uri> directories = settings.getGalleryDirectoriesAsUri(true);
            FragmentActivity activity = getActivity();
            if (activity == null || isCancelled) {
                return;
            }
            List<Uri> uriFiles = new ArrayList<>(directories.size());
            for (Uri uri : directories) {
                DocumentFile documentFile = DocumentFile.fromTreeUri(activity, uri);
                if (documentFile.canRead()) {
                    uriFiles.add(documentFile.getUri());
                }
            }

            activity.runOnUiThread(this::setLoadingAllWithProgress);

            List<GalleryFile> folders = new ArrayList<>();
            List<GalleryFile> files = new LinkedList<>();
            long start = System.currentTimeMillis();
            List<GalleryFile> filesToSearch = new ArrayList<>();
            for (Uri uri : uriFiles) {
                List<GalleryFile> filesInFolder = FileStuff.getFilesInFolder(activity, uri);
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

            activity.runOnUiThread(this::setLoadingAllWithProgress);

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
                    //finish();
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
                //finish();
                return;
            }

            files.sort(GalleryFile::compareTo);

            activity.runOnUiThread(() -> {
                setLoading(false);
                if (files.size() > MIN_FILES_FOR_FAST_SCROLL) {
                    binding.recyclerView.setFastScrollEnabled(true);
                }
                if (galleryViewModel.isInitialised()) {
                    return;
                }
                galleryViewModel.addGalleryFiles(files);
                galleryViewModel.setInitialised(true);
                galleryGridAdapter.notifyItemRangeInserted(0, files.size());
                galleryPagerAdapter.notifyItemRangeInserted(0, files.size());
            });
        }).start();
    }

    private void findFilesIn(Uri directoryUri) {
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
                }
            });

            /*for (int i = 0; i < galleryFiles.size(); i++) {
                GalleryFile g = galleryFiles.get(i);
                if (g.isDirectory()) {
                    int finalI = i;
                    new Thread(() -> {
                        FragmentActivity activity2 = getActivity();
                        if (activity2 == null || !isSafe()) {
                            return;
                        }
                        List<GalleryFile> found = FileStuff.getFilesInFolder(activity2, g.getUri());
                        if (found.isEmpty()) {
                            activity2.runOnUiThread(() -> {
                                synchronized (LOCK) {
                                    int i1 = galleryViewModel.getGalleryFiles().indexOf(g);
                                    if (i1 >= 0) {
                                        galleryViewModel.getGalleryFiles().remove(i1);
                                        galleryGridAdapter.notifyItemRemoved(i1);
                                        galleryPagerAdapter.notifyItemRemoved(i1);
                                    }
                                }
                            });
                        } else {
                            g.setFilesInDirectory(found);
                            activity2.runOnUiThread(() -> galleryGridAdapter.notifyItemChanged(finalI));
                        }
                    }).start();
                }
            }*/
        }).start();
    }

    private void setLoadingAllWithProgress() {
        binding.cLLoading.cLLoading.setVisibility(View.VISIBLE);
        binding.cLLoading.txtProgress.setText(getString(R.string.gallery_loading_all_progress, foundFiles, foundFolders));
        binding.cLLoading.txtProgress.setVisibility(View.VISIBLE);
    }

    private boolean isSafe() {
        return !(isRemoving() || getActivity() == null || isDetached() || !isAdded() || getView() == null);
    }

    private synchronized void incrementFiles(int amount) {
        foundFiles += amount;
    }

    private synchronized void incrementFolders(int amount) {
        foundFolders += amount;
    }

    @NonNull
    private List<GalleryFile> findAllFilesInFolder(Uri uri) {
        Log.e(TAG, "findAllFilesInFolder: find all files in " + uri.getLastPathSegment());
        List<GalleryFile> files = new ArrayList<>();
        FragmentActivity activity = getActivity();
        if (activity == null || !isSafe()) {
            return files;
        }
        incrementFolders(1);
        List<GalleryFile> filesInFolder = FileStuff.getFilesInFolder(activity, uri);
        for (GalleryFile galleryFile : filesInFolder) {
            if (isCancelled) {
                return files;
            }
            if (galleryFile.isDirectory()) {
                activity.runOnUiThread(this::setLoadingAllWithProgress);
                files.addAll(findAllFilesInFolder(galleryFile.getUri()));
            } else {
                files.add(galleryFile);
            }
        }
        incrementFiles(files.size());
        activity.runOnUiThread(this::setLoadingAllWithProgress);
        return files;
    }

    private void orderBy(int order) {
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
                        int size = galleryViewModel.getGalleryFiles().size();
                        galleryGridAdapter.notifyItemRangeChanged(0, size);
                        galleryPagerAdapter.notifyItemRangeChanged(0, size);
                    }
                });
            }
        }).start();
    }

    @SuppressLint("NotifyDataSetChanged")
    private void filterBy(int filter) {
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
            //moveSelected();
            return true;
        } else if (id == R.id.about) {
            Dialogs.showAboutDialog(requireContext());
            return true;
        }

        return false;
    }
}