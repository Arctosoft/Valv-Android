package se.arctosoft.vault;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.navigation.fragment.NavHostFragment;

import se.arctosoft.vault.databinding.FragmentPasswordBinding;
import se.arctosoft.vault.utils.Dialogs;
import se.arctosoft.vault.viewmodel.PasswordViewModel;

public class PasswordFragment extends Fragment {
    public static String LOGIN_SUCCESSFUL = "LOGIN_SUCCESSFUL";

    private PasswordViewModel passwordViewModel;
    private SavedStateHandle savedStateHandle;
    private FragmentPasswordBinding binding;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentPasswordBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        passwordViewModel = new ViewModelProvider(requireActivity()).get(PasswordViewModel.class);

        savedStateHandle = Navigation.findNavController(view)
                .getPreviousBackStackEntry()
                .getSavedStateHandle();
        savedStateHandle.set(LOGIN_SUCCESSFUL, false);

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
            binding.btnUnlock.setEnabled(false);
            char[] temp = new char[binding.eTPassword.length()];
            binding.eTPassword.getText().getChars(0, binding.eTPassword.length(), temp, 0);
            passwordViewModel.setPassword(temp);
            binding.eTPassword.setText(null);

            MainActivity.GLIDE_KEY = System.currentTimeMillis();
            savedStateHandle.set(LOGIN_SUCCESSFUL, true);
            NavHostFragment.findNavController(this).popBackStack();
        });
        binding.btnHelp.setOnClickListener(v -> Dialogs.showTextDialog(requireContext(), null, getString(R.string.launcher_help_message)));
    }

}