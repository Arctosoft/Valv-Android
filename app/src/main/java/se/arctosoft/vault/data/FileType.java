package se.arctosoft.vault.data;

import androidx.annotation.NonNull;
import androidx.documentfile.provider.DocumentFile;

import se.arctosoft.vault.encryption.Encryption;

public enum FileType {
    FOLDER(0),
    IMAGE(1),
    VIDEO(2);

    public final int i;

    FileType(int i) {
        this.i = i;
    }

    public static FileType fromDocument(@NonNull DocumentFile doc) {
        if (doc.isDirectory()) {
            return FOLDER;
        } else {
            if (doc.getName().contains(Encryption.PREFIX_IMAGE_FILE)) {
                return IMAGE;
            } else {
                return VIDEO;
            }
        }
    }
}
