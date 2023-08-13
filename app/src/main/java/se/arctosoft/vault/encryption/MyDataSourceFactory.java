package se.arctosoft.vault.encryption;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.media3.datasource.DataSource;

public class MyDataSourceFactory implements DataSource.Factory {
    private final Context context;

    public MyDataSourceFactory(Context context) {
        this.context = context;
    }

    @OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
    @NonNull
    @Override
    public DataSource createDataSource() {
        return new MyDataSource(context);
    }
}
