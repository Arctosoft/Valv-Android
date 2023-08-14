/*
 * Valv-Android
 * Copyright (C) 2023 Arctosoft AB
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see https://www.gnu.org/licenses/.
 */

package se.arctosoft.vault.views;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.RelativeLayout;

import androidx.appcompat.widget.AppCompatImageView;

import se.arctosoft.vault.utils.Constants;

public class PressableRelativeLayout extends RelativeLayout {

    public PressableRelativeLayout(Context context) {
        super(context);
    }

    public PressableRelativeLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public PressableRelativeLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public void setPressed(boolean pressed) {
        super.setPressed(pressed);
        setAlpha(pressed ? Constants.HALF : Constants.FULL);
    }
}
