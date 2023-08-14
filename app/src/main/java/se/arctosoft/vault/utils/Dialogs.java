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
import androidx.documentfile.provider.DocumentFile;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.LinkedList;
import java.util.List;

import se.arctosoft.vault.R;

public class Dialogs {
    private static final String TAG = "Dialogs";

    public static void showImportGalleryChooseDestinationDialog(Context context, Settings settings, IOnDirectorySelected onDirectorySelected) {
        List<Uri> directories = settings.getGalleryDirectoriesAsUri(false);
        String[] names = new String[directories.size()];
        for (int i = 0; i < names.length; i++) {
            names[i] = FileStuff.getFilenameWithPathFromUri(directories.get(i));
        }
        //Log.e(TAG, "showImportGalleryChooseDestinationDialog: " + Arrays.toString(names));
        new MaterialAlertDialogBuilder(context)
                .setTitle(context.getString(R.string.dialog_import_to_title))
                .setItems(names, (dialog, which) -> onDirectorySelected.onDirectorySelected(DocumentFile.fromTreeUri(context, directories.get(which))))
                .setNegativeButton(android.R.string.cancel, null)
                .setNeutralButton(R.string.dialog_import_to_button_neutral, (dialog, which) -> onDirectorySelected.onOtherDirectory())
                .show();
    }

    public interface IOnDirectorySelected {
        void onDirectorySelected(@NonNull DocumentFile directory);

        void onOtherDirectory();
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

    public static void showConfirmationDialog(Context context, String title, String message, DialogInterface.OnClickListener onConfirm) {
        new MaterialAlertDialogBuilder(context)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, onConfirm)
                .setNegativeButton(android.R.string.cancel, null)
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
