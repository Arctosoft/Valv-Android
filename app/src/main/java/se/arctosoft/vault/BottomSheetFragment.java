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
import android.content.DialogInterface;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

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
import se.arctosoft.vault.databinding.BottomSheetImportBinding;
import se.arctosoft.vault.utils.FileStuff;
import se.arctosoft.vault.utils.Settings;
import se.arctosoft.vault.utils.StringStuff;
import se.arctosoft.vault.utils.Toaster;
import se.arctosoft.vault.viewmodel.ImportViewModel;

public class BottomSheetFragment extends BottomSheetDialogFragment {
    private static final String TAG = "BottomSheetFragment";

    private BottomSheetImportBinding binding;
    private ImportViewModel importViewModel;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = BottomSheetImportBinding.inflate(inflater, container, false);

        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        importViewModel = new ViewModelProvider(requireParentFragment()).get(ImportViewModel.class);
        List<DocumentFile> filesToImport = importViewModel.getFilesToImport();
        if (filesToImport.isEmpty()) {
            dismiss();
            return;
        }
        long bytes = 0;
        for (DocumentFile documentFile : filesToImport) {
            bytes += documentFile.length();
        }

        Context context = requireContext();
        Settings settings = Settings.getInstance(context);
        List<Uri> directories = settings.getGalleryDirectoriesAsUri(false);
        List<String> names = new ArrayList<>(directories.size() + 1);

        final boolean hasUri = importViewModel.getCurrentDirectoryUri() != null;
        binding.title.setText(getResources().getQuantityString(R.plurals.bottom_modal_title, filesToImport.size(), filesToImport.size(), StringStuff.bytesToReadableString(bytes)));
        String currentName = hasUri ? FileStuff.getFilenameWithPathFromUri(importViewModel.getCurrentDirectoryUri()) : null;
        if (hasUri) {
            names.add(currentName);
        }

        for (int i = 0; i < directories.size(); i++) {
            names.add(FileStuff.getFilenameWithPathFromUri(directories.get(i)));
        }

        setupRecyclerView(names, context, currentName, bytes, hasUri, directories, settings);

        importViewModel.setOnImportDoneBottomSheet((destinationUri, sameDirectory, importedCount, failedCount, thumbErrorCount) -> {
            clearViewModel();
            dismiss();
        });
        importViewModel.getProgressData().observe(this, progressData -> {
            if (progressData != null) {
                binding.progress.setProgressCompat(progressData.getProgressPercentage(), true);
                binding.progressText.setText(getString(R.string.bottom_modal_importing, progressData.getProgress(), progressData.getTotal(), progressData.getDoneMB(), progressData.getTotalMB()));
            } else {
                binding.progress.setProgressCompat(0, false);
            }
        });
        if (importViewModel.isImporting()) {
            showImporting();
        }
    }

    private void setupRecyclerView(List<String> names, Context context, String currentName, long bytes, boolean hasUri, List<Uri> directories, Settings settings) {
        ImportListAdapter adapter = new ImportListAdapter(names, context, currentName);
        adapter.setOnPositionSelected(pos -> onPositionSelected(pos, hasUri, directories, context, settings, adapter, bytes, names));
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

    private void onPositionSelected(int pos, boolean hasUri, List<Uri> directories, Context context, Settings settings, ImportListAdapter adapter, long finalBytes, List<String> names) {
        int directoriesPos = hasUri ? pos - 1 : pos;
        Uri uri = hasUri && pos == 0 ? importViewModel.getCurrentDirectoryUri() : directories.get(directoriesPos);

        DocumentFile directory = DocumentFile.fromTreeUri(context, uri);
        if ((!hasUri || pos > 0) & (directory == null || !directory.isDirectory() || !directory.exists())) {
            settings.removeGalleryDirectory(uri);
            Toaster.getInstance(context).showLong(context.getString(R.string.directory_does_not_exist));
            directories.remove(directoriesPos);
            adapter.notifyItemRemoved(pos);
        } else {
            doImport(finalBytes, uri, names.get(pos), binding.checkboxDeleteAfter.isChecked(), directory, hasUri && pos == 0);
        }
    }

    private void showImporting() {
        BottomSheetDialog dialog = (BottomSheetDialog) requireDialog();
        dialog.setCancelable(false);
        dialog.setCanceledOnTouchOutside(false);
        binding.progress.setProgressCompat(0, false);
        binding.layoutContentMain.setVisibility(View.GONE);
        binding.layoutContentImporting.setVisibility(View.VISIBLE);
        binding.title.setText(getResources().getQuantityString(R.plurals.bottom_modal_title_importing, importViewModel.getFilesToImport().size(), importViewModel.getFilesToImport().size(), StringStuff.bytesToReadableString(importViewModel.getTotalBytes())));
        binding.body.setText(getString(R.string.bottom_modal_body_importing, importViewModel.getDestinationFolderName()));
        binding.buttonCancel.setOnClickListener(v -> {
            importViewModel.cancelImport();
        });
    }

    private void doImport(long totalBytes, Uri destinationUri, String destinationFolderName, boolean deleteAfterImport, DocumentFile destinationDirectory, boolean sameDirectory) {
        showImporting();
        importViewModel.getProgressData().setValue(null);
        importViewModel.setDestinationDirectory(destinationDirectory);
        importViewModel.setDestinationFolderName(destinationFolderName);
        importViewModel.setImportToUri(destinationUri);
        importViewModel.setDeleteAfterImport(deleteAfterImport);
        importViewModel.setTotalBytes(totalBytes);
        importViewModel.setSameDirectory(sameDirectory);
        importViewModel.setImporting(true);
        importViewModel.startImport(requireActivity());
    }

    private void clearViewModel() {
        importViewModel.getFilesToImport().clear();
        importViewModel.setImporting(false);
        importViewModel.setDestinationDirectory(null);
        importViewModel.setDestinationFolderName(null);
        importViewModel.setImportToUri(null);
        importViewModel.setSameDirectory(false);
        importViewModel.setDeleteAfterImport(false);
        importViewModel.setTotalBytes(0);
    }

    @Override
    public void onDismiss(@NonNull DialogInterface dialog) { // TODO triggered on rotation, save working thread/progress in view model
        Log.e(TAG, "onDismiss: ");
        super.onDismiss(dialog);
    }

    @Override
    public void onCancel(@NonNull DialogInterface dialog) {
        Log.e(TAG, "onCancel: ");
        super.onCancel(dialog);
    }
}
