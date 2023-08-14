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

package se.arctosoft.vault.utils;

import android.icu.text.DecimalFormat;

import androidx.annotation.NonNull;

import java.util.Random;

public class StringStuff {
    private static final String ALLOWED_CHARACTERS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_";
    private static final int NAME_LENGTH = 32;

    public static String getRandomFileName() {
        return getRandomFileName(NAME_LENGTH);
    }

    @NonNull
    public static String getRandomFileName(int length) {
        final Random random = new Random();
        final StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; ++i) {
            sb.append(ALLOWED_CHARACTERS.charAt(random.nextInt(ALLOWED_CHARACTERS.length())));
        }
        return sb.toString();
    }

    public static String bytesToReadableString(long bytes) {
        final DecimalFormat decimalFormat = new DecimalFormat("0.00");
        if (bytes < 1000) {
            return decimalFormat.format(bytes + 0.0) + " B";
        } else if (bytes < 1000000) {
            return decimalFormat.format(bytes / 1000.0) + " kB";
        } else {
            return decimalFormat.format(bytes / 1000000.0) + " MB";
        }
    }

}
