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

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.documentfile.provider.DocumentFile;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.ArrayList;
import java.util.List;

import se.arctosoft.vault.adapters.ImportListAdapter;
import se.arctosoft.vault.data.GalleryFile;
import se.arctosoft.vault.databinding.BottomSheetCopyBinding;
import se.arctosoft.vault.utils.FileStuff;
import se.arctosoft.vault.utils.Settings;
import se.arctosoft.vault.utils.StringStuff;
import se.arctosoft.vault.utils.Toaster;
import se.arctosoft.vault.viewmodel.CopyViewModel;
import se.arctosoft.vault.viewmodel.MoveViewModel;

public class BottomSheetMoveFragment extends BottomSheetDialogFragment {
    private static final String TAG = "BottomSheetMoveFragment";

    private BottomSheetCopyBinding binding;
    private MoveViewModel moveViewModel;

    private final ActivityResultLauncher<Uri> resultLauncherAddFolder = registerForActivityResult(new ActivityResultContracts.OpenDocumentTree(), uri -> {
        if (uri != null) {
            Context context = getContext();
            if (context == null) {
                return;
            }
            Settings.getInstance(context).addGalleryDirectory(uri, false, null);
            DocumentFile pickedDirectory = DocumentFile.fromTreeUri(context, uri);
            if (pickedDirectory != null) {
                context.getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                doCopy(uri, FileStuff.getFilenameWithPathFromUri(uri), pickedDirectory);
            }
        }
    });

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = BottomSheetCopyBinding.inflate(inflater, container, false);

        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        moveViewModel = new ViewModelProvider(requireParentFragment()).get(MoveViewModel.class);
        List<GalleryFile> files = moveViewModel.getFiles();
        if (files.isEmpty()) {
            dismiss();
            return;
        }
        long bytes = 0;
        for (GalleryFile documentFile : files) {
            bytes += documentFile.getSize();
        }
        moveViewModel.setTotalBytes(bytes);

        Context context = requireContext();
        Settings settings = Settings.getInstance(context);
        List<Uri> directories = settings.getGalleryDirectoriesAsUri(false);
        List<String> names = new ArrayList<>(directories.size() + 1);

        for (int i = 0; i < directories.size(); i++) {
            names.add(FileStuff.getFilenameWithPathFromUri(directories.get(i)));
        }

        setupRecyclerView(names, context, directories, settings);
        binding.buttonNewFolder.setOnClickListener(v -> resultLauncherAddFolder.launch(moveViewModel.getCurrentDirectoryUri()));
        binding.title.setText(getResources().getQuantityString(R.plurals.move_modal_title, files.size(), files.size(), StringStuff.bytesToReadableString(bytes)));
        binding.body.setText(getString(R.string.move_modal_body));
        moveViewModel.setOnDoneBottomSheet(deletedFiles -> {
            clearViewModel();
            dismiss();
        });
        moveViewModel.getProgressData().observe(this, progressData -> {
            if (progressData != null) {
                binding.progress.setProgressCompat(progressData.getProgressPercentage(), true);
                binding.progressText.setText(getString(R.string.move_modal_moving, progressData.getProgress(), progressData.getTotal()));
            } else {
                binding.progress.setProgressCompat(0, false);
            }
        });
        if (moveViewModel.isRunning()) {
            showDeleting();
        }
    }

    private void setupRecyclerView(List<String> names, Context context, List<Uri> directories, Settings settings) {
        ImportListAdapter adapter = new ImportListAdapter(names, context, null);
        adapter.setOnPositionSelected(pos -> {
            Uri uri = directories.get(pos);

            DocumentFile directory = DocumentFile.fromTreeUri(context, uri);
            if (directory == null || !directory.isDirectory() || !directory.exists()) {
                settings.removeGalleryDirectory(uri);
                Toaster.getInstance(context).showLong(context.getString(R.string.directory_does_not_exist));
                names.remove(pos);
                directories.remove(pos);
                adapter.notifyItemRemoved(pos);
            } else {
                doCopy(uri, names.get(pos), directory);
            }
        });
        binding.recycler.setNestedScrollingEnabled(false);
        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false);
        binding.recycler.setLayoutManager(linearLayoutManager);
        DividerItemDecoration divider = new DividerItemDecoration(binding.recycler.getContext(), DividerItemDecoration.VERTICAL);
        Drawable drawable = AppCompatResources.getDrawable(context, R.drawable.line_divider);
        if (drawable != null) {
            divider.setDrawable(drawable);
        }
        binding.recycler.addItemDecoration(divider);
        binding.recycler.setAdapter(adapter);
    }

    private void showDeleting() {
        BottomSheetDialog dialog = (BottomSheetDialog) requireDialog();
        dialog.setCancelable(false);
        dialog.setCanceledOnTouchOutside(false);
        binding.progress.setProgressCompat(0, false);
        binding.layoutContentMain.setVisibility(View.GONE);
        binding.layoutContentCopying.setVisibility(View.VISIBLE);
        binding.title.setText(getResources().getQuantityString(R.plurals.move_modal_title_moving, moveViewModel.getFiles().size(), moveViewModel.getFiles().size(),
                StringStuff.bytesToReadableString(moveViewModel.getTotalBytes())));
        binding.body.setText(getString(R.string.move_modal_body_moving, moveViewModel.getDestinationFolderName()));
        binding.buttonCancel.setOnClickListener(v -> {
            moveViewModel.cancel();
            dismiss();
        });
    }

    private void doCopy(Uri destinationUri, String destinationFolderName, DocumentFile destinationDirectory) {
        moveViewModel.getProgressData().setValue(null);
        moveViewModel.setDestinationDirectory(destinationDirectory);
        moveViewModel.setDestinationFolderName(destinationFolderName);
        moveViewModel.setDestinationUri(destinationUri);
        moveViewModel.setRunning(true);
        showDeleting();
        moveViewModel.start(requireActivity());
    }

    private void clearViewModel() {
        moveViewModel.setRunning(false);
        moveViewModel.setTotalBytes(0);
        moveViewModel.getFiles().clear();
        moveViewModel.setDestinationDirectory(null);
        moveViewModel.setDestinationFolderName(null);
        moveViewModel.setDestinationUri(null);
    }

}
