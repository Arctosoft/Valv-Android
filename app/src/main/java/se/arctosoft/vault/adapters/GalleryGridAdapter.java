package se.arctosoft.vault.adapters;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;

import java.lang.ref.WeakReference;
import java.util.List;

import se.arctosoft.vault.GalleryDirectoryActivity;
import se.arctosoft.vault.R;
import se.arctosoft.vault.adapters.viewholders.GalleryGridViewHolder;
import se.arctosoft.vault.data.GalleryFile;
import se.arctosoft.vault.exception.InvalidPasswordException;
import se.arctosoft.vault.interfaces.IOnFileDeleted;
import se.arctosoft.vault.utils.GlideStuff;

public class GalleryGridAdapter extends RecyclerView.Adapter<GalleryGridViewHolder> {
    private static final String TAG = "GalleryFolderAdapter";
    private static final Object LOCK = new Object();
    private final WeakReference<FragmentActivity> weakReference;
    private final List<GalleryFile> galleryFiles;
    private IOnFileCLicked onFileCLicked;
    private final IOnFileDeleted onFileDeleted;

    public interface IOnFileCLicked {
        void onClick(int pos);
    }

    public GalleryGridAdapter(FragmentActivity context, @NonNull List<GalleryFile> galleryFiles, @Nullable IOnFileDeleted onFileDeleted) {
        this.weakReference = new WeakReference<>(context);
        this.galleryFiles = galleryFiles;
        this.onFileDeleted = onFileDeleted;
    }

    public void setOnFileCLicked(IOnFileCLicked onFileCLicked) {
        this.onFileCLicked = onFileCLicked;
    }

    @NonNull
    @Override
    public GalleryGridViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        CardView v = (CardView) LayoutInflater.from(parent.getContext()).inflate(R.layout.adapter_gallery_grid_item, parent, false);
        return new GalleryGridViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull GalleryGridViewHolder holder, int position) {
        Context context = weakReference.get();
        GalleryFile galleryFile = galleryFiles.get(position);
        if (galleryFile.isDirectory()) {
            GalleryFile firstFile = galleryFile.getFirstFile();
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
                    .listener(new RequestListener<Drawable>() {
                        @Override
                        public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
                            if (e != null) {
                                for (Throwable t : e.getRootCauses()) {
                                    if (t instanceof InvalidPasswordException) {
                                        synchronized (LOCK) {
                                            int pos = holder.getAdapterPosition();
                                            if (pos >= 0 && pos < galleryFiles.size()) {
                                                galleryFiles.remove(pos);
                                                notifyItemRemoved(pos);
                                                if (onFileDeleted != null) {
                                                    onFileDeleted.onFileDeleted(pos);
                                                }
                                            }
                                        }
                                        break;
                                    }
                                }
                            }
                            return false;
                        }

                        @Override
                        public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
                            return false;
                        }
                    })
                    .into(holder.imageView);
            holder.txtName.setText(galleryFile.getName());
        }

        holder.imageView.setOnClickListener(v -> {
            if (galleryFile.isDirectory()) {
                context.startActivity(new Intent(context, GalleryDirectoryActivity.class)
                        .putExtra(GalleryDirectoryActivity.EXTRA_DIRECTORY, galleryFile.getUri().toString()));
            } else {
                if (onFileCLicked != null) {
                    onFileCLicked.onClick(holder.getAdapterPosition());
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
