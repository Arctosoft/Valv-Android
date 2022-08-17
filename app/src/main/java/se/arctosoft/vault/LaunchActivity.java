package se.arctosoft.vault;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.EditorInfo;

import androidx.appcompat.app.AppCompatActivity;

import java.util.concurrent.atomic.AtomicBoolean;

import se.arctosoft.vault.databinding.ActivityLaunchBinding;
import se.arctosoft.vault.encryption.Password;
import se.arctosoft.vault.utils.Dialogs;
import se.arctosoft.vault.utils.Settings;

public class LaunchActivity extends AppCompatActivity {
    private static final String TAG = "LaunchActivity";
    public static long GLIDE_KEY = System.currentTimeMillis();

    private ActivityLaunchBinding binding;
    private Settings settings;
    private AtomicBoolean isStarting;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityLaunchBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        init();
    }

    private void init() {
        settings = Settings.getInstance(this);
        isStarting = new AtomicBoolean(false);
        Password.lock(this, settings);

        setListeners();
    }

    private void setListeners() {
        binding.eTPassword.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                int length = s.length();
                binding.btnUnlock.setEnabled(length > 0);
            }
        });
        binding.eTPassword.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_SEND) {
                binding.btnUnlock.performClick();
                return true;
            }
            return false;
        });
        binding.btnUnlock.setOnClickListener(v -> {
            if (isStarting.compareAndSet(false, true)) {
                binding.btnUnlock.setEnabled(false);
                settings.setTempPassword(binding.eTPassword.getText().toString().toCharArray());
                startActivity(new Intent(this, GalleryActivity.class));
                binding.eTPassword.postDelayed(() -> {
                    binding.eTPassword.setText(null);
                    binding.eTPassword.clearFocus();
                    binding.getRoot().requestFocus();
                    isStarting.set(false);
                }, 400);
            }
        });
        binding.btnHelp.setOnClickListener(v -> Dialogs.showTextDialog(this, null, getString(R.string.launcher_help_message)));
    }

    @Override
    protected void onResume() {
        GLIDE_KEY = System.currentTimeMillis();
        super.onResume();
    }

    @Override
    public void onBackPressed() {
        Log.e(TAG, "onBackPressed: ");
        Password.lock(this, settings);
        finishAffinity();
        super.onBackPressed();
    }
}