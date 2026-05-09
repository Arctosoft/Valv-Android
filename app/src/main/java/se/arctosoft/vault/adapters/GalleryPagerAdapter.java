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

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PointF;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.PopupMenu;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.FileProvider;
import androidx.core.content.res.ResourcesCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.documentfile.provider.DocumentFile;
import androidx.exifinterface.media.ExifInterface;
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
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.google.android.material.color.MaterialColors;

import org.json.JSONException;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.security.GeneralSecurityException;
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

            // --- NEW: Audio routes into the Video View Holder! ---
        } else if (viewType == FileType.TYPE_VIDEO || viewType == FileType.TYPE_AUDIO) {
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
            // Force the top name to be permanently hidden
            holder.parentBinding.txtName.setVisibility(View.GONE);
            holder.parentBinding.lLButtons.setVisibility(View.VISIBLE);

            // --- NEW: Apply Palette Chameleon Colors ---
            applyDynamicChameleonColor(context, holder, galleryFile.getThumbUri());

            if (holder instanceof GalleryPagerViewHolder.GalleryPagerVideoViewHolder) {
                holder.parentBinding.imgFullscreen.setVisibility(View.VISIBLE);
                setupVideoView((GalleryPagerViewHolder.GalleryPagerVideoViewHolder) holder, context, galleryFile);
            } else if (holder instanceof GalleryPagerViewHolder.GalleryPagerTextViewHolder) {
                holder.parentBinding.imgFullscreen.setVisibility(View.VISIBLE);
                setupTextView((GalleryPagerViewHolder.GalleryPagerTextViewHolder) holder, context, galleryFile);
                attachPullToDismiss(((GalleryPagerViewHolder.GalleryPagerTextViewHolder) holder).binding.text, holder.parentBinding.content, context);
            } else {
                holder.parentBinding.imgFullscreen.setVisibility(View.GONE);
                setupImageView(holder, context, galleryFile);
            }
            setupButtons(holder, context, galleryFile);
            loadOriginalFilename(galleryFile, context, holder, position);
            loadNote(holder, context, galleryFile);
        }
    }

    // --- NEW: Pull to Dismiss Logic ---
    private void attachPullToDismiss(View touchView, View animateView, FragmentActivity context) {
        touchView.setOnTouchListener(new View.OnTouchListener() {
            float startY = 0;
            float startX = 0;
            boolean isDragging = false;
            boolean isHandlingTouch = false;

            @Override
            public boolean onTouch(View v, android.view.MotionEvent event) {
                if (touchView instanceof MySubsamplingScaleImageView) {
                    MySubsamplingScaleImageView img = (MySubsamplingScaleImageView) touchView;
                    if (img.getScale() > img.getMinScale() + 0.05f) {
                        return false;
                    }
                }

                switch (event.getAction()) {
                    case android.view.MotionEvent.ACTION_DOWN:
                        startY = event.getRawY();
                        startX = event.getRawX();
                        isDragging = false;
                        isHandlingTouch = true;
                        return false;

                    case android.view.MotionEvent.ACTION_MOVE:
                        if (!isHandlingTouch) return false;
                        float deltaY = event.getRawY() - startY;
                        float deltaX = event.getRawX() - startX;

                        if (!isDragging && Math.abs(deltaX) > Math.abs(deltaY)) {
                            isHandlingTouch = false;
                            return false;
                        }

                        if (!isDragging && deltaY > 150) {
                            isDragging = true;
                            v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                        }

                        if (isDragging) {
                            float screenHeight = v.getHeight();
                            float scale = 1f - (Math.abs(deltaY) / (screenHeight * 1.5f));
                            scale = Math.max(0.5f, scale);
                            animateView.setScaleX(scale);
                            animateView.setScaleY(scale);
                            animateView.setTranslationY(deltaY);
                            return true;
                        }
                        break;

                    case android.view.MotionEvent.ACTION_UP:
                    case android.view.MotionEvent.ACTION_CANCEL:
                        if (isDragging) {
                            float deltaYUp = event.getRawY() - startY;
                            if (deltaYUp > v.getHeight() * 0.20f) {
                                context.onBackPressed();
                            } else {
                                animateView.animate()
                                        .scaleX(1f).scaleY(1f).translationY(0)
                                        .setDuration(250)
                                        .setInterpolator(new androidx.interpolator.view.animation.FastOutSlowInInterpolator())
                                        .start();
                            }
                            isDragging = false;
                            return true;
                        }
                        break;
                }
                return false;
            }
        });
    }

    // --- NEW: Chameleon Background Colors ---
    private void applyDynamicChameleonColor(FragmentActivity context, GalleryPagerViewHolder holder, Uri uri) {
        if (uri == null) return;
        Glide.with(context)
                .asBitmap()
                .load(uri)
                .apply(GlideStuff.getRequestOptions(useDiskCache))
                .into(new CustomTarget<android.graphics.Bitmap>() {
                    @Override
                    public void onResourceReady(@NonNull android.graphics.Bitmap resource, @Nullable Transition<? super android.graphics.Bitmap> transition) {
                        androidx.palette.graphics.Palette.from(resource).generate(palette -> {
                            if (palette != null) {
                                int defaultColor = context.getResources().getColor(R.color.black, context.getTheme());
                                int dominantColor = palette.getDarkMutedColor(defaultColor);

                                // Save the extracted color
                                holder.parentBinding.getRoot().setTag(dominantColor);

                                // Smoothly animate to it if we are in fullscreen mode
                                if (isFullscreen) {
                                    ValueAnimator colorAnimation = ValueAnimator.ofObject(new ArgbEvaluator(), defaultColor, dominantColor);
                                    colorAnimation.setDuration(400);
                                    colorAnimation.addUpdateListener(animator -> holder.parentBinding.getRoot().setBackgroundColor((int) animator.getAnimatedValue()));
                                    colorAnimation.start();
                                }
                            }
                        });
                    }
                    @Override
                    public void onLoadCleared(@Nullable Drawable placeholder) {}
                });
    }

    private void setupDirectoryView(@NonNull GalleryPagerViewHolder holder, FragmentActivity context, GalleryFile galleryFile) {
        holder.parentBinding.lLButtons.setVisibility(View.GONE);
        holder.parentBinding.imgFullscreen.setVisibility(View.GONE);
        holder.parentBinding.txtName.setVisibility(View.GONE);

        String folderName = new java.io.File(galleryFile.getNameWithPath()).getName();
        ((GalleryPagerViewHolder.GalleryPagerDirectoryViewHolder) holder).binding.name.setText(context.getString(R.string.gallery_click_to_open_directory, folderName));

        ((GalleryPagerViewHolder.GalleryPagerDirectoryViewHolder) holder).binding.getRoot().setOnClickListener(v -> {
            Bundle bundle = new Bundle();
            if (nestedPath != null) {
                bundle.putString(DirectoryFragment.ARGUMENT_DIRECTORY, galleryFile.getUri().toString());
                bundle.putString(DirectoryFragment.ARGUMENT_NESTED_PATH, nestedPath + "/" + new File(galleryFile.getUri().getPath()).getName());
            } else {
                bundle.putString(DirectoryFragment.ARGUMENT_DIRECTORY, galleryFile.getUri().toString());
            }
            galleryViewModel.setClickedDirectoryUri(galleryFile.getUri());
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
        String displayName = galleryFile.getOriginalName() != null ? galleryFile.getOriginalName() : galleryFile.getName();
        holder.parentBinding.txtName.setText(weakReference.get().getString(R.string.gallery_adapter_file_name, displayName, StringStuff.bytesToReadableString(galleryFile.getSize())));
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
                    } else if (p.type() == GalleryGridAdapter.Payload.TYPE_RELEASE_VIDEO && holder instanceof GalleryPagerViewHolder.GalleryPagerVideoViewHolder h) {
                        showVideoReady(h);
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

    @OptIn(markerClass = UnstableApi.class)
    private void setupVideoView(GalleryPagerViewHolder.GalleryPagerVideoViewHolder holder, FragmentActivity context, GalleryFile galleryFile) {
        // Frictionless UI: Hide the play button overlay, show the player immediately
        holder.binding.rLPlay.setVisibility(View.GONE);
        holder.binding.playerView.setVisibility(View.VISIBLE);
        holder.parentBinding.txtName.setVisibility(View.GONE);

        // Audio Thumbnail Injection
        if (galleryFile.isAudio()) {
            Glide.with(context)
                    .load(R.drawable.ic_outline_audio_file_24)
                    .centerInside()
                    .into(holder.binding.imgThumb);
        } else {
            Glide.with(context)
                    .load(galleryFile.getThumbUri())
                    .apply(GlideStuff.getRequestOptions(useDiskCache))
                    .into(holder.binding.imgThumb);
        }

        View controllerView = holder.binding.playerView;
        View gestureOverlay = controllerView.findViewById(R.id.gesture_overlay);
        TextView tvGestureText = controllerView.findViewById(R.id.tv_gesture_text);

        Runnable hideOverlay = () -> {
            if (gestureOverlay != null) {
                gestureOverlay.animate().alpha(0f).setDuration(250).withEndAction(() -> gestureOverlay.setVisibility(View.GONE));
            }
        };

        TextView btnAspectRatio = controllerView.findViewById(R.id.btnAspectRatio);
        if (btnAspectRatio != null) {
            btnAspectRatio.setOnClickListener(v -> {
                int currentMode = holder.binding.playerView.getResizeMode();
                if (currentMode == androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT) {
                    holder.binding.playerView.setResizeMode(androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM);
                    btnAspectRatio.setText("ZOOM");
                } else if (currentMode == androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM) {
                    holder.binding.playerView.setResizeMode(androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL);
                    btnAspectRatio.setText("FILL");
                } else {
                    holder.binding.playerView.setResizeMode(androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT);
                    btnAspectRatio.setText("FIT");
                }
            });
        }

        TextView btnRotate = controllerView.findViewById(R.id.btnRotate);
        if (btnRotate != null) {
            btnRotate.setOnClickListener(v -> {
                int currentOrientation = context.getResources().getConfiguration().orientation;
                if (currentOrientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) {
                    context.setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                } else {
                    context.setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
                }
            });
        }

        ImageButton btnPlayPause = controllerView.findViewById(R.id.custom_play_pause);
        if (btnPlayPause != null) {
            btnPlayPause.setOnClickListener(v -> {
                int pos = holder.getBindingAdapterPosition();
                if (pos >= 0) {
                    ExoPlayer player = players.get(pos);
                    if (player != null) {
                        if (player.isPlaying()) player.pause();
                        else player.play();
                        btnPlayPause.setImageResource(player.isPlaying() ? R.drawable.ic_baseline_pause_24 : R.drawable.ic_baseline_play_arrow_24);
                    }
                }
            });
        }

        final android.media.AudioManager audioManager = (android.media.AudioManager) context.getSystemService(Context.AUDIO_SERVICE);

        // --- Video Touch Engine (Combined Gestures + Haptics + Pull-to-Dismiss) ---
        holder.binding.playerView.setOnTouchListener(new View.OnTouchListener() {
            private float startY = 0f;
            private float startX = 0f;
            private int startVolume = 0;
            private float startBrightness = 0f;
            private boolean isRightSide = false;

            // Haptic Trackers
            private int lastHapticVolume = -1;
            private int lastHapticBrightness = -1;
            private boolean isPullingToDismiss = false;

            private final android.view.GestureDetector gestureDetector = new android.view.GestureDetector(context, new android.view.GestureDetector.SimpleOnGestureListener() {

                @Override
                public boolean onDown(android.view.MotionEvent e) {
                    startY = e.getRawY();
                    startX = e.getRawX();
                    isRightSide = e.getX() > (holder.binding.playerView.getWidth() / 2f);
                    startVolume = audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC);
                    android.view.Window window = context.getWindow();
                    startBrightness = window.getAttributes().screenBrightness;
                    if (startBrightness < 0) startBrightness = 0.5f;

                    lastHapticVolume = startVolume;
                    lastHapticBrightness = (int)(startBrightness * 100);
                    isPullingToDismiss = false;
                    return true;
                }

                @Override
                public boolean onSingleTapConfirmed(android.view.MotionEvent e) {
                    if (holder.binding.playerView.isControllerFullyVisible()) {
                        holder.binding.playerView.hideController();
                    } else {
                        holder.binding.playerView.showController();
                    }
                    return true;
                }

                @Override
                public boolean onDoubleTap(android.view.MotionEvent e) {
                    int pos = holder.getBindingAdapterPosition();
                    if (pos >= 0) {
                        ExoPlayer player = players.get(pos);
                        if (player != null) {
                            long currentPos = player.getCurrentPosition();

                            // Tactile feedback on Seek
                            holder.binding.playerView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);

                            gestureOverlay.animate().cancel();
                            gestureOverlay.setVisibility(View.VISIBLE);
                            gestureOverlay.setAlpha(1f);

                            if (e.getX() > (holder.binding.playerView.getWidth() / 2f)) {
                                player.seekTo(Math.min(player.getDuration(), currentPos + 10000));
                                tvGestureText.setText("⏩ +10s");
                            } else {
                                player.seekTo(Math.max(0, currentPos - 10000));
                                tvGestureText.setText("⏪ -10s");
                            }

                            holder.binding.playerView.removeCallbacks(hideOverlay);
                            holder.binding.playerView.postDelayed(hideOverlay, 800);
                        }
                    }
                    return true;
                }

                @Override
                public boolean onScroll(android.view.MotionEvent e1, android.view.MotionEvent e2, float distanceX, float distanceY) {
                    float deltaY = e2.getRawY() - startY;
                    float deltaX = e2.getRawX() - startX;

                    // Pull-to-dismiss integration
                    if (!isPullingToDismiss && deltaY > 150 && Math.abs(deltaY) > Math.abs(deltaX)) {
                        isPullingToDismiss = true;
                        holder.binding.playerView.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                    }

                    if (isPullingToDismiss) {
                        float screenHeight = holder.binding.playerView.getHeight();
                        float scale = 1f - (deltaY / (screenHeight * 1.5f));
                        scale = Math.max(0.5f, scale);
                        holder.parentBinding.content.setScaleX(scale);
                        holder.parentBinding.content.setScaleY(scale);
                        holder.parentBinding.content.setTranslationY(deltaY);
                        return true;
                    }

                    // Otherwise, execute Volume/Brightness logic
                    if (Math.abs(deltaX) > Math.abs(deltaY)) return false;

                    float swipePercentage = (startY - e2.getRawY()) / holder.binding.playerView.getHeight();

                    gestureOverlay.animate().cancel();
                    gestureOverlay.setVisibility(View.VISIBLE);
                    gestureOverlay.setAlpha(1f);

                    if (isRightSide) {
                        int maxVolume = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC);
                        int volumeChange = (int) (maxVolume * swipePercentage);
                        int newVolume = Math.max(0, Math.min(maxVolume, startVolume + volumeChange));
                        audioManager.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, newVolume, 0);

                        // Only tick when volume changes
                        if (newVolume != lastHapticVolume) {
                            holder.binding.playerView.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                            lastHapticVolume = newVolume;
                        }

                        int displayVol = (int) (((float) newVolume / maxVolume) * 100);
                        tvGestureText.setText("🔊 " + displayVol + "%");
                    } else {
                        android.view.Window window = context.getWindow();
                        android.view.WindowManager.LayoutParams lp = window.getAttributes();
                        float newBrightness = Math.max(0.01f, Math.min(1.0f, startBrightness + swipePercentage));
                        lp.screenBrightness = newBrightness;
                        window.setAttributes(lp);

                        int brightPercent = (int) (newBrightness * 100);
                        if (Math.abs(brightPercent - lastHapticBrightness) >= 3) {
                            holder.binding.playerView.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                            lastHapticBrightness = brightPercent;
                        }

                        tvGestureText.setText("☀️ " + brightPercent + "%");
                    }

                    holder.binding.playerView.removeCallbacks(hideOverlay);
                    holder.binding.playerView.postDelayed(hideOverlay, 800);

                    return true;
                }
            });

            @Override
            public boolean onTouch(View v, android.view.MotionEvent event) {
                if (event.getAction() == android.view.MotionEvent.ACTION_UP || event.getAction() == android.view.MotionEvent.ACTION_CANCEL) {
                    if (isPullingToDismiss) {
                        float deltaY = event.getRawY() - startY;
                        if (deltaY > v.getHeight() * 0.20f) {
                            context.onBackPressed();
                        } else {
                            holder.parentBinding.content.animate().scaleX(1f).scaleY(1f).translationY(0).setDuration(250).start();
                        }
                        isPullingToDismiss = false;
                        return true;
                    }
                }

                if (event.getY() > (holder.binding.playerView.getHeight() * 0.75f)) {
                    return false;
                }
                gestureDetector.onTouchEvent(event);
                return true;
            }
        });
    }

    private void showVideoReady(GalleryPagerViewHolder.GalleryPagerVideoViewHolder holder) {
        holder.binding.rLPlay.setVisibility(View.VISIBLE);
        holder.binding.playerView.setVisibility(View.GONE);
    }

    @OptIn(markerClass = UnstableApi.class)
    private void playVideo(FragmentActivity context, Uri fileUri, GalleryPagerViewHolder.GalleryPagerVideoViewHolder holder, int version, long startFrom) {
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
        player.seekTo(startFrom);
        ExoPlayer finalPlayer = player;
        player.addListener(new Player.Listener() {
            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                Player.Listener.super.onIsPlayingChanged(isPlaying);

                ImageButton playBtn = holder.binding.playerView.findViewById(R.id.custom_play_pause);
                if (playBtn != null) playBtn.setImageResource(isPlaying ? R.drawable.ic_baseline_pause_24 : R.drawable.ic_baseline_play_arrow_24);

                if (!isPlaying) {
                    galleryViewModel.setVideoPosition(finalPlayer.getCurrentPosition(), fileUri);
                }
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
        holder.binding.playerView.showController();
    }

    private void setupImageView(GalleryPagerViewHolder holder, FragmentActivity context, GalleryFile galleryFile) {
        if (holder instanceof GalleryPagerViewHolder.GalleryPagerImageViewHolder) {

            // --- NEW: Attach Pull to dismiss to the Image ---
            attachPullToDismiss(((GalleryPagerViewHolder.GalleryPagerImageViewHolder) holder).binding.imageView, holder.parentBinding.content, context);

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
            loadImage(galleryFile, (GalleryPagerViewHolder.GalleryPagerImageViewHolder) holder, context);
        } else if (holder instanceof GalleryPagerViewHolder.GalleryPagerGifViewHolder) {

            // --- NEW: Attach Pull to dismiss to Gifs ---
            attachPullToDismiss(((GalleryPagerViewHolder.GalleryPagerGifViewHolder) holder).binding.gifImageView, holder.parentBinding.content, context);

            ((GalleryPagerViewHolder.GalleryPagerGifViewHolder) holder).binding.gifImageView.setOnClickListener(v -> onItemPressed(context));
            loadGif(galleryFile, (GalleryPagerViewHolder.GalleryPagerGifViewHolder) holder, context);
        }
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

    private void loadImage(GalleryFile galleryFile, GalleryPagerViewHolder.GalleryPagerImageViewHolder holder, FragmentActivity context) {
        if (galleryFile.getOrientation() != -1) {
            holder.binding.imageView.setOrientation(galleryFile.getOrientation());
            holder.binding.imageView.setImage(ImageSource.uri(galleryFile.getUri(), password.getPassword(), galleryFile.getVersion()));
        } else {
            new Thread(() -> {
                Encryption.Streams streams = null;
                int orientation = -1;
                try {
                    ContentResolver contentResolver = context.getContentResolver();
                    streams = Encryption.getCipherInputStream(contentResolver.openInputStream(galleryFile.getUri()), password.getPassword(), false, galleryFile.getVersion());
                    ExifInterface exifInterface = new ExifInterface(streams.getInputStream());
                    orientation = exifInterface.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
                    if (orientation == ExifInterface.ORIENTATION_UNDEFINED) {
                        orientation = -1;
                    } else {
                        orientation = exifToDegrees(orientation);
                    }
                } catch (GeneralSecurityException | InvalidPasswordException | JSONException |
                         IOException e) {
                    e.printStackTrace();
                    context.runOnUiThread(() -> {
                        int i = holder.getBindingAdapterPosition();
                        if (i >= 0) {
                            removeFileAt(i, context);
                        }
                    });
                } finally {
                    if (streams != null) {
                        streams.close();
                    }
                }

                galleryFile.setOrientation(orientation);
                context.runOnUiThread(() -> {
                    holder.binding.imageView.setOrientation(galleryFile.getOrientation());
                    holder.binding.imageView.setImage(ImageSource.uri(galleryFile.getUri(), password.getPassword(), galleryFile.getVersion()));
                });
            }).start();
        }
    }

    private int exifToDegrees(int orientation) {
        if (orientation == ExifInterface.ORIENTATION_ROTATE_90) {
            return 90;
        } else if (orientation == ExifInterface.ORIENTATION_ROTATE_180) {
            return 180;
        } else if (orientation == ExifInterface.ORIENTATION_ROTATE_270) {
            return 270;
        }
        return 0;
    }

    private void loadGif(GalleryFile galleryFile, GalleryPagerViewHolder.GalleryPagerGifViewHolder holder, FragmentActivity context) {
        Glide.with(context)
                .load(galleryFile.getUri())
                .apply(GlideStuff.getRequestOptions(useDiskCache))
                .into(holder.binding.gifImageView);
    }

    // --- NEW: Updated showButtons to respect Palette Color ---
    private void showButtons(GalleryPagerViewHolder holder, boolean show) {
        if (isFullscreen) {
            show = false;
            Object tag = holder.parentBinding.getRoot().getTag();
            int defaultColor = weakReference.get().getResources().getColor(R.color.black, weakReference.get().getTheme());
            int color = tag instanceof Integer ? (int) tag : defaultColor;

            holder.parentBinding.getRoot().setBackgroundColor(color);
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
        menu.getItem(2).setVisible(!isAllFolder);
        menu.getItem(2).setEnabled(!isAllFolder);
        menu.getItem(3).setVisible(!isAllFolder && galleryFile.isText());
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
                if (galleryFile.hasNote()) {
                    FileStuff.deleteFile(context, galleryFile.getNoteUri());
                    galleryFile.setNoteUri(null);
                }
            } else if (galleryFile.hasNote()) {
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
                    };
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
        // Intentionally left blank. Note UI was removed for a cleaner edge-to-edge layout!
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

    // --- NEW: Smart ViewPager Scroll Engine ---
    private androidx.viewpager2.widget.ViewPager2 attachedViewPager;

    @Override
    public void onAttachedToRecyclerView(@NonNull RecyclerView recyclerView) {
        super.onAttachedToRecyclerView(recyclerView);
        if (recyclerView.getParent() instanceof androidx.viewpager2.widget.ViewPager2) {
            attachedViewPager = (androidx.viewpager2.widget.ViewPager2) recyclerView.getParent();
            attachedViewPager.registerOnPageChangeCallback(new androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
                @Override
                public void onPageSelected(int position) {
                    super.onPageSelected(position);
                    triggerActiveVideo(position);
                }
            });
        }
    }

    public void triggerActiveVideo(int position) {
        if (position < 0 || position >= galleryFiles.size()) return;

        GalleryFile file = galleryFiles.get(position);
        if (file.isVideo() || file.isAudio()) {
            if (attachedViewPager != null) {
                RecyclerView rv = (RecyclerView) attachedViewPager.getChildAt(0);
                rv.post(() -> {
                    // Force pause all background players to free up the decryption engine!
                    pausePlayers();

                    RecyclerView.ViewHolder holder = rv.findViewHolderForAdapterPosition(position);
                    if (holder instanceof GalleryPagerViewHolder.GalleryPagerVideoViewHolder) {
                        playVideo(weakReference.get(), file.getUri(), (GalleryPagerViewHolder.GalleryPagerVideoViewHolder) holder, file.getVersion(), galleryViewModel.getVideoPosition(file.getUri()));
                    }
                });
            }
        } else {
            // If the user swiped to an image, immediately pause the audio/video!
            pausePlayers();
        }
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

    @OptIn(markerClass = UnstableApi.class)
    public boolean videoIsLoaded(int pos) {
        ExoPlayer player = players.get(pos);
        return player != null && !player.isReleased();
    }
}