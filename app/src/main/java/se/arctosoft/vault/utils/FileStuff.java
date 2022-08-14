package se.arctosoft.vault.utils;

import android.content.ClipData;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.documentfile.provider.DocumentFile;
import androidx.fragment.app.FragmentActivity;

import java.util.ArrayList;
import java.util.List;

import se.arctosoft.vault.data.FileType;
import se.arctosoft.vault.data.GalleryFile;
import se.arctosoft.vault.encryption.Encryption;

public class FileStuff {

    @NonNull
    public static List<Uri> getFilesInFolder(@NonNull ContentResolver resolver, DocumentFile pickedDir) {
        Uri realUri = DocumentsContract.buildChildDocumentsUriUsingTree(pickedDir.getUri(), DocumentsContract.getDocumentId(pickedDir.getUri()));
        List<Uri> files = new ArrayList<>();
        Cursor c = resolver.query(
                realUri,
                new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME},
                null,
                null,
                null);
        if (c == null || !c.moveToFirst()) {
            return files;
        }
        do {
            files.add(DocumentsContract.buildDocumentUriUsingTree(realUri, c.getString(0)));
        } while (c.moveToNext());
        c.close();
        return files;
    }

    public static void pickImageFiles(@NonNull FragmentActivity context, int requestCode) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);

        context.startActivityForResult(intent, requestCode);
    }

    @NonNull
    public static List<Uri> uriListFromClipData(@Nullable ClipData clipData) {
        List<Uri> uris = new ArrayList<>();
        if (clipData != null) {
            for (int i = 0; i < clipData.getItemCount(); i++) {
                uris.add(clipData.getItemAt(i).getUri());
            }
        }
        return uris;
    }

    @NonNull
    public static List<GalleryFile> getEncryptedFilesInFolder(Context context, @NonNull List<Uri> files) {
        List<DocumentFile> documentFiles = new ArrayList<>(files.size() / 2 + 1);
        List<DocumentFile> documentThumbs = new ArrayList<>(files.size() / 2 + 1);
        for (Uri fileUri : files) {
            DocumentFile documentFile = DocumentFile.fromSingleUri(context, fileUri);
            String fileName = documentFile.getName();
            if (!fileName.startsWith(Encryption.ENCRYPTED_PREFIX)) {
                continue;
            }
            if (fileName.startsWith(Encryption.PREFIX_THUMB)) {
                documentThumbs.add(documentFile);
            } else {
                documentFiles.add(documentFile);
            }
        }
        List<GalleryFile> galleryFiles = new ArrayList<>(documentFiles.size());
        for (DocumentFile d : documentFiles) {
            String unencryptedName = d.getName().split("-", 2)[1];
            boolean foundThumb = false;
            for (DocumentFile t : documentThumbs) {
                String unencryptedThumbName = t.getName().split("-", 2)[1];
                if (unencryptedName.equals(unencryptedThumbName)) {
                    galleryFiles.add(new GalleryFile(d.getUri(), t.getUri(), FileType.fromFilename(d.getName()), d.lastModified()));
                    foundThumb = true;
                    break;
                }
            }
            if (!foundThumb) {
                galleryFiles.add(new GalleryFile(d.getUri(), null, FileType.fromFilename(d.getName()), d.lastModified()));
            }
        }
        return galleryFiles;
    }

}
