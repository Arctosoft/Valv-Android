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
import se.arctosoft.vault.databinding.BottomSheetDeleteBinding;
import se.arctosoft.vault.utils.StringStuff;
import se.arctosoft.vault.viewmodel.DeleteViewModel;

public class BottomSheetDeleteFragment extends BottomSheetDialogFragment {
    private static final String TAG = "BottomSheetFragment";

    private BottomSheetDeleteBinding binding;
    private DeleteViewModel deleteViewModel;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = BottomSheetDeleteBinding.inflate(inflater, container, false);

        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        deleteViewModel = new ViewModelProvider(requireParentFragment()).get(DeleteViewModel.class);
        List<GalleryFile> filesToDelete = deleteViewModel.getFilesToDelete();
        if (filesToDelete.isEmpty()) {
            dismiss();
            return;
        }
        long bytes = 0;
        for (GalleryFile documentFile : filesToDelete) {
            bytes += documentFile.getSize();
        }

        binding.title.setText(getResources().getQuantityString(R.plurals.delete_modal_title, filesToDelete.size(), filesToDelete.size(), StringStuff.bytesToReadableString(bytes)));

        deleteViewModel.setOnDeleteDoneBottomSheet(deletedFiles -> {
            clearViewModel();
            dismiss();
        });
        deleteViewModel.getProgressData().observe(this, progressData -> {
            if (progressData != null) {
                binding.progress.setProgressCompat(progressData.getProgressPercentage(), true);
                binding.progressText.setText(getString(R.string.delete_modal_deleting, progressData.getProgress(), progressData.getTotal()));
            } else {
                binding.progress.setProgressCompat(0, false);
            }
        });
        if (deleteViewModel.isDeleting()) {
            showDeleting();
        } else {
            long finalBytes = bytes;
            binding.progressText.setText(getString(R.string.delete_modal_deleting, 0, filesToDelete.size()));
            binding.buttonDelete.setOnClickListener(v -> doDelete(finalBytes));
        }
    }

    private void showDeleting() {
        BottomSheetDialog dialog = (BottomSheetDialog) requireDialog();
        dialog.setCancelable(false);
        dialog.setCanceledOnTouchOutside(false);
        binding.progress.setProgressCompat(0, false);
        binding.buttonDelete.setVisibility(View.GONE);
        binding.layoutContentDeleting.setVisibility(View.VISIBLE);
        binding.title.setText(getResources().getQuantityString(R.plurals.delete_modal_title_deleting, deleteViewModel.getFilesToDelete().size(), deleteViewModel.getFilesToDelete().size(), StringStuff.bytesToReadableString(deleteViewModel.getTotalBytes())));
        binding.body.setText(getString(R.string.delete_modal_body_deleting));
        binding.buttonCancel.setOnClickListener(v -> {
            deleteViewModel.cancelDelete();
            dismiss();
        });
    }

    private void doDelete(long totalBytes) {
        showDeleting();
        deleteViewModel.getProgressData().setValue(null);
        deleteViewModel.setTotalBytes(totalBytes);
        deleteViewModel.setDeleting(true);
        deleteViewModel.startDelete(requireActivity());
    }

    private void clearViewModel() {
        deleteViewModel.setDeleting(false);
        deleteViewModel.setTotalBytes(0);
        deleteViewModel.getFilesToDelete().clear();
    }

}
