package se.arctosoft.vault;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.util.Log;
import android.view.LayoutInflater;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.documentfile.provider.DocumentFile;

import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.List;

import se.arctosoft.vault.encryption.Encryption;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final int OPEN_DIRECTORY = 1;
    private static final int DECRYPT_FILES_IN_DIRECTORY = 2;
    private static final int LOAD_IMAGES = 3;

    private Button btnEncryptFile, btnDecryptFile, btnLoadImages, btnOpenGallery;
    private LinearLayout lLImages;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        init();
    }

    private void init() {
        btnEncryptFile = findViewById(R.id.btnEncryptFile);
        btnDecryptFile = findViewById(R.id.btnDecryptFile);
        btnLoadImages = findViewById(R.id.btnLoadImages);
        btnOpenGallery = findViewById(R.id.btnOpenGallery);
        lLImages = findViewById(R.id.lLImages);

        btnEncryptFile.setOnClickListener(v -> startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), OPEN_DIRECTORY));
        btnDecryptFile.setOnClickListener(v -> startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), DECRYPT_FILES_IN_DIRECTORY));
        btnLoadImages.setOnClickListener(v -> startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), LOAD_IMAGES));
        btnOpenGallery.setOnClickListener(v -> startActivity(new Intent(this, GalleryActivity.class)));
    }

    private void loadImages(List<Uri> encryptedFiles) {
        LayoutInflater inflater = getLayoutInflater();
        if (!encryptedFiles.isEmpty()) {
            for (Uri uri : encryptedFiles) {
                Log.e(TAG, "loadImages: load " + uri);

                ImageView image = (ImageView) inflater.inflate(R.layout.image_item, lLImages, false);
                lLImages.addView(image);
                Glide.with(this)
                        .load(uri)
                        .into(image);
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == OPEN_DIRECTORY && resultCode == Activity.RESULT_OK) {
            if (data != null) {
                Uri uri = data.getData();
                getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                DocumentFile pickedDir = DocumentFile.fromTreeUri(this, uri);
                long start = System.currentTimeMillis();
                List<Uri> files = getFilesInFolder(getContentResolver(), pickedDir);
                Log.e(TAG, "onActivityResult: found " + files.size());
                Log.e(TAG, "onActivityResult: took " + (System.currentTimeMillis() - start) + " ms");
                for (int i = 0; i < Math.min(10, files.size()); i++) {
                    Uri uri1 = files.get(i);
                    DocumentFile documentFile = DocumentFile.fromSingleUri(this, uri1);
                    DocumentFile file = pickedDir.createFile("*/*", Encryption.PREFIX_IMAGE_FILE + documentFile.getName());
                    DocumentFile thumb = pickedDir.createFile("*/*", Encryption.PREFIX_THUMB + documentFile.getName());
                    Encryption.writeFile(this, uri1, file, thumb, "mypassword1".toCharArray(), new Encryption.IOnUriResult() {
                        @Override
                        public void onUriResult(Uri outputUri) {
                            Log.e(TAG, "onUriResult: new name is " + documentFile.getName());
                        }

                        @Override
                        public void onError(Exception e) {

                        }
                    });
                }
            }
        } else if (requestCode == DECRYPT_FILES_IN_DIRECTORY && resultCode == Activity.RESULT_OK) {
            if (data != null) {
                Uri uri = data.getData();
                getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                DocumentFile pickedDir = DocumentFile.fromTreeUri(this, uri);
                long start = System.currentTimeMillis();
                List<Uri> files = getFilesInFolder(getContentResolver(), pickedDir);
                Log.e(TAG, "onActivityResult: found " + files.size());
                Log.e(TAG, "onActivityResult: took " + (System.currentTimeMillis() - start) + " ms");
                for (int i = 0; i < files.size(); i++) {
                    Uri uri1 = files.get(i);
                    DocumentFile documentFile = DocumentFile.fromSingleUri(this, uri1);
                    String name = documentFile.getName();
                    if (name.startsWith(Encryption.PREFIX_THUMB) || !name.startsWith(Encryption.ENCRYPTED_PREFIX)) {
                        continue;
                    }
                    DocumentFile file = pickedDir.createFile("*/*", System.currentTimeMillis() + "_" + name.substring(9));
                    /*Encryption.decryptAndWriteFile(this, uri1, file.getUri(), "mypassword1".toCharArray(), new Encryption.IOnUriResult() {
                        @Override
                        public void onUriResult(Uri uri) {
                            DocumentFile documentFile = DocumentFile.fromSingleUri(MainActivity.this, uri);
                            Log.e(TAG, "onUriResult: new name2 is " + documentFile.getName());
                        }

                        @Override
                        public void onError(Exception e) {

                        }
                    });*/
                }
            }
        } else if (requestCode == LOAD_IMAGES && resultCode == Activity.RESULT_OK) {
            if (data != null) {
                Uri uri = data.getData();
                getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                DocumentFile pickedDir = DocumentFile.fromTreeUri(this, uri);
                long start = System.currentTimeMillis();
                List<Uri> files = getFilesInFolder(getContentResolver(), pickedDir);
                Log.e(TAG, "onActivityResult: found " + files.size());
                Log.e(TAG, "onActivityResult: took " + (System.currentTimeMillis() - start) + " ms");
                List<Uri> encryptedFiles = new ArrayList<>();
                for (int i = 0; i < files.size(); i++) {
                    Uri uri1 = files.get(i);
                    Log.e(TAG, "onActivityResult: last: " + uri1.getLastPathSegment());
                    if (uri1.getLastPathSegment().contains(Encryption.PREFIX_THUMB)) {
                        encryptedFiles.add(uri1);
                    }
                }
                loadImages(encryptedFiles);
            }
        }
    }

    @NonNull
    private List<Uri> getFilesInFolder(@NonNull ContentResolver resolver, DocumentFile pickedDir) {
        Uri realUri = DocumentsContract.buildChildDocumentsUriUsingTree(pickedDir.getUri(), DocumentsContract.getDocumentId(pickedDir.getUri()));
        List<Uri> files = new ArrayList<>();
        Cursor c = resolver.query(
                realUri,
                new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME},
                null,
                null,
                null);
        if (c == null || !c.moveToFirst()) {
            return files;
        }
        do {
            files.add(DocumentsContract.buildDocumentUriUsingTree(realUri, c.getString(0)));
        } while (c.moveToNext());
        c.close();
        return files;
    }
}