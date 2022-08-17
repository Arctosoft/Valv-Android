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

import se.arctosoft.vault.encryption.Encryption;
import se.arctosoft.vault.exception.InvalidPasswordException;
import se.arctosoft.vault.utils.Settings;

public class CipherDataFetcher implements DataFetcher<InputStream> {
    private static final String TAG = "CipherDataFetcher";
    private Encryption.Streams streams;
    private final Context context;
    private final Uri uri;
    private final Settings settings;

    public CipherDataFetcher(@NonNull Context context, Uri uri) {
        this.context = context.getApplicationContext();
        this.uri = uri;
        this.settings = Settings.getInstance(context);
    }

    @Override
    public void loadData(@NonNull Priority priority, @NonNull DataCallback<? super InputStream> callback) {
        try {
            InputStream inputStream = context.getContentResolver().openInputStream(uri);
            streams = Encryption.getCipherInputStream(inputStream, settings.getTempPassword());
            callback.onDataReady(streams.getInputStream());
        } catch (GeneralSecurityException | IOException | InvalidPasswordException e) {
            //e.printStackTrace();
            callback.onLoadFailed(e);
        }
    }

    @Override
    public void cleanup() {
        Log.i(TAG, "cleanup: ");
        cancel();
    }

    @Override
    public void cancel() {
        Log.i(TAG, "cancel:");
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
