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

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;

import androidx.annotation.NonNull;

/**
 * Interface for image decoding classes, allowing the default {@link android.graphics.BitmapFactory}
 * based on the Skia library to be replaced with a custom class.
 */
public interface ImageDecoder {

    /**
     * Decode an image. The URI can be in one of the following formats:
     * <br>
     * File: <code>file:///scard/picture.jpg</code>
     * <br>
     * Asset: <code>file:///android_asset/picture.png</code>
     * <br>
     * Resource: <code>android.resource://com.example.app/drawable/picture</code>
     *
     * @param context  Application context
     * @param uri      URI of the image
     * @param password
     * @param version
     * @return the decoded bitmap
     * @throws Exception if decoding fails.
     */
    @NonNull
    Bitmap decode(Context context, @NonNull Uri uri, char[] password, int version) throws Exception;

}