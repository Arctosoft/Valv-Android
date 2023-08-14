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

package se.arctosoft.vault.encryption;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.TransferListener;

import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;

import javax.crypto.CipherInputStream;

import se.arctosoft.vault.exception.InvalidPasswordException;
import se.arctosoft.vault.utils.Settings;

@OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
public class MyDataSource implements DataSource {
    private static final String TAG = "MyDataSource";
    private final Context context;

    private Encryption.Streams streams;
    private Uri uri;

    public MyDataSource(@NonNull Context context) {
        this.context = context.getApplicationContext();
    }

    @Override
    public long open(@NonNull DataSpec dataSpec) throws IOException {
        uri = dataSpec.uri;
        try {
            InputStream fileStream = context.getContentResolver().openInputStream(uri);
            streams = Encryption.getCipherInputStream(fileStream, Settings.getInstance(context).getTempPassword(), false);
        } catch (GeneralSecurityException | InvalidPasswordException e) {
            e.printStackTrace();
            Log.e(TAG, "open error", e);
            return 0;
        }

        if (dataSpec.position != 0) {
            long skipped = forceSkip(dataSpec.position, (CipherInputStream) streams.getInputStream());
        }
        return dataSpec.length;
    }

    private long forceSkip(long skipBytes, CipherInputStream inputStream) throws IOException {
        long skipped = 0L;
        while (skipped < skipBytes) {
            inputStream.read();
            skipped++;
        }
        return skipped;
    }

    @Override
    public int read(@NonNull byte[] buffer, int offset, int length) throws IOException {
        if (length == 0) {
            return 0;
        }

        return streams.getInputStream().read(buffer, offset, length);
    }

    @Nullable
    @Override
    public Uri getUri() {
        return uri;
    }

    @Override
    public void close() {
        Log.d(TAG, "close: ");
        if (streams != null) {
            streams.close();
        }
    }

    @Override
    public void addTransferListener(@NonNull TransferListener transferListener) {
    }

}
