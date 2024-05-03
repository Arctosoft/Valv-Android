/*
 * Valv-Android
 * Copyright (C) 2023 Arctosoft AB
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
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see https://www.gnu.org/licenses/.
 */

package se.arctosoft.vault.utils;

import android.content.Context;
import android.content.DialogInterface;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.documentfile.provider.DocumentFile;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.mikepenz.aboutlibraries.LibsBuilder;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import se.arctosoft.vault.BuildConfig;
import se.arctosoft.vault.R;
import se.arctosoft.vault.adapters.ImportListAdapter;
import se.arctosoft.vault.databinding.DialogEditTextBinding;
import se.arctosoft.vault.databinding.DialogImportBinding;
import se.arctosoft.vault.interfaces.IOnEdited;

public class Dialogs {
    private static final String TAG = "Dialogs";

    public static void showImportGalleryChooseDestinationDialog(FragmentActivity context, Settings settings, int fileCount, IOnDirectorySelected onDirectorySelected) {
        List<Uri> directories = settings.getGalleryDirectoriesAsUri(false);
        List<String> names = new ArrayList<>(directories.size());
        for (int i = 0; i < directories.size(); i++) {
            names.add(FileStuff.getFilenameWithPathFromUri(directories.get(i)));
        }
        DialogImportBinding binding = DialogImportBinding.inflate(context.getLayoutInflater());

        AlertDialog alertDialog = new MaterialAlertDialogBuilder(context)
                .setTitle(context.getString(R.string.dialog_import_to_title))
                .setView(binding.getRoot())
                .setNegativeButton(android.R.string.cancel, null)
                .setNeutralButton(R.string.dialog_import_to_button_neutral, (dialog, which) -> onDirectorySelected.onOtherDirectory())
                .show();

        ImportListAdapter adapter = new ImportListAdapter(names, pos -> {
            alertDialog.dismiss();
            Uri uri = directories.get(pos);

            DocumentFile directory = DocumentFile.fromTreeUri(context, uri);
            if (directory == null || !directory.isDirectory() || !directory.exists()) {
                settings.removeGalleryDirectory(uri);
                Toaster.getInstance(context).showLong(context.getString(R.string.directory_does_not_exist));
                showImportGalleryChooseDestinationDialog(context, settings, fileCount, onDirectorySelected);
            } else {
                onDirectorySelected.onDirectorySelected(directory, binding.checkbox.isChecked());
            }
        });
        binding.checkbox.setText(context.getResources().getQuantityString(R.plurals.dialog_import_to_delete_original, fileCount));
        binding.recycler.setLayoutManager(new LinearLayoutManager(context));
        binding.recycler.setAdapter(adapter);
    }

    public static void showCopyMoveChooseDestinationDialog(FragmentActivity context, Settings settings, int fileCount, IOnDirectorySelected onDirectorySelected) {
        List<Uri> directories = settings.getGalleryDirectoriesAsUri(false);
        String[] names = new String[directories.size()];
        for (int i = 0; i < names.length; i++) {
            names[i] = FileStuff.getFilenameWithPathFromUri(directories.get(i));
        }

        new MaterialAlertDialogBuilder(context)
                .setTitle(context.getString(R.string.dialog_import_to_title))
                .setItems(names, (dialog, which) -> {
                    Uri uri = directories.get(which);
                    DocumentFile directory = DocumentFile.fromTreeUri(context, uri);
                    if (directory == null || !directory.isDirectory() || !directory.exists()) {
                        settings.removeGalleryDirectory(uri);
                        Toaster.getInstance(context).showLong(context.getString(R.string.directory_does_not_exist));
                        showCopyMoveChooseDestinationDialog(context, settings, fileCount, onDirectorySelected);
                    } else {
                        onDirectorySelected.onDirectorySelected(directory, false);
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .setNeutralButton(R.string.dialog_import_to_button_neutral, (dialog, which) -> onDirectorySelected.onOtherDirectory())
                .show();
    }

    public interface IOnDirectorySelected {
        void onDirectorySelected(@NonNull DocumentFile directory, boolean deleteOriginal);

        void onOtherDirectory();
    }

    public interface IOnPositionSelected {
        void onSelected(int pos);
    }

    public interface IOnEditedIncludedFolders {
        void onRemoved(@NonNull List<Uri> selectedToRemove);
    }

    public static void showTextDialog(Context context, String title, String message) {
        new MaterialAlertDialogBuilder(context)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    public static void showAboutDialog(Context context) {
        new MaterialAlertDialogBuilder(context)
                .setTitle(context.getString(R.string.dialog_about_title))
                .setMessage(context.getString(R.string.dialog_about_message, BuildConfig.BUILD_TYPE, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE))
                .setPositiveButton(android.R.string.ok, null)
                .setNeutralButton(context.getString(R.string.licenses), (dialogInterface, i) -> {
                    new LibsBuilder()
                            .withActivityTitle(context.getString(R.string.licenses))
                            .start(context);
                })
                .show();
    }

    public static void showConfirmationDialog(Context context, String title, String message, DialogInterface.OnClickListener onConfirm) {
        new MaterialAlertDialogBuilder(context)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, onConfirm)
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    public static void showEditTextDialog(FragmentActivity context, @Nullable String title, @Nullable String editTextBody, IOnEdited onEdited) {
        DialogEditTextBinding binding = DialogEditTextBinding.inflate(context.getLayoutInflater(), null, false);
        if (editTextBody != null) {
            binding.text.setText(editTextBody);
        }

        new MaterialAlertDialogBuilder(context)
                .setTitle(title)
                .setView(binding.getRoot())
                .setPositiveButton(R.string.gallery_note_save, (dialog, which) -> onEdited.onEdited(binding.text.getText().toString()))
                .setNegativeButton(android.R.string.cancel, null)
                .setNeutralButton(R.string.gallery_note_delete, (dialog, which) -> onEdited.onEdited(null))
                .show();
    }

    public static void showEditIncludedFolders(Context context, @NonNull Settings settings, @NonNull IOnEditedIncludedFolders onEditedIncludedFolders) {
        List<Uri> directories = settings.getGalleryDirectoriesAsUri(false);
        String[] names = new String[directories.size()];
        for (int i = 0; i < names.length; i++) {
            names[i] = FileStuff.getFilenameWithPathFromUri(directories.get(i));
        }
        List<Uri> selectedToRemove = new LinkedList<>();
        new MaterialAlertDialogBuilder(context)
                .setTitle(context.getString(R.string.dialog_edit_included_title))
                .setMultiChoiceItems(names, null, (dialog, which, isChecked) -> {
                    if (isChecked) {
                        selectedToRemove.add(directories.get(which));
                    } else {
                        selectedToRemove.remove(directories.get(which));
                    }
                })
                .setPositiveButton(context.getString(R.string.remove), (dialog, which) -> onEditedIncludedFolders.onRemoved(selectedToRemove))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

}
