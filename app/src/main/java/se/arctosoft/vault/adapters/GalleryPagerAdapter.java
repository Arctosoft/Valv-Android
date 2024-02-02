/*
 * Valv-Android
 * Copyright (C) 2023 Arctosoft AB
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see https://www.gnu.org/licenses/.
 */

package se.arctosoft.vault.adapters;

import android.content.Intent;
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
import androidx.core.content.FileProvider;
import androidx.core.content.res.ResourcesCompat;
import androidx.documentfile.provider.DocumentFile;
import androidx.fragment.app.FragmentActivity;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.ProgressiveMediaSource;
import androidx.media3.ui.PlayerView;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.davemorrissey.labs.subscaleview.ImageSource;
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView;
import com.google.android.material.color.MaterialColors;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.List;

import se.arctosoft.vault.R;
import se.arctosoft.vault.adapters.viewholders.GalleryPagerViewHolder;
import se.arctosoft.vault.data.FileType;
import se.arctosoft.vault.data.GalleryFile;
import se.arctosoft.vault.databinding.AdapterGalleryViewpagerItemBinding;
import se.arctosoft.vault.databinding.AdapterGalleryViewpagerItemGifBinding;
import se.arctosoft.vault.databinding.AdapterGalleryViewpagerItemImageBinding;
import se.arctosoft.vault.databinding.AdapterGalleryViewpagerItemVideoBinding;
import se.arctosoft.vault.encryption.Encryption;
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
    private final DocumentFile currentDirectory;
    private final boolean isAllFolder;
    private boolean isFullscreen;
    private final Settings settings;
    private ExoPlayer player;
    private PlayerView playerView;
    private int lastPlayerPos = -1;

    public GalleryPagerAdapter(FragmentActivity context, @NonNull List<GalleryFile> galleryFiles, IOnFileDeleted onFileDeleted, DocumentFile currentDirectory, boolean isAllFolder) {
        this.weakReference = new WeakReference<>(context);
        this.galleryFiles = galleryFiles;
        this.onFileDeleted = onFileDeleted;
        this.currentDirectory = currentDirectory;
        this.isFullscreen = false;
        this.settings = Settings.getInstance(context);
        this.isAllFolder = isAllFolder;
    }

    @NonNull
    @Override
    public GalleryPagerViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater layoutInflater = LayoutInflater.from(parent.getContext());
        AdapterGalleryViewpagerItemBinding parentBinding = AdapterGalleryViewpagerItemBinding.inflate(layoutInflater, parent, false);
        if (viewType == FileType.IMAGE.i) {
            AdapterGalleryViewpagerItemImageBinding imageBinding = AdapterGalleryViewpagerItemImageBinding.inflate(layoutInflater, parentBinding.content, true);
            return new GalleryPagerViewHolder.GalleryPagerImageViewHolder(parentBinding, imageBinding);
        } else if (viewType == FileType.GIF.i) {
            AdapterGalleryViewpagerItemGifBinding gifBinding = AdapterGalleryViewpagerItemGifBinding.inflate(layoutInflater, parentBinding.content, true);
            return new GalleryPagerViewHolder.GalleryPagerGifViewHolder(parentBinding, gifBinding);
        } else {
            AdapterGalleryViewpagerItemVideoBinding videoBinding = AdapterGalleryViewpagerItemVideoBinding.inflate(layoutInflater, parentBinding.content, true);
            return new GalleryPagerViewHolder.GalleryPagerVideoViewHolder(parentBinding, videoBinding);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull GalleryPagerViewHolder holder, int position) {
        FragmentActivity context = weakReference.get();
        GalleryFile galleryFile = galleryFiles.get(position);

        holder.parentBinding.txtName.setText(galleryFile.getName());
        if (holder instanceof GalleryPagerViewHolder.GalleryPagerVideoViewHolder) {
            setupVideoView((GalleryPagerViewHolder.GalleryPagerVideoViewHolder) holder, context, galleryFile);
        } else {
            setupImageView(holder, context, galleryFile);
        }
        setupButtons(holder, context, galleryFile);
        loadOriginalFilename(galleryFile, context, holder, position);
        loadNote(holder, context, galleryFile);
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
                } else if (o instanceof GalleryGridAdapter.Payload p) {
                    if (p.type() == GalleryGridAdapter.Payload.TYPE_NEW_FILENAME) {
                        holder.parentBinding.txtName.setText(galleryFiles.get(position).getName());
                        found = true;
                    } else if (p.type() == GalleryGridAdapter.Payload.TYPE_LOADED_NOTE) {
                        loadNote(holder, weakReference.get(), galleryFiles.get(position));
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
                        context.runOnUiThread(() -> holder.parentBinding.txtName.setText(galleryFile.getName()));
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
        holder.binding.rLPlay.setVisibility(View.VISIBLE);
        holder.binding.playerView.setVisibility(View.INVISIBLE);
        Glide.with(context)
                .load(galleryFile.getThumbUri())
                .into(holder.binding.imgThumb);
        holder.binding.imgFullscreen.setOnClickListener(v -> toggleFullscreen(weakReference.get()));
        holder.binding.imgFullscreen.setVisibility(isFullscreen ? View.GONE : View.VISIBLE);
        holder.binding.rLPlay.setOnClickListener(v -> {
            holder.binding.rLPlay.setVisibility(View.GONE);
            holder.binding.playerView.setVisibility(View.VISIBLE);
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
        holder.binding.playerView.setControllerShowTimeoutMs(1500);
        //player.setMediaSource(videoSource);
        player.addListener(new Player.Listener() {
            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                Player.Listener.super.onIsPlayingChanged(isPlaying);
                holder.parentBinding.lLButtons.setVisibility(isPlaying ? View.INVISIBLE : View.VISIBLE);
            }

            @Override
            public void onPlayerError(@NonNull PlaybackException error) {
                Player.Listener.super.onPlayerError(error);
                Toaster.getInstance(context).showLong(context.getString(R.string.gallery_video_error, error.getMessage()));
            }
        });
        if (playerView != null) {
            PlayerView.switchTargetView(player, playerView, holder.binding.playerView);
            playerView.setPlayer(null);
        } else {
            holder.binding.playerView.setPlayer(player);
        }
        playerView = holder.binding.playerView;
        player.prepare();
        player.setPlayWhenReady(true);
        playerView.hideController();
    }

    private void setupImageView(GalleryPagerViewHolder holder, FragmentActivity context, GalleryFile galleryFile) {
        if (holder instanceof GalleryPagerViewHolder.GalleryPagerImageViewHolder) {
            ((GalleryPagerViewHolder.GalleryPagerImageViewHolder) holder).binding.imageView.setOnClickListener(v -> onItemPressed(context));
            ((GalleryPagerViewHolder.GalleryPagerImageViewHolder) holder).binding.imageView.setMinimumDpi(40);
            ((GalleryPagerViewHolder.GalleryPagerImageViewHolder) holder).binding.imageView.setOrientation(SubsamplingScaleImageView.ORIENTATION_USE_EXIF);
            ((GalleryPagerViewHolder.GalleryPagerImageViewHolder) holder).binding.imageView.setOnStateChangedListener(new SubsamplingScaleImageView.OnStateChangedListener() {
                @Override
                public void onScaleChanged(float newScale, int origin) {
                    showButtons(holder, newScale <= ((GalleryPagerViewHolder.GalleryPagerImageViewHolder) holder).binding.imageView.getMinScale());
                }

                @Override
                public void onCenterChanged(PointF newCenter, int origin) {

                }
            });
        } else if (holder instanceof GalleryPagerViewHolder.GalleryPagerGifViewHolder) {
            ((GalleryPagerViewHolder.GalleryPagerGifViewHolder) holder).binding.gifImageView.setOnClickListener(v -> onItemPressed(context));
        }
        if (galleryFile.getDecryptedCacheUri() == null) {
            new Thread(() -> Encryption.decryptToCache(context, galleryFile.getUri(), FileStuff.getExtension(galleryFile.getName()), Settings.getInstance(context).getTempPassword(), new Encryption.IOnUriResult() {
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
            ((GalleryPagerViewHolder.GalleryPagerImageViewHolder) holder).binding.imageView.setImage(ImageSource.uri(outputUri));
        } else if (holder instanceof GalleryPagerViewHolder.GalleryPagerGifViewHolder) {
            Glide.with(context)
                    .asGif()
                    .load(outputUri)
                    .into(((GalleryPagerViewHolder.GalleryPagerGifViewHolder) holder).binding.gifImageView);
        }
    }

    private void showButtons(GalleryPagerViewHolder holder, boolean show) {
        if (isFullscreen) {
            show = false;
            holder.parentBinding.getRoot().setBackgroundColor(weakReference.get().getResources().getColor(R.color.black, weakReference.get().getTheme()));
        } else {
            holder.parentBinding.getRoot().setBackgroundColor(MaterialColors.getColor(weakReference.get(), R.attr.gallery_viewpager_background, Color.WHITE));
        }
        if (show) {
            holder.parentBinding.lLButtons.setVisibility(View.VISIBLE);
            holder.parentBinding.txtName.setVisibility(View.VISIBLE);
        } else {
            holder.parentBinding.lLButtons.setVisibility(View.GONE);
            holder.parentBinding.txtName.setVisibility(View.GONE);
        }
    }

    private void setupButtons(GalleryPagerViewHolder holder, FragmentActivity context, GalleryFile galleryFile) {
        if (isAllFolder) {
            holder.parentBinding.buttonNoteLayout.setVisibility(View.GONE);
            holder.parentBinding.lLButtons.setWeightSum(3);
        } else {
            holder.parentBinding.buttonNoteLayout.setVisibility(View.VISIBLE);
            holder.parentBinding.lLButtons.setWeightSum(4);
        }
        showButtons(holder, true);
        holder.parentBinding.btnDelete.setOnClickListener(v -> Dialogs.showConfirmationDialog(context, context.getString(R.string.dialog_delete_file_title), context.getString(R.string.dialog_delete_file_message), (dialog, which) -> {
            boolean deletedFile = FileStuff.deleteFile(context, galleryFile.getUri());
            boolean deletedThumb = FileStuff.deleteFile(context, galleryFile.getThumbUri());
            if (deletedFile) {
                int pos = holder.getBindingAdapterPosition();
                removeFileAt(pos, context);
            } else {
                Toaster.getInstance(context).showLong(context.getString(R.string.gallery_file_not_deleted));
            }
        }));
        holder.parentBinding.btnExport.setOnClickListener(v -> Dialogs.showConfirmationDialog(context, context.getString(R.string.dialog_export_title), context.getString(R.string.dialog_export_message),
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
                    Encryption.decryptAndExport(context, galleryFile.getUri(), currentDirectory, galleryFile, Settings.getInstance(context).getTempPassword(), result, galleryFile.isVideo());
                }).start()));
        holder.parentBinding.btnShare.setOnClickListener(v -> {
            if (galleryFile.getDecryptedCacheUri() != null) {
                shareWith(context, galleryFile.getDecryptedCacheUri());
            } else {
                Toaster.getInstance(context).showShort(context.getString(R.string.gallery_share_decrypting));
                Encryption.decryptToCache(context, galleryFile.getUri(), FileStuff.getExtension(galleryFile.getName()), Settings.getInstance(context).getTempPassword(), new Encryption.IOnUriResult() {
                    @Override
                    public void onUriResult(Uri outputUri) {
                        galleryFile.setDecryptedCacheUri(outputUri);
                        shareWith(context, outputUri);
                    }

                    @Override
                    public void onError(Exception e) {
                        e.printStackTrace();
                        Toaster.getInstance(context).showShort(context.getString(R.string.gallery_share_decrypting_error, e.getMessage()));
                    }

                    @Override
                    public void onInvalidPassword(InvalidPasswordException e) {
                        e.printStackTrace();
                        Toaster.getInstance(context).showShort(context.getString(R.string.gallery_share_decrypting_error, e.getMessage()));
                    }
                });
            }
        });
        if (!isAllFolder) {
            holder.parentBinding.btnNote.setOnClickListener(v -> Dialogs.showEditTextDialog(context, null, galleryFile.getNote(), text -> {
                if (text != null && text.isBlank()) {
                    text = null;
                }
                galleryFile.setNote(text);
                if (text == null) {
                    // delete note
                    if (galleryFile.hasNote()) {
                        FileStuff.deleteFile(context, galleryFile.getNoteUri());
                        galleryFile.setNoteUri(null);
                    }
                } else if (galleryFile.hasNote()) {
                    // overwrite
                    deleteNote(context, galleryFile);
                    saveNote(context, galleryFile, text);
                } else {
                    saveNote(context, galleryFile, text);
                }
                loadNote(holder, context, galleryFile);
            }));
        }
    }

    private void shareWith(FragmentActivity context, Uri decryptedCacheUri) {
        Uri uri = FileProvider.getUriForFile(weakReference.get(), "se.arctosoft.vault.fileprovider", new File(decryptedCacheUri.getPath()));
        if (uri != null) {
            Intent intent = new Intent()
                    .setAction(Intent.ACTION_SEND)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    .setDataAndType(uri, context.getContentResolver().getType(uri))
                    .putExtra(Intent.EXTRA_STREAM, uri);
            context.startActivity(Intent.createChooser(intent, context.getString(R.string.gallery_share_with)));
        }
    }

    private void deleteNote(FragmentActivity context, GalleryFile galleryFile) {
        DocumentFile oldFile = DocumentFile.fromSingleUri(context, galleryFile.getNoteUri());
        boolean deleted = oldFile.delete();
    }

    private void saveNote(FragmentActivity context, GalleryFile galleryFile, String text) {
        DocumentFile createdFile = Encryption.importNoteToDirectory(context, text, FileStuff.getNameWithoutPrefix(galleryFile.getEncryptedName()), currentDirectory, settings);
        if (createdFile != null) {
            galleryFile.setNoteUri(createdFile.getUri());
        }
    }

    private void loadNote(GalleryPagerViewHolder holder, FragmentActivity context, GalleryFile galleryFile) {
        if (galleryFile.hasNote()) {
            if (galleryFile.getNote() != null) {
                holder.parentBinding.noteLayout.setVisibility(View.VISIBLE);
                holder.parentBinding.note.setText(context.getString(R.string.gallery_note_click_to_show));
                final boolean[] expanded = {false};
                View.OnClickListener onClickListener = v -> {
                    if (expanded[0]) {
                        holder.parentBinding.noteAction.setImageDrawable(ResourcesCompat.getDrawable(context.getResources(), R.drawable.round_expand_less_24, context.getTheme()));
                        holder.parentBinding.note.setText(context.getString(R.string.gallery_note_click_to_show));
                        holder.parentBinding.noteLayout.setBackgroundColor(MaterialColors.getColor(context, R.attr.gallery_viewpager_buttons_background, Color.BLACK));
                    } else {
                        holder.parentBinding.noteAction.setImageDrawable(ResourcesCompat.getDrawable(context.getResources(), R.drawable.round_expand_more_24, context.getTheme()));
                        holder.parentBinding.note.setText(galleryFile.getNote());
                        holder.parentBinding.noteLayout.setBackgroundColor(MaterialColors.getColor(context, R.attr.gallery_viewpager_note_background, Color.BLACK));
                    }
                    expanded[0] = !expanded[0];
                };
                holder.parentBinding.noteAction.setOnClickListener(onClickListener);
                holder.parentBinding.note.setOnClickListener(onClickListener);
            } else {
                holder.parentBinding.noteLayout.setVisibility(View.VISIBLE);
                holder.parentBinding.note.setText(context.getString(R.string.gallery_loading_note));
                Encryption.decryptToCache(context, galleryFile.getNoteUri(), FileStuff.getExtension(galleryFile.getName()), settings.getTempPassword(), new Encryption.IOnUriResult() {
                    @Override
                    public void onUriResult(Uri outputUri) { // decrypted, now read it
                        try {
                            galleryFile.setNote(FileStuff.readTextFromUri(outputUri, context));
                        } catch (IOException e) {
                            e.printStackTrace();
                            galleryFile.setNote(context.getString(R.string.gallery_note_read_failed, e.getMessage()));
                        }
                        context.runOnUiThread(() -> notifyItemChanged(holder.getBindingAdapterPosition(), new GalleryGridAdapter.Payload(GalleryGridAdapter.Payload.TYPE_LOADED_NOTE)));
                    }

                    @Override
                    public void onError(Exception e) {
                        e.printStackTrace();
                        galleryFile.setNote(context.getString(R.string.gallery_note_decrypt_failed, e.getMessage()));
                        context.runOnUiThread(() -> notifyItemChanged(holder.getBindingAdapterPosition(), new GalleryGridAdapter.Payload(GalleryGridAdapter.Payload.TYPE_LOADED_NOTE)));

                    }

                    @Override
                    public void onInvalidPassword(InvalidPasswordException e) {
                        galleryFile.setNote(context.getString(R.string.gallery_note_decrypt_failed, e.getMessage()));
                        context.runOnUiThread(() -> notifyItemChanged(holder.getBindingAdapterPosition(), new GalleryGridAdapter.Payload(GalleryGridAdapter.Payload.TYPE_LOADED_NOTE)));
                    }
                });
            }
        } else {
            holder.parentBinding.noteLayout.setVisibility(View.GONE);
            holder.parentBinding.note.setText("");
        }
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
        if (holder instanceof GalleryPagerViewHolder.GalleryPagerImageViewHolder h) {
            h.binding.imageView.recycle();
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
