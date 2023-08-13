package se.arctosoft.vault.fastscroll;

import android.content.res.Resources;
import android.util.TypedValue;
import android.view.View;

public class Utils {
    /**
     * Converts dp to px
     *
     * @param res Resources
     * @param dp  the value in dp
     * @return int
     */
    public static int toPixels(Resources res, float dp) {
        return (int) (dp * res.getDisplayMetrics().density);
    }

    /**
     * Converts sp to px
     *
     * @param res Resources
     * @param sp  the value in sp
     * @return int
     */
    public static int toScreenPixels(Resources res, float sp) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, res.getDisplayMetrics());
    }

    public static boolean isRtl(Resources res) {
        return res.getConfiguration().getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
    }
}
