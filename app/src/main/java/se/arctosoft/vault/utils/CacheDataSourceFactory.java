package se.arctosoft.vault.utils;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.media3.database.StandaloneDatabaseProvider;
import androidx.media3.datasource.ContentDataSource;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.FileDataSource;
import androidx.media3.datasource.cache.CacheDataSink;
import androidx.media3.datasource.cache.CacheDataSource;
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor;
import androidx.media3.datasource.cache.SimpleCache;

import java.io.File;

@OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
public class CacheDataSourceFactory implements DataSource.Factory {
    private final Context context;
    private final ContentDataSource dataSource;
    private final long maxFileSize, maxCacheSize;

    private static SimpleCache simpleCache = null;

    public CacheDataSourceFactory(Context context, long maxCacheSize, long maxFileSize) {
        super();
        this.context = context;
        this.maxCacheSize = maxCacheSize;
        this.maxFileSize = maxFileSize;
        dataSource = new ContentDataSource(this.context);
    }

    @NonNull
    @Override
    public DataSource createDataSource() {
        LeastRecentlyUsedCacheEvictor evictor = new LeastRecentlyUsedCacheEvictor(maxCacheSize);
        if (simpleCache == null) {
            simpleCache = new SimpleCache(new File(context.getCacheDir(), "media"), evictor, new StandaloneDatabaseProvider(context));
        }
        return new CacheDataSource(simpleCache, dataSource,
                new FileDataSource(), new CacheDataSink(simpleCache, maxFileSize),
                CacheDataSource.FLAG_BLOCK_ON_CACHE | CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR, null);
    }
}