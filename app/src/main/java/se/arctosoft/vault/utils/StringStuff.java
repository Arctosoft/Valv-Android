package se.arctosoft.vault.utils;

import android.icu.text.DecimalFormat;

public class StringStuff {

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
