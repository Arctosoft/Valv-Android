package se.arctosoft.vault.loader;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;

import com.bumptech.glide.load.model.ModelLoader;
import com.bumptech.glide.load.model.ModelLoaderFactory;
import com.bumptech.glide.load.model.MultiModelLoaderFactory;

import javax.crypto.CipherInputStream;

public class CipherModelLoaderFactory implements ModelLoaderFactory<Uri, CipherInputStream> {
    private final Context context;

    public CipherModelLoaderFactory(@NonNull Context context) {
        this.context = context.getApplicationContext();
    }

    @NonNull
    @Override
    public ModelLoader<Uri, CipherInputStream> build(@NonNull MultiModelLoaderFactory multiFactory) {
        return new CipherModelLoader(context);
    }

    @Override
    public void teardown() {

    }
}
