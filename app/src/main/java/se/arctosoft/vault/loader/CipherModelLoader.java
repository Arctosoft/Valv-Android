package se.arctosoft.vault.loader;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.load.Options;
import com.bumptech.glide.load.model.ModelLoader;
import com.bumptech.glide.signature.ObjectKey;

import java.io.InputStream;

import se.arctosoft.vault.encryption.Encryption;

public class CipherModelLoader implements ModelLoader<Uri, InputStream> {
    private final Context context;

    public CipherModelLoader(@NonNull Context context) {
        this.context = context.getApplicationContext();
    }

    @Nullable
    @Override
    public LoadData<InputStream> buildLoadData(@NonNull Uri uri, int width, int height, @NonNull Options options) {
        return new LoadData<>(new ObjectKey(uri), new CipherDataFetcher(context, uri));
    }

    @Override
    public boolean handles(@NonNull Uri uri) {
        String lastSegment = uri.getLastPathSegment();
        return lastSegment != null && lastSegment.toLowerCase().contains("/" + Encryption.ENCRYPTED_PREFIX);
    }

}
