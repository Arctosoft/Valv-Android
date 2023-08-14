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
import android.provider.DocumentsContract;

public class CursorFile implements Comparable<CursorFile> {
    private final String name;
    private final Uri uri;
    private final long lastModified, size;
    private final String mimeType;
    private final boolean isDirectory;
    private String nameWithoutPrefix;

    public CursorFile(String name, Uri uri, long lastModified, String mimeType, long size) {
        this.name = name;
        this.uri = uri;
        this.mimeType = mimeType;
        this.size = size;
        this.isDirectory = DocumentsContract.Document.MIME_TYPE_DIR.equals(mimeType);
        this.lastModified = lastModified;
    }

    public void setNameWithoutPrefix(String nameWithoutPrefix) {
        this.nameWithoutPrefix = nameWithoutPrefix;
    }

    public long getSize() {
        return size;
    }

    public String getNameWithoutPrefix() {
        return nameWithoutPrefix;
    }

    public boolean isDirectory() {
        return isDirectory;
    }

    public String getName() {
        return name;
    }

    public Uri getUri() {
        return uri;
    }

    public long getLastModified() {
        return lastModified;
    }

    public String getMimeType() {
        return mimeType;
    }

    @Override
    public int compareTo(CursorFile o) {
        return Long.compare(o.lastModified, this.lastModified);
    }
}
