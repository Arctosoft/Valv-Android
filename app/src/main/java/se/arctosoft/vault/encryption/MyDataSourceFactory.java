/*
 * Valv-Android
 * Copyright (C) 2024 Arctosoft AB
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

package se.arctosoft.vault.encryption;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.media3.datasource.DataSource;

import se.arctosoft.vault.data.Password;
import se.arctosoft.vault.viewmodel.PasswordViewModel;

public class MyDataSourceFactory implements DataSource.Factory {
    private final Context context;
    private final int version;
    private final Password password;

    public MyDataSourceFactory(Context context, int version, Password password) {
        this.context = context;
        this.version = version;
        this.password = password;
    }

    @OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
    @NonNull
    @Override
    public DataSource createDataSource() {
        return new MyDataSource(context, version, password);
    }
}
