package se.arctosoft.vault.utils;

import androidx.annotation.NonNull;

import java.util.Random;

public class StringStuff {
    private static final String ALLOWED_CHARACTERS = "abcdefghijklmnopqrstuvwxyz0123456789-_";

    public static String getRandomFileName() {
        return getRandomFileName(20);
    }

    @NonNull
    public static String getRandomFileName(int length) {
        final Random random = new Random();
        final StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; ++i)
            sb.append(ALLOWED_CHARACTERS.charAt(random.nextInt(ALLOWED_CHARACTERS.length())));
        return sb.toString();
    }

}
