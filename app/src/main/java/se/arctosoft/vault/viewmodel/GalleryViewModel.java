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

package se.arctosoft.vault.viewmodel;

import androidx.annotation.Nullable;
import androidx.documentfile.provider.DocumentFile;
import androidx.lifecycle.ViewModel;

import java.util.List;

public class GalleryViewModel extends ViewModel {
    private List<DocumentFile> filesToAdd;
    private String textToImport;

    public void setFilesToAdd(@Nullable List<DocumentFile> filesToAdd) {
        this.filesToAdd = filesToAdd;
    }

    @Nullable
    public List<DocumentFile> getFilesToAdd() {
        return filesToAdd;
    }

    public void setTextToImport(String textToImport) {
        this.textToImport = textToImport;
    }

    @Nullable
    public String getTextToImport() {
        return textToImport;
    }
}
