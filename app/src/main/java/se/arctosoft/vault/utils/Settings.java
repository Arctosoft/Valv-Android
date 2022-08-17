package se.arctosoft.vault.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.documentfile.provider.DocumentFile;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import se.arctosoft.vault.encryption.Password;

public class Settings {
    private static final String TAG = "Settings";
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
        Log.e(TAG, "finalize: ");
        Password.lock(context, this);
        super.finalize();
    }

    @Nullable
    public char[] getTempPassword() {
        return password;
    }

    public boolean isLocked() {
        return password == null || password.length <= 0;
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

    public boolean addGalleryDirectory(@NonNull Uri uri) {
        List<String> directories = getGalleryDirectories();
        String uriString = uri.toString();
        if (directories.contains(uriString)) {
            Log.e(TAG, "addGalleryDirectory: uri already saved");
            return false;
        }
        directories.add(0, uriString);
        getSharedPrefsEditor().putString(PREF_DIRECTORIES, stringListAsString(directories)).apply();
        return true;
    }

    public void removeGalleryDirectory(@NonNull Uri uri) {
        List<String> directories = getGalleryDirectories();
        directories.remove(uri.toString());
        getSharedPrefsEditor().putString(PREF_DIRECTORIES, stringListAsString(directories)).apply();
    }

    @NonNull
    private String stringListAsString(@NonNull List<String> list) {
        if (list.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        Iterator<String> iterator = list.iterator();
        while (iterator.hasNext()) {
            sb.append(iterator.next());
            if (iterator.hasNext()) {
                sb.append("\n");
            }
        }
        //Log.e(TAG, "stringListAsString: " + sb);
        return sb.toString();
    }

    @NonNull
    public List<Uri> getGalleryDirectoriesAsUri() {
        List<String> directories = getGalleryDirectories();
        //Log.e(TAG, "getGalleryDirectoriesAsUri: " + directories.size());
        List<Uri> uris = new ArrayList<>(directories.size());
        for (String s : directories) {
            if (s != null) {
                uris.add(Uri.parse(s));
            }
        }
        return uris;
    }

    @NonNull
    public List<DocumentFile> getGalleryDirectoriesAsDocumentFile(Context context) {
        List<Uri> uris = getGalleryDirectoriesAsUri();
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
    private List<String> getGalleryDirectories() {
        String s = getSharedPrefs().getString(PREF_DIRECTORIES, null);
        List<String> uris = new ArrayList<>();
        if (s != null && !s.isEmpty()) {
            String[] split = s.split("\n");
            for (String value : split) {
                if (value != null && !value.isEmpty()) {
                    uris.add(value);
                }
            }
        }
        return uris;
    }
}
