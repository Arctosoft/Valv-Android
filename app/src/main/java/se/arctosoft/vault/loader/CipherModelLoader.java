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

package se.arctosoft.vault.loader;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.load.Options;
import com.bumptech.glide.load.model.ModelLoader;
import com.bumptech.glide.signature.ObjectKey;

import java.io.InputStream;

import se.arctosoft.vault.encryption.Encryption;

public class CipherModelLoader implements ModelLoader<Uri, InputStream> {
    private final Context context;

    public CipherModelLoader(@NonNull Context context) {
        this.context = context.getApplicationContext();
    }

    @Nullable
    @Override
    public LoadData<InputStream> buildLoadData(@NonNull Uri uri, int width, int height, @NonNull Options options) {
        return new LoadData<>(new ObjectKey(uri), new CipherDataFetcher(context, uri));
    }

    @Override
    public boolean handles(@NonNull Uri uri) {
        String lastSegment = uri.getLastPathSegment();
        return lastSegment != null && lastSegment.toLowerCase().contains("/" + Encryption.ENCRYPTED_PREFIX);
    }

}
