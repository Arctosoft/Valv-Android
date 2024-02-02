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

import android.content.Context;
import android.widget.Toast;

import androidx.annotation.NonNull;

import java.lang.ref.WeakReference;

public class Toaster {
    private static Toaster toaster;
    private static android.widget.Toast toast;
    private final WeakReference<Context> weakReference;

    private Toaster(@NonNull Context context) {
        weakReference = new WeakReference<>(context);
    }

    public static Toaster getInstance(@NonNull Context context) {
        if (toaster == null) {
            toaster = new Toaster(context.getApplicationContext());
        }
        return toaster;
    }

    public void showShort(@NonNull String message) {
        show(message, Toast.LENGTH_SHORT);
    }

    public void showLong(@NonNull String message) {
        show(message, Toast.LENGTH_LONG);
    }

    private void show(@NonNull String message, int duration) {
        if (toast != null) {
            toast.cancel();
        }
        toast = Toast.makeText(weakReference.get(), message, duration);
        toast.show();
    }

}