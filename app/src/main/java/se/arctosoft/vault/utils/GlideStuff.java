package se.arctosoft.vault.utils;

import androidx.annotation.NonNull;

import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.signature.ObjectKey;

import se.arctosoft.vault.LaunchActivity;

public class GlideStuff {

    @NonNull
    public static RequestOptions getRequestOptions() {
        return new RequestOptions()
                .signature(new ObjectKey(LaunchActivity.GLIDE_KEY));
    }

}
