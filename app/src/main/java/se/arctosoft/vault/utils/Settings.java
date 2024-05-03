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

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import se.arctosoft.vault.data.StoredDirectory;
import se.arctosoft.vault.encryption.Password;
import se.arctosoft.vault.interfaces.IOnDirectoryAdded;

public class Settings {
    private static final String TAG = "Settings";
    private static final String SHARED_PREFERENCES_NAME = "prefs";
    private static final String PREF_DIRECTORIES = "p.gallery.dirs";
    private static final String PREF_SHOW_FILENAMES_IN_GRID = "p.gallery.fn";

    private final Context context;
    private static Settings settings;

    private char[] password = null;

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

    @Override
    protected void finalize() throws Throwable {
        Log.d(TAG, "finalize: ");
        Password.lock(context, this);
        super.finalize();
    }

    @Nullable
    public char[] getTempPassword() {
        return password;
    }

    public boolean isLocked() {
        return password == null || password.length == 0;
    }

    public void setTempPassword(@NonNull char[] password) {
        this.password = password;
    }

    public void clearTempPassword() {
        if (password != null) {
            Arrays.fill(password, (char) 0);
            password = null;
        }
    }

    public void addGalleryDirectory(@NonNull Uri uri, @Nullable IOnDirectoryAdded onDirectoryAdded) {
        List<StoredDirectory> directories = getGalleryDirectories(false);
        boolean isRootDir = true;
        Uri parentFolder = null;
        final String newLast = uri.getLastPathSegment() + "/";
        for (StoredDirectory storedDirectory : directories) {
            if (!storedDirectory.isRootDir()) {
                continue;
            }
            if (!uri.equals(storedDirectory.getUri()) && newLast.startsWith(storedDirectory.getUri().getLastPathSegment() + "/")) { // prevent adding a child of an already added folder
                isRootDir = false;
                parentFolder = storedDirectory.getUri();
                break;
            }
        }
        String uriString = uri.toString();
        StoredDirectory newDir = new StoredDirectory(uriString, isRootDir);
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
                onDirectoryAdded.onAlreadyExists(isRootDir);
            } else if (isRootDir) {
                onDirectoryAdded.onAddedAsRoot();
            } else {
                onDirectoryAdded.onAddedAsChildOf(parentFolder);
            }
        }
    }

    public void removeGalleryDirectory(@NonNull Uri uri) {
        List<StoredDirectory> directories = getGalleryDirectories(false);
        directories.remove(new StoredDirectory(uri.toString(), false));
        getSharedPrefsEditor().putString(PREF_DIRECTORIES, stringListAsString(directories)).apply();
    }

    public void removeGalleryDirectories(@NonNull List<Uri> uris) {
        List<StoredDirectory> directories = getGalleryDirectories(false);
        for (Uri u : uris) {
            directories.remove(new StoredDirectory(u.toString(), false));
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
