package se.arctosoft.vault.adapters;

import android.graphics.Color;
import android.graphics.PointF;
import android.net.Uri;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.davemorrissey.labs.subscaleview.ImageSource;
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView;
import com.google.android.material.color.MaterialColors;

import java.lang.ref.WeakReference;
import java.util.List;

import se.arctosoft.vault.R;
import se.arctosoft.vault.adapters.viewholders.GalleryPagerViewHolder;
import se.arctosoft.vault.data.FileType;
import se.arctosoft.vault.data.GalleryFile;
import se.arctosoft.vault.encryption.Encryption;
import se.arctosoft.vault.exception.InvalidPasswordException;
import se.arctosoft.vault.interfaces.IOnFileDeleted;
import se.arctosoft.vault.utils.Dialogs;
import se.arctosoft.vault.utils.FileStuff;
import se.arctosoft.vault.utils.Settings;
import se.arctosoft.vault.utils.Toaster;

public class GalleryPagerAdapter extends RecyclerView.Adapter<GalleryPagerViewHolder> {
    private static final String TAG = "GalleryFullscreenAdapter";
    private final WeakReference<FragmentActivity> weakReference;
    private final List<GalleryFile> galleryFiles;
    private final IOnFileDeleted onFileDeleted;
    private final Uri currentDirectory;
    private boolean isFullscreen;

    public GalleryPagerAdapter(FragmentActivity context, @NonNull List<GalleryFile> galleryFiles, IOnFileDeleted onFileDeleted, Uri currentDirectory) {
        this.weakReference = new WeakReference<>(context);
        this.galleryFiles = galleryFiles;
        this.onFileDeleted = onFileDeleted;
        this.currentDirectory = currentDirectory;
        this.isFullscreen = false;
    }

    @NonNull
    @Override
    public GalleryPagerViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater layoutInflater = LayoutInflater.from(parent.getContext());
        ConstraintLayout v = (ConstraintLayout) layoutInflater.inflate(R.layout.adapter_gallery_viewpager_item, parent, false);
        FrameLayout fLContent = v.findViewById(R.id.fLContent);
        if (viewType == FileType.IMAGE.i) {
            fLContent.addView(layoutInflater.inflate(R.layout.adapter_gallery_viewpager_item_image, fLContent, false));
            return new GalleryPagerViewHolder.GalleryPagerImageViewHolder(v);
        } else {
            fLContent.addView(layoutInflater.inflate(R.layout.adapter_gallery_viewpager_item_gif, fLContent, false));
            return new GalleryPagerViewHolder.GalleryPagerGifViewHolder(v);
        } // TODO video
    }

    @Override
    public void onBindViewHolder(@NonNull GalleryPagerViewHolder holder, int position) {
        FragmentActivity context = weakReference.get();
        GalleryFile galleryFile = galleryFiles.get(position);

        holder.txtName.setText(galleryFile.getName());
        Log.e(TAG, "onBindViewHolder: " + galleryFile.getUri() + " " + position + " " + galleryFile.getFileType());
        setupImageView(holder, context, galleryFile);
        setupButtons(holder, context, galleryFile);
    }

    @Override
    public void onBindViewHolder(@NonNull GalleryPagerViewHolder holder, int position, @NonNull List<Object> payloads) {
        boolean found = false;
        if (!payloads.isEmpty()) {
            for (Object o : payloads) {
                if (o instanceof Boolean) {
                    showButtons(holder, !((Boolean) o));
                    found = true;
                    break;
                }
            }
        }
        if (!found) {
            super.onBindViewHolder(holder, position, payloads);
        }
    }

    private void setupImageView(GalleryPagerViewHolder holder, FragmentActivity context, GalleryFile galleryFile) {
        if (holder instanceof GalleryPagerViewHolder.GalleryPagerImageViewHolder) {
            ((GalleryPagerViewHolder.GalleryPagerImageViewHolder) holder).imageView.setOnClickListener(v -> onItemPressed(context, holder));
            ((GalleryPagerViewHolder.GalleryPagerImageViewHolder) holder).imageView.setMinimumDpi(40);
            ((GalleryPagerViewHolder.GalleryPagerImageViewHolder) holder).imageView.setOnStateChangedListener(new SubsamplingScaleImageView.OnStateChangedListener() {
                @Override
                public void onScaleChanged(float newScale, int origin) {
                    showButtons(holder, newScale <= ((GalleryPagerViewHolder.GalleryPagerImageViewHolder) holder).imageView.getMinScale());
                }

                @Override
                public void onCenterChanged(PointF newCenter, int origin) {

                }
            });
        } else if (holder instanceof GalleryPagerViewHolder.GalleryPagerGifViewHolder) {
            ((GalleryPagerViewHolder.GalleryPagerGifViewHolder) holder).gifImageView.setOnClickListener(v -> onItemPressed(context, holder));
        }
        if (galleryFile.getDecryptedCacheUri() == null) {
            new Thread(() -> Encryption.decryptToCache(context, galleryFile.getUri(), Settings.getInstance(context).getTempPassword(), new Encryption.IOnUriResult() {
                @Override
                public void onUriResult(Uri outputUri) {
                    galleryFile.setDecryptedCacheUri(outputUri);
                    loadImage(outputUri, holder, context);
                }

                @Override
                public void onError(Exception e) {
                    e.printStackTrace();
                    Toaster.getInstance(context).showLong("Failed to decrypt " + galleryFile.getName() + ": " + e.getMessage());
                }

                @Override
                public void onInvalidPassword(InvalidPasswordException e) {
                    e.printStackTrace();
                    //Log.e(TAG, "onInvalidPassword: " + e.getMessage());
                    //removeFileAt(holder.getAdapterPosition(), context);
                }
            })).start();
        } else {
            loadImage(galleryFile.getDecryptedCacheUri(), holder, context);
        }
    }

    private void onItemPressed(FragmentActivity context, GalleryPagerViewHolder holder) {
        //context.onBackPressed();
        toggleFullscreen(context);
        //showButtons(holder, !this.isFullscreen);
    }

    private void toggleFullscreen(@NonNull FragmentActivity context) {
        this.isFullscreen = !isFullscreen;
        WindowManager.LayoutParams attrs = context.getWindow().getAttributes();
        attrs.flags ^= WindowManager.LayoutParams.FLAG_FULLSCREEN;
        context.getWindow().setAttributes(attrs);
        context.getWindow().getDecorView().setSystemUiVisibility(this.isFullscreen ? View.SYSTEM_UI_FLAG_HIDE_NAVIGATION : View.SYSTEM_UI_FLAG_VISIBLE);
        notifyItemRangeChanged(0, galleryFiles.size(), isFullscreen);
    }

    private void loadImage(Uri outputUri, GalleryPagerViewHolder holder, FragmentActivity context) {
        if (holder instanceof GalleryPagerViewHolder.GalleryPagerImageViewHolder) {
            ((GalleryPagerViewHolder.GalleryPagerImageViewHolder) holder).imageView.setImage(ImageSource.uri(outputUri));
        } else if (holder instanceof GalleryPagerViewHolder.GalleryPagerGifViewHolder) {
            Glide.with(context)
                    .asGif()
                    .load(outputUri)
                    .into(((GalleryPagerViewHolder.GalleryPagerGifViewHolder) holder).gifImageView);
        }
    }

    private void showButtons(GalleryPagerViewHolder holder, boolean show) {
        if (isFullscreen) {
            show = false;
            holder.root.setBackgroundColor(weakReference.get().getResources().getColor(R.color.black));
        } else {
            holder.root.setBackgroundColor(MaterialColors.getColor(weakReference.get(), R.attr.gallery_viewpager_background, Color.WHITE));
        }
        if (show) {
            holder.lLButtons.setVisibility(View.VISIBLE);
            holder.txtName.setVisibility(View.VISIBLE);
        } else {
            holder.lLButtons.setVisibility(View.GONE);
            holder.txtName.setVisibility(View.GONE);
        }
    }

    private void setupButtons(GalleryPagerViewHolder holder, FragmentActivity context, GalleryFile galleryFile) {
        showButtons(holder, true);
        holder.btnDelete.setOnClickListener(v -> Dialogs.showConfirmationDialog(context, context.getString(R.string.dialog_delete_file_title), context.getString(R.string.dialog_delete_file_message), (dialog, which) -> {
            boolean deletedFile = FileStuff.deleteFile(context, galleryFile.getUri());
            boolean deletedThumb = FileStuff.deleteFile(context, galleryFile.getThumbUri());
            if (deletedFile) {
                int pos = holder.getAdapterPosition();
                removeFileAt(pos, context);
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

                    @Override
                    public void onInvalidPassword(InvalidPasswordException e) {
                        //removeFileAt(holder.getAdapterPosition(), context);
                    }
                })));
    }

    private void removeFileAt(int pos, FragmentActivity context) {
        //Log.e(TAG, "removeFileAt: " + pos);
        galleryFiles.remove(pos);
        notifyItemRemoved(pos);
        onFileDeleted.onFileDeleted(pos);
        Toaster.getInstance(context).showLong(context.getString(R.string.gallery_file_deleted));
        if (galleryFiles.size() == 0) {
            context.onBackPressed();
        }
    }

    @Override
    public int getItemViewType(int position) {
        GalleryFile galleryFile = galleryFiles.get(position);
        if (galleryFile.getFileType() == FileType.IMAGE) {
            return FileType.IMAGE.i;
        } else if (galleryFile.getFileType() == FileType.GIF) {
            return FileType.GIF.i;
        } else if (galleryFile.getFileType() == FileType.VIDEO) {
            return FileType.VIDEO.i;
        }
        return super.getItemViewType(position);
    }

    @Override
    public void onViewRecycled(@NonNull GalleryPagerViewHolder holder) {
        if (holder instanceof GalleryPagerViewHolder.GalleryPagerImageViewHolder) {
            ((GalleryPagerViewHolder.GalleryPagerImageViewHolder) holder).imageView.recycle();
        }
        super.onViewRecycled(holder);
    }

    @Override
    public int getItemCount() {
        return galleryFiles.size();
    }

}
