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

import androidx.annotation.NonNull;

import java.lang.reflect.InvocationTargetException;

/**
 * Interface for {@link ImageDecoder} and {@link ImageRegionDecoder} factories.
 * @param <T> the class of decoder that will be produced.
 */
public interface DecoderFactory<T> {

    /**
     * Produce a new instance of a decoder with type {@link T}.
     * @return a new instance of your decoder.
     * @throws IllegalAccessException if the factory class cannot be instantiated.
     * @throws InstantiationException if the factory class cannot be instantiated.
     * @throws NoSuchMethodException if the factory class cannot be instantiated.
     * @throws InvocationTargetException if the factory class cannot be instantiated.
     */
    @NonNull
    T make() throws IllegalAccessException, InstantiationException, NoSuchMethodException, InvocationTargetException;

}