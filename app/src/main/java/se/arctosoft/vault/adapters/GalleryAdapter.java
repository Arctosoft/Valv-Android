package se.arctosoft.vault.adapters;

import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
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
    private final List<GalleryFile> galleryFiles;
    private IOnFileCLicked onFileCLicked;

    public interface IOnFileCLicked {
        void onClick(int pos);
    }

    public GalleryAdapter(FragmentActivity context, @NonNull List<GalleryFile> galleryFiles) {
        this.weakReference = new WeakReference<>(context);
        this.galleryFiles = galleryFiles;
    }

    public void setOnFileCLicked(IOnFileCLicked onFileCLicked) {
        this.onFileCLicked = onFileCLicked;
    }

    @NonNull
    @Override
    public GalleryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        CardView v = (CardView) LayoutInflater.from(parent.getContext()).inflate(R.layout.adapter_item_gallery_folder, parent, false);
        return new GalleryViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull GalleryViewHolder holder, int position) {
        Context context = weakReference.get();
        GalleryFile galleryFile = galleryFiles.get(position);
        if (galleryFile.isDirectory()) {
            GalleryFile firstFile = galleryFile.getFirstFile();
            Log.e(TAG, "onBindViewHolder: " + position + " " + firstFile);
            if (firstFile != null) {
                Glide.with(context)
                        .load(firstFile.getThumbUri())
                        .apply(GlideStuff.getRequestOptions())
                        .into(holder.imageView);
            }
            holder.txtName.setText(context.getString(R.string.gallery_adapter_folder_name, galleryFile.getNameWithPath(), galleryFile.getFileCount()));
        } else {
            Glide.with(context)
                    .load(galleryFile.getThumbUri())
                    .apply(GlideStuff.getRequestOptions())
                    .into(holder.imageView);
            holder.txtName.setText(galleryFile.getName());
        }

        holder.imageView.setOnClickListener(v -> {
            if (galleryFile.isDirectory()) {
                //GalleryDirectoryActivity.LAST_POS = position;
                context.startActivity(new Intent(context, GalleryDirectoryActivity.class)
                        .putExtra(GalleryDirectoryActivity.EXTRA_DIRECTORY, galleryFile.getUri().toString()));
            } else {
                //GalleryFullscreenActivity.FILES = galleryFiles;
                //context.startActivity(new Intent(context, GalleryFullscreenActivity.class)
                //        .putExtra(GalleryFullscreenActivity.EXTRA_POSITION, position));
                if (onFileCLicked != null) {
                    onFileCLicked.onClick(position);
                }
            }
        });
        holder.imageView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                return true;
            }
        });
    }

    @Override
    public int getItemCount() {
        return galleryFiles.size();
    }

}
