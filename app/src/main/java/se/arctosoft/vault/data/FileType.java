package se.arctosoft.vault.data;

import androidx.annotation.NonNull;

import se.arctosoft.vault.encryption.Encryption;

public enum FileType {
    IMAGE(0),
    VIDEO(1);

    public final int i;

    FileType(int i) {
        this.i = i;
    }

    public static FileType fromFilename(@NonNull String name) {
        if (name.contains(Encryption.PREFIX_IMAGE_FILE)) {
            return IMAGE;
        } else {
            return VIDEO;
        }
    }
}
