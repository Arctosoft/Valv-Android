package se.arctosoft.vault.utils;

import android.content.Intent;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCaller;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContract;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class BetterActivityResult<Input, Result> {

    @NonNull
    public static BetterActivityResult<Intent, ActivityResult> registerActivityForResult(@NonNull ActivityResultCaller caller) {
        return new BetterActivityResult<>(caller, new ActivityResultContracts.StartActivityForResult(), null);
    }

    public interface OnActivityResult<O> {
        void onActivityResult(O result);
    }

    private final ActivityResultLauncher<Input> launcher;
    @Nullable
    private OnActivityResult<Result> onActivityResult;

    private BetterActivityResult(@NonNull ActivityResultCaller caller, @NonNull ActivityResultContract<Input, Result> contract, @Nullable OnActivityResult<Result> onActivityResult) {
        this.onActivityResult = onActivityResult;
        this.launcher = caller.registerForActivityResult(contract, this::callOnActivityResult);
    }

    public void launch(Input input, @Nullable OnActivityResult<Result> onActivityResult) {
        if (onActivityResult != null) {
            this.onActivityResult = onActivityResult;
        }
        launcher.launch(input);
    }

    private void callOnActivityResult(Result result) {
        if (onActivityResult != null) {
            onActivityResult.onActivityResult(result);
        }
    }
}