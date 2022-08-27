package se.arctosoft.vault.encryption;

import android.content.Context;

import androidx.annotation.NonNull;

import com.google.android.exoplayer2.upstream.DataSource;

public class MyDataSourceFactory implements DataSource.Factory {
    private final Context context;

    public MyDataSourceFactory(Context context) {
        this.context = context;
    }

    @NonNull
    @Override
    public DataSource createDataSource() {
        return new MyDataSource(context);
    }
}
