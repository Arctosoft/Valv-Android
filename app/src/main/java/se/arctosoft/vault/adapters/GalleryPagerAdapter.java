package se.arctosoft.vault.adapters;

import android.graphics.Color;
import android.graphics.PointF;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.fragment.app.FragmentActivity;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.ProgressiveMediaSource;
import androidx.media3.ui.PlayerView;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.davemorrissey.labs.subscaleview.ImageSource;
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView;
import com.google.android.material.color.MaterialColors;

import java.io.FileNotFoundException;
import java.lang.ref.WeakReference;
import java.util.List;

import se.arctosoft.vault.R;
import se.arctosoft.vault.adapters.viewholders.GalleryPagerViewHolder;
import se.arctosoft.vault.data.FileType;
import se.arctosoft.vault.data.GalleryFile;
import se.arctosoft.vault.encryption.Encryption;
import se.arctosoft.vault.encryption.MyDataSource;
import se.arctosoft.vault.encryption.MyDataSourceFactory;
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
    private final Settings settings;
    private ExoPlayer player;
    private PlayerView playerView;
    private int lastPlayerPos = -1;

    public GalleryPagerAdapter(FragmentActivity context, @NonNull List<GalleryFile> galleryFiles, IOnFileDeleted onFileDeleted, Uri currentDirectory) {
        this.weakReference = new WeakReference<>(context);
        this.galleryFiles = galleryFiles;
        this.onFileDeleted = onFileDeleted;
        this.currentDirectory = currentDirectory;
        this.isFullscreen = false;
        this.settings = Settings.getInstance(context);
    }

    @NonNull
    @Override
    public GalleryPagerViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater layoutInflater = LayoutInflater.from(parent.getContext());
        ConstraintLayout v = (ConstraintLayout) layoutInflater.inflate(R.layout.adapter_gallery_viewpager_item, parent, false);
        ViewGroup view = v.findViewById(R.id.content);
        if (viewType == FileType.IMAGE.i) {
            view.addView(layoutInflater.inflate(R.layout.adapter_gallery_viewpager_item_image, view, false));
            return new GalleryPagerViewHolder.GalleryPagerImageViewHolder(v);
        } else if (viewType == FileType.GIF.i) {
            view.addView(layoutInflater.inflate(R.layout.adapter_gallery_viewpager_item_gif, view, false));
            return new GalleryPagerViewHolder.GalleryPagerGifViewHolder(v);
        } else {
            view.addView(layoutInflater.inflate(R.layout.adapter_gallery_viewpager_item_video, view, false));
            return new GalleryPagerViewHolder.GalleryPagerVideoViewHolder(v);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull GalleryPagerViewHolder holder, int position) {
        FragmentActivity context = weakReference.get();
        GalleryFile galleryFile = galleryFiles.get(position);

        holder.txtName.setText(galleryFile.getName());
        if (holder instanceof GalleryPagerViewHolder.GalleryPagerVideoViewHolder) {
            setupVideoView((GalleryPagerViewHolder.GalleryPagerVideoViewHolder) holder, context, galleryFile);
        } else {
            setupImageView(holder, context, galleryFile);
        }
        setupButtons(holder, context, galleryFile);
        loadOriginalFilename(galleryFile, context, holder, position);
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
                } else if (o instanceof GalleryGridAdapter.Payload) {
                    if (((GalleryGridAdapter.Payload) o).getType() == GalleryGridAdapter.Payload.TYPE_NEW_FILENAME) {
                        holder.txtName.setText(galleryFiles.get(position).getName());
                        found = true;
                    }
                }
            }
        }
        if (!found) {
            super.onBindViewHolder(holder, position, payloads);
        }
    }

    private void loadOriginalFilename(@NonNull GalleryFile galleryFile, FragmentActivity context, @NonNull GalleryPagerViewHolder holder, int position) {
        if (position < 0 || position >= galleryFiles.size() - 1) {
            return;
        }
        if (!galleryFile.isDirectory() && galleryFile.getOriginalName() == null) {
            new Thread(() -> {
                try {
                    String originalFilename = Encryption.getOriginalFilename(context.getContentResolver().openInputStream(galleryFile.getUri()), settings.getTempPassword(), false);
                    galleryFile.setOriginalName(originalFilename);
                    int pos = holder.getBindingAdapterPosition();
                    if (pos == position) {
                        context.runOnUiThread(() -> holder.txtName.setText(galleryFile.getName()));
                    } else if (pos >= 0 && pos < galleryFiles.size()) {
                        context.runOnUiThread(() -> notifyItemChanged(pos, new GalleryGridAdapter.Payload(GalleryGridAdapter.Payload.TYPE_NEW_FILENAME)));
                    }
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                    galleryFile.setOriginalName("");
                }
            }).start();
        }
    }


    private void setupVideoView(GalleryPagerViewHolder.GalleryPagerVideoViewHolder holder, FragmentActivity context, GalleryFile galleryFile) {
        holder.rLPlay.setVisibility(View.VISIBLE);
        holder.playerView.setVisibility(View.INVISIBLE);
        Glide.with(context)
                .load(galleryFile.getThumbUri())
                .into(holder.imgThumb);
        holder.imgFullscreen.setOnClickListener(v -> toggleFullscreen(weakReference.get()));
        holder.imgFullscreen.setVisibility(isFullscreen ? View.GONE : View.VISIBLE);
        holder.rLPlay.setOnClickListener(v -> {
            holder.rLPlay.setVisibility(View.GONE);
            holder.playerView.setVisibility(View.VISIBLE);
            playVideo(context, galleryFile.getUri(), holder);
        });
    }

    @OptIn(markerClass = UnstableApi.class)
    private void playVideo(FragmentActivity context, Uri fileUri, GalleryPagerViewHolder.GalleryPagerVideoViewHolder holder) {
        lastPlayerPos = holder.getBindingAdapterPosition();
        if (player == null) {
            DataSource.Factory dataSourceFactory = new MyDataSourceFactory(context);
            ProgressiveMediaSource.Factory progressiveFactory = new ProgressiveMediaSource.Factory(dataSourceFactory);
            player = new ExoPlayer.Builder(context)
                    .setMediaSourceFactory(progressiveFactory)
                    .build();
            player.setRepeatMode(Player.REPEAT_MODE_ONE);
        }
        MediaItem mediaItem = new MediaItem.Builder()
                .setMimeType("video/*")
                .setUri(fileUri)
                .build();
        player.setMediaItem(mediaItem);
        holder.playerView.setControllerShowTimeoutMs(1500);
        //player.setMediaSource(videoSource);
        player.addListener(new Player.Listener() {
            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                Player.Listener.super.onIsPlayingChanged(isPlaying);
                holder.lLButtons.setVisibility(isPlaying ? View.INVISIBLE : View.VISIBLE);
            }

            @Override
            public void onPlayerError(@NonNull PlaybackException error) {
                Player.Listener.super.onPlayerError(error);
                Toaster.getInstance(context).showLong(context.getString(R.string.gallery_video_error, error.getMessage()));
            }
        });
        if (playerView != null) {
            PlayerView.switchTargetView(player, playerView, holder.playerView);
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
            ((GalleryPagerViewHolder.GalleryPagerImageViewHolder) holder).imageView.setOnClickListener(v -> onItemPressed(context));
            ((GalleryPagerViewHolder.GalleryPagerImageViewHolder) holder).imageView.setMinimumDpi(40);
            ((GalleryPagerViewHolder.GalleryPagerImageViewHolder) holder).imageView.setOrientation(SubsamplingScaleImageView.ORIENTATION_USE_EXIF);
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
            ((GalleryPagerViewHolder.GalleryPagerGifViewHolder) holder).gifImageView.setOnClickListener(v -> onItemPressed(context));
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
            })).start();
        } else {
            loadImage(galleryFile.getDecryptedCacheUri(), holder, context);
        }
    }

    private void onItemPressed(FragmentActivity context) {
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
            holder.root.setBackgroundColor(weakReference.get().getResources().getColor(R.color.black, weakReference.get().getTheme()));
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
                    Encryption.decryptAndExport(context, galleryFile.getUri(), currentDirectory, Settings.getInstance(context).getTempPassword(), result, galleryFile.isVideo());
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

    public void releaseVideo() {
        if (player != null) {
            player.release();
            player = null;
        }
        if (lastPlayerPos >= 0 && playerView != null) {
            playerView.postDelayed(() -> notifyItemChanged(lastPlayerPos), 100);
        }
    }

    public void pauseVideo() {
        if (player != null) {
            player.pause();
        }
    }

    public void showPager(boolean showPager) {
        if (!showPager) {
            releaseVideo();
        }
        setFullscreen(weakReference.get(), false);
    }
}
