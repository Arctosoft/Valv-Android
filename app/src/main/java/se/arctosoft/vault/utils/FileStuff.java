/*
 * Valv-Android
 * Copyright (C) 2024 Arctosoft AB
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

import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.documentfile.provider.DocumentFile;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import se.arctosoft.vault.data.CursorFile;
import se.arctosoft.vault.data.GalleryFile;
import se.arctosoft.vault.encryption.Encryption;

public class FileStuff {
    private static final String TAG = "FileStuff";

    @NonNull
    public static List<GalleryFile> getFilesInFolder(Context context, Uri pickedDir) {
        //Log.e(TAG, "getFilesInFolder: " + pickedDir);
        Uri realUri = DocumentsContract.buildChildDocumentsUriUsingTree(pickedDir, DocumentsContract.getDocumentId(pickedDir));
        List<CursorFile> files = new ArrayList<>();
        Cursor c = context.getContentResolver().query(
                realUri,
                new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                        DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_SIZE},
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
        List<GalleryFile> encryptedFilesInFolder = getEncryptedFilesInFolder(files);
        Collections.sort(encryptedFilesInFolder);
        return encryptedFilesInFolder;
    }

    @NonNull
    private static List<GalleryFile> getEncryptedFilesInFolder(@NonNull List<CursorFile> files) {
        List<CursorFile> documentFiles = new ArrayList<>();
        List<CursorFile> documentThumbs = new ArrayList<>();
        List<CursorFile> documentNote = new ArrayList<>();
        List<GalleryFile> galleryFiles = new ArrayList<>();
        for (CursorFile file : files) {
            String name = file.getName();
            if (!name.startsWith(Encryption.ENCRYPTED_PREFIX) && !name.endsWith(Encryption.ENCRYPTED_SUFFIX) && !file.isDirectory()) {
                continue;
            }
            //Log.e(TAG, "getEncryptedFilesInFolder: found " + name);

            if (name.endsWith(Encryption.SUFFIX_THUMB) || name.startsWith(Encryption.PREFIX_THUMB)) {
                documentThumbs.add(file);
            } else if (name.endsWith(Encryption.SUFFIX_NOTE_FILE) || name.startsWith(Encryption.PREFIX_NOTE_FILE)) {
                documentNote.add(file);
            } else {
                documentFiles.add(file);
            }
        }

        for (CursorFile file : documentFiles) {
            if (file.isDirectory()) {
                galleryFiles.add(GalleryFile.asDirectory(file));
                continue;
            }
            file.setNameWithoutPrefix(FileStuff.getNameWithoutPrefix(file.getName()));
            CursorFile foundThumb = findCursorFile(documentThumbs, file.getNameWithoutPrefix());
            CursorFile foundNote = findCursorFile(documentNote, file.getNameWithoutPrefix());
            galleryFiles.add(GalleryFile.asFile(file, foundThumb, foundNote));
        }
        return galleryFiles;
    }

    @Nullable
    private static CursorFile findCursorFile(@NonNull List<CursorFile> list, String nameWithoutPrefix) {
        for (CursorFile cf : list) {
            cf.setNameWithoutPrefix(FileStuff.getNameWithoutPrefix(cf.getName()));
            if (cf.getNameWithoutPrefix().startsWith(nameWithoutPrefix)) {
                return cf;
            }
        }
        return null;
    }

    public static Intent getPickFilesIntent(String mimeType) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(mimeType);
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        return intent;
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
        Log.e(TAG, "getFilenameWithPathFromUri: " + Arrays.toString(split));
        return split[split.length - 1];
    }

    public static String getFilenameFromUri(@NonNull Uri uri, boolean withoutPrefix) {
        String[] split = uri.getLastPathSegment().split("/");
        String s = split[split.length - 1];
        if (withoutPrefix) {
            if (s.startsWith(Encryption.ENCRYPTED_PREFIX)) {
                return s.substring(s.indexOf("-") + 1);
            } else {
                return s.substring(0, s.lastIndexOf("-"));
            }
        }
        return s;
    }

    public static String getNameWithoutPrefix(@NonNull String encryptedName) {
        if (encryptedName.startsWith(Encryption.ENCRYPTED_PREFIX)) {
            return encryptedName.substring(encryptedName.indexOf("-") + 1);
        } else {
            return encryptedName.substring(0, encryptedName.lastIndexOf("-"));
        }
    }

    @NonNull
    public static List<DocumentFile> getDocumentsFromDirectoryResult(Context context, List<Uri> uris) {
        List<DocumentFile> documentFiles = new ArrayList<>();
        if (context == null) {
            return documentFiles;
        }
        for (Uri uri : uris) {
            DocumentFile pickedFile = DocumentFile.fromSingleUri(context, uri);
            if (pickedFile != null && pickedFile.getType() != null && (pickedFile.getType().startsWith("image/") || pickedFile.getType().startsWith("video/")) &&
                    (!pickedFile.getName().endsWith(Encryption.ENCRYPTED_SUFFIX) || !pickedFile.getName().startsWith(Encryption.ENCRYPTED_PREFIX))) {
                documentFiles.add(pickedFile);
            }
        }
        return documentFiles;
    }

    @NonNull
    public static List<DocumentFile> getDocumentsFromShareIntent(Context context, @NonNull List<Uri> uris) {
        List<DocumentFile> documentFiles = new ArrayList<>();
        if (context == null) {
            return documentFiles;
        }
        for (Uri uri : uris) {
            DocumentFile pickedFile = DocumentFile.fromSingleUri(context, uri);
            if (pickedFile != null && pickedFile.getType() != null && (pickedFile.getType().startsWith("image/") || pickedFile.getType().startsWith("video/")) &&
                    (!pickedFile.getName().endsWith(Encryption.ENCRYPTED_SUFFIX) || !pickedFile.getName().startsWith(Encryption.ENCRYPTED_PREFIX))) {
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
        if (documentFile == null || !documentFile.exists()) {
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

    @Nullable
    public static String getExtension(String name) {
        try {
            return name.substring(name.lastIndexOf("."));
        } catch (Exception ignored) {
        }
        return null;
    }

    @Nullable
    public static String getExtensionOrDefault(GalleryFile file) {
        String extension = getExtension(file.getName());
        if (extension != null) {
            return extension;
        }
        return file.getFileType().extension;
    }

    public static boolean copyTo(Context context, GalleryFile sourceFile, DocumentFile directory) {
        if (sourceFile.getUri().getLastPathSegment().equals(directory.getUri().getLastPathSegment() + "/" + sourceFile.getEncryptedName())) {
            Log.e(TAG, "moveTo: can't copy " + sourceFile.getUri().getLastPathSegment() + " to the same folder");
            return false;
        }
        String generatedName = StringStuff.getRandomFileName();
        int version = sourceFile.getVersion();
        DocumentFile file = directory.createFile("", version < 2 ? sourceFile.getFileType().suffixPrefix + generatedName : generatedName + sourceFile.getFileType().suffixPrefix);
        DocumentFile thumbFile = sourceFile.getThumbUri() == null ? null : directory.createFile("", version < 2 ? Encryption.PREFIX_THUMB + generatedName : generatedName + Encryption.SUFFIX_THUMB);
        DocumentFile noteFile = sourceFile.getNoteUri() == null ? null : directory.createFile("", version < 2 ? Encryption.PREFIX_NOTE_FILE + generatedName : generatedName + Encryption.SUFFIX_NOTE_FILE);

        if (file == null) {
            Log.e(TAG, "copyTo: could not create file from " + sourceFile.getUri());
            return false;
        }
        if (thumbFile != null) {
            writeTo(context, sourceFile.getThumbUri(), thumbFile.getUri());
        }
        if (noteFile != null) {
            writeTo(context, sourceFile.getNoteUri(), noteFile.getUri());
        }
        return writeTo(context, sourceFile.getUri(), file.getUri());
    }

    public static boolean moveTo(Context context, GalleryFile sourceFile, DocumentFile directory) {
        if (sourceFile.getUri().getLastPathSegment().equals(directory.getUri().getLastPathSegment() + "/" + sourceFile.getEncryptedName())) {
            Log.e(TAG, "moveTo: can't move " + sourceFile.getUri().getLastPathSegment() + " to the same folder");
            return false;
        }
        String nameWithoutPrefix = getNameWithoutPrefix(sourceFile.getEncryptedName());
        int version = sourceFile.getVersion();
        DocumentFile file = directory.createFile("", version < 2 ? sourceFile.getFileType().suffixPrefix + nameWithoutPrefix : nameWithoutPrefix + sourceFile.getFileType().suffixPrefix);
        DocumentFile thumbFile = sourceFile.getThumbUri() == null ? null : directory.createFile("", version < 2 ? Encryption.PREFIX_THUMB + nameWithoutPrefix : nameWithoutPrefix + Encryption.SUFFIX_THUMB);
        DocumentFile noteFile = sourceFile.getNoteUri() == null ? null : directory.createFile("", version < 2 ? Encryption.PREFIX_NOTE_FILE + nameWithoutPrefix : nameWithoutPrefix + Encryption.SUFFIX_NOTE_FILE);

        if (file == null) {
            Log.e(TAG, "moveTo: could not create file from " + sourceFile.getUri());
            return false;
        }
        if (thumbFile != null) {
            writeTo(context, sourceFile.getThumbUri(), thumbFile.getUri());
        }
        if (noteFile != null) {
            writeTo(context, sourceFile.getNoteUri(), noteFile.getUri());
        }
        return writeTo(context, sourceFile.getUri(), file.getUri());
    }

    public static boolean writeTo(Context context, Uri src, Uri dest) {
        try {
            InputStream inputStream = new BufferedInputStream(context.getContentResolver().openInputStream(src), 1024 * 32);
            OutputStream outputStream = new BufferedOutputStream(context.getContentResolver().openOutputStream(dest));
            int read;
            byte[] buffer = new byte[2048];
            while ((read = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
            }
            try {
                outputStream.close();
                inputStream.close();
            } catch (IOException ignored) {
            }
        } catch (IOException e) {
            Log.e(TAG, "writeTo: failed to write: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
        return true;
    }
}
