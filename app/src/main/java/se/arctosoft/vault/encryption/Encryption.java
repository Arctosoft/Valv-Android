package se.arctosoft.vault.encryption;

import android.graphics.Bitmap;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.documentfile.provider.DocumentFile;
import androidx.fragment.app.FragmentActivity;

import com.bumptech.glide.Glide;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.security.auth.DestroyFailedException;

import se.arctosoft.vault.data.FileType;
import se.arctosoft.vault.exception.InvalidPasswordException;
import se.arctosoft.vault.utils.FileStuff;
import se.arctosoft.vault.utils.Settings;

public class Encryption {
    private static final String TAG = "Encryption";
    private static final String CIPHER = "ChaCha20/NONE/NoPadding";
    public static final String KEY_ALGORITHM = "PBKDF2withHmacSHA512";
    public static final int ITERATION_COUNT = 20000;
    public static final int KEY_LENGTH = 256;
    private static final int SALT_LENGTH = 16;
    private static final int IV_LENGTH = 12;
    private static final int CHECK_LENGTH = 12;

    public static final String PREFIX_IMAGE_FILE = ".arcv1.i-";
    public static final String PREFIX_GIF_FILE = ".arcv1.g-";
    public static final String ENCRYPTED_PREFIX = ".arcv1.";
    public static final String PREFIX_VIDEO_FILE = ".arcv1.v-";
    public static final String PREFIX_THUMB = ".arcv1.t-";

    public static boolean importImageFileToDirectory(FragmentActivity context, DocumentFile sourceFile, DocumentFile directory, Settings settings) {
        char[] tempPassword = settings.getTempPassword();
        if (tempPassword == null || tempPassword.length == 0) {
            throw new RuntimeException("No password");
        }

        String name = sourceFile.getName();
        Log.e(TAG, "importImageFileToDirectory: mimetype is " + sourceFile.getType() + " for " + name);
        DocumentFile file = directory.createFile("*/*", FileType.fromMimeType(sourceFile.getType()).encryptionPrefix + name);
        DocumentFile thumb = directory.createFile("*/*", Encryption.PREFIX_THUMB + name);

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

    public static class Streams {
        private final InputStream inputStream;
        private final CipherOutputStream outputStream;
        private final SecretKey secretKey;

        private Streams(@NonNull InputStream inputStream, @NonNull CipherOutputStream outputStream, @NonNull SecretKey secretKey) {
            this.inputStream = inputStream;
            this.outputStream = outputStream;
            this.secretKey = secretKey;
        }

        @NonNull
        public InputStream getInputStream() {
            return inputStream;
        }

        @Nullable
        public CipherOutputStream getOutputStream() {
            return outputStream;
        }

        @NonNull
        public SecretKey getSecretKey() {
            return secretKey;
        }

        private Streams(@NonNull InputStream inputStream, @NonNull SecretKey secretKey) {
            this.inputStream = inputStream;
            this.outputStream = null;
            this.secretKey = secretKey;
        }

        public void close() {
            if (outputStream != null) {
                try {
                    outputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
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

    private static void createFile(FragmentActivity context, Uri input, DocumentFile outputFile, char[] password) throws GeneralSecurityException, IOException {
        Streams streams = getCipherOutputStream(context, input, outputFile, password);

        int read;
        byte[] buffer = new byte[2048];
        while ((read = streams.inputStream.read(buffer)) != -1) {
            streams.outputStream.write(buffer, 0, read);
        }

        streams.close();
    }

    private static void createThumb(FragmentActivity context, Uri input, DocumentFile outputThumbFile, char[] password) throws GeneralSecurityException, IOException, ExecutionException, InterruptedException {
        Streams streams = getCipherOutputStream(context, input, outputThumbFile, password);

        Bitmap bitmap = Glide.with(context)
                .asBitmap()
                .load(input)
                .centerCrop()
                .submit(512, 512)
                .get();

        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 75, stream);
        byte[] byteArray = stream.toByteArray();
        streams.outputStream.write(byteArray);
        bitmap.recycle();

        streams.close();
    }

    private static Streams getCipherOutputStream(FragmentActivity context, Uri input, DocumentFile outputFile, char[] password) throws GeneralSecurityException, IOException {
        SecureRandom sr = SecureRandom.getInstanceStrong();
        byte[] salt = new byte[SALT_LENGTH];
        sr.nextBytes(salt);
        byte[] ivBytes = new byte[IV_LENGTH];
        sr.nextBytes(ivBytes);
        byte[] checkBytes = new byte[CHECK_LENGTH];
        sr.nextBytes(checkBytes);

        SecretKeyFactory secretKeyFactory = SecretKeyFactory.getInstance(KEY_ALGORITHM);
        KeySpec keySpec = new PBEKeySpec(password, salt, ITERATION_COUNT, KEY_LENGTH);
        SecretKey secretKey = secretKeyFactory.generateSecret(keySpec);
        IvParameterSpec ivParameterSpec = new IvParameterSpec(ivBytes);

        Cipher cipher = Cipher.getInstance(CIPHER);
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivParameterSpec);

        InputStream inputStream = context.getContentResolver().openInputStream(input);
        OutputStream fos = new BufferedOutputStream(context.getContentResolver().openOutputStream(outputFile.getUri()), 1024 * 32);
        fos.write(salt);
        fos.write(ivBytes);
        fos.write(checkBytes);
        fos.flush();
        CipherOutputStream cipherOutputStream = new CipherOutputStream(fos, cipher);
        cipherOutputStream.write(checkBytes);
        return new Streams(inputStream, cipherOutputStream, secretKey);
    }

    public static void decryptToCache(FragmentActivity context, Uri encryptedInput, char[] password, IOnUriResult onUriResult) {
        new Thread(() -> {
            try {
                InputStream inputStream = new BufferedInputStream(context.getContentResolver().openInputStream(encryptedInput), 1024 * 32);

                Path file = Files.createTempFile("temp_", ".dcrpt");
                Uri fileUri = Uri.fromFile(file.toFile());
                OutputStream fos = context.getContentResolver().openOutputStream(fileUri);
                Streams cis = getCipherInputStream(inputStream, password);

                int read;
                byte[] buffer = new byte[2048];
                while ((read = cis.inputStream.read(buffer)) != -1) {
                    fos.write(buffer, 0, read);
                }
                fos.close();
                cis.close();
                inputStream.close();

                context.runOnUiThread(() -> onUriResult.onUriResult(fileUri));
            } catch (GeneralSecurityException | IOException e) {
                e.printStackTrace();
                context.runOnUiThread(() -> onUriResult.onError(e));
            } catch (InvalidPasswordException e) {
                Log.e(TAG, "decryptToCache: catch invalid password");
                e.printStackTrace();
                context.runOnUiThread(() -> onUriResult.onInvalidPassword(e));
            }
        }).start();
    }

    public static void decryptAndExport(FragmentActivity context, Uri encryptedInput, Uri directoryUri, char[] password, IOnUriResult onUriResult) {
        DocumentFile documentFile = DocumentFile.fromTreeUri(context, directoryUri);
        DocumentFile file = documentFile.createFile("image/*", System.currentTimeMillis() + "_" + FileStuff.getFilenameFromUri(encryptedInput, true));
        new Thread(() -> {
            try {
                InputStream inputStream = new BufferedInputStream(context.getContentResolver().openInputStream(encryptedInput), 1024 * 32);

                OutputStream fos = context.getContentResolver().openOutputStream(file.getUri());
                Streams cis = getCipherInputStream(inputStream, password);

                int read;
                byte[] buffer = new byte[2048];
                while ((read = cis.inputStream.read(buffer)) != -1) {
                    fos.write(buffer, 0, read);
                }
                fos.close();
                cis.close();
                inputStream.close();

                context.runOnUiThread(() -> onUriResult.onUriResult(file.getUri()));
            } catch (GeneralSecurityException | IOException e) {
                e.printStackTrace();
                context.runOnUiThread(() -> onUriResult.onError(e));
            } catch (InvalidPasswordException e) {
                e.printStackTrace();
                context.runOnUiThread(() -> onUriResult.onInvalidPassword(e));
            }
        }).start();
    }

    public static void decryptToByteArray(FragmentActivity context, Uri encryptedInput, char[] password, IOnByteArrayResult onByteArrayResult) {
        try {
            InputStream inputStream = new BufferedInputStream(context.getContentResolver().openInputStream(encryptedInput), 1024 * 32);
            Streams cis = getCipherInputStream(inputStream, password);

            byte[] data = inputStreamToBytes(cis.inputStream);
            cis.close();
            inputStream.close();

            onByteArrayResult.onBytesResult(data);
        } catch (GeneralSecurityException | IOException e) {
            e.printStackTrace();
            onByteArrayResult.onError(e);
        } catch (InvalidPasswordException e) {
            e.printStackTrace();
            onByteArrayResult.onInvalidPassword(e);
        }
    }

    public static byte[] inputStreamToBytes(InputStream is) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        int nRead;
        byte[] data = new byte[16384];
        while ((nRead = is.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }

        return buffer.toByteArray();
    }

    public static Streams getCipherInputStream(@NonNull InputStream inputStream, char[] password) throws IOException, GeneralSecurityException, InvalidPasswordException {
        byte[] salt = new byte[SALT_LENGTH];
        byte[] ivBytes = new byte[IV_LENGTH];
        byte[] checkBytes1 = new byte[CHECK_LENGTH];
        byte[] checkBytes2 = new byte[CHECK_LENGTH];

        inputStream = new BufferedInputStream(inputStream, 1024 * 32);
        inputStream.read(salt, 0, salt.length);
        inputStream.read(ivBytes, 0, ivBytes.length);
        inputStream.read(checkBytes1, 0, checkBytes1.length);
        //Log.e(TAG, "getCipherInputStream: read " + new String(checkBytes1));

        SecretKeyFactory secretKeyFactory = SecretKeyFactory.getInstance(KEY_ALGORITHM);
        KeySpec keySpec = new PBEKeySpec(password, salt, ITERATION_COUNT, KEY_LENGTH);
        SecretKey secretKey = secretKeyFactory.generateSecret(keySpec);
        IvParameterSpec ivParameterSpec = new IvParameterSpec(ivBytes);

        Cipher cipher = Cipher.getInstance(CIPHER);
        cipher.init(Cipher.DECRYPT_MODE, secretKey, ivParameterSpec);
        CipherInputStream cipherInputStream = new CipherInputStream(inputStream, cipher);
        cipherInputStream.read(checkBytes2, 0, checkBytes2.length);
        //Log.e(TAG, "getCipherInputStream: " + new String(checkBytes1) + new String(checkBytes2));
        if (!Arrays.equals(checkBytes1, checkBytes2)) {
            //Log.e(TAG, "getCipherInputStream: do not match");
            throw new InvalidPasswordException("Invalid password");
        }
        return new Streams(cipherInputStream, secretKey);
    }

    public interface IOnUriResult {
        void onUriResult(Uri outputUri);

        void onError(Exception e);

        void onInvalidPassword(InvalidPasswordException e);
    }

    public interface IOnByteArrayResult {
        void onBytesResult(byte[] bytes);

        void onError(Exception e);

        void onInvalidPassword(InvalidPasswordException e);
    }

}
