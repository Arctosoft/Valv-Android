package se.arctosoft.vault;

import static androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG;

import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.FragmentActivity;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreferenceCompat;

import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.concurrent.Executor;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;

import se.arctosoft.vault.data.Password;
import se.arctosoft.vault.encryption.Encryption;
import se.arctosoft.vault.utils.Dialogs;
import se.arctosoft.vault.utils.Settings;
import se.arctosoft.vault.utils.Toaster;

public class SettingsFragment extends PreferenceFragmentCompat implements MenuProvider {
    private static final String TAG = "SettingsFragment";

    private BiometricPrompt biometricPrompt;
    private BiometricPrompt.PromptInfo promptInfo;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.root_preferences, rootKey);

        Preference iterationCount = findPreference(Settings.PREF_ENCRYPTION_ITERATION_COUNT);
        Preference editFolders = findPreference(Settings.PREF_APP_EDIT_FOLDERS);
        SwitchPreferenceCompat biometrics = findPreference(Settings.PREF_APP_BIOMETRICS);
        SwitchPreferenceCompat useDiskCache = findPreference(Settings.PREF_ENCRYPTION_USE_DISK_CACHE);
        SwitchPreferenceCompat secure = findPreference(Settings.PREF_APP_SECURE);
        SwitchPreferenceCompat deleteByDefault = findPreference(Settings.PREF_ENCRYPTION_DELETE_BY_DEFAULT);
        SwitchPreferenceCompat exitOnLock = findPreference(Settings.PREF_APP_EXIT_ON_LOCK);

        FragmentActivity activity = requireActivity();
        Settings settings = Settings.getInstance(activity);

        Executor executor = ContextCompat.getMainExecutor(activity);
        biometricPrompt = new BiometricPrompt(activity, executor, new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                super.onAuthenticationError(errorCode, errString);
                Log.e(TAG, "onAuthenticationError: " + errorCode + ", " + errString);
                biometrics.setChecked(false);
            }

            @Override
            public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                super.onAuthenticationSucceeded(result);
                Log.e(TAG, "onAuthenticationSucceeded: " + result);
                BiometricPrompt.CryptoObject cryptoObject = result.getCryptoObject();
                if (cryptoObject != null) {
                    try {
                        Cipher cipher = cryptoObject.getCipher();
                        byte[] iv = cipher.getIV();
                        byte[] encryptedInfo = cipher.doFinal(Encryption.toBytes(Password.getInstance().getPassword()));

                        settings.setBiometricsEnabled(iv, encryptedInfo);
                        Toaster.getInstance(activity).showLong(getString(R.string.settings_biometrics_enabled));
                    } catch (BadPaddingException | IllegalBlockSizeException e) {
                        e.printStackTrace();
                        Toaster.getInstance(activity).showShort(e.toString());
                        biometrics.setChecked(false);
                    }
                }
            }

            @Override
            public void onAuthenticationFailed() {
                super.onAuthenticationFailed();
                Log.e(TAG, "onAuthenticationFailed: ");
            }
        });

        promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle(getString(R.string.biometrics_prompt_title))
                .setSubtitle(getString(R.string.biometrics_prompt_subtitle))
                .setNegativeButtonText(getString(R.string.cancel))
                .setAllowedAuthenticators(BIOMETRIC_STRONG)
                .build();

        iterationCount.setSummary(getString(R.string.settings_iteration_count_summary, settings.getIterationCount()));
        iterationCount.setOnPreferenceClickListener(preference -> {
            Dialogs.showSetIterationCountDialog(activity, settings.getIterationCount() + "", text -> {
                try {
                    int ic = Integer.parseInt(text);
                    if (ic < 20000 || ic > 500000) {
                        Toaster.getInstance(activity).showLong(getString(R.string.settings_iteration_count_hint));
                        return;
                    }
                    settings.setIterationCount(ic);
                    iterationCount.setSummary(getString(R.string.settings_iteration_count_summary, ic));
                } catch (NumberFormatException e) {
                    e.printStackTrace();
                }
            });
            return true;
        });

        secure.setOnPreferenceChangeListener((preference, newValue) -> {
            if ((boolean) newValue) {
                requireActivity().getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
            } else {
                requireActivity().getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
            }
            settings.setSecureFlag((boolean) newValue);
            return true;
        });

        useDiskCache.setOnPreferenceChangeListener((preference, newValue) -> {
            settings.setUseDiskCache((boolean) newValue);
            return true;
        });

        deleteByDefault.setOnPreferenceChangeListener((preference, newValue) -> {
            settings.setDeleteByDefault((boolean) newValue);
            return true;
        });

        exitOnLock.setOnPreferenceChangeListener((preference, newValue) -> {
            settings.setExitOnLock((boolean) newValue);
            return true;
        });

        editFolders.setOnPreferenceClickListener(preference -> {
            Dialogs.showEditIncludedFolders(activity, settings, selectedToRemove -> {
                settings.removeGalleryDirectories(selectedToRemove);
                Toaster.getInstance(activity).showLong(getResources().getQuantityString(R.plurals.edit_included_removed, selectedToRemove.size(), selectedToRemove.size()));
            });
            return true;
        });

        biometrics.setOnPreferenceChangeListener((preference, newValue) -> {
            if ((boolean) newValue) {
                return enableBiometrics();
            } else {
                try {
                    Encryption.deleteBiometricSecretKey();
                } catch (NoSuchAlgorithmException | KeyStoreException | CertificateException |
                         IOException e) {
                    e.printStackTrace();
                    Toaster.getInstance(activity).showLong(e.toString());
                }
                settings.setBiometricsEnabled(null, null);
            }
            return true;
        });
    }

    private boolean enableBiometrics() {
        BiometricManager biometricManager = BiometricManager.from(requireContext());
        if (biometricManager.canAuthenticate(BIOMETRIC_STRONG) != BiometricManager.BIOMETRIC_SUCCESS) {
            Toaster.getInstance(requireContext()).showLong(getString(R.string.biometrics_not_enabled));
            return false;
        }
        try {
            Cipher cipher = Encryption.getBiometricCipher();
            SecretKey secretKey = Encryption.getOrGenerateBiometricSecretKey();
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            biometricPrompt.authenticate(promptInfo, new BiometricPrompt.CryptoObject(cipher));

            return true;
        } catch (KeyStoreException | CertificateException | IOException |
                 NoSuchAlgorithmException | NoSuchProviderException |
                 InvalidAlgorithmParameterException | UnrecoverableKeyException |
                 InvalidKeyException | NoSuchPaddingException e) {
            e.printStackTrace();
            Toaster.getInstance(requireContext()).showShort(e.toString());
            return false;
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        requireActivity().addMenuProvider(this, getViewLifecycleOwner());

        NavController navController = NavHostFragment.findNavController(this);
        OnBackPressedCallback callback = new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (!navController.popBackStack()) {
                    FragmentActivity activity = requireActivity();
                    Password.lock(activity, false);
                    activity.finish();
                }
            }
        };
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), callback);
    }

    @Override
    public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {
        menu.clear();
    }

    @Override
    public boolean onMenuItemSelected(@NonNull MenuItem menuItem) {
        return false;
    }
}