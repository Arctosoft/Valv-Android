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

public class ChaChaDataSource implements DataSource {
    private static final String TAG = "ChaChaDataSource";
    private final Context context;

    private Encryption.Streams streams;
    private Uri uri;
    private final Uri thumbUri;

    public ChaChaDataSource(@NonNull Context context, @NonNull Uri thumbUri) {
        Log.e(TAG, "ChaChaDataSource: created");
        this.context = context.getApplicationContext();
        this.thumbUri = thumbUri;
    }

    @Override
    public long open(@NonNull DataSpec dataSpec) throws IOException {
        Log.e(TAG, "open: " + dataSpec);
        uri = dataSpec.uri;
        try {
            InputStream fileStream = context.getContentResolver().openInputStream(uri);
            InputStream thumbStream = context.getContentResolver().openInputStream(thumbUri);
            streams = Encryption.getCipherInputStreamForVideo(fileStream, thumbStream, Settings.getInstance(context).getTempPassword());
        } catch (GeneralSecurityException | InvalidPasswordException e) {
            e.printStackTrace();
            Log.e(TAG, "open error: " + e.getMessage());
            return 0;
        }

        Log.e(TAG, "open: position " + dataSpec.position);
        Log.e(TAG, "open: available: " + streams.getInputStream().available());
        long skipped = forceSkip(dataSpec.position, (CipherInputStream) streams.getInputStream());//streams.getInputStream().skip(dataSpec.position);
        return dataSpec.position;
    }

    private long forceSkip(long skipBytes, CipherInputStream inputStream) throws IOException {
        long skipped = 0L;
        while (skipped < skipBytes) {
            inputStream.read();
            skipped++;
        }
        Log.e(TAG, "forceSkip: skipped " + skipped);
        return skipped;
    }

    @Override
    public int read(@NonNull byte[] buffer, int offset, int length) throws IOException { // https://stackoverflow.com/questions/66884377/how-do-i-play-locally-stored-encrypted-video-files-in-android-studio
        if (length == 0) {
            Log.e(TAG, "read: length is 0");
            return 0;
        }

        return streams.getInputStream().read(buffer, offset, length);
    }

    @Nullable
    @Override
    public Uri getUri() {
        Log.e(TAG, "getUri: return " + uri);
        return uri;
    }

    @Override
    public void close() {
        Log.e(TAG, "close: ");
        if (streams != null) {
            streams.close();
        }
    }

    @Override
    public void addTransferListener(@NonNull TransferListener transferListener) {
    }

}
