/*
 * Valv-Android
 * Copyright (C) 2024 Arctosoft AB
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

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.documentfile.provider.DocumentFile;
import androidx.fragment.app.FragmentActivity;

import com.bumptech.glide.Glide;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
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
import java.util.concurrent.atomic.AtomicBoolean;

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
    private static final int ITERATION_COUNT_V1 = 20000;
    private static final int KEY_LENGTH = 256;
    private static final int SALT_LENGTH = 16;
    private static final int IV_LENGTH = 12;
    private static final int CHECK_LENGTH = 12;
    private static final int INTEGER_LENGTH = 4;
    private static final String JSON_ORIGINAL_NAME = "originalName";

    public static final String ENCRYPTED_PREFIX = ".valv.";
    public static final String PREFIX_IMAGE_FILE = ".valv.i.1-";
    public static final String PREFIX_GIF_FILE = ".valv.g.1-";
    public static final String PREFIX_VIDEO_FILE = ".valv.v.1-";
    public static final String PREFIX_TEXT_FILE = ".valv.x.1-";
    public static final String PREFIX_NOTE_FILE = ".valv.n.1-";
    public static final String PREFIX_THUMB = ".valv.t.1-";

    public static final String ENCRYPTED_SUFFIX = ".valv";
    public static final String SUFFIX_IMAGE_FILE = "-i.valv";
    public static final String SUFFIX_GIF_FILE = "-g.valv";
    public static final String SUFFIX_VIDEO_FILE = "-v.valv";
    public static final String SUFFIX_TEXT_FILE = "-x.valv";
    public static final String SUFFIX_NOTE_FILE = "-n.valv";
    public static final String SUFFIX_THUMB = "-t.valv";

    public static String getSuffixFromMime(@Nullable String mimeType) {
        if (mimeType == null) {
            return Encryption.SUFFIX_IMAGE_FILE;
        } else if (mimeType.equals("image/gif")) {
            return Encryption.SUFFIX_GIF_FILE;
        } else if (mimeType.startsWith("image/")) {
            return Encryption.SUFFIX_IMAGE_FILE;
        } else if (mimeType.startsWith("text/")) {
            return Encryption.SUFFIX_TEXT_FILE;
        } else {
            return Encryption.SUFFIX_VIDEO_FILE;
        }
    }

    public static Pair<Boolean, Boolean> importFileToDirectory(FragmentActivity context, DocumentFile sourceFile, DocumentFile directory, char[] password, int version, @Nullable IOnProgress onProgress, AtomicBoolean interrupted) {
        if (password == null || password.length == 0) {
            throw new RuntimeException("No password");
        }

        String generatedName = StringStuff.getRandomFileName();
        DocumentFile file = directory.createFile("", generatedName + getSuffixFromMime(sourceFile.getType()));
        DocumentFile thumb = directory.createFile("", generatedName + SUFFIX_THUMB);

        if (file == null) {
            Log.e(TAG, "importFileToDirectory: could not create file from " + sourceFile.getUri());
            return new Pair<>(false, false);
        }
        try {
            createFile(context, sourceFile.getUri(), file, password, sourceFile.getName(), onProgress, version, interrupted);
        } catch (GeneralSecurityException | IOException | JSONException e) {
            e.printStackTrace();
            file.delete();
            return new Pair<>(false, false);
        }
        if (interrupted.get()) {
            file.delete();
            if (thumb != null) {
                thumb.delete();
            }
            return new Pair<>(false, false);
        }
        boolean createdThumb = false;
        try {
            createThumb(context, sourceFile.getUri(), thumb, password, sourceFile.getName(), version);
            createdThumb = true;
        } catch (GeneralSecurityException | IOException | ExecutionException |
                 InterruptedException | JSONException e) {
            e.printStackTrace();
            thumb.delete();
        }
        return new Pair<>(true, createdThumb);
    }

    public static DocumentFile importNoteToDirectory(FragmentActivity context, String note, String fileNameWithoutPrefix, DocumentFile directory, char[] password, int version) {
        if (password == null || password.length == 0) {
            throw new RuntimeException("No password");
        }

        DocumentFile file = directory.createFile("", version < 2 ? Encryption.PREFIX_NOTE_FILE + fileNameWithoutPrefix : fileNameWithoutPrefix + Encryption.SUFFIX_NOTE_FILE);

        try {
            createTextFile(context, note, file, password, fileNameWithoutPrefix, version);
        } catch (GeneralSecurityException | IOException | JSONException e) {
            Log.e(TAG, "importNoteToDirectory: failed " + e.getMessage());
            e.printStackTrace();
            file.delete();
            return null;
        }

        return file;
    }

    public static DocumentFile importTextToDirectory(FragmentActivity context, String text, @Nullable String fileNameWithoutSuffix, DocumentFile directory, char[] password, int version) {
        if (password == null || password.length == 0) {
            throw new RuntimeException("No password");
        }

        if (fileNameWithoutSuffix == null) {
            fileNameWithoutSuffix = StringStuff.getRandomFileName();
        }
        DocumentFile file = directory.createFile("", version < 2 ? Encryption.PREFIX_TEXT_FILE + fileNameWithoutSuffix : fileNameWithoutSuffix + Encryption.SUFFIX_TEXT_FILE);

        try {
            createTextFile(context, text, file, password, version < 2 ? fileNameWithoutSuffix + FileType.TEXT_V1.extension : fileNameWithoutSuffix + FileType.TEXT_V2.extension, version);
        } catch (GeneralSecurityException | IOException | JSONException e) {
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

    private static void createFile(FragmentActivity context, Uri input, DocumentFile outputFile, char[] password, String sourceFileName, @Nullable IOnProgress onProgress, int version, AtomicBoolean interrupted) throws GeneralSecurityException, IOException, JSONException {
        Streams streams = getCipherOutputStream(context, input, outputFile, password, sourceFileName, version);

        int read;
        byte[] buffer = new byte[2048];
        long progress = 0;
        while ((read = streams.inputStream.read(buffer)) != -1) {
            if (interrupted.get()) {
                streams.close();
                return;
            }
            streams.outputStream.write(buffer, 0, read);
            if (onProgress != null) {
                progress += read;
                onProgress.onProgress(progress);
            }
        }

        streams.close();
    }

    private static void createTextFile(FragmentActivity context, String input, DocumentFile outputFile, char[] password, String sourceFileName, int version) throws GeneralSecurityException, IOException, JSONException {
        Streams streams = getTextCipherOutputStream(context, input, outputFile, password, sourceFileName, version);
        streams.outputStream.write(streams.inputString.getBytes(StandardCharsets.UTF_8));
        streams.close();
    }

    private static void createThumb(FragmentActivity context, Uri input, DocumentFile outputThumbFile, char[] password, String sourceFileName, int version) throws GeneralSecurityException, IOException, ExecutionException, InterruptedException, JSONException {
        Streams streams = getCipherOutputStream(context, input, outputThumbFile, password, sourceFileName, version);

        Bitmap bitmap = Glide.with(context).asBitmap().load(input).centerCrop().submit(512, 512).get();

        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 75, stream);
        byte[] byteArray = stream.toByteArray();
        streams.outputStream.write(byteArray);
        bitmap.recycle();

        streams.close();
    }

    public static Streams getCipherInputStream(@NonNull InputStream inputStream, char[] password, boolean isThumb, int version) throws IOException, GeneralSecurityException, InvalidPasswordException, JSONException {
        if (version < 2) {
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
            KeySpec keySpec = new PBEKeySpec(password, salt, ITERATION_COUNT_V1, KEY_LENGTH);
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
        } else {
            byte[] versionBytes = new byte[INTEGER_LENGTH];
            byte[] salt = new byte[SALT_LENGTH];
            byte[] ivBytes = new byte[IV_LENGTH];
            byte[] iterationCount = new byte[INTEGER_LENGTH];
            byte[] checkBytes1 = new byte[CHECK_LENGTH];
            byte[] checkBytes2 = new byte[CHECK_LENGTH];

            //1. VERSION SALT IVBYTES ITERATIONCOUNT CHECKBYTES CHECKBYTES_ENC\n
            //2. {originalName}\n
            //3. file data
            inputStream.read(versionBytes);
            inputStream.read(salt);
            inputStream.read(ivBytes);
            inputStream.read(iterationCount);
            inputStream.read(checkBytes1);

            //final int VERSION = fromByteArray(versionBytes); // not used until version 3
            final int ITERATION_COUNT = fromByteArray(iterationCount);

            SecretKeyFactory secretKeyFactory = SecretKeyFactory.getInstance(KEY_ALGORITHM);
            KeySpec keySpec = new PBEKeySpec(password, salt, ITERATION_COUNT, KEY_LENGTH);
            SecretKey secretKey = secretKeyFactory.generateSecret(keySpec);
            IvParameterSpec ivParameterSpec = new IvParameterSpec(ivBytes);

            Cipher cipher = Cipher.getInstance(CIPHER);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, ivParameterSpec);
            CipherInputStream cipherInputStream = new MyCipherInputStream(inputStream, cipher);

            cipherInputStream.read(checkBytes2);
            if (!Arrays.equals(checkBytes1, checkBytes2)) {
                throw new InvalidPasswordException("Invalid password");
            }
            if (cipherInputStream.read() != 0x0A) { // jump to line 2
                throw new IOException("Not valid file");
            }
            byte[] jsonBytes = readUntilNewline(cipherInputStream); // read line 2
            JSONObject json = new JSONObject(new String(jsonBytes, StandardCharsets.UTF_8));
            String originalName = json.has(JSON_ORIGINAL_NAME) ? json.getString(JSON_ORIGINAL_NAME) : "";

            return new Streams(cipherInputStream, secretKey, originalName); // pass on line 3
        }
    }

    @NonNull
    private static byte[] readUntilNewline(@NonNull InputStream inputStream) throws IOException {
        ArrayList<Byte> bytes = new ArrayList<>();
        byte[] read = new byte[1];
        while ((inputStream.read(read)) > 0) {
            if (read[0] == 0x0A) { // newline \n character
                break;
            }
            bytes.add(read[0]);
        }
        byte[] arr = new byte[bytes.size()];
        for (int i = 0; i < bytes.size(); i++) {
            arr[i] = bytes.get(i);
        }
        return arr;
    }

    private static Streams getCipherOutputStream(FragmentActivity context, Uri input, DocumentFile outputFile, char[] password, String sourceFileName, int version) throws GeneralSecurityException, IOException, JSONException {
        if (version < 2) {
            SecureRandom sr = SecureRandom.getInstanceStrong();
            byte[] salt = new byte[SALT_LENGTH];
            byte[] ivBytes = new byte[IV_LENGTH];
            generateSecureRandom(sr, salt, ivBytes, null);

            SecretKeyFactory secretKeyFactory = SecretKeyFactory.getInstance(KEY_ALGORITHM);
            KeySpec keySpec = new PBEKeySpec(password, salt, ITERATION_COUNT_V1, KEY_LENGTH);
            SecretKey secretKey = secretKeyFactory.generateSecret(keySpec);
            IvParameterSpec ivParameterSpec = new IvParameterSpec(ivBytes);

            Cipher cipher = Cipher.getInstance(CIPHER);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivParameterSpec);

            InputStream inputStream = context.getContentResolver().openInputStream(input);
            OutputStream fos = new BufferedOutputStream(context.getContentResolver().openOutputStream(outputFile.getUri()), 1024 * 32);
            writeSaltAndIV(null, salt, ivBytes, null, null, fos);
            fos.flush();
            CipherOutputStream cipherOutputStream = new CipherOutputStream(fos, cipher);
            cipherOutputStream.write(("\n" + sourceFileName + "\n").getBytes(StandardCharsets.UTF_8));
            return new Streams(inputStream, cipherOutputStream, secretKey);
        } else {
            SecureRandom sr = SecureRandom.getInstanceStrong();
            Settings settings = Settings.getInstance(context);
            final int ITERATION_COUNT = settings.getIterationCount();
            byte[] versionBytes = toByteArray(version);
            byte[] salt = new byte[SALT_LENGTH];
            byte[] ivBytes = new byte[IV_LENGTH];
            byte[] iterationCount = toByteArray(ITERATION_COUNT);
            byte[] checkBytes = new byte[CHECK_LENGTH];
            generateSecureRandom(sr, salt, ivBytes, checkBytes);

            SecretKeyFactory secretKeyFactory = SecretKeyFactory.getInstance(KEY_ALGORITHM);
            KeySpec keySpec = new PBEKeySpec(password, salt, ITERATION_COUNT, KEY_LENGTH);
            SecretKey secretKey = secretKeyFactory.generateSecret(keySpec);
            IvParameterSpec ivParameterSpec = new IvParameterSpec(ivBytes);

            Cipher cipher = Cipher.getInstance(CIPHER);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivParameterSpec);

            InputStream inputStream = context.getContentResolver().openInputStream(input);
            OutputStream fos = new BufferedOutputStream(context.getContentResolver().openOutputStream(outputFile.getUri()), 1024 * 32);
            writeSaltAndIV(versionBytes, salt, ivBytes, iterationCount, checkBytes, fos);
            fos.flush();
            CipherOutputStream cipherOutputStream = new CipherOutputStream(fos, cipher);
            cipherOutputStream.write(checkBytes);
            JSONObject json = new JSONObject();
            json.put(JSON_ORIGINAL_NAME, sourceFileName);
            cipherOutputStream.write(("\n" + json + "\n").getBytes(StandardCharsets.UTF_8));
            return new Streams(inputStream, cipherOutputStream, secretKey);
        }
    }

    private static Streams getTextCipherOutputStream(FragmentActivity context, String input, DocumentFile outputFile, char[] password, String sourceFileName, int version) throws GeneralSecurityException, IOException, JSONException {
        if (version < 2) {
            SecureRandom sr = SecureRandom.getInstanceStrong();
            byte[] salt = new byte[SALT_LENGTH];
            byte[] ivBytes = new byte[IV_LENGTH];
            generateSecureRandom(sr, salt, ivBytes, null);

            SecretKeyFactory secretKeyFactory = SecretKeyFactory.getInstance(KEY_ALGORITHM);
            KeySpec keySpec = new PBEKeySpec(password, salt, ITERATION_COUNT_V1, KEY_LENGTH);
            SecretKey secretKey = secretKeyFactory.generateSecret(keySpec);
            IvParameterSpec ivParameterSpec = new IvParameterSpec(ivBytes);

            Cipher cipher = Cipher.getInstance(CIPHER);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivParameterSpec);

            OutputStream fos = new BufferedOutputStream(context.getContentResolver().openOutputStream(outputFile.getUri()), 1024 * 32);
            writeSaltAndIV(null, salt, ivBytes, null, null, fos);
            fos.flush();
            CipherOutputStream cipherOutputStream = new CipherOutputStream(fos, cipher);
            cipherOutputStream.write(("\n" + sourceFileName + "\n").getBytes(StandardCharsets.UTF_8));
            return new Streams(input, cipherOutputStream, secretKey);
        } else {
            SecureRandom sr = SecureRandom.getInstanceStrong();
            Settings settings = Settings.getInstance(context);
            final int ITERATION_COUNT = settings.getIterationCount();
            byte[] versionBytes = toByteArray(version);
            byte[] salt = new byte[SALT_LENGTH];
            byte[] ivBytes = new byte[IV_LENGTH];
            byte[] iterationCount = toByteArray(ITERATION_COUNT);
            byte[] checkBytes = new byte[CHECK_LENGTH];
            generateSecureRandom(sr, salt, ivBytes, checkBytes);

            SecretKeyFactory secretKeyFactory = SecretKeyFactory.getInstance(KEY_ALGORITHM);
            KeySpec keySpec = new PBEKeySpec(password, salt, ITERATION_COUNT, KEY_LENGTH);
            SecretKey secretKey = secretKeyFactory.generateSecret(keySpec);
            IvParameterSpec ivParameterSpec = new IvParameterSpec(ivBytes);

            Cipher cipher = Cipher.getInstance(CIPHER);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivParameterSpec);

            OutputStream fos = new BufferedOutputStream(context.getContentResolver().openOutputStream(outputFile.getUri()), 1024 * 32);
            writeSaltAndIV(versionBytes, salt, ivBytes, iterationCount, checkBytes, fos);
            fos.flush();
            CipherOutputStream cipherOutputStream = new CipherOutputStream(fos, cipher);
            cipherOutputStream.write(checkBytes);
            JSONObject json = new JSONObject();
            json.put(JSON_ORIGINAL_NAME, sourceFileName);
            cipherOutputStream.write(("\n" + json + "\n").getBytes(StandardCharsets.UTF_8));
            return new Streams(input, cipherOutputStream, secretKey);
        }
    }

    public static byte[] toByteArray(int value) {
        return new byte[]{(byte) (value >> 24), (byte) (value >> 16), (byte) (value >> 8), (byte) value};
    }

    public static int fromByteArray(byte[] bytes) {
        return bytes[0] << 24 | (bytes[1] & 0xFF) << 16 | (bytes[2] & 0xFF) << 8 | (bytes[3] & 0xFF);
    }

    @NonNull
    public static String getOriginalFilename(@NonNull InputStream inputStream, char[] password, boolean isThumb, int version) {
        String name = "";
        try {
            Streams streams = getCipherInputStream(inputStream, password, isThumb, version);
            name = streams.getOriginalFileName();
            streams.close();
        } catch (IOException | GeneralSecurityException | InvalidPasswordException |
                 JSONException e) {
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

    private static void writeSaltAndIV(@Nullable byte[] version, byte[] salt, byte[] ivBytes, @Nullable byte[] iterationCount, @Nullable byte[] checkBytes, OutputStream fos) throws IOException {
        if (version != null) {
            fos.write(version);
        }
        fos.write(salt);
        fos.write(ivBytes);
        if (iterationCount != null) {
            fos.write(iterationCount);
        }
        if (checkBytes != null) {
            fos.write(checkBytes);
        }
    }

    public static String readEncryptedTextFromUri(@NonNull Uri encryptedInput, Context context, int version, char[] password) {
        try {
            InputStream inputStream = context.getContentResolver().openInputStream(encryptedInput);
            Streams cis = getCipherInputStream(inputStream, password, false, version);

            BufferedReader br = new BufferedReader(new InputStreamReader(cis.inputStream, StandardCharsets.UTF_8));

            StringBuilder sb = new StringBuilder();
            int read;
            char[] buffer = new char[8192];
            while ((read = br.read(buffer)) != -1) {
                sb.append(buffer, 0, read);
            }

            return sb.toString();
        } catch (GeneralSecurityException | InvalidPasswordException | JSONException |
                 IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static void decryptToCache(FragmentActivity context, Uri encryptedInput, @Nullable String extension, int version, char[] password, IOnUriResult onUriResult) {
        new Thread(() -> {
            try {
                InputStream inputStream = new BufferedInputStream(context.getContentResolver().openInputStream(encryptedInput), 1024 * 32);

                File cacheDir = context.getCacheDir();
                cacheDir.mkdir();
                Path file = Files.createTempFile(null, extension);
                Uri fileUri = Uri.fromFile(file.toFile());
                OutputStream fos = context.getContentResolver().openOutputStream(fileUri);
                Streams cis = getCipherInputStream(inputStream, password, false, version);

                int read;
                byte[] buffer = new byte[2048];
                while ((read = cis.inputStream.read(buffer)) != -1) {
                    fos.write(buffer, 0, read);
                }
                fos.close();
                cis.close();
                inputStream.close();

                context.runOnUiThread(() -> onUriResult.onUriResult(fileUri));
            } catch (GeneralSecurityException | IOException | JSONException e) {
                e.printStackTrace();
                context.runOnUiThread(() -> onUriResult.onError(e));
            } catch (InvalidPasswordException e) {
                Log.e(TAG, "decryptToCache: catch invalid password");
                e.printStackTrace();
                context.runOnUiThread(() -> onUriResult.onInvalidPassword(e));
            }
        }).start();
    }

    public static void decryptAndExport(FragmentActivity context, Uri encryptedInput, DocumentFile directory, GalleryFile galleryFile, boolean isVideo, int version, char[] password, IOnUriResult onUriResult) {
        DocumentFile documentFile = directory != null ? directory : DocumentFile.fromTreeUri(context, encryptedInput);
        String originalFileName = galleryFile.getOriginalName();
        if (originalFileName == null) {
            try {
                originalFileName = Encryption.getOriginalFilename(context.getContentResolver().openInputStream(encryptedInput), password, false, version);
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
            Streams cis = getCipherInputStream(inputStream, password, false, version);

            int read;
            byte[] buffer = new byte[2048];
            while ((read = cis.inputStream.read(buffer)) != -1) {
                fos.write(buffer, 0, read);
            }
            fos.close();
            cis.close();
            inputStream.close();

            onUriResult.onUriResult(file.getUri());
        } catch (GeneralSecurityException | IOException | JSONException e) {
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
