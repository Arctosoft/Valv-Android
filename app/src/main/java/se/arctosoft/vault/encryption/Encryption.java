/*
 * Valv-Android
 * Copyright (C) 2023 Arctosoft AB
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see https://www.gnu.org/licenses/.
 */

package se.arctosoft.vault.encryption;

import android.graphics.Bitmap;
import android.net.Uri;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.documentfile.provider.DocumentFile;
import androidx.fragment.app.FragmentActivity;

import com.bumptech.glide.Glide;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.ArrayList;
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
import se.arctosoft.vault.data.GalleryFile;
import se.arctosoft.vault.exception.InvalidPasswordException;
import se.arctosoft.vault.interfaces.IOnProgress;
import se.arctosoft.vault.utils.FileStuff;
import se.arctosoft.vault.utils.Settings;
import se.arctosoft.vault.utils.StringStuff;

public class Encryption {
    private static final String TAG = "Encryption";
    private static final String CIPHER = "ChaCha20/NONE/NoPadding";
    private static final String KEY_ALGORITHM = "PBKDF2withHmacSHA512";
    private static final int ITERATION_COUNT = 20000;
    private static final int KEY_LENGTH = 256;
    private static final int SALT_LENGTH = 16;
    private static final int IV_LENGTH = 12;
    private static final int CHECK_LENGTH = 12;

    public static final String ENCRYPTED_PREFIX = ".valv.";
    public static final String PREFIX_IMAGE_FILE = ".valv.i.1-";
    public static final String PREFIX_GIF_FILE = ".valv.g.1-";
    public static final String PREFIX_VIDEO_FILE = ".valv.v.1-";
    public static final String PREFIX_TEXT_FILE = ".valv.x.1-";
    public static final String PREFIX_NOTE_FILE = ".valv.n.1-";
    public static final String PREFIX_THUMB = ".valv.t.1-";

    public static Pair<Boolean, Boolean> importFileToDirectory(FragmentActivity context, DocumentFile sourceFile, DocumentFile directory, Settings settings, @Nullable IOnProgress onProgress) {
        char[] tempPassword = settings.getTempPassword();
        if (tempPassword == null || tempPassword.length == 0) {
            throw new RuntimeException("No password");
        }

        String generatedName = StringStuff.getRandomFileName();
        DocumentFile file = directory.createFile("", FileType.fromMimeType(sourceFile.getType()).encryptionPrefix + generatedName);
        DocumentFile thumb = directory.createFile("", PREFIX_THUMB + generatedName);

        if (file == null) {
            Log.e(TAG, "importFileToDirectory: could not create file from " + sourceFile.getUri());
            return new Pair<>(false, false);
        }
        try {
            createFile(context, sourceFile.getUri(), file, tempPassword, sourceFile.getName(), onProgress);
        } catch (GeneralSecurityException | IOException e) {
            e.printStackTrace();
            file.delete();
            return new Pair<>(false, false);
        }
        boolean createdThumb = false;
        try {
            createThumb(context, sourceFile.getUri(), thumb, tempPassword, sourceFile.getName());
            createdThumb = true;
        } catch (GeneralSecurityException | IOException | ExecutionException |
                 InterruptedException e) {
            e.printStackTrace();
            thumb.delete();
        }
        return new Pair<>(true, createdThumb);
    }

    public static DocumentFile importNoteToDirectory(FragmentActivity context, String note, String fileNameWithoutPrefix, DocumentFile directory, Settings settings) {
        char[] tempPassword = settings.getTempPassword();
        if (tempPassword == null || tempPassword.length == 0) {
            throw new RuntimeException("No password");
        }

        DocumentFile file = directory.createFile("", Encryption.PREFIX_NOTE_FILE + fileNameWithoutPrefix);

        try {
            createTextFile(context, note, file, tempPassword, fileNameWithoutPrefix);
        } catch (GeneralSecurityException | IOException e) {
            Log.e(TAG, "importNoteToDirectory: failed " + e.getMessage());
            e.printStackTrace();
            file.delete();
            return null;
        }

        return file;
    }

    public static DocumentFile importTextToDirectory(FragmentActivity context, String text, @Nullable String fileNameWithoutPrefix, DocumentFile directory, Settings settings) {
        char[] tempPassword = settings.getTempPassword();
        if (tempPassword == null || tempPassword.length == 0) {
            throw new RuntimeException("No password");
        }

        if (fileNameWithoutPrefix == null) {
            fileNameWithoutPrefix = StringStuff.getRandomFileName();
        }
        DocumentFile file = directory.createFile("", Encryption.PREFIX_TEXT_FILE + fileNameWithoutPrefix);

        try {
            createTextFile(context, text, file, tempPassword, fileNameWithoutPrefix + FileType.TEXT.extension);
        } catch (GeneralSecurityException | IOException e) {
            Log.e(TAG, "importTextToDirectory: failed " + e.getMessage());
            e.printStackTrace();
            file.delete();
            return null;
        }

        return file;
    }

    public static class Streams {
        private final InputStream inputStream;
        private final CipherOutputStream outputStream;
        private final SecretKey secretKey;
        private final String originalFileName, inputString;

        private Streams(@NonNull InputStream inputStream, @NonNull CipherOutputStream outputStream, @NonNull SecretKey secretKey) {
            this.inputStream = inputStream;
            this.outputStream = outputStream;
            this.secretKey = secretKey;
            this.originalFileName = "";
            this.inputString = null;
        }

        private Streams(@NonNull String inputString, @NonNull CipherOutputStream outputStream, @NonNull SecretKey secretKey) {
            this.inputString = inputString;
            this.inputStream = null;
            this.outputStream = outputStream;
            this.secretKey = secretKey;
            this.originalFileName = "";
        }

        private Streams(@NonNull InputStream inputStream, @NonNull SecretKey secretKey, @NonNull String originalFileName) {
            this.inputStream = inputStream;
            this.outputStream = null;
            this.secretKey = secretKey;
            this.originalFileName = originalFileName;
            this.inputString = null;
        }

        public String getInputString() {
            return inputString;
        }

        @Nullable
        public InputStream getInputStream() {
            return inputStream;
        }

        @NonNull
        public String getOriginalFileName() {
            return originalFileName;
        }

        public void close() {
            if (outputStream != null) {
                try {
                    outputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            try {
                secretKey.destroy();
            } catch (DestroyFailedException e) {
                e.printStackTrace();
            }
        }
    }

    private static void createFile(FragmentActivity context, Uri input, DocumentFile outputFile, char[] password, String sourceFileName, @Nullable IOnProgress onProgress) throws GeneralSecurityException, IOException {
        Streams streams = getCipherOutputStream(context, input, outputFile, password, false, sourceFileName);

        int read;
        byte[] buffer = new byte[2048];
        long progress = 0;
        while ((read = streams.inputStream.read(buffer)) != -1) {
            streams.outputStream.write(buffer, 0, read);
            if (onProgress != null) {
                progress += read;
                onProgress.onProgress(progress);
            }
        }

        streams.close();
    }

    private static void createTextFile(FragmentActivity context, String input, DocumentFile outputFile, char[] password, String sourceFileName) throws GeneralSecurityException, IOException {
        Streams streams = getTextCipherOutputStream(context, input, outputFile, password, sourceFileName);
        streams.outputStream.write(streams.inputString.getBytes(StandardCharsets.UTF_8));
        streams.close();
    }

    private static void createThumb(FragmentActivity context, Uri input, DocumentFile outputThumbFile, char[] password, String sourceFileName) throws GeneralSecurityException, IOException, ExecutionException, InterruptedException {
        Streams streams = getCipherOutputStream(context, input, outputThumbFile, password, true, sourceFileName);

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

    public static Streams getCipherInputStream(@NonNull InputStream inputStream, char[] password, boolean isThumb) throws IOException, GeneralSecurityException, InvalidPasswordException {
        byte[] salt = new byte[SALT_LENGTH];
        byte[] ivBytes = new byte[IV_LENGTH];
        byte[] checkBytes1 = new byte[CHECK_LENGTH];
        byte[] checkBytes2 = new byte[CHECK_LENGTH];

        inputStream.read(salt);
        inputStream.read(ivBytes);
        if (isThumb) {
            inputStream.read(checkBytes1);
        }

        SecretKeyFactory secretKeyFactory = SecretKeyFactory.getInstance(KEY_ALGORITHM);
        KeySpec keySpec = new PBEKeySpec(password, salt, ITERATION_COUNT, KEY_LENGTH);
        SecretKey secretKey = secretKeyFactory.generateSecret(keySpec);
        IvParameterSpec ivParameterSpec = new IvParameterSpec(ivBytes);

        Cipher cipher = Cipher.getInstance(CIPHER);
        cipher.init(Cipher.DECRYPT_MODE, secretKey, ivParameterSpec);
        CipherInputStream cipherInputStream = new MyCipherInputStream(inputStream, cipher);
        if (isThumb) {
            cipherInputStream.read(checkBytes2);
            if (!Arrays.equals(checkBytes1, checkBytes2)) {
                throw new InvalidPasswordException("Invalid password");
            }
        }
        ArrayList<Byte> bytes = new ArrayList<>();

        if (cipherInputStream.read() == 0x0A) {
            int count = 0;
            byte[] read = new byte[1];
            while ((cipherInputStream.read(read)) > 0) {
                if (read[0] == 0x0A) {
                    break;
                }
                bytes.add(read[0]);
                if (++count > 300) {
                    throw new IOException("Not valid file");
                }
            }
        } else {
            throw new IOException("Not valid file");
        }
        byte[] arr = new byte[bytes.size()];
        for (int i = 0; i < bytes.size(); i++) {
            arr[i] = bytes.get(i);
        }
        return new Streams(cipherInputStream, secretKey, new String(arr, StandardCharsets.UTF_8));
    }

    private static Streams getCipherOutputStream(FragmentActivity context, Uri input, DocumentFile outputFile, char[] password, boolean isThumb, String sourceFileName) throws GeneralSecurityException, IOException {
        SecureRandom sr = SecureRandom.getInstanceStrong();
        byte[] salt = new byte[SALT_LENGTH];
        byte[] ivBytes = new byte[IV_LENGTH];
        byte[] checkBytes = new byte[CHECK_LENGTH];
        generateSecureRandom(sr, salt, ivBytes, isThumb ? checkBytes : null);

        SecretKeyFactory secretKeyFactory = SecretKeyFactory.getInstance(KEY_ALGORITHM);
        KeySpec keySpec = new PBEKeySpec(password, salt, ITERATION_COUNT, KEY_LENGTH);
        SecretKey secretKey = secretKeyFactory.generateSecret(keySpec);
        IvParameterSpec ivParameterSpec = new IvParameterSpec(ivBytes);

        Cipher cipher = Cipher.getInstance(CIPHER);
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivParameterSpec);

        InputStream inputStream = context.getContentResolver().openInputStream(input);
        OutputStream fos = new BufferedOutputStream(context.getContentResolver().openOutputStream(outputFile.getUri()), 1024 * 32);
        writeSaltAndIV(isThumb, salt, ivBytes, checkBytes, fos);
        fos.flush();
        CipherOutputStream cipherOutputStream = new CipherOutputStream(fos, cipher);
        if (isThumb) {
            cipherOutputStream.write(checkBytes);
        }
        cipherOutputStream.write(("\n" + sourceFileName + "\n").getBytes(StandardCharsets.UTF_8));
        return new Streams(inputStream, cipherOutputStream, secretKey);
    }

    private static Streams getTextCipherOutputStream(FragmentActivity context, String input, DocumentFile outputFile, char[] password, String sourceFileName) throws GeneralSecurityException, IOException {
        SecureRandom sr = SecureRandom.getInstanceStrong();
        byte[] salt = new byte[SALT_LENGTH];
        byte[] ivBytes = new byte[IV_LENGTH];
        generateSecureRandom(sr, salt, ivBytes, null);

        SecretKeyFactory secretKeyFactory = SecretKeyFactory.getInstance(KEY_ALGORITHM);
        KeySpec keySpec = new PBEKeySpec(password, salt, ITERATION_COUNT, KEY_LENGTH);
        SecretKey secretKey = secretKeyFactory.generateSecret(keySpec);
        IvParameterSpec ivParameterSpec = new IvParameterSpec(ivBytes);

        Cipher cipher = Cipher.getInstance(CIPHER);
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivParameterSpec);

        OutputStream fos = new BufferedOutputStream(context.getContentResolver().openOutputStream(outputFile.getUri()), 1024 * 32);
        writeSaltAndIV(false, salt, ivBytes, null, fos);
        fos.flush();
        CipherOutputStream cipherOutputStream = new CipherOutputStream(fos, cipher);
        cipherOutputStream.write(("\n" + sourceFileName + "\n").getBytes(StandardCharsets.UTF_8));
        return new Streams(input, cipherOutputStream, secretKey);
    }

    @NonNull
    public static String getOriginalFilename(@NonNull InputStream inputStream, char[] password, boolean isThumb) {
        String name = "";
        try {
            Streams streams = getCipherInputStream(inputStream, password, isThumb);
            name = streams.getOriginalFileName();
            streams.close();
        } catch (IOException | GeneralSecurityException | InvalidPasswordException e) {
            e.printStackTrace();
        }
        return name;
    }

    private static void generateSecureRandom(SecureRandom sr, byte[] salt, byte[] ivBytes, @Nullable byte[] checkBytes) {
        sr.nextBytes(salt);
        sr.nextBytes(ivBytes);
        if (checkBytes != null) {
            sr.nextBytes(checkBytes);
        }
    }

    private static void writeSaltAndIV(boolean isThumb, byte[] salt, byte[] ivBytes, byte[] checkBytes, OutputStream fos) throws IOException {
        fos.write(salt);
        fos.write(ivBytes);
        if (isThumb) {
            fos.write(checkBytes);
        }
    }

    public static void decryptToCache(FragmentActivity context, Uri encryptedInput, @Nullable String extension, char[] password, IOnUriResult onUriResult) {
        new Thread(() -> {
            try {
                InputStream inputStream = new BufferedInputStream(context.getContentResolver().openInputStream(encryptedInput), 1024 * 32);

                Path file = Files.createTempFile("temp_", extension);
                Uri fileUri = Uri.fromFile(file.toFile());
                OutputStream fos = context.getContentResolver().openOutputStream(fileUri);
                Streams cis = getCipherInputStream(inputStream, password, false);

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

    public static void decryptAndExport(FragmentActivity context, Uri encryptedInput, DocumentFile directory, GalleryFile galleryFile, char[] password, IOnUriResult onUriResult, boolean isVideo) {
        DocumentFile documentFile = directory != null ? directory : DocumentFile.fromTreeUri(context, encryptedInput);
        String originalFileName = galleryFile.getOriginalName();
        if (originalFileName == null) {
            try {
                originalFileName = Encryption.getOriginalFilename(context.getContentResolver().openInputStream(encryptedInput), password, false);
                galleryFile.setOriginalName(originalFileName);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
                Log.e(TAG, "decryptAndExport: failed to decrypt original name");
            }
        }
        DocumentFile file = documentFile.createFile(isVideo ? "video/*" : "image/*", originalFileName != null ? originalFileName : (System.currentTimeMillis() + "_" + FileStuff.getFilenameFromUri(encryptedInput, true)));
        try {
            InputStream inputStream = new BufferedInputStream(context.getContentResolver().openInputStream(encryptedInput), 1024 * 32);

            OutputStream fos = context.getContentResolver().openOutputStream(file.getUri());
            Streams cis = getCipherInputStream(inputStream, password, false);

            int read;
            byte[] buffer = new byte[2048];
            while ((read = cis.inputStream.read(buffer)) != -1) {
                fos.write(buffer, 0, read);
            }
            fos.close();
            cis.close();
            inputStream.close();

            onUriResult.onUriResult(file.getUri());
        } catch (GeneralSecurityException | IOException e) {
            e.printStackTrace();
            onUriResult.onError(e);
        } catch (InvalidPasswordException e) {
            e.printStackTrace();
            onUriResult.onInvalidPassword(e);
        }
    }

    /*public static void decryptToByteArray(FragmentActivity context, Uri encryptedInput, char[] password, IOnByteArrayResult onByteArrayResult) {
        try {
            InputStream inputStream = new BufferedInputStream(context.getContentResolver().openInputStream(encryptedInput), 1024 * 32);
            Streams cis = getCipherInputStream(inputStream, password, false);

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
    }*/

    public interface IOnUriResult {
        void onUriResult(Uri outputUri);

        void onError(Exception e);

        void onInvalidPassword(InvalidPasswordException e);
    }

    /*public interface IOnByteArrayResult {
        void onBytesResult(byte[] bytes);

        void onError(Exception e);

        void onInvalidPassword(InvalidPasswordException e);
    }*/

}
