//package se.arctosoft.vault;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.documentfile.provider.DocumentFile;

import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.upstream.DataSource;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.GeneralSecurityException;
import java.security.spec.KeySpec;

import javax.crypto.Cipher;
import javax.crypto.CipherOutputStream;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;

import se.arctosoft.vault.data.FileType;
import se.arctosoft.vault.databinding.ActivityTestBinding;
import se.arctosoft.vault.encryption.ChaCha20DataSourceFactory;
import se.arctosoft.vault.encryption.Encryption;
import se.arctosoft.vault.utils.Settings;
import se.arctosoft.vault.utils.Toaster;

/*public class TestActivity extends AppCompatActivity {
    private static final String TAG = "TestActivity";
    private ActivityTestBinding binding;
    private ExoPlayer player;

    private Uri selectedFile;
    private Settings settings;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityTestBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        settings = Settings.getInstance(this);
        settings.setTempPassword("2".toCharArray());
        chooseDocuments();
    }

    private void chooseFolder() {
        startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), 2);
    }

    private void chooseDocuments() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("video/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, false);

        startActivityForResult(intent, 1);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1 && resultCode == Activity.RESULT_OK) {
            if (data != null) {
                selectedFile = data.getData();
                playVideo(selectedFile);
                //chooseFolder();
            }
        } else if (requestCode == 2 && resultCode == Activity.RESULT_OK) {
            Uri uri = data.getData();
            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            DocumentFile directory = DocumentFile.fromTreeUri(this, uri);
            try {
                encrypt(directory);
            } catch (GeneralSecurityException | IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void encrypt(DocumentFile directory) throws GeneralSecurityException, IOException {
        SecretKeyFactory secretKeyFactory = SecretKeyFactory.getInstance(Encryption.KEY_ALGORITHM);
        KeySpec keySpec = new PBEKeySpec(settings.getTempPassword(), Encryption.TEST_SALT, Encryption.ITERATION_COUNT, Encryption.KEY_LENGTH);
        SecretKey secretKey = secretKeyFactory.generateSecret(keySpec);
        IvParameterSpec ivParameterSpec = new IvParameterSpec(Encryption.TEST_IV);

        Cipher cipher = Cipher.getInstance(Encryption.CIPHER);
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivParameterSpec);

        DocumentFile file = directory.createFile("video/*", FileType.VIDEO.encryptionPrefix + "" + System.currentTimeMillis() + "test.mp4");

        InputStream inputStream = getContentResolver().openInputStream(selectedFile);
        OutputStream fos = new BufferedOutputStream(getContentResolver().openOutputStream(file.getUri()), 1024 * 32);
        CipherOutputStream cipherOutputStream = new CipherOutputStream(fos, cipher);

        int read;
        byte[] buffer = new byte[2048];
        while ((read = inputStream.read(buffer)) != -1) {
            cipherOutputStream.write(buffer, 0, read);
        }

        cipherOutputStream.close();
        inputStream.close();
        Toaster.getInstance(this).showLong("Encrypted " + file.getUri());
    }

    private void playVideo(Uri uri) {
        DataSource.Factory dataSourceFactory = new ChaCha20DataSourceFactory(this, null);
        ProgressiveMediaSource.Factory factory = new ProgressiveMediaSource.Factory(dataSourceFactory);
        player = new ExoPlayer.Builder(this)
                .setMediaSourceFactory(factory)
                .build();
        //player.setRepeatMode(Player.REPEAT_MODE_ONE);
        MediaItem mediaItem = new MediaItem.Builder()
                .setMimeType("video/mp4")
                .setUri(uri)
                .build();
        //player.setMediaSource(factory.createMediaSource(mediaItem));
        player.setMediaItem(MediaItem.fromUri(uri));
        player.addListener(new Player.Listener() {
            @Override
            public void onPlayerError(@NonNull PlaybackException error) {
                Log.e(TAG, "onPlayerError: " + error.getErrorCodeName() + ", " + error.getMessage());
                Player.Listener.super.onPlayerError(error);
            }
        });
        binding.playerView.setPlayer(player);
        player.prepare();
        //player.setPlayWhenReady(true);
    }
}*/