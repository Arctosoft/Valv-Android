package se.arctosoft.vault.data;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class GalleryFile extends AbstractFile {
    private Uri thumbUri;

    public GalleryFile(@NonNull Uri fileUri, @Nullable Uri thumbUri, @NonNull FileType fileType, long createdAt) {
        super(fileUri, fileType, createdAt);
        this.thumbUri = thumbUri;
    }

    public void setThumbUri(Uri thumbUri) {
        this.thumbUri = thumbUri;
    }

    @Nullable
    public Uri getThumbUri() {
        return thumbUri;
    }

    public boolean hasThumb() {
        return thumbUri != null;
    }
}
