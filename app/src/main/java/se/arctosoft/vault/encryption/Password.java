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
    private static final byte[] SALT = new byte[]{0x66, 0x6d, 0x34, 0x63, 0x6b, 0x41, 0x76, 0x32, 0x63, 0x6d, 0x34, 0x39, 0x35, 0x37, 0x6e, 0x64};

    public static boolean checkPassword(Context context, String pwd) {
        String s = Settings.getInstance(context).getPasswordHash();
        try {
            return calculateHash(pwd).equals(calculateHash(Objects.requireNonNull(s)));
        } catch (GeneralSecurityException e) {
            e.printStackTrace();
        }
        return false;
    }

    public static String calculateHash(String s) throws GeneralSecurityException {
        SecretKeyFactory secretKeyFactory = SecretKeyFactory.getInstance(Encryption.KEY_ALGORITHM);
        KeySpec keySpec = new PBEKeySpec(s.toCharArray(), SALT, Encryption.ITERATION_COUNT, Encryption.KEY_LENGTH);
        return new String(secretKeyFactory.generateSecret(keySpec).getEncoded());
    }

    public static void lock(Context context, @NonNull Settings settings) {
        Log.e(TAG, "lock");
        settings.clearTempPassword();
        FileStuff.deleteCache(context);
        Glide.get(context).clearMemory();
        new Thread(() -> Glide.get(context).clearDiskCache()).start();
        LaunchActivity.GLIDE_KEY = System.currentTimeMillis();
    }
}
