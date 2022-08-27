package se.arctosoft.vault.utils;

import android.content.ClipData;
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
import java.util.Collections;
import java.util.List;

import se.arctosoft.vault.data.CursorFile;
import se.arctosoft.vault.data.GalleryFile;
import se.arctosoft.vault.encryption.Encryption;

public class FileStuff {
    private static final String TAG = "FileStuff";

    @NonNull
    public static List<GalleryFile> getFilesInFolder(Context context, Uri pickedDir) {
        Uri realUri = DocumentsContract.buildChildDocumentsUriUsingTree(pickedDir, DocumentsContract.getDocumentId(pickedDir));
        List<CursorFile> files = new ArrayList<>();
        Cursor c = context.getContentResolver().query(
                realUri,
                new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_LAST_MODIFIED, DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_SIZE},
                null,
                null,
                null);
        if (c == null || !c.moveToFirst()) {
            if (c != null) {
                c.close();
            }
            return new ArrayList<>();
        }
        do {
            Uri uri = DocumentsContract.buildDocumentUriUsingTree(realUri, c.getString(0));
            String name = c.getString(1);
            long lastModified = c.getLong(2);
            String mimeType = c.getString(3);
            long size = c.getLong(4);
            files.add(new CursorFile(name, uri, lastModified, mimeType, size));
        } while (c.moveToNext());
        c.close();
        Collections.sort(files);
        return getEncryptedFilesInFolder(files);
    }

    @NonNull
    private static List<GalleryFile> getEncryptedFilesInFolder(@NonNull List<CursorFile> files) {
        List<CursorFile> documentFiles = new ArrayList<>();
        List<CursorFile> documentThumbs = new ArrayList<>();
        List<GalleryFile> galleryFiles = new ArrayList<>();
        for (CursorFile file : files) {
            if (!file.getName().startsWith(Encryption.ENCRYPTED_PREFIX) && !file.isDirectory()) {
                continue;
            }

            if (file.getName().startsWith(Encryption.PREFIX_THUMB)) {
                documentThumbs.add(file);
            } else {
                documentFiles.add(file);
            }
        }

        for (CursorFile file : documentFiles) {
            if (file.isDirectory()) {
                galleryFiles.add(GalleryFile.asDirectory(file, null)); // TODO fix later
                continue;
            }
            file.setNameWithoutPrefix(FileStuff.getNameWithoutPrefix(file.getName()));
            boolean foundThumb = false;
            for (CursorFile thumb : documentThumbs) {
                thumb.setNameWithoutPrefix(FileStuff.getNameWithoutPrefix(thumb.getName()));
                if (file.getNameWithoutPrefix().equals(thumb.getNameWithoutPrefix())) {
                    galleryFiles.add(GalleryFile.asFile(file, thumb));
                    foundThumb = true;
                    break;
                }
            }
            if (!foundThumb) {
                galleryFiles.add(GalleryFile.asFile(file, null));
            }
        }
        return galleryFiles;
    }

    public static void pickImageFiles(@NonNull FragmentActivity context, int requestCode) {
        pickFiles(context, requestCode, "image/*");
    }

    private static void pickFiles(@NonNull FragmentActivity context, int requestCode, String mimeType) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(mimeType);
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);

        context.startActivityForResult(intent, requestCode);
    }

    public static void pickVideoFiles(@NonNull FragmentActivity context, int requestCode) {
        pickFiles(context, requestCode, "video/*");
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

    public static String getNameWithoutPrefix(@NonNull String s) {
        return s.split("-", 2)[1];
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
            if (pickedFile != null && pickedFile.getType() != null && (pickedFile.getType().startsWith("image/") || pickedFile.getType().startsWith("video/")) && !pickedFile.getName().startsWith(Encryption.ENCRYPTED_PREFIX)) {
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
