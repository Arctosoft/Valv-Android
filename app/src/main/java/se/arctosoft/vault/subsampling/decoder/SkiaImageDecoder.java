/*
 * Valv-Android
 * Copyright (c) 2024 Arctosoft AB.
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
 * You should have received a copy of the GNU General Public License along with this program.  If not, see https://www.gnu.org/licenses/.
 */

package se.arctosoft.vault.subsampling.decoder;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import se.arctosoft.vault.encryption.Encryption;
import se.arctosoft.vault.subsampling.MySubsamplingScaleImageView;

/**
 * Default implementation of {@link com.davemorrissey.labs.subscaleview.decoder.ImageDecoder}
 * using Android's {@link android.graphics.BitmapFactory}, based on the Skia library. This
 * works well in most circumstances and has reasonable performance, however it has some problems
 * with grayscale, indexed and CMYK images.
 */
public class SkiaImageDecoder implements ImageDecoder {
    private final Bitmap.Config bitmapConfig;

    @Keep
    @SuppressWarnings("unused")
    public SkiaImageDecoder() {
        this(null);
    }

    @SuppressWarnings({"WeakerAccess", "SameParameterValue"})
    public SkiaImageDecoder(@Nullable Bitmap.Config bitmapConfig) {
        Bitmap.Config globalBitmapConfig = MySubsamplingScaleImageView.getPreferredBitmapConfig();
        if (bitmapConfig != null) {
            this.bitmapConfig = bitmapConfig;
        } else if (globalBitmapConfig != null) {
            this.bitmapConfig = globalBitmapConfig;
        } else {
            this.bitmapConfig = Bitmap.Config.RGB_565;
        }
    }

    @Override
    @NonNull
    public Bitmap decode(Context context, @NonNull Uri uri, char[] password, int version) throws Exception {
        BitmapFactory.Options options = new BitmapFactory.Options();
        Bitmap bitmap;
        options.inPreferredConfig = bitmapConfig;
        Encryption.Streams streams = null;
        try {
            ContentResolver contentResolver = context.getContentResolver();
            streams = Encryption.getCipherInputStream(contentResolver.openInputStream(uri), password, false, version);
            bitmap = BitmapFactory.decodeStream(streams.getInputStream(), null, options);
        } finally {
            if (streams != null) {
                streams.close();
            }
        }
        if (bitmap == null) {
            throw new RuntimeException("Skia image region decoder returned null bitmap - image format may not be supported");
        }
        return bitmap;
    }
}
