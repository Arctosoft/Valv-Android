package se.arctosoft.vault;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.EditorInfo;

import androidx.appcompat.app.AppCompatActivity;

import se.arctosoft.vault.databinding.ActivityLaunchBinding;
import se.arctosoft.vault.encryption.Password;
import se.arctosoft.vault.utils.Settings;

public class LaunchActivity extends AppCompatActivity {
    private ActivityLaunchBinding binding;
    private Settings settings;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityLaunchBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        init();
    }

    private void init() {
        settings = Settings.getInstance(this);
        Password.lock(this, settings);

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
                if (length >= 8) {
                    binding.btnUnlock.setVisibility(View.VISIBLE);
                } else {
                    binding.btnUnlock.setVisibility(View.GONE);
                }
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
            if (binding.btnUnlock.getVisibility() != View.VISIBLE) {
                return;
            }
            settings.setTempPassword(binding.eTPassword.getText().toString().toCharArray());
            startActivity(new Intent(this, GalleryActivity.class));
            binding.eTPassword.postDelayed(() -> binding.eTPassword.setText(null), 100);
        });
    }

    @Override
    protected void onDestroy() {
        Password.lock(this, settings);
        super.onDestroy();
    }
}