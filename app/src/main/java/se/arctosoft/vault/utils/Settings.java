/*
 * Valv-Android
 * Copyright (C) 2024 Arctosoft AB
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

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import se.arctosoft.vault.data.StoredDirectory;
import se.arctosoft.vault.interfaces.IOnDirectoryAdded;

public class Settings {
    private static final String TAG = "Settings";
    private static final String SHARED_PREFERENCES_NAME = "prefs";
    private static final String PREF_DIRECTORIES = "p.gallery.dirs";
    private static final String PREF_SHOW_FILENAMES_IN_GRID = "p.gallery.fn";
    public static final String PREF_ENCRYPTION_ITERATION_COUNT = "encryption_iteration_count";
    public static final String PREF_ENCRYPTION_USE_DISK_CACHE = "encryption_use_disk_cache";
    public static final String PREF_ENCRYPTION_DELETE_BY_DEFAULT = "encryption_delete_by_default";
    public static final String PREF_ENCRYPTION_DISPLAY_DECRYPTABLE_ONLY = "encryption_display_decryptable_only";
    public static final String PREF_APP_SECURE = "app_secure";
    public static final String PREF_APP_EDIT_FOLDERS = "app_edit_folders";
    public static final String PREF_APP_EXIT_ON_LOCK = "app_exit_on_lock";

    private final Context context;
    private static Settings settings;

    public static Settings getInstance(@NonNull Context context) {
        if (settings == null) {
            settings = new Settings(context);
        }
        return settings;
    }

    private Settings(@NonNull Context context) {
        this.context = context.getApplicationContext();
    }

    public SharedPreferences getSharedPrefs() {
        return context.getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE);
    }

    private SharedPreferences.Editor getSharedPrefsEditor() {
        return context.getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE).edit();
    }

    public int getIterationCount() {
        return getSharedPrefs().getInt(PREF_ENCRYPTION_ITERATION_COUNT, 50000);
    }

    public void setIterationCount(int iterationCount) {
        getSharedPrefsEditor().putInt(PREF_ENCRYPTION_ITERATION_COUNT, iterationCount).apply();
    }

    public boolean useDiskCache() {
        return getSharedPrefs().getBoolean(PREF_ENCRYPTION_USE_DISK_CACHE, true);
    }

    public void setUseDiskCache(boolean useDiskCache) {
        getSharedPrefsEditor().putBoolean(PREF_ENCRYPTION_USE_DISK_CACHE, useDiskCache).apply();
    }

    public boolean isDeleteByDefault() {
        return getSharedPrefs().getBoolean(PREF_ENCRYPTION_DELETE_BY_DEFAULT, false);
    }

    public void setDeleteByDefault(boolean deleteByDefault) {
        getSharedPrefsEditor().putBoolean(PREF_ENCRYPTION_DELETE_BY_DEFAULT, deleteByDefault).apply();
    }

    public boolean displayDecryptableFilesOnly() {
        return getSharedPrefs().getBoolean(PREF_ENCRYPTION_DISPLAY_DECRYPTABLE_ONLY, false);
    }

    public void setDisplayDecryptableFilesOnly(boolean displayDecryptableFilesOnly) {
        getSharedPrefsEditor().putBoolean(PREF_ENCRYPTION_DISPLAY_DECRYPTABLE_ONLY, displayDecryptableFilesOnly).apply();
    }

    public boolean isSecureFlag() {
        return getSharedPrefs().getBoolean(PREF_APP_SECURE, true);
    }

    public void setSecureFlag(boolean secureFlag) {
        getSharedPrefsEditor().putBoolean(PREF_APP_SECURE, secureFlag).apply();
    }

    public boolean exitOnLock() {
        return getSharedPrefs().getBoolean(PREF_APP_EXIT_ON_LOCK, true);
    }

    public void setExitOnLock(boolean exitOnLock) {
        getSharedPrefsEditor().putBoolean(PREF_APP_EXIT_ON_LOCK, exitOnLock).apply();
    }

    public void addGalleryDirectory(@NonNull Uri uri, boolean asRootDir, @Nullable IOnDirectoryAdded onDirectoryAdded) {
        List<StoredDirectory> directories = getGalleryDirectories(false);
        StoredDirectory newDir = new StoredDirectory(uri, asRootDir);
        boolean reordered = false;
        if (directories.contains(newDir)) {
            Log.d(TAG, "addGalleryDirectory: uri already saved");
            if (directories.remove(newDir)) {
                directories.add(0, newDir);
                reordered = true;
            }
        } else {
            directories.add(0, newDir);
        }
        getSharedPrefsEditor().putString(PREF_DIRECTORIES, stringListAsString(directories)).apply();
        if (onDirectoryAdded != null) {
            if (reordered) {
                onDirectoryAdded.onAlreadyExists();
            } else if (asRootDir) {
                onDirectoryAdded.onAddedAsRoot();
            } else {
                onDirectoryAdded.onAdded();
            }
        }
    }

    public void removeGalleryDirectory(@NonNull Uri uri) {
        List<StoredDirectory> directories = getGalleryDirectories(false);
        String[] split = uri.toString().split("/document/");
        directories.remove(new StoredDirectory(split[0], false));
        directories.remove(new StoredDirectory(uri, false));
        getSharedPrefsEditor().putString(PREF_DIRECTORIES, stringListAsString(directories)).apply();
    }

    public void removeGalleryDirectories(@NonNull List<Uri> uris) {
        List<StoredDirectory> directories = getGalleryDirectories(false);
        for (Uri u : uris) {
            directories.remove(new StoredDirectory(u, false));
        }
        getSharedPrefsEditor().putString(PREF_DIRECTORIES, stringListAsString(directories)).apply();
    }

    @NonNull
    private String stringListAsString(@NonNull List<StoredDirectory> list) {
        if (list.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        Iterator<StoredDirectory> iterator = list.iterator();
        while (iterator.hasNext()) {
            sb.append(iterator.next().toString());
            if (iterator.hasNext()) {
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    @NonNull
    public List<Uri> getGalleryDirectoriesAsUri(boolean rootDirsOnly) {
        List<StoredDirectory> directories = getGalleryDirectories(rootDirsOnly);
        List<Uri> uris = new ArrayList<>(directories.size());
        for (StoredDirectory s : directories) {
            if (s != null) {
                uris.add(s.getUri());
            }
        }
        return uris;
    }

    @NonNull
    private List<StoredDirectory> getGalleryDirectories(boolean rootDirsOnly) {
        String s = getSharedPrefs().getString(PREF_DIRECTORIES, null);
        List<StoredDirectory> storedDirectories = new ArrayList<>();
        if (s != null && !s.isEmpty()) {
            String[] split = s.split("\n");
            for (String value : split) {
                if (value != null && !value.isEmpty()) {
                    boolean isRootDir = value.charAt(0) == '1';
                    if (!rootDirsOnly || isRootDir) {
                        storedDirectories.add(new StoredDirectory(value.substring(1), isRootDir));
                    }
                }
            }
        }
        return storedDirectories;
    }

    public void setShowFilenames(boolean show) {
        getSharedPrefsEditor().putBoolean(PREF_SHOW_FILENAMES_IN_GRID, show).apply();
    }

    public boolean showFilenames() {
        return getSharedPrefs().getBoolean(PREF_SHOW_FILENAMES_IN_GRID, true);
    }
}
