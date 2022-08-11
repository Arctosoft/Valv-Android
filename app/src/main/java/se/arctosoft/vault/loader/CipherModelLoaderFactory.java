package se.arctosoft.vault.loader;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;

import com.bumptech.glide.load.model.ModelLoader;
import com.bumptech.glide.load.model.ModelLoaderFactory;
import com.bumptech.glide.load.model.MultiModelLoaderFactory;

import java.io.InputStream;

import javax.crypto.CipherInputStream;

public class CipherModelLoaderFactory implements ModelLoaderFactory<Uri, InputStream> {
    private final Context context;

    public CipherModelLoaderFactory(@NonNull Context context) {
        this.context = context.getApplicationContext();
    }

    @NonNull
    @Override
    public ModelLoader<Uri, InputStream> build(@NonNull MultiModelLoaderFactory multiFactory) {
        return new CipherModelLoader(context);
    }

    @Override
    public void teardown() {

    }
}
