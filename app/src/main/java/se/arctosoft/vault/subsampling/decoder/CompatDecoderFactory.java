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

import android.graphics.Bitmap;
import androidx.annotation.NonNull;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

/**
 * Compatibility factory to instantiate decoders with empty public constructors.
 * @param <T> The base type of the decoder this factory will produce.
 */
@SuppressWarnings("WeakerAccess")
public class CompatDecoderFactory<T> implements DecoderFactory<T> {

    private final Class<? extends T> clazz;
    private final Bitmap.Config bitmapConfig;

    /**
     * Construct a factory for the given class. This must have a default constructor.
     * @param clazz a class that implements {@link ImageDecoder} or {@link com.davemorrissey.labs.subscaleview.decoder.ImageRegionDecoder}.
     */
    public CompatDecoderFactory(@NonNull Class<? extends T> clazz) {
        this(clazz, null);
    }

    /**
     * Construct a factory for the given class. This must have a constructor that accepts a {@link Bitmap.Config} instance.
     * @param clazz a class that implements {@link ImageDecoder} or {@link ImageRegionDecoder}.
     * @param bitmapConfig bitmap configuration to be used when loading images.
     */
    public CompatDecoderFactory(@NonNull Class<? extends T> clazz, Bitmap.Config bitmapConfig) {
        this.clazz = clazz;
        this.bitmapConfig = bitmapConfig;
    }

    @Override
    @NonNull
    public T make() throws IllegalAccessException, InstantiationException, NoSuchMethodException, InvocationTargetException {
        if (bitmapConfig == null) {
            return clazz.newInstance();
        } else {
            Constructor<? extends T> ctor = clazz.getConstructor(Bitmap.Config.class);
            return ctor.newInstance(bitmapConfig);
        }
    }

}
