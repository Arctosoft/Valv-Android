package se.arctosoft.vault;

import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.FragmentActivity;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SeekBarPreference;

import se.arctosoft.vault.data.Password;
import se.arctosoft.vault.utils.Settings;

public class SettingsFragment extends PreferenceFragmentCompat implements MenuProvider {
    private static final String TAG = "SettingsFragment";

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.root_preferences, rootKey);

        SeekBarPreference iterationCount = findPreference(Settings.PREF_ENCRYPTION_ITERATION_COUNT);
        iterationCount.setOnPreferenceChangeListener((preference, newValue) -> {
            final int increment = iterationCount.getSeekBarIncrement();
            float value = (int) newValue;
            final int rounded = Math.round(value / increment);
            final int finalValue = rounded * increment;
            if (finalValue == value) {
                return true;
            } else {
                iterationCount.setValue(finalValue);
            }
            return false;
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