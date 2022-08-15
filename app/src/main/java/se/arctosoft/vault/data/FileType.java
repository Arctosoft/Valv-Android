package se.arctosoft.vault.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import se.arctosoft.vault.encryption.Encryption;

public enum FileType {
    FOLDER(0, null),
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
        } else {
            return VIDEO;
        }
    }

    public static FileType fromMimeType(@Nullable String mimeType) {
        if (mimeType == null) {
            return FileType.IMAGE;
        } else if (mimeType.equals("image/gif")) {
            return FileType.GIF;
        } else if (mimeType.startsWith("image/")) {
            return FileType.IMAGE;
        } else {
            return FileType.VIDEO;
        }
    }
}
