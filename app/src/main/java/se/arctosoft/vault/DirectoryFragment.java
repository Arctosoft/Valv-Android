package se.arctosoft.vault;

import android.animation.Animator;
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

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import se.arctosoft.vault.adapters.GalleryGridAdapter;
import se.arctosoft.vault.adapters.GalleryPagerAdapter;
import se.arctosoft.vault.data.GalleryFile;
import se.arctosoft.vault.data.Password;
import se.arctosoft.vault.databinding.FragmentDirectoryBinding;
import se.arctosoft.vault.interfaces.IOnDirectoryAdded;
import se.arctosoft.vault.utils.Dialogs;
import se.arctosoft.vault.utils.FileStuff;
import se.arctosoft.vault.utils.Settings;
import se.arctosoft.vault.utils.Toaster;
import se.arctosoft.vault.viewmodel.GalleryViewModel;
import se.arctosoft.vault.viewmodel.ImportViewModel;
import se.arctosoft.vault.viewmodel.PasswordViewModel;

public class DirectoryFragment extends Fragment implements MenuProvider {
    private static final String TAG = "DirectoryFragment";

    private static final Object LOCK = new Object();
    private static final int MIN_FILES_FOR_FAST_SCROLL = 60;
    public static final String ARGUMENT_DIRECTORY = "directory";
    public static final String ARGUMENT_NESTED_PATH = "nestedPath";
    public static final String ARGUMENT_IS_ALL = "all";

    private NavController navController;
    private FragmentDirectoryBinding binding;
    private PasswordViewModel passwordViewModel;
    private GalleryViewModel galleryViewModel;
    private ImportViewModel importViewModel;

    private GalleryGridAdapter galleryGridAdapter;
    private GalleryPagerAdapter galleryPagerAdapter;
    private Settings settings;

    private int foundFiles = 0, foundFolders = 0;
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
                showImportModal();
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

        requireActivity().addMenuProvider(this, getViewLifecycleOwner());

        passwordViewModel = new ViewModelProvider(requireActivity()).get(PasswordViewModel.class);
        galleryViewModel = new ViewModelProvider(this).get(GalleryViewModel.class);
        importViewModel = new ViewModelProvider(this).get(ImportViewModel.class);
        //binding.buttonFirst.setOnClickListener(v -> NavHostFragment.findNavController(DirectoryFragment.this).navigate(R.id.action_FirstFragment_to_SecondFragment));
        navController = NavHostFragment.findNavController(this);
        Log.e(TAG, "onViewCreated: locked? " + passwordViewModel.isLocked());
        if (passwordViewModel.isLocked()) {
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
            galleryViewModel.setDirectory(arguments.getString(ARGUMENT_DIRECTORY), context);
            galleryViewModel.setNestedPath(arguments.getString(ARGUMENT_NESTED_PATH, ""));
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
        ActionBar ab = ((AppCompatActivity) requireActivity()).getSupportActionBar();

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
                onAddFolderClicked(context);
            } else {
                onDeleteSelected(context);
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

    private void onDeleteSelected(FragmentActivity activity) {
        Dialogs.showConfirmationDialog(activity, getString(R.string.dialog_delete_files_title),
                getResources().getQuantityString(R.plurals.dialog_delete_files_message, galleryGridAdapter.getSelectedFiles().size()),
                (dialog, which) -> {
                    //deleteSelectedFiles(activity);
                });
    }

    /*private void deleteSelectedFiles(FragmentActivity activity) {
        setLoading(true);
        new Thread(() -> {
            synchronized (LOCK) {
                List<GalleryFile> selectedFiles = galleryGridAdapter.getSelectedFiles();
                ConcurrentLinkedQueue<GalleryFile> queue = new ConcurrentLinkedQueue<>(selectedFiles);
                List<Integer> positionsDeleted = Collections.synchronizedList(new ArrayList<>());
                AtomicInteger deletedCount = new AtomicInteger(0);
                AtomicInteger failedCount = new AtomicInteger(0);
                final int total = selectedFiles.size();
                final int threadCount;
                if (total < 4) {
                    threadCount = 1;
                } else if (total < 20) {
                    threadCount = 4;
                } else {
                    threadCount = 8;
                }
                //activity.runOnUiThread(() -> setLoadingWithProgress(0, 0, 0, R.string.gallery_deleting_progress));
                // TODO use viewmodel livedata instead and listen to it
                List<Thread> threads = new ArrayList<>(threadCount);
                for (int i = 0; i < threadCount; i++) {
                    Thread t = new Thread(() -> {
                        GalleryFile f;
                        while (!isCancelled && (f = queue.poll()) != null) {
                            deleteFile(total, f, positionsDeleted, deletedCount, failedCount, activity);
                        }
                    });
                    threads.add(t);
                    t.start();
                }

                for (Thread thread : threads) {
                    try {
                        thread.join();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                if (isCancelled) {
                    isCancelled = false;
                }
                Collections.sort(positionsDeleted);
                activity.runOnUiThread(() -> {
                    while (!positionsDeleted.isEmpty()) {
                        int pos = positionsDeleted.remove(positionsDeleted.size() - 1);
                        galleryViewModel.getGalleryFiles().remove(pos);
                        galleryGridAdapter.notifyItemRemoved(pos);
                        galleryPagerAdapter.notifyItemRemoved(pos);
                    }
                    galleryGridAdapter.onSelectionModeChanged(false);
                    setLoading(false);
                });
            }
        }).start();
    }

    private void deleteFile(int total, GalleryFile file, List<Integer> positionsDeleted, AtomicInteger deletedCount, AtomicInteger failedCount, FragmentActivity context) {
        if (file == null || isCancelled) {
            return;
        }
        boolean deleted = FileStuff.deleteFile(context, file.getUri());
        FileStuff.deleteFile(context, file.getThumbUri());
        FileStuff.deleteFile(context, file.getNoteUri());
        if (deleted) {
            deletedCount.addAndGet(1);
            int i = galleryViewModel.getGalleryFiles().indexOf(file);
            if (i >= 0) {
                positionsDeleted.add(i);
            }
        } else {
            failedCount.addAndGet(1);
        }
        //context.runOnUiThread(() -> setLoadingWithProgress(deletedCount.get() + failedCount.get(), failedCount.get(), total, R.string.gallery_deleting_progress));
    }*/

    private void onAddFolderClicked(Context context) {
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

    private void showImportModal() {
        BottomSheetFragment bottomSheetFragment = new BottomSheetFragment();
        FragmentManager childFragmentManager = getChildFragmentManager();
        bottomSheetFragment.show(childFragmentManager, null);
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
                /*new Thread(() -> {
                    List<GalleryFile> galleryFiles = FileStuff.getFilesInFolder(activity, uri);
                    galleryFile.setFilesInDirectory(galleryFiles);
                    activity.runOnUiThread(() -> galleryGridAdapter.notifyItemChanged(galleryViewModel.getGalleryFiles().indexOf(galleryFile)));
                }).start();*/
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

    @Override
    public void onStop() {
        if (galleryPagerAdapter != null) {
            galleryPagerAdapter.pausePlayers();
        }
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
            menuInflater.inflate(R.menu.menu_main, menu);
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
        }

        return false;
    }

}