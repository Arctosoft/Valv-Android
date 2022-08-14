package se.arctosoft.vault.views;

import android.content.Context;
import android.util.AttributeSet;

import androidx.appcompat.widget.AppCompatImageView;

import se.arctosoft.vault.utils.Constants;

public class PressableGridImageView extends AppCompatImageView {

    public PressableGridImageView(Context context) {
        super(context);
    }

    public PressableGridImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public PressableGridImageView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public void setPressed(boolean pressed) {
        super.setPressed(pressed);
        setAlpha(pressed ? Constants.HALF : Constants.FULL);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        int width = getMeasuredWidth();
        setMeasuredDimension(width, (int) (width * 1.2));
    }
}
