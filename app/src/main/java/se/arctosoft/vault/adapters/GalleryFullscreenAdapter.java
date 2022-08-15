package se.arctosoft.vault.adapters;

import android.graphics.PointF;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.RecyclerView;

import com.davemorrissey.labs.subscaleview.ImageSource;
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView;

import java.lang.ref.WeakReference;
import java.util.List;

import se.arctosoft.vault.R;
import se.arctosoft.vault.adapters.viewholders.GalleryFullscreenViewHolder;
import se.arctosoft.vault.data.GalleryFile;
import se.arctosoft.vault.encryption.Encryption;
import se.arctosoft.vault.interfaces.IOnFileDeleted;
import se.arctosoft.vault.utils.Dialogs;
import se.arctosoft.vault.utils.FileStuff;
import se.arctosoft.vault.utils.Settings;
import se.arctosoft.vault.utils.Toaster;

public class GalleryFullscreenAdapter extends RecyclerView.Adapter<GalleryFullscreenViewHolder> {
    private static final String TAG = "GalleryFullscreenAdapter";
    private final WeakReference<FragmentActivity> weakReference;
    private final List<GalleryFile> galleryFiles;
    private final IOnFileDeleted onFileDeleted;
    private final Uri currentDirectory;

    public GalleryFullscreenAdapter(FragmentActivity context, @NonNull List<GalleryFile> galleryFiles, IOnFileDeleted onFileDeleted, Uri currentDirectory) {
        this.weakReference = new WeakReference<>(context);
        this.galleryFiles = galleryFiles;
        this.onFileDeleted = onFileDeleted;
        this.currentDirectory = currentDirectory;
    }

    @NonNull
    @Override
    public GalleryFullscreenViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.adapter_item_gallery_fullscreen, parent, false);
        return new GalleryFullscreenViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull GalleryFullscreenViewHolder holder, int position) {
        FragmentActivity context = weakReference.get();
        GalleryFile galleryFile = galleryFiles.get(position);

        holder.txtName.setText(galleryFile.getName());
        setupImageView(holder, context, galleryFile);
        setupButtons(holder, context, galleryFile);
    }

    private void setupImageView(GalleryFullscreenViewHolder holder, FragmentActivity context, GalleryFile galleryFile) {
        holder.imageView.setOnClickListener(v -> context.onBackPressed());
        holder.imageView.setMinimumDpi(40);
        holder.imageView.setOnStateChangedListener(new SubsamplingScaleImageView.OnStateChangedListener() {
            @Override
            public void onScaleChanged(float newScale, int origin) {
                showButtons(holder, newScale <= holder.imageView.getMinScale());
            }

            @Override
            public void onCenterChanged(PointF newCenter, int origin) {

            }
        });
        new Thread(() -> Encryption.decryptToCache(context, galleryFile.getUri(), Settings.getInstance(context).getTempPassword(), new Encryption.IOnUriResult() {
            @Override
            public void onUriResult(Uri outputUri) {
                context.runOnUiThread(() -> holder.imageView.setImage(ImageSource.uri(outputUri)));
            }

            @Override
            public void onError(Exception e) {
                e.printStackTrace();
                Toaster.getInstance(context).showLong("Failed to decrypt " + galleryFile.getName() + ": " + e.getMessage());
            }
        })).start();
    }

    private void showButtons(GalleryFullscreenViewHolder holder, boolean show) {
        if (show) {
            holder.lLButtons.setVisibility(View.VISIBLE);
            holder.txtName.setVisibility(View.VISIBLE);
        } else {
            holder.lLButtons.setVisibility(View.GONE);
            holder.txtName.setVisibility(View.GONE);
        }
    }

    private void setupButtons(GalleryFullscreenViewHolder holder, FragmentActivity context, GalleryFile galleryFile) {
        showButtons(holder, true);
        holder.btnDelete.setOnClickListener(v -> Dialogs.showConfirmationDialog(context, context.getString(R.string.dialog_delete_file_title), context.getString(R.string.dialog_delete_file_message), (dialog, which) -> {
            boolean deletedFile = FileStuff.deleteFile(context, galleryFile.getUri());
            boolean deletedThumb = FileStuff.deleteFile(context, galleryFile.getThumbUri());
            if (deletedFile) {
                int pos = holder.getAdapterPosition();
                galleryFiles.remove(pos);
                notifyItemRemoved(pos);
                onFileDeleted.onFileDeleted(pos);
                Toaster.getInstance(context).showLong(context.getString(R.string.gallery_file_deleted));
                if (galleryFiles.size() == 0) {
                    context.onBackPressed();
                }
            } else {
                Toaster.getInstance(context).showLong(context.getString(R.string.gallery_file_not_deleted));
            }
        }));
        holder.btnExport.setOnClickListener(v -> Dialogs.showConfirmationDialog(context, context.getString(R.string.dialog_export_title), context.getString(R.string.dialog_export_message),
                (dialog, which) -> Encryption.decryptAndExport(context, galleryFile.getUri(), currentDirectory, Settings.getInstance(context).getTempPassword(), new Encryption.IOnUriResult() {
                    @Override
                    public void onUriResult(Uri outputUri) {
                        Toaster.getInstance(context).showLong(context.getString(R.string.gallery_file_exported, FileStuff.getFilenameWithPathFromUri(outputUri)));
                    }

                    @Override
                    public void onError(Exception e) {
                        Toaster.getInstance(context).showLong(context.getString(R.string.gallery_file_not_exported, e.getMessage()));
                    }
                })));
    }

    @Override
    public void onViewRecycled(@NonNull GalleryFullscreenViewHolder holder) {
        holder.imageView.recycle();
        super.onViewRecycled(holder);
    }

    @Override
    public int getItemCount() {
        return galleryFiles.size();
    }

}
