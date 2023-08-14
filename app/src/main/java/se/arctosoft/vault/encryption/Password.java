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

package se.arctosoft.vault.encryption;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.bumptech.glide.Glide;

import java.security.GeneralSecurityException;
import java.security.spec.KeySpec;
import java.util.Objects;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

import se.arctosoft.vault.LaunchActivity;
import se.arctosoft.vault.utils.FileStuff;
import se.arctosoft.vault.utils.Settings;

public class Password {
    private static final String TAG = "Password";

    public static void lock(Context context, @NonNull Settings settings) {
        Log.d(TAG, "lock");
        settings.clearTempPassword();
        FileStuff.deleteCache(context);
        Glide.get(context).clearMemory();
        //new Thread(() -> Glide.get(context).clearDiskCache()).start();
        LaunchActivity.GLIDE_KEY = System.currentTimeMillis();
    }
}
