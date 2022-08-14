package se.arctosoft.vault.utils;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.documentfile.provider.DocumentFile;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Arrays;
import java.util.List;

import se.arctosoft.vault.R;

public class Dialogs {
    private static final String TAG = "Dialogs";

    public static void showImportGalleryChooseDestinationDialog(Context context, Settings settings, int filesToImport, IOnDirectorySelected onDirectorySelected) {
        List<DocumentFile> directories = settings.getGalleryDirectoriesAsDocumentFile(context);
        String[] names = new String[directories.size()];
        for (int i = 0; i < names.length; i++) {
            names[i] = directories.get(i).getName();
        }
        Log.e(TAG, "showImportGalleryChooseDestinationDialog: " + Arrays.toString(names));
        new MaterialAlertDialogBuilder(context)
                .setTitle(context.getString(R.string.dialog_import_to_title))
                //.setMessage(filesToImport == 1 ? context.getString(R.string.dialog_import_to_message_one) : context.getString(R.string.dialog_import_to_message_many, filesToImport))
                .setItems(names, (dialog, which) -> onDirectorySelected.onDirectorySelected(directories.get(which)))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    public interface IOnDirectorySelected {
        void onDirectorySelected(@NonNull DocumentFile directory);
    }

}
