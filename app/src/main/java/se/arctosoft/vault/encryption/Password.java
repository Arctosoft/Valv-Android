package se.arctosoft.vault.encryption;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.bumptech.glide.Glide;

import java.security.GeneralSecurityException;
import java.security.spec.KeySpec;
import java.util.Objects;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

import se.arctosoft.vault.LaunchActivity;
import se.arctosoft.vault.utils.FileStuff;
import se.arctosoft.vault.utils.Settings;

public class Password {
    private static final String TAG = "Password";

    public static void lock(Context context, @NonNull Settings settings) {
        Log.d(TAG, "lock");
        settings.clearTempPassword();
        FileStuff.deleteCache(context);
        Glide.get(context).clearMemory();
        //new Thread(() -> Glide.get(context).clearDiskCache()).start();
        LaunchActivity.GLIDE_KEY = System.currentTimeMillis();
    }
}
