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

package se.arctosoft.vault;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.WindowManager;
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
    public static String EXTRA_ONLY_UNLOCK = "u";

    private ActivityLaunchBinding binding;
    private Settings settings;
    private AtomicBoolean isStarting;
    private boolean onlyUnlock;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        binding = ActivityLaunchBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        onlyUnlock = getIntent().getBooleanExtra(EXTRA_ONLY_UNLOCK, false);
        init();
    }

    private void init() {
        settings = Settings.getInstance(this);
        isStarting = new AtomicBoolean(false);
        if (!onlyUnlock) {
            Password.lock(this, settings);
        }

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
                if (onlyUnlock) {
                    finish();
                } else {
                    startActivity(new Intent(this, GalleryActivity.class));
                    binding.eTPassword.postDelayed(() -> {
                        binding.eTPassword.setText(null);
                        binding.eTPassword.clearFocus();
                        binding.getRoot().requestFocus();
                        isStarting.set(false);
                    }, 400);
                }
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
        Log.d(TAG, "onBackPressed: ");
        Password.lock(this, settings);
        finishAffinity();
        super.onBackPressed();
    }
}