package se.arctosoft.vault.loader;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;

import com.bumptech.glide.Priority;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.data.DataFetcher;

import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;

import javax.crypto.CipherInputStream;

import se.arctosoft.vault.util.Encryption;

public class CipherDataFetcher implements DataFetcher<CipherInputStream> {
    private static final String TAG = "CipherDataFetcher";
    private CipherInputStream cipherInputStream;
    private final Context context;
    private final Uri uri;

    public CipherDataFetcher(@NonNull Context context, Uri uri) {
        this.context = context.getApplicationContext();
        this.uri = uri;
    }

    @Override
    public void loadData(@NonNull Priority priority, @NonNull DataCallback<? super CipherInputStream> callback) {
        try {
            InputStream inputStream = context.getContentResolver().openInputStream(uri);
            this.cipherInputStream = Encryption.getCipherInputStream(inputStream);
            Log.e(TAG, "loadData: " + uri.getLastPathSegment());
            callback.onDataReady(cipherInputStream);
        } catch (GeneralSecurityException | IOException e) {
            e.printStackTrace();
            callback.onLoadFailed(e);
        }
    }

    @Override
    public void cleanup() {
        cancel();
    }

    @Override
    public void cancel() {
        Log.e(TAG, "cancel:");
        if (cipherInputStream != null) {
            try {
                cipherInputStream.close(); // interrupts decode if any
                cipherInputStream = null;
            } catch (IOException ignore) {
            }
        }
    }

    @NonNull
    @Override
    public Class<CipherInputStream> getDataClass() {
        return CipherInputStream.class;
    }

    @NonNull
    @Override
    public DataSource getDataSource() {
        return DataSource.LOCAL;
    }
}
