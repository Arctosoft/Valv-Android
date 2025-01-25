package se.arctosoft.vault;

import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.FragmentActivity;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreferenceCompat;

import se.arctosoft.vault.data.Password;
import se.arctosoft.vault.utils.Dialogs;
import se.arctosoft.vault.utils.Settings;
import se.arctosoft.vault.utils.Toaster;

public class SettingsFragment extends PreferenceFragmentCompat implements MenuProvider {
    private static final String TAG = "SettingsFragment";

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.root_preferences, rootKey);

        Preference iterationCount = findPreference(Settings.PREF_ENCRYPTION_ITERATION_COUNT);
        Preference editFolders = findPreference(Settings.PREF_APP_EDIT_FOLDERS);
        SwitchPreferenceCompat useDiskCache = findPreference(Settings.PREF_ENCRYPTION_USE_DISK_CACHE);
        SwitchPreferenceCompat secure = findPreference(Settings.PREF_APP_SECURE);
        SwitchPreferenceCompat deleteByDefault = findPreference(Settings.PREF_ENCRYPTION_DELETE_BY_DEFAULT);
        SwitchPreferenceCompat showDecryptableOnly = findPreference(Settings.PREF_ENCRYPTION_DISPLAY_DECRYPTABLE_ONLY);
        SwitchPreferenceCompat exitOnLock = findPreference(Settings.PREF_APP_EXIT_ON_LOCK);

        FragmentActivity activity = requireActivity();
        Settings settings = Settings.getInstance(activity);

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

        showDecryptableOnly.setOnPreferenceChangeListener((preference, newValue) -> {
            settings.setDisplayDecryptableFilesOnly((boolean) newValue);
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
                    Password.lock(activity);
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