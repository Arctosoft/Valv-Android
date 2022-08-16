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

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import se.arctosoft.vault.data.GalleryFile;
import se.arctosoft.vault.encryption.Encryption;

public class FileStuff {
    private static final String TAG = "FileStuff";

    @NonNull
    public static List<Uri> getFilesInFolder(@NonNull ContentResolver resolver, Uri pickedDir) {
        //Log.e(TAG, "getFilesInFolder: path " + pickedDir.getPathSegments());
        Uri realUri = DocumentsContract.buildChildDocumentsUriUsingTree(pickedDir, DocumentsContract.getDocumentId(pickedDir));
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
            files.add(0, DocumentsContract.buildDocumentUriUsingTree(realUri, c.getString(0)));
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

    public static String getFilenameWithPathFromUri(@NonNull Uri uri) {
        String[] split = uri.getLastPathSegment().split(":");
        return split[split.length - 1];
    }

    public static String getFilenameFromUri(@NonNull Uri uri, boolean withoutPrefix) {
        String[] split = uri.getLastPathSegment().split("/");
        String s = split[split.length - 1];
        if (withoutPrefix) {
            return s.split("-", 2)[1];
        }
        return s;
    }

    @NonNull
    public static List<GalleryFile> getEncryptedFilesInFolder(@NonNull List<Uri> files) {
        List<Uri> documentFiles = new ArrayList<>();
        List<Uri> documentThumbs = new ArrayList<>();
        List<GalleryFile> galleryFiles = new ArrayList<>();
        //long start = System.currentTimeMillis();
        for (Uri fileUri : files) {
            //Log.e(TAG, "getEncryptedFilesInFolder: check " + fileUri.getLastPathSegment());

            String fileName = FileStuff.getFilenameFromUri(fileUri, false);
            if (!fileName.startsWith(Encryption.ENCRYPTED_PREFIX)) { // && !documentFile.isDirectory()
                continue;
            }
            //DocumentFile documentFile = DocumentFile.fromSingleUri(context, fileUri);
            //String fileName = documentFile.getName();

            /*if (documentFile.isDirectory()) {
                //Log.e(TAG, "getEncryptedFilesInFolder: add dir " + fileUri);
                galleryFiles.add(GalleryFile.asDirectory(documentFile,
                        getEncryptedFilesInFolder(context,
                                FileStuff.getFilesInFolder(context.getContentResolver(), documentFile))));
            } else {*/
            if (fileName.startsWith(Encryption.PREFIX_THUMB)) {
                //Log.e(TAG, "getEncryptedFilesInFolder: add thumb " + fileUri);
                documentThumbs.add(fileUri);
            } else {
                //Log.e(TAG, "getEncryptedFilesInFolder: add file " + fileUri);
                documentFiles.add(fileUri);
            }
            //}
        }

        for (Uri fileUri : documentFiles) {
            String unencryptedName = FileStuff.getFilenameFromUri(fileUri, true);
            boolean foundThumb = false;
            for (Uri thumbUri : documentThumbs) {
                String unencryptedThumbName = FileStuff.getFilenameFromUri(thumbUri, true);
                if (unencryptedName.equals(unencryptedThumbName)) {
                    galleryFiles.add(GalleryFile.asFile(fileUri, thumbUri));
                    foundThumb = true;
                    break;
                }
            }
            if (!foundThumb) {
                galleryFiles.add(GalleryFile.asFile(fileUri, null));
            }
        }
        //Log.e(TAG, "getEncryptedFilesInFolder: took " + (System.currentTimeMillis() - start) + " ms to find encrypted files");
        return galleryFiles;
    }

    @NonNull
    public static List<DocumentFile> getDocumentsFromDirectoryResult(Context context, @NonNull Intent data) {
        ClipData clipData = data.getClipData();
        List<Uri> uris = FileStuff.uriListFromClipData(clipData);
        //Log.e(TAG, "getDocumentsFromDirectoryResult: got " + uris.size());
        if (uris.isEmpty()) {
            Uri dataUri = data.getData();
            if (dataUri != null) {
                uris.add(dataUri);
            }
        }
        //Log.e(TAG, "getDocumentsFromDirectoryResult: got " + uris.size());
        List<DocumentFile> documentFiles = new ArrayList<>();
        for (Uri uri : uris) {
            DocumentFile pickedFile = DocumentFile.fromSingleUri(context, uri);
            if (pickedFile != null && pickedFile.getType() != null && pickedFile.getType().startsWith("image/") && !pickedFile.getName().startsWith(Encryption.ENCRYPTED_PREFIX)) {
                documentFiles.add(pickedFile);
            }
        }
        return documentFiles;
    }

    public static boolean deleteFile(Context context, @Nullable Uri uri) {
        if (uri == null) {
            return true;
        }
        DocumentFile documentFile = DocumentFile.fromSingleUri(context, uri);
        assert documentFile != null;
        if (!documentFile.exists()) {
            return true;
        }
        return documentFile.delete();
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
