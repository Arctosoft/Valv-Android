package se.arctosoft.vault.utils;

import android.content.Context;

import androidx.annotation.NonNull;

import com.google.android.exoplayer2.database.StandaloneDatabaseProvider;
import com.google.android.exoplayer2.upstream.ContentDataSource;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.FileDataSource;
import com.google.android.exoplayer2.upstream.cache.CacheDataSink;
import com.google.android.exoplayer2.upstream.cache.CacheDataSource;
import com.google.android.exoplayer2.upstream.cache.LeastRecentlyUsedCacheEvictor;
import com.google.android.exoplayer2.upstream.cache.SimpleCache;

import java.io.File;

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