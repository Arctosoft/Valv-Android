package se.arctosoft.vault.encryption;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;

import com.google.android.exoplayer2.upstream.DataSource;

public class ChaCha20DataSourceFactory implements DataSource.Factory {
    private final Context context;
    private final Uri thumbUri;

    public ChaCha20DataSourceFactory(Context context, Uri thumbUri) {
        this.context = context;
        this.thumbUri = thumbUri;
    }

    @NonNull
    @Override
    public DataSource createDataSource() {
        return new ChaChaDataSource(context, thumbUri);
    }
}
