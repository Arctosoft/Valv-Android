package se.arctosoft.vault.utils;

import android.content.ClipData;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.documentfile.provider.DocumentFile;
import androidx.fragment.app.FragmentActivity;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import se.arctosoft.vault.data.GalleryFile;
import se.arctosoft.vault.encryption.Encryption;

public class FileStuff {
    private static final String TAG = "FileStuff";

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
        List<DocumentFile> documentFiles = new ArrayList<>();
        List<DocumentFile> documentThumbs = new ArrayList<>();
        List<GalleryFile> galleryFiles = new ArrayList<>();
        for (Uri fileUri : files) {
            Log.e(TAG, "getEncryptedFilesInFolder: check " + fileUri.getLastPathSegment());
            String[] split = fileUri.getLastPathSegment().split("/");
            String fileName = split[split.length - 1];
            if (!fileName.startsWith(Encryption.ENCRYPTED_PREFIX)) { // && !documentFile.isDirectory()
                continue;
            }
            DocumentFile documentFile = DocumentFile.fromSingleUri(context, fileUri);
            //String fileName = documentFile.getName();

            if (documentFile.isDirectory()) {
                Log.e(TAG, "getEncryptedFilesInFolder: add dir " + fileUri);
                galleryFiles.add(GalleryFile.asDirectory(documentFile,
                        getEncryptedFilesInFolder(context,
                                FileStuff.getFilesInFolder(context.getContentResolver(), documentFile))));
            } else {
                if (fileName.startsWith(Encryption.PREFIX_THUMB)) {
                    Log.e(TAG, "getEncryptedFilesInFolder: add thumb " + fileUri);
                    documentThumbs.add(documentFile);
                } else {
                    Log.e(TAG, "getEncryptedFilesInFolder: add file " + fileUri);
                    documentFiles.add(documentFile);
                }
            }
        }

        for (DocumentFile d : documentFiles) {
            Log.e(TAG, "getEncryptedFilesInFolder: " + d.getName());
            String unencryptedName = d.getName().split("-", 2)[1];
            boolean foundThumb = false;
            for (DocumentFile t : documentThumbs) {
                String unencryptedThumbName = t.getName().split("-", 2)[1];
                if (unencryptedName.equals(unencryptedThumbName)) {
                    galleryFiles.add(GalleryFile.asFile(d, t.getUri()));
                    foundThumb = true;
                    break;
                }
            }
            if (!foundThumb) {
                galleryFiles.add(GalleryFile.asFile(d, null));
            }
        }
        return galleryFiles;
    }

    public static void deleteCache(Context context) {
        deleteDir(context.getCacheDir());
    }

    private static boolean deleteDir(File dir) {
        if (dir != null && dir.isDirectory()) {
            String[] children = dir.list();
            if (children != null) {
                for (String child : children) {
                    boolean success = deleteDir(new File(dir, child));
                    if (!success) {
                        return false;
                    }
                }
            }
            return dir.delete();
        } else if (dir != null && dir.isFile()) {
            return dir.delete();
        } else {
            return false;
        }
    }

}
