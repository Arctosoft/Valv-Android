package se.arctosoft.vault;

import android.os.Bundle;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

import java.util.List;

import se.arctosoft.vault.adapters.GalleryFullscreenAdapter;
import se.arctosoft.vault.data.GalleryFile;
import se.arctosoft.vault.databinding.ActivityGalleryFullscreenBinding;
import se.arctosoft.vault.utils.Settings;

public class GalleryFullscreenActivity extends AppCompatActivity {
    private static final String TAG = "GalleryFullscreenActivi";
    public static final String EXTRA_POSITION = "p";
    public static List<GalleryFile> FILES = null;

    private ActivityGalleryFullscreenBinding binding;
    private List<GalleryFile> galleryFiles;
    private Settings settings;
    private GalleryFullscreenAdapter galleryFullscreenAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityGalleryFullscreenBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        Log.e(TAG, "onCreate: ");
        Bundle extras = getIntent().getExtras();
        int pos = 0;
        if (extras != null) {
            pos = extras.getInt(EXTRA_POSITION, 0);
        }
        if (FILES == null) {
            finish();
            return;
        }
        galleryFiles = FILES;
        FILES = null;

        init(pos);
    }

    private void init(int pos) {
        settings = Settings.getInstance(this);
        if (!settings.isUnlocked()) {
            finish();
            return;
        }
        galleryFullscreenAdapter = new GalleryFullscreenAdapter(this, galleryFiles);
        binding.viewPager.setAdapter(galleryFullscreenAdapter);
        binding.viewPager.setCurrentItem(pos);
    }

    @Override
    public void onBackPressed() {
        GalleryDirectoryActivity.LAST_POS = binding.viewPager.getCurrentItem();
        super.onBackPressed();
    }
}