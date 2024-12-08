package se.arctosoft.vault;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.documentfile.provider.DocumentFile;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import java.util.ArrayList;
import java.util.List;

import se.arctosoft.vault.data.Password;
import se.arctosoft.vault.databinding.ActivityMainBinding;
import se.arctosoft.vault.utils.FileStuff;
import se.arctosoft.vault.utils.Settings;
import se.arctosoft.vault.viewmodel.ShareViewModel;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    public static long GLIDE_KEY = System.currentTimeMillis();

    private AppBarConfiguration appBarConfiguration;
    private ActivityMainBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Settings settings = Settings.getInstance(this);
        if (settings.isSecureFlag()) {
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        }

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);

        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment_content_main);
        assert navHostFragment != null;
        NavController navController = navHostFragment.getNavController();
        appBarConfiguration = new AppBarConfiguration.Builder(navController.getGraph()).build();
        NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration);

        navController.addOnDestinationChangedListener((controller, destination, arguments) -> {
            if (destination.getId() == R.id.password) {
                binding.appBar.setVisibility(View.GONE);
            } else {
                binding.appBar.setVisibility(View.VISIBLE);
            }
        });

        Intent intent = getIntent();
        String action = intent.getAction();
        String type = intent.getType();

        if (type != null && Intent.ACTION_SEND.equals(action)) {
            if (type.startsWith("image/") || type.startsWith("video/")) {
                handleSendSingle(intent);
            }
        } else if (type != null && Intent.ACTION_SEND_MULTIPLE.equals(action)) {
            if (type.startsWith("image/") || type.startsWith("video/") || type.equals("*/*")) {
                handleSendMultiple(intent);
            }
        }
    }

    private void handleSendSingle(@NonNull Intent intent) {
        Uri uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
        if (uri != null) {
            List<Uri> list = new ArrayList<>(1);
            list.add(uri);
            List<DocumentFile> documentFiles = FileStuff.getDocumentsFromShareIntent(this, list);
            if (!documentFiles.isEmpty()) {
                addSharedFiles(documentFiles);
            }
        }
    }

    private void handleSendMultiple(@NonNull Intent intent) {
        ArrayList<Uri> uris = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
        if (uris != null) {
            List<DocumentFile> documentFiles = FileStuff.getDocumentsFromShareIntent(this, uris);
            if (!documentFiles.isEmpty()) {
                addSharedFiles(documentFiles);
            }
        }
    }

    private void addSharedFiles(@NonNull List<DocumentFile> documentFiles) {
        Log.e(TAG, "addSharedFiles: " + documentFiles.size());
        ShareViewModel shareViewModel = new ViewModelProvider(this).get(ShareViewModel.class);
        shareViewModel.clear();
        shareViewModel.getFilesReceived().addAll(documentFiles);
        shareViewModel.setHasData(true);
    }

    @Override
    public boolean onSupportNavigateUp() {
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
        return NavigationUI.navigateUp(navController, appBarConfiguration) || super.onSupportNavigateUp();
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "onDestroy: " + isChangingConfigurations());
        if (!isChangingConfigurations()) {
            Password.lock(this);
        }
        super.onDestroy();
    }
}