package se.arctosoft.vault.adapters;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.lang.ref.WeakReference;
import java.util.List;

import se.arctosoft.vault.R;
import se.arctosoft.vault.adapters.viewholders.GalleryItemViewHolder;
import se.arctosoft.vault.data.GalleryFile;
import se.arctosoft.vault.utils.Settings;

public class GalleryItemAdapter extends RecyclerView.Adapter<GalleryItemViewHolder> {
    private final WeakReference<FragmentActivity> weakReference;
    private final List<GalleryFile> galleryFiles;
    private final Settings settings;

    public GalleryItemAdapter(FragmentActivity context, @NonNull List<GalleryFile> galleryFiles, @NonNull Settings settings) {
        this.weakReference = new WeakReference<>(context);
        this.galleryFiles = galleryFiles;
        this.settings = settings;
    }

    @NonNull
    @Override
    public GalleryItemViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        CardView v = (CardView) LayoutInflater.from(parent.getContext()).inflate(R.layout.adapter_gallery_item, parent, false);
        return new GalleryItemViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull GalleryItemViewHolder holder, int position) {
        GalleryFile file = galleryFiles.get(position);
        if (file.hasThumb()) {
            Glide.with(weakReference.get())
                    .load(file.getThumbUri())
                    .into(holder.imageView);
        }
    }

    @Override
    public int getItemCount() {
        return galleryFiles.size();
    }

    @Override
    public int getItemViewType(int position) {
        GalleryFile file = galleryFiles.get(position);
        return file.getFileType().i;
    }
}
