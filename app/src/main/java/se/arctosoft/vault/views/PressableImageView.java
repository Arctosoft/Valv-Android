package se.arctosoft.vault.views;

import android.content.Context;
import android.util.AttributeSet;

import androidx.appcompat.widget.AppCompatImageView;

import se.arctosoft.vault.utils.Constants;

public class PressableImageView extends AppCompatImageView {

    public PressableImageView(Context context) {
        super(context);
    }

    public PressableImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public PressableImageView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public void setPressed(boolean pressed) {
        super.setPressed(pressed);
        setAlpha(pressed ? Constants.HALF : Constants.FULL);
    }
}
