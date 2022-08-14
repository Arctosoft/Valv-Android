package se.arctosoft.vault.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.documentfile.provider.DocumentFile;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import se.arctosoft.vault.encryption.Password;

public class Settings {
    private static final String SHARED_PREFERENCES_NAME = "prefs";
    private static final String PREF_DIRECTORIES = "p.gallery.dirs";
    private static final String PREF_PASSWORD_HASH = "p.p";

    private final Context context;
    private static Settings settings;
    // TODO add e.g. "hello" to encrypted file and check it on decrypt. If it does not say "hello" the supplied password is incorrect

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
        Password.lock(context, this);
        super.finalize();
    }

    @Nullable
    public char[] getTempPassword() {
        return password;
    }

    public boolean isUnlocked() {
        return password != null && password.length >= 8;
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

    @Nullable
    public String getPasswordHash() {
        return getSharedPrefs().getString(PREF_PASSWORD_HASH, null);
    }

    public void setPasswordHash(@Nullable String hash) {
        getSharedPrefsEditor().putString(PREF_PASSWORD_HASH, hash).apply();
    }

    public void addGalleryDirectory(@NonNull Uri uri) {
        Set<String> directories = getGalleryDirectories();
        directories.add(uri.toString());
        getSharedPrefsEditor().putStringSet(PREF_DIRECTORIES, directories).apply();
    }

    @NonNull
    public Set<Uri> getGalleryDirectoriesAsUri() {
        Set<String> stringSet = getGalleryDirectories();
        Set<Uri> uris = new HashSet<>();
        for (String s : stringSet) {
            if (s != null) {
                uris.add(Uri.parse(s));
            }
        }
        return uris;
    }

    @NonNull
    public List<DocumentFile> getGalleryDirectoriesAsDocumentFile(Context context) {
        Set<Uri> uris = getGalleryDirectoriesAsUri();
        List<DocumentFile> documentFiles = new ArrayList<>(uris.size());
        for (Uri uri : uris) {
            DocumentFile pickedDir = DocumentFile.fromTreeUri(context, uri);
            if (pickedDir != null && pickedDir.isDirectory()) {
                documentFiles.add(pickedDir);
            }
        }
        return documentFiles;
    }

    @NonNull
    public Set<String> getGalleryDirectories() {
        return getSharedPrefs().getStringSet(PREF_DIRECTORIES, new HashSet<>());
    }
}
