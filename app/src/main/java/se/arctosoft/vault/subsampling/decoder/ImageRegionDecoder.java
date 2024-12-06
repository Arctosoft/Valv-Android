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
import android.graphics.Point;
import android.graphics.Rect;
import android.net.Uri;

import androidx.annotation.NonNull;

/**
 * Interface for image decoding classes, allowing the default {@link android.graphics.BitmapRegionDecoder}
 * based on the Skia library to be replaced with a custom class.
 */
public interface ImageRegionDecoder {

    /**
     * Initialise the decoder. When possible, perform initial setup work once in this method. The
     * dimensions of the image must be returned. The URI can be in one of the following formats:
     * <br>
     * File: <code>file:///scard/picture.jpg</code>
     * <br>
     * Asset: <code>file:///android_asset/picture.png</code>
     * <br>
     * Resource: <code>android.resource://com.example.app/drawable/picture</code>
     *
     * @param context  Application context. A reference may be held, but must be cleared on recycle.
     * @param uri      URI of the image.
     * @param password
     * @param version
     * @return Dimensions of the image.
     * @throws Exception if initialisation fails.
     */
    @NonNull
    Point init(Context context, @NonNull Uri uri, char[] password, int version) throws Exception;

    /**
     * <p>
     * Decode a region of the image with the given sample size. This method is called off the UI
     * thread so it can safely load the image on the current thread. It is called from
     * {@link android.os.AsyncTask}s running in an executor that may have multiple threads, so
     * implementations must be thread safe. Adding <code>synchronized</code> to the method signature
     * is the simplest way to achieve this, but bear in mind the {@link #recycle()} method can be
     * called concurrently.
     * </p><p>
     * See {@link SkiaImageRegionDecoder} and {@link SkiaPooledImageRegionDecoder} for examples of
     * internal locking and synchronization.
     * </p>
     *
     * @param sRect      Source image rectangle to decode.
     * @param sampleSize Sample size.
     * @return The decoded region. It is safe to return null if decoding fails.
     */
    @NonNull
    Bitmap decodeRegion(@NonNull Rect sRect, int sampleSize);

    /**
     * Status check. Should return false before initialisation and after recycle.
     *
     * @return true if the decoder is ready to be used.
     */
    boolean isReady();

    /**
     * This method will be called when the decoder is no longer required. It should clean up any resources still in use.
     */
    void recycle();

}
