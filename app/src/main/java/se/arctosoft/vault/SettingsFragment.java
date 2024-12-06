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
        SwitchPreferenceCompat secure = findPreference(Settings.PREF_APP_SECURE);

        FragmentActivity activity = requireActivity();
        Settings settings = Settings.getInstance(activity);

        iterationCount.setSummary(getString(R.string.settings_iteration_count_summary, settings.getIterationCount()));
        iterationCount.setOnPreferenceClickListener(preference -> {
            Dialogs.showSetIterationCountDialog(activity, settings.getIterationCount() + "", text -> {
                try {
                    int ic = Integer.parseInt(text);
                    if (ic < 20000 || ic > 500000) {
                        Toaster.getInstance(activity).showLong(getString(R.string.gallery_iteration_count_hint));
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
            boolean enabled = (boolean) newValue;
            if (enabled) {
                requireActivity().getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
            } else {
                requireActivity().getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
            }
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