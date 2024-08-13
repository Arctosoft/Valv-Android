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

package se.arctosoft.vault.data;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import se.arctosoft.vault.utils.FileStuff;

public class GalleryFile implements Comparable<GalleryFile> {
    private static final String TAG = "GalleryFile";
    private final FileType fileType;
    private final String encryptedName, name;
    private final boolean isDirectory, isAllFolder;
    private Uri fileUri;
    private final long lastModified, size;
    private Uri thumbUri, noteUri, decryptedCacheUri;
    private List<GalleryFile> filesInDirectory;
    private String originalName, nameWithPath, note, text;

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
        this.size = -1;
        this.isAllFolder = true;
    }

    private GalleryFile(@NonNull CursorFile file, @Nullable CursorFile thumb, @Nullable CursorFile note) {
        this.fileUri = file.getUri();
        this.encryptedName = file.getName();
        this.name = encryptedName.split("-", 2)[1];
        this.thumbUri = thumb == null ? null : thumb.getUri();
        this.noteUri = note == null ? null : note.getUri();
        this.decryptedCacheUri = null;
        this.lastModified = file.getLastModified();
        this.isDirectory = false;
        this.fileType = FileType.fromFilename(encryptedName);
        this.size = file.getSize();
        this.isAllFolder = false;
    }

    private GalleryFile(@NonNull Uri fileUri, List<GalleryFile> filesInDirectory) {
        this.fileUri = fileUri;
        this.encryptedName = FileStuff.getFilenameFromUri(fileUri, false);
        this.name = encryptedName;
        this.thumbUri = null;
        this.noteUri = null;
        this.decryptedCacheUri = null;
        this.lastModified = System.currentTimeMillis();
        this.isDirectory = true;
        this.fileType = FileType.fromFilename(encryptedName);
        this.filesInDirectory = filesInDirectory;
        this.size = 0;
        this.isAllFolder = false;
    }

    private GalleryFile(@NonNull CursorFile file, List<GalleryFile> filesInDirectory) {
        this.fileUri = file.getUri();
        this.encryptedName = file.getName();
        this.name = encryptedName;
        this.thumbUri = null;
        this.noteUri = null;
        this.decryptedCacheUri = null;
        this.lastModified = file.getLastModified();
        this.isDirectory = true;
        this.fileType = FileType.DIRECTORY;
        this.filesInDirectory = filesInDirectory;
        this.size = 0;
        this.isAllFolder = false;
    }

    public static GalleryFile asDirectory(Uri directoryUri, List<GalleryFile> filesInDirectory) {
        return new GalleryFile(directoryUri, filesInDirectory);
    }

    public static GalleryFile asDirectory(CursorFile cursorFile, List<GalleryFile> filesInDirectory) {
        return new GalleryFile(cursorFile, filesInDirectory);
    }

    public static GalleryFile asFile(CursorFile cursorFile, @Nullable CursorFile thumbUri, @Nullable CursorFile noteUri) {
        return new GalleryFile(cursorFile, thumbUri, noteUri);
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

    public boolean isVideo() {
        return FileType.VIDEO == fileType;
    }

    public boolean isGif() {
        return FileType.GIF == fileType;
    }

    public boolean isText() {
        return FileType.TEXT == fileType;
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
    public List<GalleryFile> getFilesInDirectory() {
        return filesInDirectory;
    }

    @Nullable
    public GalleryFile getFirstFile() {
        if (filesInDirectory == null || filesInDirectory.isEmpty()) {
            return null;
        }
        for (GalleryFile g : filesInDirectory) {
            if (!g.isDirectory() && g.hasThumb()) {
                return g;
            }
        }
        return null;
    }

    public int getFileCount() {
        return filesInDirectory == null ? 0 : filesInDirectory.size();
    }

    public void setFilesInDirectory(List<GalleryFile> filesInDirectory) {
        if (this.filesInDirectory != null) {
            this.filesInDirectory.clear();
        } else {
            this.filesInDirectory = new ArrayList<>(filesInDirectory.size());
        }
        this.filesInDirectory.addAll(filesInDirectory);
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
        return Objects.hash(fileUri);
    }
}
