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

package se.arctosoft.vault.data;

import android.content.Context;
import android.icu.text.SimpleDateFormat;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

import se.arctosoft.vault.interfaces.IOnDone;
import se.arctosoft.vault.utils.FileStuff;

public class GalleryFile implements Comparable<GalleryFile> {
    private static final String TAG = "GalleryFile";
    private static final int FIND_FILES_NOT_STARTED = 0;
    private static final int FIND_FILES_RUNNING = 1;
    private static final int FIND_FILES_DONE = 2;

    private final AtomicInteger findFilesInDirectoryStatus = new AtomicInteger(FIND_FILES_NOT_STARTED);
    private GalleryFile firstFileInDirectoryWithThumb;

    private final FileType fileType;
    private final String encryptedName, name;
    private final boolean isDirectory, isAllFolder;
    private final long lastModified, size;
    private final int version;
    private Uri fileUri;
    private Uri thumbUri, noteUri, decryptedCacheUri;
    private String originalName, nameWithPath, note, text;
    private int fileCount, orientation;

    private GalleryFile(String name) {
        this.fileUri = null;
        this.encryptedName = name;
        this.name = name;
        this.thumbUri = null;
        this.noteUri = null;
        this.decryptedCacheUri = null;
        this.lastModified = Long.MAX_VALUE;
        this.isDirectory = true;
        this.fileType = FileType.DIRECTORY;
        this.version = fileType.version;
        this.size = -1;
        this.isAllFolder = true;
        this.orientation = -1;
    }

    private GalleryFile(String name, String text) {
        this.fileUri = null;
        this.encryptedName = name;
        this.name = name;
        this.thumbUri = null;
        this.noteUri = null;
        this.decryptedCacheUri = null;
        this.lastModified = Long.MAX_VALUE;
        this.isDirectory = false;
        this.fileType = FileType.TEXT_V2;
        this.version = fileType.version;
        this.size = text.getBytes(StandardCharsets.UTF_8).length;
        this.isAllFolder = false;
        this.text = text;
        this.orientation = -1;
    }

    private GalleryFile(@NonNull CursorFile file, @Nullable CursorFile thumb, @Nullable CursorFile note) {
        this.fileUri = file.getUri();
        this.encryptedName = file.getName();
        this.thumbUri = thumb == null ? null : thumb.getUri();
        this.noteUri = note == null ? null : note.getUri();
        this.decryptedCacheUri = null;
        this.lastModified = file.getLastModified();
        this.isDirectory = false;
        this.fileType = FileType.fromFilename(encryptedName);
        this.version = fileType.version;
        this.size = file.getSize();
        this.isAllFolder = false;
        this.name = FileStuff.getNameWithoutPrefix(encryptedName);
        this.orientation = -1;
    }

    private GalleryFile(@NonNull Uri fileUri) {
        this.fileUri = fileUri;
        this.encryptedName = FileStuff.getFilenameFromUri(fileUri, false);
        this.name = encryptedName;
        this.thumbUri = null;
        this.noteUri = null;
        this.decryptedCacheUri = null;
        this.lastModified = System.currentTimeMillis();
        this.isDirectory = true;
        this.fileType = FileType.fromFilename(encryptedName);
        this.version = fileType.version;
        this.size = 0;
        this.isAllFolder = false;
        this.orientation = -1;
    }

    private GalleryFile(@NonNull CursorFile file) {
        this.fileUri = file.getUri();
        this.encryptedName = file.getName();
        this.name = encryptedName;
        this.thumbUri = null;
        this.noteUri = null;
        this.decryptedCacheUri = null;
        this.lastModified = file.getLastModified();
        this.isDirectory = true;
        this.fileType = FileType.DIRECTORY;
        this.version = fileType.version;
        this.size = 0;
        this.isAllFolder = false;
        this.orientation = -1;
    }

    public static GalleryFile asDirectory(Uri directoryUri) {
        return new GalleryFile(directoryUri);
    }

    public static GalleryFile asDirectory(CursorFile cursorFile) {
        return new GalleryFile(cursorFile);
    }

    public static GalleryFile asFile(CursorFile cursorFile, @Nullable CursorFile thumbUri, @Nullable CursorFile noteUri) {
        return new GalleryFile(cursorFile, thumbUri, noteUri);
    }

    public static GalleryFile asTempText(String text) {
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        return new GalleryFile(simpleDateFormat.format(new Date()), text);
    }

    public static GalleryFile asAllFolder(String name) {
        return new GalleryFile(name);
    }

    public void setOriginalName(String originalName) {
        this.originalName = originalName;
    }

    @Nullable
    public String getOriginalName() {
        return originalName;
    }

    public void setDecryptedCacheUri(Uri decryptedCacheUri) {
        this.decryptedCacheUri = decryptedCacheUri;
    }

    public int getVersion() {
        return version;
    }

    public void setOrientation(int orientation) {
        this.orientation = orientation;
    }

    public int getOrientation() {
        return orientation;
    }

    public AtomicInteger getFindFilesInDirectoryStatus() {
        return findFilesInDirectoryStatus;
    }

    public boolean isVideo() {
        return fileType.type == FileType.TYPE_VIDEO;
    }

    public boolean isGif() {
        return fileType.type == FileType.TYPE_GIF;
    }

    public boolean isText() {
        return fileType.type == FileType.TYPE_TEXT;
    }

    public long getSize() {
        return size;
    }

    @Nullable
    public Uri getDecryptedCacheUri() {
        return decryptedCacheUri;
    }


    public String getNameWithPath() {
        if (isAllFolder) {
            return name;
        }
        if (nameWithPath == null) {
            nameWithPath = FileStuff.getFilenameWithPathFromUri(fileUri);
        }
        return nameWithPath;
    }

    public String getName() {
        return (originalName != null && !originalName.isEmpty()) ? originalName : name;
    }

    public String getEncryptedName() {
        return encryptedName;
    }

    public Uri getUri() {
        return fileUri;
    }

    @Nullable
    public Uri getThumbUri() {
        return thumbUri;
    }

    public void setThumbUri(Uri thumbUri) {
        this.thumbUri = thumbUri;
    }

    @Nullable
    public Uri getNoteUri() {
        return noteUri;
    }

    public void setNoteUri(Uri noteUri) {
        this.noteUri = noteUri;
    }

    public void setFileUri(@NonNull Uri fileUri) {
        this.fileUri = fileUri;
    }

    @Nullable
    public String getNote() {
        return note;
    }

    public void setNote(@Nullable String note) {
        this.note = note;
    }

    @Nullable
    public String getText() {
        return text;
    }

    public void setText(@Nullable String text) {
        this.text = text;
    }

    public boolean hasThumb() {
        return thumbUri != null;
    }

    public boolean hasNote() {
        return noteUri != null || note != null;
    }

    public FileType getFileType() {
        return fileType;
    }

    public boolean isAllFolder() {
        return isAllFolder;
    }

    public boolean isDirectory() {
        return isDirectory;
    }

    public long getLastModified() {
        return lastModified;
    }

    @Nullable
    public GalleryFile getFirstFile() {
        return firstFileInDirectoryWithThumb;
    }

    public int getFileCount() {
        return fileCount;
    }

    public void findFilesInDirectory(Context context, IOnDone onDone) {
        if (!isDirectory || fileUri == null || !findFilesInDirectoryStatus.compareAndSet(FIND_FILES_NOT_STARTED, FIND_FILES_RUNNING)) {
            return;
        }
        new Thread(() -> {
            List<GalleryFile> galleryFiles = FileStuff.getFilesInFolder(context, fileUri);
            this.fileCount = 0;
            this.firstFileInDirectoryWithThumb = null;
            for (GalleryFile f : galleryFiles) {
                if (!f.isDirectory() && f.hasThumb()) {
                    this.firstFileInDirectoryWithThumb = f;
                    break;
                }
            }
            this.fileCount = galleryFiles.size();
            findFilesInDirectoryStatus.set(FIND_FILES_DONE);
            if (onDone != null) {
                onDone.onDone();
            }
        }).start();
    }

    public void resetFilesInDirectory() {
        findFilesInDirectoryStatus.set(FIND_FILES_NOT_STARTED);
    }

    @Override
    public int compareTo(GalleryFile o) {
        return Long.compare(o.lastModified, this.lastModified);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GalleryFile that = (GalleryFile) o;
        return isDirectory == that.isDirectory && isAllFolder == that.isAllFolder && lastModified == that.lastModified && size == that.size && fileType == that.fileType && Objects.equals(fileUri, that.fileUri);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fileType, isDirectory, isAllFolder, lastModified, size, version, fileUri, encryptedName);
    }
}
