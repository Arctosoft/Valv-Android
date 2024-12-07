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

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.List;

import se.arctosoft.vault.data.GalleryFile;
import se.arctosoft.vault.databinding.BottomSheetExportBinding;
import se.arctosoft.vault.utils.FileStuff;
import se.arctosoft.vault.utils.StringStuff;
import se.arctosoft.vault.viewmodel.ExportViewModel;

public class BottomSheetExportFragment extends BottomSheetDialogFragment {
    private static final String TAG = "BottomSheetFragment";

    private BottomSheetExportBinding binding;
    private ExportViewModel exportViewModel;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = BottomSheetExportBinding.inflate(inflater, container, false);

        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        exportViewModel = new ViewModelProvider(requireParentFragment()).get(ExportViewModel.class);
        List<GalleryFile> filesToExport = exportViewModel.getFilesToExport();
        if (filesToExport.isEmpty()) {
            dismiss();
            return;
        }
        long bytes = 0;
        for (GalleryFile documentFile : filesToExport) {
            bytes += documentFile.getSize();
        }
        exportViewModel.setTotalBytes(bytes);

        binding.title.setText(getResources().getQuantityString(R.plurals.export_modal_title, filesToExport.size(), filesToExport.size(), StringStuff.bytesToReadableString(bytes)));
        binding.body.setText(getString(R.string.export_modal_body, FileStuff.getFilenameWithPathFromUri(exportViewModel.getCurrentDocumentDirectory().getUri())));
        exportViewModel.setOnDoneBottomSheet(deletedFiles -> {
            clearViewModel();
            dismiss();
        });
        exportViewModel.getProgressData().observe(this, progressData -> {
            if (progressData != null) {
                binding.progress.setProgressCompat(progressData.getProgressPercentage(), true);
                binding.progressText.setText(getString(R.string.export_modal_exporting, progressData.getProgress(), progressData.getTotal()));
            } else {
                binding.progress.setProgressCompat(0, false);
            }
        });
        if (exportViewModel.isDeleting()) {
            showDeleting();
        } else {
            long finalBytes = bytes;
            binding.progressText.setText(getString(R.string.export_modal_exporting, 0, filesToExport.size()));
            binding.buttonExport.setOnClickListener(v -> doExport(finalBytes));
        }
    }

    private void showDeleting() {
        BottomSheetDialog dialog = (BottomSheetDialog) requireDialog();
        dialog.setCancelable(false);
        dialog.setCanceledOnTouchOutside(false);
        binding.progress.setProgressCompat(0, false);
        binding.buttonExport.setVisibility(View.GONE);
        binding.layoutContentProgress.setVisibility(View.VISIBLE);
        binding.title.setText(getResources().getQuantityString(R.plurals.export_modal_title_exporting, exportViewModel.getFilesToExport().size(), exportViewModel.getFilesToExport().size(),
                StringStuff.bytesToReadableString(exportViewModel.getTotalBytes())));
        binding.body.setText(getString(R.string.export_modal_body_exporting));
        binding.buttonCancel.setOnClickListener(v -> {
            exportViewModel.cancel();
            dismiss();
        });
    }

    private void doExport(long totalBytes) {
        showDeleting();
        exportViewModel.getProgressData().setValue(null);
        exportViewModel.setTotalBytes(totalBytes);
        exportViewModel.setDeleting(true);
        exportViewModel.start(requireActivity());
    }

    private void clearViewModel() {
        exportViewModel.setDeleting(false);
        exportViewModel.setTotalBytes(0);
        exportViewModel.getFilesToExport().clear();
    }

}
