package se.arctosoft.vault.util;

import android.net.Uri;
import android.util.Log;

import androidx.documentfile.provider.DocumentFile;
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

    public static void writeFile(FragmentActivity context, Uri input, Uri output, char[] password, IOnUriResult onUriResult) {
        new Thread(() -> {
            try {
                SecureRandom sr = SecureRandom.getInstanceStrong();
                byte[] salt = new byte[16];
                sr.nextBytes(salt);
                byte[] ivBytes = new byte[12];
                sr.nextBytes(ivBytes);

                SecretKeyFactory secretKeyFactory = SecretKeyFactory.getInstance("PBKDF2withHmacSHA512");
                KeySpec keySpec = new PBEKeySpec(password, salt, 20000, 256);
                SecretKey secretKey = secretKeyFactory.generateSecret(keySpec);
                Log.d(TAG, "decryptAndWriteFile: generated secret key for encryption: " + new String(secretKey.getEncoded()));
                IvParameterSpec ivParameterSpec = new IvParameterSpec(ivBytes);

                Cipher cipher = Cipher.getInstance("ChaCha20/Poly1305/NoPadding"); // https://developer.android.com/reference/javax/crypto/Cipher
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
                    if (read != 2048) {
                        Log.e(TAG, "decryptAndWriteFile: read " + read);
                    }
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
                byte[] salt = new byte[16];
                byte[] ivBytes = new byte[12];

                InputStream inputStream = context.getContentResolver().openInputStream(encryptedInput);
                inputStream.read(salt, 0, salt.length);
                inputStream.read(ivBytes, 0, ivBytes.length);
                Log.d(TAG, "decryptAndWriteFile: read salt + iv");

                Log.d(TAG, "decryptAndWriteFile: " + new String(salt) + " " + new String(ivBytes));

                SecretKeyFactory secretKeyFactory = SecretKeyFactory.getInstance("PBKDF2withHmacSHA512");
                KeySpec keySpec = new PBEKeySpec(password, salt, 20000, 256);
                SecretKey secretKey = secretKeyFactory.generateSecret(keySpec);
                Log.d(TAG, "decryptAndWriteFile: generated secret key for decryption: " + new String(secretKey.getEncoded()));
                IvParameterSpec ivParameterSpec = new IvParameterSpec(ivBytes);

                Cipher cipher = Cipher.getInstance("ChaCha20/Poly1305/NoPadding"); // https://developer.android.com/reference/javax/crypto/Cipher
                cipher.init(Cipher.DECRYPT_MODE, secretKey, ivParameterSpec);

                OutputStream fos = context.getContentResolver().openOutputStream(output);
                CipherInputStream cos = new CipherInputStream(inputStream, cipher);

                int read;
                byte[] buffer = new byte[2048];
                while ((read = cos.read(buffer)) != -1) {
                    if (read != 2048) {
                        Log.e(TAG, "decryptAndWriteFile: read " + read);
                    }
                    fos.write(buffer, 0, read);
                }

                inputStream.close();
                fos.close();
                cos.close();
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

    public interface IOnUriResult {
        void onUriResult(Uri uri);

        void onError(Exception e);
    }

    /*public static SecretKey generateKey(char[] password) throws GeneralSecurityException, IOException {
        // Number of PBKDF2 hardening rounds to use. Larger values increase
        // computation time. You should select a value that causes computation
        // to take >100ms.
        //final int iterations = 20000;

        // Generate a long key
        //final int outputKeyLength = 128 * 8;

        SecureRandom sr = SecureRandom.getInstanceStrong();
        byte[] salt = new byte[16];
        sr.nextBytes(salt);
        byte[] ivBytes = new byte[16];
        sr.nextBytes(ivBytes);

        SecretKeyFactory secretKeyFactory = SecretKeyFactory.getInstance("PBKDF2withHmacSHA512");
        KeySpec keySpec = new PBEKeySpec(password, salt, 20000, 128 * 8);
        SecretKey secretKey = secretKeyFactory.generateSecret(keySpec);
        IvParameterSpec ivParameterSpec = new IvParameterSpec(ivBytes);

        //Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5PADDING");
        Cipher cipher = Cipher.getInstance("ChaCha20/Poly1305/NoPadding"); // https://developer.android.com/reference/javax/crypto/Cipher
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivParameterSpec);

        InputStream fis = new FileInputStream("dir" + "fileName");
        FileOutputStream fos = new FileOutputStream("dir" + "fileName");
        fos.write(ivBytes);
        fos.flush();
        CipherOutputStream cos = new CipherOutputStream(fos, cipher);

        int b;
        byte[] d = new byte[2048];
        while ((b = fis.read(d)) != -1) {
            cos.write(d, 0, b);
        }

        cos.flush();
        cos.close();
        fis.close();
        try {
            secretKey.destroy();
        } catch (DestroyFailedException e) {
            e.printStackTrace();
        }

        return secretKey;
    }*/
}
