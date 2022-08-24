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
import androidx.core.content.res.ResourcesCompat;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

import se.arctosoft.vault.GalleryDirectoryActivity;
import se.arctosoft.vault.R;
import se.arctosoft.vault.adapters.viewholders.GalleryGridViewHolder;
import se.arctosoft.vault.data.GalleryFile;
import se.arctosoft.vault.exception.InvalidPasswordException;
import se.arctosoft.vault.interfaces.IOnFileClicked;
import se.arctosoft.vault.interfaces.IOnFileDeleted;
import se.arctosoft.vault.interfaces.IOnSelectionModeChanged;
import se.arctosoft.vault.utils.GlideStuff;
import se.arctosoft.vault.utils.StringStuff;

public class GalleryGridAdapter extends RecyclerView.Adapter<GalleryGridViewHolder> implements IOnSelectionModeChanged {
    private static final String TAG = "GalleryFolderAdapter";

    private static final Object LOCK = new Object();

    private final WeakReference<FragmentActivity> weakReference;
    private final List<GalleryFile> galleryFiles, selectedFiles;
    private boolean showFileNames;
    private IOnFileDeleted onFileDeleted;
    private IOnFileClicked onFileCLicked;
    private IOnSelectionModeChanged onSelectionModeChanged;
    private boolean selectMode;

    private static class Payload {
        private static final int TYPE_SELECT_ALL = 0;
        private static final int TYPE_TOGGLE_FILENAME = 1;
        private final int type;

        private Payload(int type) {
            this.type = type;
        }
    }

    public GalleryGridAdapter(FragmentActivity context, @NonNull List<GalleryFile> galleryFiles, boolean showFileNames) {
        this.weakReference = new WeakReference<>(context);
        this.galleryFiles = galleryFiles;
        this.showFileNames = showFileNames;
        this.selectedFiles = new ArrayList<>();
    }

    public void setOnFileCLicked(IOnFileClicked onFileCLicked) {
        this.onFileCLicked = onFileCLicked;
    }

    public void setOnFileDeleted(IOnFileDeleted onFileDeleted) {
        this.onFileDeleted = onFileDeleted;
    }

    public void setOnSelectionModeChanged(IOnSelectionModeChanged onSelectionModeChanged) {
        this.onSelectionModeChanged = onSelectionModeChanged;
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

        updateSelectedView(holder, galleryFile);
        holder.txtName.setVisibility(showFileNames ? View.VISIBLE : View.GONE);
        if (galleryFile.isGif() || galleryFile.isVideo()) {
            holder.imgType.setVisibility(View.VISIBLE);
            holder.imgType.setImageDrawable(ResourcesCompat.getDrawable(context.getResources(), galleryFile.isGif() ? R.drawable.ic_round_gif_24 : R.drawable.ic_outline_video_file_24, context.getTheme()));
        } else {
            holder.imgType.setVisibility(View.GONE);
        }
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
                                        removeItem(holder.getAdapterPosition());
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
            if (galleryFile.getSize() > 0) {
                holder.txtName.setText(context.getString(R.string.gallery_adapter_file_name, galleryFile.getName(), StringStuff.bytesToReadableString(galleryFile.getSize())));
            } else {
                holder.txtName.setText(galleryFile.getName());
            }
        }
        holder.imageView.setOnClickListener(v -> {
            if (selectMode) {
                boolean selected = !selectedFiles.contains(galleryFile);
                if (selected) {
                    selectedFiles.add(galleryFile);
                } else {
                    selectedFiles.remove(galleryFile);
                    if (selectedFiles.isEmpty()) {
                        setSelectMode(false);
                    }
                }
                updateSelectedView(holder, galleryFile);
            } else {
                if (galleryFile.isDirectory()) {
                    context.startActivity(new Intent(context, GalleryDirectoryActivity.class)
                            .putExtra(GalleryDirectoryActivity.EXTRA_DIRECTORY, galleryFile.getUri().toString()));
                } else {
                    if (onFileCLicked != null) {
                        onFileCLicked.onClick(holder.getAdapterPosition());
                    }
                }
            }
        });
        holder.imageView.setOnLongClickListener(v -> {
            setSelectMode(true);
            holder.imageView.performClick();
            return true;
        });
    }

    @Override
    public void onBindViewHolder(@NonNull GalleryGridViewHolder holder, int position, @NonNull List<Object> payloads) {
        boolean found = false;
        for (Object o : payloads) {
            if (o instanceof Payload) {
                if (((Payload) o).type == Payload.TYPE_SELECT_ALL) {
                    updateSelectedView(holder, galleryFiles.get(position));
                    found = true;
                    break;
                } else if (((Payload) o).type == Payload.TYPE_TOGGLE_FILENAME) {
                    holder.txtName.setVisibility(showFileNames ? View.VISIBLE : View.GONE);
                    found = true;
                }
            }
        }
        if (!found) {
            super.onBindViewHolder(holder, position, payloads);
        }
    }

    private void setSelectMode(boolean selectionMode) {
        if (selectionMode && !selectMode) {
            selectMode = true;
            notifyItemRangeChanged(0, galleryFiles.size(), new Payload(Payload.TYPE_SELECT_ALL));
        } else if (!selectionMode && selectMode) {
            selectMode = false;
            selectedFiles.clear();
            notifyItemRangeChanged(0, galleryFiles.size(), new Payload(Payload.TYPE_SELECT_ALL));
        }
        if (onSelectionModeChanged != null) {
            onSelectionModeChanged.onSelectionModeChanged(selectMode);
        }
    }

    private void updateSelectedView(GalleryGridViewHolder holder, GalleryFile galleryFile) {
        if (selectMode) {
            holder.checked.setVisibility(View.VISIBLE);
            holder.checked.setChecked(selectedFiles.contains(galleryFile));
        } else {
            holder.checked.setVisibility(View.GONE);
            holder.checked.setChecked(false);
        }
    }

    private void removeItem(int pos) {
        synchronized (LOCK) {
            if (pos >= 0 && pos < galleryFiles.size()) {
                galleryFiles.remove(pos);
                notifyItemRemoved(pos);
                if (onFileDeleted != null) {
                    onFileDeleted.onFileDeleted(pos);
                }
            }
        }
    }

    @Override
    public int getItemCount() {
        return galleryFiles.size();
    }

    @Override
    public void onSelectionModeChanged(boolean inSelectionMode) {
        setSelectMode(inSelectionMode);
    }

    public void selectAll() {
        synchronized (LOCK) {
            selectedFiles.clear();
            selectedFiles.addAll(galleryFiles);
            notifyItemRangeChanged(0, galleryFiles.size(), new Payload(Payload.TYPE_SELECT_ALL));
        }
    }

    public void toggleFilenames() {
        showFileNames = !showFileNames;
        notifyItemRangeChanged(0, galleryFiles.size(), new Payload(Payload.TYPE_TOGGLE_FILENAME));
    }

    @NonNull
    public List<GalleryFile> getSelectedFiles() {
        return selectedFiles;
    }
}
