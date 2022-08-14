package se.arctosoft.vault.encryption;

import android.graphics.Bitmap;
import android.net.Uri;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.documentfile.provider.DocumentFile;
import androidx.fragment.app.FragmentActivity;

import com.bumptech.glide.Glide;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.concurrent.ExecutionException;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.security.auth.DestroyFailedException;

import se.arctosoft.vault.utils.Settings;

public class Encryption {
    private static final String TAG = "Encryption";
    private static final String CIPHER = "ChaCha20/Poly1305/NoPadding";
    public static final String KEY_ALGORITHM = "PBKDF2withHmacSHA512";
    public static final int ITERATION_COUNT = 20000;
    public static final int KEY_LENGTH = 256;
    public static final int SALT_LENGTH = 16;
    private static final int IV_LENGTH = 12;

    public static final String PREFIX_IMAGE_FILE = ".arcv1.i-";
    public static final String ENCRYPTED_PREFIX = ".arcv1.";
    public static final String PREFIX_VIDEO_FILE = ".arcv1.v-";
    public static final String PREFIX_THUMB = ".arcv1.t-";

    public static boolean importImageFileToDirectory(FragmentActivity context, DocumentFile sourceFile, DocumentFile directory, Settings settings) {
        char[] tempPassword = settings.getTempPassword();
        if (tempPassword == null || tempPassword.length == 0) {
            throw new RuntimeException("No password");
        }

        DocumentFile file = directory.createFile("*/*", Encryption.PREFIX_IMAGE_FILE + sourceFile.getName());
        DocumentFile thumb = directory.createFile("*/*", Encryption.PREFIX_THUMB + sourceFile.getName());

        try {
            createFile(context, sourceFile.getUri(), file, tempPassword);
            createThumb(context, sourceFile.getUri(), thumb, tempPassword);
        } catch (GeneralSecurityException | IOException | ExecutionException | InterruptedException e) {
            e.printStackTrace();
            file.delete();
            thumb.delete();
            return false;
        }
        return true;
    }

    public static void writeFile(FragmentActivity context, Uri input, DocumentFile file, DocumentFile thumb, char[] password, IOnUriResult onUriResult) {
        new Thread(() -> {
            try {
                createFile(context, input, file, password);

                createThumb(context, input, thumb, password);

                context.runOnUiThread(() -> onUriResult.onUriResult(file.getUri()));
            } catch (GeneralSecurityException | IOException | ExecutionException | InterruptedException e) {
                e.printStackTrace();
                context.runOnUiThread(() -> onUriResult.onError(e));
            }
        }).start();
    }

    private static class Streams {
        private final InputStream inputStream;
        private final CipherOutputStream outputStream;
        private final SecretKey secretKey;

        private Streams(InputStream inputStream, CipherOutputStream outputStream, SecretKey secretKey) {
            this.inputStream = inputStream;
            this.outputStream = outputStream;
            this.secretKey = secretKey;
        }

        private void close() {
            try {
                outputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            try {
                inputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            try {
                secretKey.destroy();
            } catch (DestroyFailedException e) {
                e.printStackTrace();
            }
        }
    }

    private static void createFile(FragmentActivity context, Uri input, DocumentFile file, char[] password) throws GeneralSecurityException, IOException {
        Streams streams = getCipherOutputStream(context, input, file, password);

        int read;
        byte[] buffer = new byte[2048];
        while ((read = streams.inputStream.read(buffer)) != -1) {
            streams.outputStream.write(buffer, 0, read);
        }

        streams.close();
    }

    private static void createThumb(FragmentActivity context, Uri input, DocumentFile thumb, char[] password) throws GeneralSecurityException, IOException, ExecutionException, InterruptedException {
        Streams streams = getCipherOutputStream(context, input, thumb, password);

        Bitmap bitmap = Glide.with(context)
                .asBitmap()
                .load(input)
                .centerCrop()
                .submit(512, 512)
                .get();

        //bitmap.compress(Bitmap.CompressFormat.JPEG, 95, streams.outputStream);
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 75, stream);
        byte[] byteArray = stream.toByteArray();
        streams.outputStream.write(byteArray);
        bitmap.recycle();

        streams.close();
    }

    private static Streams getCipherOutputStream(FragmentActivity context, Uri input, DocumentFile file, char[] password) throws GeneralSecurityException, IOException {
        SecureRandom sr = SecureRandom.getInstanceStrong();
        byte[] salt = new byte[SALT_LENGTH];
        sr.nextBytes(salt);
        byte[] ivBytes = new byte[IV_LENGTH];
        sr.nextBytes(ivBytes);

        SecretKeyFactory secretKeyFactory = SecretKeyFactory.getInstance(KEY_ALGORITHM);
        KeySpec keySpec = new PBEKeySpec(password, salt, ITERATION_COUNT, KEY_LENGTH);
        SecretKey secretKey = secretKeyFactory.generateSecret(keySpec);
        IvParameterSpec ivParameterSpec = new IvParameterSpec(ivBytes);

        Cipher cipher = Cipher.getInstance(CIPHER);
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivParameterSpec);

        InputStream inputStream = context.getContentResolver().openInputStream(input);
        OutputStream fos = new BufferedOutputStream(context.getContentResolver().openOutputStream(file.getUri()), 1024 * 32);
        fos.write(salt);
        fos.write(ivBytes);
        fos.flush();
        return new Streams(inputStream, new CipherOutputStream(fos, cipher), secretKey);
    }

    public static void decryptAndWriteFile(FragmentActivity context, Uri encryptedInput, Uri output, char[] password, IOnUriResult onUriResult) {
        // Number of PBKDF2 hardening rounds to use. Larger values increase
        // computation time. You should select a value that causes computation
        // to take >100ms.
        //final int iterations = 20000;

        Log.d(TAG, "decryptAndWriteFile: input: " + encryptedInput + ", output: " + output);
        new Thread(() -> {
            try {
                long start = System.currentTimeMillis();
                byte[] salt = new byte[SALT_LENGTH];
                byte[] ivBytes = new byte[IV_LENGTH];

                InputStream inputStream = new BufferedInputStream(context.getContentResolver().openInputStream(encryptedInput), 1024 * 32);
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
                long end = System.currentTimeMillis();
                context.runOnUiThread(() -> Toast.makeText(context, "decrypt took " + (end - start) + " ms", Toast.LENGTH_LONG).show());
                Log.d(TAG, "decryptAndWriteFile: decrypted");

                context.runOnUiThread(() -> onUriResult.onUriResult(output));
            } catch (GeneralSecurityException | IOException e) {
                e.printStackTrace();
                context.runOnUiThread(() -> onUriResult.onError(e));
            }
        }).start();
    }

    public static InputStream getCipherInputStream(@NonNull InputStream inputStream, char[] password) throws IOException, GeneralSecurityException {
        byte[] salt = new byte[SALT_LENGTH];
        byte[] ivBytes = new byte[IV_LENGTH];

        inputStream.read(salt, 0, salt.length);
        inputStream.read(ivBytes, 0, ivBytes.length);
        inputStream = new BufferedInputStream(inputStream, 1024 * 32);

        SecretKeyFactory secretKeyFactory = SecretKeyFactory.getInstance(KEY_ALGORITHM);
        KeySpec keySpec = new PBEKeySpec(password, salt, ITERATION_COUNT, KEY_LENGTH);
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
