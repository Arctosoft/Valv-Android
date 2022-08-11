package se.arctosoft.vault.util;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.security.spec.KeySpec;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.security.auth.DestroyFailedException;

public class Encryption {
    private static final String TAG = "Encryption";
    private static final String CIPHER = "ChaCha20/Poly1305/NoPadding";
    private static final String KEY_ALGORITHM = "PBKDF2withHmacSHA512";
    private static final int ITERATION_COUNT = 20000;
    private static final int KEY_LENGTH = 256;
    private static final int SALT_LENGTH = 16;
    private static final int IV_LENGTH = 12;

    public static void writeFile(FragmentActivity context, Uri input, Uri output, char[] password, IOnUriResult onUriResult) {
        new Thread(() -> {
            try {
                SecureRandom sr = SecureRandom.getInstanceStrong();
                byte[] salt = new byte[SALT_LENGTH];
                sr.nextBytes(salt);
                byte[] ivBytes = new byte[IV_LENGTH];
                sr.nextBytes(ivBytes);

                SecretKeyFactory secretKeyFactory = SecretKeyFactory.getInstance(KEY_ALGORITHM);
                KeySpec keySpec = new PBEKeySpec(password, salt, ITERATION_COUNT, KEY_LENGTH);
                SecretKey secretKey = secretKeyFactory.generateSecret(keySpec);
                Log.d(TAG, "decryptAndWriteFile: generated secret key for encryption: " + new String(secretKey.getEncoded()));
                IvParameterSpec ivParameterSpec = new IvParameterSpec(ivBytes);

                Cipher cipher = Cipher.getInstance(CIPHER); // https://developer.android.com/reference/javax/crypto/Cipher
                cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivParameterSpec);

                InputStream inputStream = context.getContentResolver().openInputStream(input);
                OutputStream fos = context.getContentResolver().openOutputStream(output);
                fos.write(salt);
                fos.write(ivBytes);
                fos.flush();
                CipherOutputStream cos = new CipherOutputStream(fos, cipher);

                int read;
                byte[] buffer = new byte[2048];
                while ((read = inputStream.read(buffer)) != -1) {
                    cos.write(buffer, 0, read);
                }

                cos.close();
                fos.close();
                inputStream.close();
                try {
                    secretKey.destroy();
                } catch (DestroyFailedException e) {
                    e.printStackTrace();
                }
                context.runOnUiThread(() -> onUriResult.onUriResult(output));
            } catch (GeneralSecurityException | IOException e) {
                e.printStackTrace();
                context.runOnUiThread(() -> onUriResult.onError(e));
            }
        }).start();
    }

    public static void decryptAndWriteFile(FragmentActivity context, Uri encryptedInput, Uri output, char[] password, IOnUriResult onUriResult) {
        // Number of PBKDF2 hardening rounds to use. Larger values increase
        // computation time. You should select a value that causes computation
        // to take >100ms.
        //final int iterations = 20000;

        Log.d(TAG, "decryptAndWriteFile: input: " + encryptedInput + ", output: " + output);
        new Thread(() -> {
            try {
                byte[] salt = new byte[SALT_LENGTH];
                byte[] ivBytes = new byte[IV_LENGTH];

                InputStream inputStream = context.getContentResolver().openInputStream(encryptedInput);
                inputStream.read(salt, 0, salt.length);
                inputStream.read(ivBytes, 0, ivBytes.length);
                Log.d(TAG, "decryptAndWriteFile: read salt + iv");

                Log.d(TAG, "decryptAndWriteFile: " + new String(salt) + " " + new String(ivBytes));

                SecretKeyFactory secretKeyFactory = SecretKeyFactory.getInstance(KEY_ALGORITHM);
                KeySpec keySpec = new PBEKeySpec(password, salt, ITERATION_COUNT, KEY_LENGTH);
                SecretKey secretKey = secretKeyFactory.generateSecret(keySpec);
                Log.d(TAG, "decryptAndWriteFile: generated secret key for decryption: " + new String(secretKey.getEncoded()));
                IvParameterSpec ivParameterSpec = new IvParameterSpec(ivBytes);

                Cipher cipher = Cipher.getInstance(CIPHER); // https://developer.android.com/reference/javax/crypto/Cipher
                cipher.init(Cipher.DECRYPT_MODE, secretKey, ivParameterSpec);

                OutputStream fos = context.getContentResolver().openOutputStream(output);
                CipherInputStream cis = new CipherInputStream(inputStream, cipher);

                int read;
                byte[] buffer = new byte[2048];
                while ((read = cis.read(buffer)) != -1) {
                    fos.write(buffer, 0, read);
                }
                fos.close();
                cis.close();
                inputStream.close();
                try {
                    secretKey.destroy();
                } catch (DestroyFailedException e) {
                    e.printStackTrace();
                }
                Log.d(TAG, "decryptAndWriteFile: decrypted");

                context.runOnUiThread(() -> onUriResult.onUriResult(output));
            } catch (GeneralSecurityException | IOException e) {
                e.printStackTrace();
                context.runOnUiThread(() -> onUriResult.onError(e));
            }
        }).start();
    }

    public static CipherInputStream getCipherInputStream(@NonNull InputStream inputStream) throws IOException, GeneralSecurityException {
        byte[] salt = new byte[SALT_LENGTH];
        byte[] ivBytes = new byte[IV_LENGTH];

        inputStream.read(salt, 0, salt.length);
        inputStream.read(ivBytes, 0, ivBytes.length);

        SecretKeyFactory secretKeyFactory = SecretKeyFactory.getInstance(KEY_ALGORITHM);
        KeySpec keySpec = new PBEKeySpec("mypassword1".toCharArray(), salt, ITERATION_COUNT, KEY_LENGTH);
        SecretKey secretKey = secretKeyFactory.generateSecret(keySpec);
        IvParameterSpec ivParameterSpec = new IvParameterSpec(ivBytes);

        Cipher cipher = Cipher.getInstance(CIPHER);
        cipher.init(Cipher.DECRYPT_MODE, secretKey, ivParameterSpec);
        return new CipherInputStream(inputStream, cipher);
    }

    public interface IOnUriResult {
        void onUriResult(Uri uri);

        void onError(Exception e);
    }

}
