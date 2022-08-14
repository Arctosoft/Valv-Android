package se.arctosoft.vault.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

public class LauncherViewModel extends ViewModel {
    private MutableLiveData<Boolean> mBoolean;

    public LiveData<Boolean> getUsers() {
        if (mBoolean == null) {
            mBoolean = new MutableLiveData<Boolean>();
            loadUsers();
        }
        return mBoolean;
    }

    private void loadUsers() {
        // Do an asynchronous operation to fetch users.
    }

}
