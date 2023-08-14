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

import androidx.annotation.NonNull;

import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.signature.ObjectKey;

import se.arctosoft.vault.LaunchActivity;

public class GlideStuff {

    @NonNull
    public static RequestOptions getRequestOptions() {
        return new RequestOptions()
                .signature(new ObjectKey(LaunchActivity.GLIDE_KEY));
    }

}
