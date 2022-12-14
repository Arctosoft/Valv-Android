package se.arctosoft.vault.encryption;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.TransferListener;

import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;

import javax.crypto.CipherInputStream;

import se.arctosoft.vault.exception.InvalidPasswordException;
import se.arctosoft.vault.utils.Settings;

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
