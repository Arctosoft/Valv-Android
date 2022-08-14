package se.arctosoft.vault.adapters;

import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.lang.ref.WeakReference;
import java.util.List;

import se.arctosoft.vault.GalleryDirectoryActivity;
import se.arctosoft.vault.R;
import se.arctosoft.vault.adapters.viewholders.GalleryViewHolder;
import se.arctosoft.vault.data.GalleryFile;
import se.arctosoft.vault.utils.GlideStuff;

public class GalleryAdapter extends RecyclerView.Adapter<GalleryViewHolder> {
    private static final String TAG = "GalleryFolderAdapter";
    private final WeakReference<FragmentActivity> weakReference;
    private final List<GalleryFile> galleryDirectories;

    public GalleryAdapter(FragmentActivity context, @NonNull List<GalleryFile> galleryDirectories) {
        this.weakReference = new WeakReference<>(context);
        this.galleryDirectories = galleryDirectories;
    }

    @NonNull
    @Override
    public GalleryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        CardView v = (CardView) LayoutInflater.from(parent.getContext()).inflate(R.layout.adapter_gallery_folder, parent, false);
        return new GalleryViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull GalleryViewHolder holder, int position) {
        Context context = weakReference.get();
        GalleryFile galleryFile = galleryDirectories.get(position);
        if (galleryFile.isDirectory()) {
            GalleryFile firstFile = galleryFile.getFirstFile();
            Log.e(TAG, "onBindViewHolder: " + position + " " + firstFile);
            if (firstFile != null) {
                Glide.with(context)
                        .load(firstFile.getThumbUri())
                        .apply(GlideStuff.getRequestOptions())
                        .into(holder.imageView);
            }
            holder.txtName.setText(context.getString(R.string.gallery_adapter_folder_name, galleryFile.getName(), galleryFile.getFileCount()));
        } else {
            Glide.with(context)
                    .load(galleryFile.getThumbUri())
                    .apply(GlideStuff.getRequestOptions())
                    .into(holder.imageView);
            holder.txtName.setText(galleryFile.getName());
        }

        holder.imageView.setOnClickListener(v -> {
            if (galleryFile.isDirectory()) {
                context.startActivity(new Intent(context, GalleryDirectoryActivity.class)
                        .putExtra(GalleryDirectoryActivity.EXTRA_DIRECTORY, galleryFile.getUri().toString()));
            } else {
                // TODO open image in fullscreen
            }
        });
    }

    @Override
    public int getItemCount() {
        return galleryDirectories.size();
    }

}
