package se.arctosoft.vault;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.util.Log;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.documentfile.provider.DocumentFile;

import java.util.ArrayList;
import java.util.List;

import se.arctosoft.vault.util.Encryption;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final int OPEN_DIRECTORY = 1;
    private static final int DECRYPT_FILES_IN_DIRECTORY = 2;

    private Button btnEncryptFile, btnDecryptFile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        init();
    }

    private void init() {
        btnEncryptFile = findViewById(R.id.btnEncryptFile);
        btnDecryptFile = findViewById(R.id.btnDecryptFile);
        btnEncryptFile.setOnClickListener(v -> {
            startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), OPEN_DIRECTORY);
        });
        btnDecryptFile.setOnClickListener(v -> {
            startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), DECRYPT_FILES_IN_DIRECTORY);
        });
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
                for (int i = 0; i < Math.min(4, files.size()); i++) {
                    Uri uri1 = files.get(i);
                    DocumentFile documentFile = DocumentFile.fromSingleUri(this, uri1);
                    Encryption.writeFile(this, uri1, pickedDir.createFile("*/*", ".arcv1-" + documentFile.getName()).getUri(), "mypassword1".toCharArray(), new Encryption.IOnUriResult() {
                        @Override
                        public void onUriResult(Uri uri) {
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
                int count = 0;
                for (int i = 0; i < files.size(); i++) {
                    if (count >= 4) {
                        break;
                    }
                    Uri uri1 = files.get(i);
                    DocumentFile documentFile = DocumentFile.fromSingleUri(this, uri1);
                    String name = documentFile.getName();
                    if (name.startsWith(".arcv1")) {
                        count++;
                    } else {
                        continue;
                    }
                    DocumentFile file = pickedDir.createFile("*/*", System.currentTimeMillis() + "_" + name.substring(7));
                    Encryption.decryptAndWriteFile(this, uri1, file.getUri(), "mypassword1".toCharArray(), new Encryption.IOnUriResult() {
                        @Override
                        public void onUriResult(Uri uri) {
                            DocumentFile documentFile = DocumentFile.fromSingleUri(MainActivity.this, uri);
                            Log.e(TAG, "onUriResult: new name2 is " + documentFile.getName());
                        }

                        @Override
                        public void onError(Exception e) {

                        }
                    });
                }
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