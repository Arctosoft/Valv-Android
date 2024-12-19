/*
 * Valv-Android
 * Copyright (C) 2024 Arctosoft AB
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
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.PopupMenu;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.FileProvider;
import androidx.core.content.res.ResourcesCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.documentfile.provider.DocumentFile;
import androidx.fragment.app.FragmentActivity;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.ProgressiveMediaSource;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.color.MaterialColors;

import java.io.File;
import java.io.FileNotFoundException;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import se.arctosoft.vault.BuildConfig;
import se.arctosoft.vault.DirectoryFragment;
import se.arctosoft.vault.R;
import se.arctosoft.vault.adapters.viewholders.GalleryPagerViewHolder;
import se.arctosoft.vault.data.FileType;
import se.arctosoft.vault.data.GalleryFile;
import se.arctosoft.vault.data.Password;
import se.arctosoft.vault.databinding.AdapterGalleryViewpagerItemBinding;
import se.arctosoft.vault.databinding.AdapterGalleryViewpagerItemDirectoryBinding;
import se.arctosoft.vault.databinding.AdapterGalleryViewpagerItemGifBinding;
import se.arctosoft.vault.databinding.AdapterGalleryViewpagerItemImageBinding;
import se.arctosoft.vault.databinding.AdapterGalleryViewpagerItemTextBinding;
import se.arctosoft.vault.databinding.AdapterGalleryViewpagerItemVideoBinding;
import se.arctosoft.vault.encryption.Encryption;
import se.arctosoft.vault.encryption.MyDataSourceFactory;
import se.arctosoft.vault.exception.InvalidPasswordException;
import se.arctosoft.vault.interfaces.IOnFileDeleted;
import se.arctosoft.vault.subsampling.ImageSource;
import se.arctosoft.vault.subsampling.MySubsamplingScaleImageView;
import se.arctosoft.vault.utils.Dialogs;
import se.arctosoft.vault.utils.FileStuff;
import se.arctosoft.vault.utils.GlideStuff;
import se.arctosoft.vault.utils.Pixels;
import se.arctosoft.vault.utils.Settings;
import se.arctosoft.vault.utils.StringStuff;
import se.arctosoft.vault.utils.Toaster;
import se.arctosoft.vault.viewmodel.GalleryViewModel;

public class GalleryPagerAdapter extends RecyclerView.Adapter<GalleryPagerViewHolder> {
    private static final String TAG = "GalleryFullscreenAdapter";

    private final WeakReference<FragmentActivity> weakReference;
    private final List<GalleryFile> galleryFiles;
    private final IOnFileDeleted onFileDeleted;
    private final DocumentFile currentDirectory;
    private final GalleryViewModel galleryViewModel;
    private final boolean isAllFolder, useDiskCache;
    private final String nestedPath;
    private final Map<Integer, ExoPlayer> players;
    private final Password password;
    private boolean isFullscreen;

    private final View.OnAttachStateChangeListener onAttachStateChangeListener = new View.OnAttachStateChangeListener() {
        @Override
        public void onViewAttachedToWindow(@NonNull View view) {
            view.requestApplyInsets();
        }

        @Override
        public void onViewDetachedFromWindow(@NonNull View view) {
        }
    };

    public GalleryPagerAdapter(FragmentActivity context, @NonNull List<GalleryFile> galleryFiles, IOnFileDeleted onFileDeleted, DocumentFile currentDirectory, boolean isAllFolder, String nestedPath, GalleryViewModel galleryViewModel) {
        this.weakReference = new WeakReference<>(context);
        this.galleryFiles = galleryFiles;
        this.onFileDeleted = onFileDeleted;
        this.currentDirectory = currentDirectory;
        this.galleryViewModel = galleryViewModel;
        this.isFullscreen = false;
        this.isAllFolder = isAllFolder;
        this.nestedPath = nestedPath;
        this.players = new HashMap<>();
        this.password = Password.getInstance();
        this.useDiskCache = Settings.getInstance(context).useDiskCache();
    }

    @NonNull
    @Override
    public GalleryPagerViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater layoutInflater = LayoutInflater.from(parent.getContext());
        AdapterGalleryViewpagerItemBinding parentBinding = AdapterGalleryViewpagerItemBinding.inflate(layoutInflater, parent, false);
        setPadding(parentBinding);
        if (viewType == FileType.TYPE_IMAGE) {
            AdapterGalleryViewpagerItemImageBinding imageBinding = AdapterGalleryViewpagerItemImageBinding.inflate(layoutInflater, parentBinding.content, true);
            return new GalleryPagerViewHolder.GalleryPagerImageViewHolder(parentBinding, imageBinding);
        } else if (viewType == FileType.TYPE_GIF) {
            AdapterGalleryViewpagerItemGifBinding gifBinding = AdapterGalleryViewpagerItemGifBinding.inflate(layoutInflater, parentBinding.content, true);
            return new GalleryPagerViewHolder.GalleryPagerGifViewHolder(parentBinding, gifBinding);
        } else if (viewType == FileType.TYPE_VIDEO) {
            AdapterGalleryViewpagerItemVideoBinding videoBinding = AdapterGalleryViewpagerItemVideoBinding.inflate(layoutInflater, parentBinding.content, true);
            return new GalleryPagerViewHolder.GalleryPagerVideoViewHolder(parentBinding, videoBinding);
        } else if (viewType == FileType.TYPE_TEXT) {
            AdapterGalleryViewpagerItemTextBinding textBinding = AdapterGalleryViewpagerItemTextBinding.inflate(layoutInflater, parentBinding.content, true);
            setViewPadding(textBinding.text);
            return new GalleryPagerViewHolder.GalleryPagerTextViewHolder(parentBinding, textBinding);
        } else {
            AdapterGalleryViewpagerItemDirectoryBinding videoBinding = AdapterGalleryViewpagerItemDirectoryBinding.inflate(layoutInflater, parentBinding.content, true);
            return new GalleryPagerViewHolder.GalleryPagerDirectoryViewHolder(parentBinding, videoBinding);
        }
    }

    private void setPadding(@NonNull AdapterGalleryViewpagerItemBinding parentBinding) {
        ViewCompat.setOnApplyWindowInsetsListener(parentBinding.lLButtons, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(bars.left, 0, bars.right, bars.bottom);
            return WindowInsetsCompat.CONSUMED;
        });
        ViewCompat.setOnApplyWindowInsetsListener(parentBinding.txtName, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(bars.left, bars.top, bars.right, 0);
            return WindowInsetsCompat.CONSUMED;
        });
        ViewCompat.setOnApplyWindowInsetsListener(parentBinding.imgFullscreen, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            ConstraintLayout.LayoutParams layoutParams = (ConstraintLayout.LayoutParams) v.getLayoutParams();
            layoutParams.setMargins(bars.left, bars.top, bars.right, 0);
            return WindowInsetsCompat.CONSUMED;
        });
        parentBinding.lLButtons.addOnAttachStateChangeListener(onAttachStateChangeListener);
        parentBinding.txtName.addOnAttachStateChangeListener(onAttachStateChangeListener);
        parentBinding.imgFullscreen.addOnAttachStateChangeListener(onAttachStateChangeListener);
    }

    private void setViewPadding(@NonNull View view) {
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            int horizontalPadding = Pixels.dpToPixel(4, weakReference.get());
            v.setPadding(bars.left + horizontalPadding, bars.top, bars.right + horizontalPadding, bars.bottom);
            return WindowInsetsCompat.CONSUMED;
        });
        view.addOnAttachStateChangeListener(onAttachStateChangeListener);
    }

    @Override
    public void onBindViewHolder(@NonNull GalleryPagerViewHolder holder, int position) {
        FragmentActivity context = weakReference.get();
        GalleryFile galleryFile = galleryFiles.get(position);

        if (holder instanceof GalleryPagerViewHolder.GalleryPagerDirectoryViewHolder) {
            setupDirectoryView(holder, context, galleryFile);
        } else {
            holder.parentBinding.txtName.setVisibility(View.VISIBLE);
            holder.parentBinding.lLButtons.setVisibility(View.VISIBLE);
            setName(holder, galleryFile);
            if (holder instanceof GalleryPagerViewHolder.GalleryPagerVideoViewHolder) {
                holder.parentBinding.imgFullscreen.setVisibility(View.VISIBLE);
                setupVideoView((GalleryPagerViewHolder.GalleryPagerVideoViewHolder) holder, context, galleryFile);
            } else if (holder instanceof GalleryPagerViewHolder.GalleryPagerTextViewHolder) {
                holder.parentBinding.imgFullscreen.setVisibility(View.VISIBLE);
                setupTextView((GalleryPagerViewHolder.GalleryPagerTextViewHolder) holder, context, galleryFile);
            } else {
                holder.parentBinding.imgFullscreen.setVisibility(View.GONE);
                setupImageView(holder, context, galleryFile);
            }
            setupButtons(holder, context, galleryFile);
            loadOriginalFilename(galleryFile, context, holder, position);
            loadNote(holder, context, galleryFile);
        }
    }

    private void setupDirectoryView(@NonNull GalleryPagerViewHolder holder, FragmentActivity context, GalleryFile galleryFile) {
        holder.parentBinding.lLButtons.setVisibility(View.GONE);
        holder.parentBinding.imgFullscreen.setVisibility(View.GONE);
        holder.parentBinding.noteLayout.setVisibility(View.GONE);
        holder.parentBinding.txtName.setVisibility(View.GONE);
        ((GalleryPagerViewHolder.GalleryPagerDirectoryViewHolder) holder).binding.name.setText(context.getString(R.string.gallery_click_to_open_directory, galleryFile.getNameWithPath()));
        ((GalleryPagerViewHolder.GalleryPagerDirectoryViewHolder) holder).binding.getRoot().setOnClickListener(v -> {
            /*Intent intent = new Intent(context, GalleryDirectoryActivity.class);
            if (nestedPath != null) {
                intent.putExtra(GalleryDirectoryActivity.EXTRA_DIRECTORY, galleryFile.getUri().toString())
                        .putExtra(GalleryDirectoryActivity.EXTRA_NESTED_PATH, nestedPath + "/" + new File(galleryFile.getUri().getPath()).getName());
            } else {
                intent.putExtra(GalleryDirectoryActivity.EXTRA_DIRECTORY, galleryFile.getUri().toString());
            }
            context.startActivity(intent);*/
            Bundle bundle = new Bundle();
            if (nestedPath != null) {
                bundle.putString(DirectoryFragment.ARGUMENT_DIRECTORY, galleryFile.getUri().toString());
                bundle.putString(DirectoryFragment.ARGUMENT_NESTED_PATH, nestedPath + "/" + new File(galleryFile.getUri().getPath()).getName());
            } else {
                bundle.putString(DirectoryFragment.ARGUMENT_DIRECTORY, galleryFile.getUri().toString());
            }
            Navigation.findNavController(((GalleryPagerViewHolder.GalleryPagerDirectoryViewHolder) holder).binding.getRoot()).navigate(R.id.action_directory_self, bundle);
        });
        GalleryFile firstFile = galleryFile.getFirstFile();
        if (firstFile != null) {
            Glide.with(context)
                    .load(firstFile.getThumbUri())
                    .apply(GlideStuff.getRequestOptions(useDiskCache))
                    .into(((GalleryPagerViewHolder.GalleryPagerDirectoryViewHolder) holder).binding.thumb);
        }
    }

    private void setName(@NonNull GalleryPagerViewHolder holder, GalleryFile galleryFile) {
        holder.parentBinding.txtName.setText(weakReference.get().getString(R.string.gallery_adapter_file_name, galleryFile.getName(), StringStuff.bytesToReadableString(galleryFile.getSize())));
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
                        setName(holder, galleryFiles.get(position));
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
                    String originalFilename = Encryption.getOriginalFilename(context.getContentResolver().openInputStream(galleryFile.getUri()), password.getPassword(), false, galleryFile.getVersion());
                    galleryFile.setOriginalName(originalFilename);
                    int pos = holder.getBindingAdapterPosition();
                    if (pos == position) {
                        context.runOnUiThread(() -> setName(holder, galleryFile));
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

    private void setupTextView(GalleryPagerViewHolder.GalleryPagerTextViewHolder holder, FragmentActivity context, GalleryFile galleryFile) {
        holder.binding.text.setText(galleryFile.getText());
        holder.binding.text.setTextColor(context.getResources().getColor(this.isFullscreen || context.getResources().getBoolean(R.bool.night) ? R.color.text_color_light : R.color.text_color_dark, context.getTheme()));
        holder.binding.text.setTextIsSelectable(true);
    }

    private void setupVideoView(GalleryPagerViewHolder.GalleryPagerVideoViewHolder holder, FragmentActivity context, GalleryFile galleryFile) {
        holder.binding.rLPlay.setVisibility(View.VISIBLE);
        holder.binding.playerView.setVisibility(View.INVISIBLE);
        Glide.with(context)
                .load(galleryFile.getThumbUri())
                .apply(GlideStuff.getRequestOptions(useDiskCache))
                .into(holder.binding.imgThumb);
        holder.parentBinding.imgFullscreen.setVisibility(isFullscreen ? View.GONE : View.VISIBLE);
        holder.binding.rLPlay.setOnClickListener(v -> {
            holder.binding.rLPlay.setVisibility(View.GONE);
            holder.binding.playerView.setVisibility(View.VISIBLE);
            playVideo(context, galleryFile.getUri(), holder, galleryFile.getVersion());
        });
    }

    @OptIn(markerClass = UnstableApi.class)
    private void playVideo(FragmentActivity context, Uri fileUri, GalleryPagerViewHolder.GalleryPagerVideoViewHolder holder, int version) {
        final int pos = holder.getBindingAdapterPosition();
        ExoPlayer player = players.get(pos);
        for (ExoPlayer player1 : players.values()) {
            if (player1 != player && player1 != null) {
                player1.pause();
            }
        }
        if (player == null) {
            DataSource.Factory dataSourceFactory = new MyDataSourceFactory(context, version, password);
            ProgressiveMediaSource.Factory progressiveFactory = new ProgressiveMediaSource.Factory(dataSourceFactory);
            player = new ExoPlayer.Builder(context)
                    .setMediaSourceFactory(progressiveFactory)
                    .build();
            player.setRepeatMode(Player.REPEAT_MODE_ONE);
            players.put(pos, player);
        }
        MediaItem mediaItem = new MediaItem.Builder()
                .setMimeType("video/*")
                .setUri(fileUri)
                .build();
        player.setMediaItem(mediaItem);
        holder.binding.playerView.setControllerShowTimeoutMs(1500);
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
        holder.binding.playerView.setPlayer(player);
        player.prepare();
        player.setPlayWhenReady(true);
        holder.binding.playerView.hideController();
    }

    private void setupImageView(GalleryPagerViewHolder holder, FragmentActivity context, GalleryFile galleryFile) {
        if (holder instanceof GalleryPagerViewHolder.GalleryPagerImageViewHolder) {
            ((GalleryPagerViewHolder.GalleryPagerImageViewHolder) holder).binding.imageView.setOnClickListener(v -> onItemPressed(context));
            ((GalleryPagerViewHolder.GalleryPagerImageViewHolder) holder).binding.imageView.setMinimumDpi(40);
            ((GalleryPagerViewHolder.GalleryPagerImageViewHolder) holder).binding.imageView.setOrientation(MySubsamplingScaleImageView.ORIENTATION_USE_EXIF);
            ((GalleryPagerViewHolder.GalleryPagerImageViewHolder) holder).binding.imageView.setOnStateChangedListener(new MySubsamplingScaleImageView.OnStateChangedListener() {
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
        loadImage(galleryFile.getUri(), holder, context, galleryFile.getVersion());
    }

    private void onItemPressed(FragmentActivity context) {
        setFullscreen(context, !this.isFullscreen);
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

    private void loadImage(Uri uri, GalleryPagerViewHolder holder, FragmentActivity context, int version) {
        if (holder instanceof GalleryPagerViewHolder.GalleryPagerImageViewHolder) {
            ((GalleryPagerViewHolder.GalleryPagerImageViewHolder) holder).binding.imageView.setImage(ImageSource.uri(uri, password.getPassword(), version));
        } else if (holder instanceof GalleryPagerViewHolder.GalleryPagerGifViewHolder) {
            Glide.with(context)
                    //.asGif()
                    .load(uri)
                    .apply(GlideStuff.getRequestOptions(useDiskCache))
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
        showButtons(holder, true);
        holder.parentBinding.btnDelete.setOnClickListener(v -> showDelete(context, galleryFile, holder));
        holder.parentBinding.btnExport.setOnClickListener(v -> showExport(context, galleryFile));
        holder.parentBinding.btnMenu.setOnClickListener(v -> showMenu(context, galleryFile, holder));
        holder.parentBinding.imgFullscreen.setOnClickListener(v -> {
            toggleFullscreen(weakReference.get());
            if (holder instanceof GalleryPagerViewHolder.GalleryPagerTextViewHolder) {
                setupTextView((GalleryPagerViewHolder.GalleryPagerTextViewHolder) holder, context, galleryFile);
            }
        });
    }

    private void showMenu(FragmentActivity context, GalleryFile galleryFile, GalleryPagerViewHolder holder) {
        PopupMenu popup = new PopupMenu(context, holder.parentBinding.btnMenu);
        Menu menu = popup.getMenu();
        popup.getMenuInflater().inflate(R.menu.menu_gallery_viewpager, menu);
        popup.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.edit_note) {
                showEditNote(context, galleryFile, holder);
            } else if (id == R.id.share) {
                loadShareOrOpen(context, galleryFile, false);
            } else if (id == R.id.open_with) {
                loadShareOrOpen(context, galleryFile, true);
            } else if (id == R.id.edit_text) {
                showEditFile(context, galleryFile, holder);
            }
            return true;
        });
        menu.getItem(2).setVisible(!isAllFolder); // hide edit note in All folder
        menu.getItem(2).setEnabled(!isAllFolder);
        menu.getItem(3).setVisible(!isAllFolder && galleryFile.isText()); // hide edit text in All folder and for non-text files
        menu.getItem(3).setEnabled(!isAllFolder && galleryFile.isText());

        popup.show();
    }

    private void showEditNote(FragmentActivity context, GalleryFile galleryFile, GalleryPagerViewHolder holder) {
        Dialogs.showEditNoteDialog(context, galleryFile.getNote(), text -> {
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
        });
    }

    private void showEditFile(FragmentActivity context, GalleryFile galleryFile, GalleryPagerViewHolder holder) {
        Dialogs.showImportTextDialog(context, galleryFile.getText(), true, text -> {
            if (text == null || text.isBlank()) {
                return;
            }
            galleryFile.setText(text);
            String name = FileStuff.getNameWithoutPrefix(galleryFile.getEncryptedName());
            int lio = name.lastIndexOf(".txt");
            if (lio > 0) {
                name = name.substring(0, lio);
            }
            Log.e(TAG, "showEditFile: " + name + ", " + galleryFile.getVersion());
            DocumentFile.fromSingleUri(context, galleryFile.getUri()).delete();
            if (galleryFile.getNoteUri() != null) {
                DocumentFile.fromSingleUri(context, galleryFile.getNoteUri()).delete();
            }

            DocumentFile createdFile = Encryption.importTextToDirectory(context, text, name, currentDirectory, password.getPassword(), galleryFile.getVersion());
            if (createdFile != null) {
                galleryFile.setFileUri(createdFile.getUri());
            }
            galleryViewModel.getOnAdapterItemChanged().onChanged(holder.getBindingAdapterPosition());
        });
    }

    private void loadShareOrOpen(FragmentActivity context, GalleryFile galleryFile, boolean open) {
        if (galleryFile.getDecryptedCacheUri() != null) {
            shareOrOpenWith(context, galleryFile.getDecryptedCacheUri(), open);
        } else {
            Toaster.getInstance(context).showShort(context.getString(R.string.gallery_share_decrypting));
            Encryption.decryptToCache(context, galleryFile.getUri(), FileStuff.getExtensionOrDefault(galleryFile), galleryFile.getVersion(), password.getPassword(), new Encryption.IOnUriResult() {
                @Override
                public void onUriResult(Uri outputUri) {
                    galleryFile.setDecryptedCacheUri(outputUri);
                    shareOrOpenWith(context, outputUri, open);
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
    }

    private void showExport(FragmentActivity context, GalleryFile galleryFile) {
        Dialogs.showConfirmationDialog(context, context.getString(R.string.dialog_export_title), context.getString(R.string.dialog_export_message),
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
                    }; // TODO does not export to current directory
                    Encryption.decryptAndExport(context, galleryFile.getUri(), currentDirectory, galleryFile, galleryFile.isVideo(), galleryFile.getVersion(), password.getPassword(), result);
                }).start());
    }

    private void showDelete(FragmentActivity context, GalleryFile galleryFile, GalleryPagerViewHolder holder) {
        Dialogs.showConfirmationDialog(context, context.getString(R.string.dialog_delete_file_title), context.getString(R.string.dialog_delete_file_message), (dialog, which) -> {
            boolean deletedFile = FileStuff.deleteFile(context, galleryFile.getUri());
            boolean deletedThumb = FileStuff.deleteFile(context, galleryFile.getThumbUri());
            boolean deletedNote = FileStuff.deleteFile(context, galleryFile.getNoteUri());
            if (deletedFile) {
                int pos = holder.getBindingAdapterPosition();
                removeFileAt(pos, context);
            } else {
                Toaster.getInstance(context).showLong(context.getString(R.string.gallery_file_not_deleted));
            }
        });
    }

    private void shareOrOpenWith(FragmentActivity context, Uri decryptedCacheUri, boolean open) {
        Uri uri = FileProvider.getUriForFile(weakReference.get(), BuildConfig.APPLICATION_ID + ".fileprovider", new File(decryptedCacheUri.getPath()));
        if (uri != null) {
            Intent intent;
            if (open) {
                intent = new Intent(Intent.ACTION_VIEW, uri)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } else {
                intent = new Intent()
                        .setAction(Intent.ACTION_SEND)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        .setDataAndType(uri, context.getContentResolver().getType(uri))
                        .putExtra(Intent.EXTRA_STREAM, uri);
            }
            context.startActivity(Intent.createChooser(intent, context.getString(R.string.gallery_share_with)));
        }
    }

    private void deleteNote(FragmentActivity context, GalleryFile galleryFile) {
        DocumentFile oldFile = DocumentFile.fromSingleUri(context, galleryFile.getNoteUri());
        boolean deleted = oldFile.delete();
    }

    private void saveNote(FragmentActivity context, GalleryFile galleryFile, String text) {
        DocumentFile createdFile = Encryption.importNoteToDirectory(context, text, FileStuff.getNameWithoutPrefix(galleryFile.getEncryptedName()), currentDirectory, password.getPassword(), galleryFile.getVersion());
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
                new Thread(() -> {
                    String text = Encryption.readEncryptedTextFromUri(galleryFile.getNoteUri(), context, galleryFile.getVersion(), password.getPassword());
                    galleryFile.setNote(text);
                    context.runOnUiThread(() -> notifyItemChanged(holder.getBindingAdapterPosition(), new GalleryGridAdapter.Payload(GalleryGridAdapter.Payload.TYPE_LOADED_NOTE)));
                }).start();
            }
        } else {
            holder.parentBinding.noteLayout.setVisibility(View.GONE);
            holder.parentBinding.note.setText("");
        }
    }

    private void removeFileAt(int pos, FragmentActivity context) {
        galleryFiles.remove(pos);
        notifyItemRemoved(pos);
        onFileDeleted.onFileDeleted(pos);
        Toaster.getInstance(context).showLong(context.getString(R.string.gallery_file_deleted));
        if (galleryFiles.isEmpty()) {
            context.onBackPressed();
        }
    }

    @Override
    public int getItemViewType(int position) {
        GalleryFile galleryFile = galleryFiles.get(position);
        return galleryFile.getFileType().type;
    }

    @Override
    public void onViewDetachedFromWindow(@NonNull GalleryPagerViewHolder holder) {
        if (holder instanceof GalleryPagerViewHolder.GalleryPagerVideoViewHolder vh) {
            pauseVideo(vh);
        }
        super.onViewDetachedFromWindow(holder);
    }

    @Override
    public void onViewRecycled(@NonNull GalleryPagerViewHolder holder) {
        if (holder instanceof GalleryPagerViewHolder.GalleryPagerImageViewHolder h) {
            h.binding.imageView.recycle();
        } else if (holder instanceof GalleryPagerViewHolder.GalleryPagerVideoViewHolder vh) {
            releaseVideo(vh);
        }
        super.onViewRecycled(holder);
    }

    private void releaseVideo(GalleryPagerViewHolder.GalleryPagerVideoViewHolder holder) {
        final int pos = holder.getBindingAdapterPosition();
        holder.binding.playerView.setPlayer(null);
        if (pos >= 0) {
            ExoPlayer player = players.remove(pos);
            if (player != null) {
                player.release();
            }
        }
    }

    private void pauseVideo(GalleryPagerViewHolder.GalleryPagerVideoViewHolder holder) {
        final int pos = holder.getBindingAdapterPosition();
        if (pos >= 0) {
            ExoPlayer player = players.get(pos);
            if (player != null) {
                player.pause();
            }
        }
    }

    @Override
    public int getItemCount() {
        return galleryFiles.size();
    }

    public void releasePlayers() {
        for (Player p : players.values()) {
            if (p != null) {
                p.release();
            }
        }
        players.clear();
    }

    public void pausePlayers() {
        for (Player p : players.values()) {
            if (p != null && p.isPlaying()) {
                p.pause();
            }
        }
    }

    public void showPager(boolean showPager) {
        if (!showPager) {
            pausePlayers();
        }
        setFullscreen(weakReference.get(), false);
    }
}
