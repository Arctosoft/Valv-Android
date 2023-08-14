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

package se.arctosoft.vault.data;

import android.net.Uri;

import androidx.annotation.NonNull;

import java.util.Objects;

public class StoredDirectory {
    private final String uriString;
    private final Uri uri;
    private final boolean rootDir;

    public StoredDirectory(String uriString, boolean rootDir) {
        this.uriString = uriString;
        this.uri = Uri.parse(uriString);
        this.rootDir = rootDir;
    }

    public String getUriString() {
        return uriString;
    }

    public Uri getUri() {
        return uri;
    }

    public boolean isRootDir() {
        return rootDir;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        StoredDirectory that = (StoredDirectory) o;
        return uri.equals(that.uri);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uri);
    }

    @NonNull
    @Override
    public String toString() {
        return (rootDir ? '1' : '0') + uriString;
    }
}
