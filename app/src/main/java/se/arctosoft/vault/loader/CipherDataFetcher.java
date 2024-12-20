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

package se.arctosoft.vault.loader;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;

import com.bumptech.glide.Priority;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.data.DataFetcher;

import org.json.JSONException;

import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;

import se.arctosoft.vault.data.FileType;
import se.arctosoft.vault.data.Password;
import se.arctosoft.vault.encryption.Encryption;
import se.arctosoft.vault.exception.InvalidPasswordException;

public class CipherDataFetcher implements DataFetcher<InputStream> {
    private static final String TAG = "CipherDataFetcher";
    private Encryption.Streams streams;
    private final Context context;
    private final Uri uri;
    private final int version;
    private final Password password;

    public CipherDataFetcher(@NonNull Context context, Uri uri, int version) {
        this.context = context.getApplicationContext();
        this.uri = uri;
        this.version = version;
        this.password = Password.getInstance();
    }

    @Override
    public void loadData(@NonNull Priority priority, @NonNull DataCallback<? super InputStream> callback) {
        try {
            InputStream inputStream = context.getContentResolver().openInputStream(uri);
            streams = Encryption.getCipherInputStream(
                    inputStream,
                    password.getPassword(),
                    version > 1 || !uri.getLastPathSegment().contains(FileType.GIF_V1.suffixPrefix), // don't load as thumb for GIF file
                    version
            );
            callback.onDataReady(streams.getInputStream());
        } catch (GeneralSecurityException | IOException | InvalidPasswordException |
                 JSONException e) {
            //e.printStackTrace();
            callback.onLoadFailed(e);
        }
    }

    @Override
    public void cleanup() {
        cancel();
    }

    @Override
    public void cancel() {
        if (streams != null) {
            streams.close(); // interrupts decode if any
        }
    }

    @NonNull
    @Override
    public Class<InputStream> getDataClass() {
        return InputStream.class;
    }

    @NonNull
    @Override
    public DataSource getDataSource() {
        return DataSource.LOCAL;
    }
}
