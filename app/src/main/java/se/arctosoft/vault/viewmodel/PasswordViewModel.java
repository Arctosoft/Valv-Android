package se.arctosoft.vault.viewmodel;

import androidx.lifecycle.ViewModel;

import se.arctosoft.vault.data.Password;

public class PasswordViewModel extends ViewModel {
    private static final String TAG = "PasswordViewModel";

    private Password password;

    public boolean isLocked() {
        initPassword();
        return password.getPassword() == null;
    }

    private void initPassword() {
        if (password == null) {
            this.password = Password.getInstance();
        }
    }

    public void setPassword(char[] password) {
        initPassword();
        this.password.setPassword(password);
    }

    public char[] getPassword() {
        initPassword();
        return password.getPassword();
    }

    public void clearPassword() {
        if (password != null) {
            password.clearPassword();
            password = null;
        }
    }

}
