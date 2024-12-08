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

package se.arctosoft.vault.data;

import android.content.Context;
import android.util.Log;

import com.bumptech.glide.Glide;

import java.util.Arrays;

import se.arctosoft.vault.MainActivity;
import se.arctosoft.vault.utils.FileStuff;

public class Password {
    private static final String TAG = "Password";

    private static Password instance;
    private char[] password;

    private Password() {
    }

    public void setPassword(char[] password) {
        this.password = password;
    }

    public char[] getPassword() {
        return password;
    }

    public static Password getInstance() {
        if (instance == null) {
            instance = new Password();
        }
        return instance;
    }

    public void clearPassword() {
        if (password != null) {
            Arrays.fill(password, (char) 0);
            password = null;
        }
    }

    public static void lock(Context context) {
        Log.d(TAG, "lock");
        Password p = Password.getInstance();
        p.clearPassword();
        if (context != null) {
            FileStuff.deleteCache(context);
            Glide.get(context).clearMemory();
        }
        //new Thread(() -> Glide.get(context).clearDiskCache()).start();
        MainActivity.GLIDE_KEY = System.currentTimeMillis();
    }
}
