package se.arctosoft.vault.loader;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.load.Options;
import com.bumptech.glide.load.model.ModelLoader;
import com.bumptech.glide.signature.ObjectKey;

import javax.crypto.CipherInputStream;

public class CipherModelLoader implements ModelLoader<Uri, CipherInputStream> {
    private static final String TAG = "CipherModelLoader";
    private final Context context;

    public CipherModelLoader(@NonNull Context context) {
        this.context = context.getApplicationContext();
    }

    @Nullable
    @Override
    public LoadData<CipherInputStream> buildLoadData(@NonNull Uri uri, int width, int height, @NonNull Options options) {
        return new LoadData<>(new ObjectKey(uri), new CipherDataFetcher(context, uri));
    }

    @Override
    public boolean handles(@NonNull Uri uri) {
        String lastSegment = uri.getLastPathSegment().toLowerCase();
        boolean handles = lastSegment.contains("/.arcv1-") && (lastSegment.endsWith("jpg") || lastSegment.endsWith("png") || lastSegment.endsWith("gif"));
        Log.e(TAG, "handles: " + lastSegment + " " + handles);
        return handles;
    }

}
