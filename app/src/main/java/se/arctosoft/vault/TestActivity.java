package se.arctosoft.vault;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.documentfile.provider.DocumentFile;

import com.bumptech.glide.Glide;

import se.arctosoft.vault.util.Encryption;

public class TestActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final int PICK_IMAGE_FILE = 2;
    private static final int SAVE_IMAGE_FILE = 3;
    private static final int READ_ENCRYPTED_FILE = 4;
    private static final int SAVE_DECRYPTED_FILE = 5;

    private Button btnEncryptFile, btnDecryptFile;
    private ImageView imgPreview, imgPreview2;

    private Uri input, encInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_test);

        init();
    }

    private void init() {
        btnEncryptFile = findViewById(R.id.btnEncryptFile);
        btnDecryptFile = findViewById(R.id.btnDecryptFile);
        imgPreview = findViewById(R.id.imgPreview);
        imgPreview2 = findViewById(R.id.imgPreview2);

        btnEncryptFile.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("image/*");

            // Optionally, specify a URI for the file that should appear in the system file picker when it loads.
            //intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, pickerInitialUri);

            startActivityForResult(intent, PICK_IMAGE_FILE);
        });

        btnDecryptFile.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");

            // Optionally, specify a URI for the file that should appear in the system file picker when it loads.
            //intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, pickerInitialUri);

            startActivityForResult(intent, READ_ENCRYPTED_FILE);
        });
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent resultData) {
        super.onActivityResult(requestCode, resultCode, resultData);
        if (requestCode == PICK_IMAGE_FILE && resultCode == Activity.RESULT_OK) {
            // The result data contains a URI for the document or directory that
            // the user selected.
            if (resultData != null) {
                Uri uri = resultData.getData();
                this.input = uri;
                Log.d(TAG, "onActivityResult: " + uri);
                // Perform operations on the document using its URI.
                DocumentFile documentFile = DocumentFile.fromSingleUri(this, uri);
                if (documentFile != null) {
                    Log.d(TAG, "onActivityResult: name: " + documentFile.getName() + " " + documentFile.getType());
                    Glide.with(this).load(uri).into(imgPreview);
                    createFile(documentFile.getName() + ".arcv1", SAVE_IMAGE_FILE);
                }
            }
        } else if (requestCode == SAVE_IMAGE_FILE && resultCode == Activity.RESULT_OK) {
            if (resultData != null) {
                Uri uri = resultData.getData();
                Log.d(TAG, "onActivityResult: " + uri);
                char[] pw = "mypassword1".toCharArray();
                Log.d(TAG, "onActivityResult: do encrypt\n---------");
                /*Encryption.writeFile(this, this.input, uri, documentFile.getName(), pw, new Encryption.IOnUriResult() {
                    @Override
                    public void onUriResult(Uri uri) {
                        Log.d(TAG, "onActivityResult: done: " + uri);
                        btnDecryptFile.setVisibility(View.VISIBLE);
                    }

                    @Override
                    public void onError(Exception e) {

                    }
                });*/
            }
        } else if (requestCode == READ_ENCRYPTED_FILE && resultCode == Activity.RESULT_OK) {
            if (resultData != null) {
                Uri uri = resultData.getData();
                this.encInput = uri;
                Log.d(TAG, "onActivityResult: " + uri);
                DocumentFile documentFile = DocumentFile.fromSingleUri(this, uri);
                if (documentFile != null) {
                    Log.d(TAG, "onActivityResult: name: " + documentFile.getName() + " " + documentFile.getType());
                    String name = documentFile.getName();
                    createFile(name.substring(0, name.length() - 6), SAVE_DECRYPTED_FILE);
                }
            }
        } else if (requestCode == SAVE_DECRYPTED_FILE && resultCode == Activity.RESULT_OK) {
            if (resultData != null) {
                Uri uri = resultData.getData();
                Log.d(TAG, "onActivityResult: " + uri);
                char[] pw = "mypassword1".toCharArray();
                Log.d(TAG, "onActivityResult: do decrypt\n---------");
                /*Encryption.decryptAndWriteFile(this, this.encInput, uri, pw, new Encryption.IOnUriResult() {
                    @Override
                    public void onUriResult(Uri uri) {
                        Log.d(TAG, "onActivityResult: done: " + uri);
                        DocumentFile documentFile = DocumentFile.fromSingleUri(TestActivity.this, uri);
                        if (documentFile != null) {
                            Log.d(TAG, "onActivityResult: name: " + documentFile.getName() + " " + documentFile.getType());
                            Glide.with(TestActivity.this).load(uri).into(imgPreview2);
                        }
                    }

                    @Override
                    public void onError(Exception e) {
                        DocumentFile.fromSingleUri(TestActivity.this, uri).delete();
                    }
                });*/
            }
        }
    }

    private void createFile(String name, int requestCode) { // https://developer.android.com/training/data-storage/shared/documents-files#create-file
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_TITLE, name);

        // Optionally, specify a URI for the directory that should be opened in
        // the system file picker when your app creates the document.
        //intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, pickerInitialUri);

        startActivityForResult(intent, requestCode);
    }


}