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

import static android.provider.DocumentsContract.Document.MIME_TYPE_DIR;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import se.arctosoft.vault.encryption.Encryption;

public enum FileType {
    DIRECTORY(0, null),
    IMAGE(1, Encryption.PREFIX_IMAGE_FILE),
    GIF(2, Encryption.PREFIX_GIF_FILE),
    VIDEO(3, Encryption.PREFIX_VIDEO_FILE);

    public final int i;
    public final String encryptionPrefix;

    FileType(int i, String encryptionPrefix) {
        this.i = i;
        this.encryptionPrefix = encryptionPrefix;
    }

    public static FileType fromFilename(@NonNull String name) {
        if (name.contains(Encryption.PREFIX_IMAGE_FILE)) {
            return IMAGE;
        } else if (name.contains(Encryption.PREFIX_GIF_FILE)) {
            return GIF;
        } else if (name.contains(Encryption.PREFIX_VIDEO_FILE)) {
            return VIDEO;
        } else {
            return DIRECTORY;
        }
    }

    public static FileType fromMimeType(@Nullable String mimeType) {
        if (mimeType == null) {
            return FileType.IMAGE;
        } else if (mimeType.equals("image/gif")) {
            return FileType.GIF;
        } else if (mimeType.equals(MIME_TYPE_DIR)) {
            return FileType.DIRECTORY;
        } else if (mimeType.startsWith("image/")) {
            return FileType.IMAGE;
        } else {
            return FileType.VIDEO;
        }
    }
}
