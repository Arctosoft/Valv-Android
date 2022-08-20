package se.arctosoft.vault.adapters;

import android.graphics.Color;
import android.graphics.PointF;
import android.net.Uri;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.davemorrissey.labs.subscaleview.ImageSource;
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.ui.StyledPlayerView;
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
import se.arctosoft.vault.utils.CacheDataSourceFactory;
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
    private boolean isPagerShown;

    private ExoPlayer player;
    private StyledPlayerView playerView;
    private int lastPlayerPos = -1;

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
        } else if (viewType == FileType.GIF.i) {
            fLContent.addView(layoutInflater.inflate(R.layout.adapter_gallery_viewpager_item_gif, fLContent, false));
            return new GalleryPagerViewHolder.GalleryPagerGifViewHolder(v);
        } else {
            fLContent.addView(layoutInflater.inflate(R.layout.adapter_gallery_viewpager_item_video, fLContent, false));
            return new GalleryPagerViewHolder.GalleryPagerVideoViewHolder(v);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull GalleryPagerViewHolder holder, int position) {
        FragmentActivity context = weakReference.get();
        GalleryFile galleryFile = galleryFiles.get(position);

        holder.txtName.setText(galleryFile.getName());
        if (holder instanceof GalleryPagerViewHolder.GalleryPagerVideoViewHolder) {
            setupVideoView((GalleryPagerViewHolder.GalleryPagerVideoViewHolder) holder, context, galleryFile, position);
        } else {
            setupImageView(holder, context, galleryFile);
        }
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

    private void setupVideoView(GalleryPagerViewHolder.GalleryPagerVideoViewHolder holder, FragmentActivity context, GalleryFile galleryFile, int position) {
        holder.rLPlay.setVisibility(View.VISIBLE);
        Glide.with(context)
                .load(galleryFile.getThumbUri())
                .into(holder.imgThumb);
        holder.imgFullscreen.setOnClickListener(v -> toggleFullscreen(weakReference.get()));
        holder.imgFullscreen.setVisibility(isFullscreen ? View.GONE : View.VISIBLE);
        holder.rLPlay.setOnClickListener(v -> {
            holder.rLPlay.setVisibility(View.GONE);
            if (galleryFile.getDecryptedCacheUri() != null) {
                playVideo(context, galleryFile.getDecryptedCacheUri(), holder);
            } else {
                Encryption.decryptToCache(context, galleryFile.getUri(), Settings.getInstance(context).getTempPassword(), new Encryption.IOnUriResult() {
                    @Override
                    public void onUriResult(Uri outputUri) {
                        galleryFile.setDecryptedCacheUri(outputUri);
                        Log.e(TAG, "onUriResult: created " + outputUri);
                        if (holder.getBindingAdapterPosition() == position && isPagerShown) {
                            playVideo(context, outputUri, holder);
                        }
                    }

                    @Override
                    public void onError(Exception e) {
                        e.printStackTrace();
                    }

                    @Override
                    public void onInvalidPassword(InvalidPasswordException e) {
                        e.printStackTrace();
                    }
                }, true, galleryFile.getThumbUri());
            }
        });
    }

    private void playVideo(FragmentActivity context, Uri outputUri, GalleryPagerViewHolder.GalleryPagerVideoViewHolder holder) {
        lastPlayerPos = holder.getBindingAdapterPosition();
        if (player == null) {
            //DefaultDataSource.Factory defaultFactory = new DefaultDataSource.Factory(context);
            //DataSource.Factory dataSourceFactory = new ChaCha20DataSourceFactory(context, galleryFile.getThumbUri(), defaultFactory.createDataSource());
            //ProgressiveMediaSource mediaSource = new ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(MediaItem.fromUri(galleryFile.getUri()));
            player = new ExoPlayer.Builder(context)
                    //.setMediaSourceFactory(new ProgressiveMediaSource.Factory(dataSourceFactory))
                    .build();
            player.setRepeatMode(Player.REPEAT_MODE_ONE);
        }
        CacheDataSourceFactory cacheDataSourceFactory = new CacheDataSourceFactory(context, 300 * 1024 * 1024, 50 * 1024 * 1024);
        MediaSource videoSource = new ProgressiveMediaSource.Factory(cacheDataSourceFactory).createMediaSource(MediaItem.fromUri(outputUri));
        /*MediaItem mediaItem = new MediaItem.Builder()
                .setMimeType("video/mp4")
                .setUri(outputUri)
                .build();
        player.setMediaItem(mediaItem);*/
        holder.playerView.setControllerShowTimeoutMs(1500);
        player.setMediaSource(videoSource);
        player.addListener(new Player.Listener() {
            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                Player.Listener.super.onIsPlayingChanged(isPlaying);
                holder.lLButtons.setVisibility(isPlaying ? View.INVISIBLE : View.VISIBLE);
            }
        });
        if (playerView != null) {
            StyledPlayerView.switchTargetView(player, playerView, holder.playerView);
            playerView.setPlayer(null);
        } else {
            holder.playerView.setPlayer(player);
        }
        playerView = holder.playerView;
        player.prepare();
        player.setPlayWhenReady(true);
        playerView.hideController();
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
                    //Toaster.getInstance(context).showLong("Failed to decrypt " + galleryFile.getName() + ": " + e.getMessage());
                }

                @Override
                public void onInvalidPassword(InvalidPasswordException e) {
                    e.printStackTrace();
                    //Log.e(TAG, "onInvalidPassword: " + e.getMessage());
                    //removeFileAt(holder.getAdapterPosition(), context);
                }
            }, false, null)).start();
        } else {
            loadImage(galleryFile.getDecryptedCacheUri(), holder, context);
        }
    }

    private void onItemPressed(FragmentActivity context, GalleryPagerViewHolder holder) {
        //context.onBackPressed();
        //toggleFullscreen(context);
        setFullscreen(context, !this.isFullscreen);
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

    private void setFullscreen(@NonNull FragmentActivity context, boolean fullscreen) {
        this.isFullscreen = fullscreen;
        Window window = context.getWindow();
        if (fullscreen) {
            window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
            window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
        }
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
                (dialog, which) -> new Thread(() -> {
                    Encryption.IOnUriResult result = new Encryption.IOnUriResult() {
                        @Override
                        public void onUriResult(Uri outputUri) {
                            context.runOnUiThread(() -> Toaster.getInstance(context).showLong(context.getString(R.string.gallery_file_exported, FileStuff.getFilenameWithPathFromUri(outputUri))));
                        }

                        @Override
                        public void onError(Exception e) {
                            context.runOnUiThread(() -> Toaster.getInstance(context).showLong(context.getString(R.string.gallery_file_not_exported, e.getMessage())));
                        }

                        @Override
                        public void onInvalidPassword(InvalidPasswordException e) {
                            //removeFileAt(holder.getAdapterPosition(), context);
                        }
                    };
                    if (galleryFile.isVideo()) {
                        Encryption.decryptAndExportVideo(context, galleryFile.getUri(), currentDirectory, Settings.getInstance(context).getTempPassword(), result, true, galleryFile.getThumbUri());
                    } else {
                        Encryption.decryptAndExportImage(context, galleryFile.getUri(), currentDirectory, Settings.getInstance(context).getTempPassword(), result);
                    }
                }).start()));
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

    public void pauseVideo() {
        if (player != null) {
            player.pause();
        }
        if (lastPlayerPos >= 0) {
            playerView.postDelayed(() -> notifyItemChanged(lastPlayerPos), 1);
        }
    }

    public void setIsPagerShown(boolean isPagerShown) {
        this.isPagerShown = isPagerShown;
        if (!isPagerShown) {
            pauseVideo();
        }
        setFullscreen(weakReference.get(), false);
    }
}
