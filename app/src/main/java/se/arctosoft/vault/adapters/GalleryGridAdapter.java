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

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.icu.text.SimpleDateFormat;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.res.ResourcesCompat;
import androidx.documentfile.provider.DocumentFile;
import androidx.fragment.app.FragmentActivity;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import se.arctosoft.vault.DirectoryFragment;
import se.arctosoft.vault.R;
import se.arctosoft.vault.adapters.viewholders.GalleryGridViewHolder;
import se.arctosoft.vault.data.GalleryFile;
import se.arctosoft.vault.data.Password;
import se.arctosoft.vault.data.UniqueLinkedList;
import se.arctosoft.vault.databinding.AdapterGalleryGridItemBinding;
import se.arctosoft.vault.encryption.Encryption;
import se.arctosoft.vault.exception.InvalidPasswordException;
import se.arctosoft.vault.fastscroll.views.FastScrollRecyclerView;
import se.arctosoft.vault.interfaces.IOnFileClicked;
import se.arctosoft.vault.interfaces.IOnFileDeleted;
import se.arctosoft.vault.interfaces.IOnSelectionModeChanged;
import se.arctosoft.vault.utils.GlideStuff;
import se.arctosoft.vault.utils.Settings;
import se.arctosoft.vault.utils.StringStuff;
import se.arctosoft.vault.viewmodel.GalleryViewModel;

public class GalleryGridAdapter extends RecyclerView.Adapter<GalleryGridViewHolder> implements IOnSelectionModeChanged, FastScrollRecyclerView.SectionedAdapter {
    private static final String TAG = "GalleryFolderAdapter";

    private static final Object LOCK = new Object();
    private static final SimpleDateFormat simpleDateFormat = new SimpleDateFormat("dd MMM yyyy HH:mm:ss", Locale.ENGLISH);

    private final boolean isRootDir, useDiskCache;
    private boolean showFileNames, selectMode;
    private int lastSelectedPos;
    private String nestedPath;

    private final WeakReference<FragmentActivity> weakReference;
    private final List<GalleryFile> galleryFiles;
    private final UniqueLinkedList<GalleryFile> selectedFiles;
    private final Password password;
    private final GalleryViewModel galleryViewModel;
    private IOnFileDeleted onFileDeleted;
    private IOnFileClicked onFileCLicked;
    private IOnSelectionModeChanged onSelectionModeChanged;

    @NonNull
    @Override
    public String getSectionName(int position) {
        return simpleDateFormat.format(new Date(galleryFiles.get(position).getLastModified()));
    }

    record Payload(int type) {
        static final int TYPE_SELECT_ALL = 0;
        static final int TYPE_TOGGLE_FILENAME = 1;
        static final int TYPE_NEW_FILENAME = 2;
        static final int TYPE_LOADED_NOTE = 3;
    }

    public GalleryGridAdapter(FragmentActivity context, @NonNull List<GalleryFile> galleryFiles, boolean showFileNames, boolean isRootDir, GalleryViewModel galleryViewModel) {
        this.weakReference = new WeakReference<>(context);
        this.galleryFiles = galleryFiles;
        this.showFileNames = showFileNames;
        this.galleryViewModel = galleryViewModel;
        this.selectedFiles = new UniqueLinkedList<>();
        this.isRootDir = isRootDir;
        password = Password.getInstance();
        useDiskCache = Settings.getInstance(context).useDiskCache();
        Log.e(TAG, "GalleryGridAdapter: useDiskCache " + useDiskCache);
    }

    public void setNestedPath(String nestedPath) {
        this.nestedPath = nestedPath;
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
        AdapterGalleryGridItemBinding binding = AdapterGalleryGridItemBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new GalleryGridViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull GalleryGridViewHolder holder, int position) {
        FragmentActivity context = weakReference.get();
        GalleryFile galleryFile = galleryFiles.get(position);

        updateSelectedView(holder, galleryFile);
        holder.binding.txtName.setVisibility(showFileNames || galleryFile.isDirectory() ? View.VISIBLE : View.GONE);
        holder.binding.imageView.setImageDrawable(null);
        if (!isRootDir && (galleryFile.isGif() || galleryFile.isVideo() || galleryFile.isDirectory())) {
            holder.binding.imgType.setVisibility(View.VISIBLE);
            holder.binding.imgType.setImageDrawable(ResourcesCompat.getDrawable(context.getResources(), galleryFile.isGif()
                            ? R.drawable.ic_round_gif_24 : (galleryFile.isVideo()
                            ? R.drawable.ic_outline_video_file_24 : (galleryFile.isText() ? R.drawable.outline_text_snippet_24 : R.drawable.ic_round_folder_open_24)),
                    context.getTheme()));
        } else {
            holder.binding.imgType.setVisibility(View.GONE);
        }
        holder.binding.hasDescription.setVisibility(!isRootDir && galleryFile.hasNote() ? View.VISIBLE : View.GONE);

        holder.binding.imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        holder.binding.textView.setVisibility(View.GONE);
        holder.binding.textView.setText(null);
        if (galleryFile.isAllFolder()) {
            holder.binding.imageView.setVisibility(View.VISIBLE);
            holder.binding.imageView.setImageDrawable(ResourcesCompat.getDrawable(context.getResources(), R.drawable.round_all_inclusive_24, context.getTheme()));
            holder.binding.imageView.setScaleType(ImageView.ScaleType.CENTER);
            holder.binding.txtName.setText(context.getString(R.string.gallery_all));
        } else if (galleryFile.isDirectory()) {
            holder.binding.imageView.setVisibility(View.VISIBLE);
            galleryFile.findFilesInDirectory(context, () -> {
                if (context != null) {
                    context.runOnUiThread(() -> {
                        int bindingAdapterPosition = holder.getBindingAdapterPosition();
                        if (bindingAdapterPosition != RecyclerView.NO_POSITION) {
                            galleryViewModel.getOnAdapterItemChanged().onChanged(bindingAdapterPosition);
                        }
                    });
                }
            });
            GalleryFile firstFile = galleryFile.getFirstFile();
            if (firstFile == null) {
                Glide.with(context).clear(holder.binding.imageView);
            } else {
                Glide.with(context)
                        .load(firstFile.getThumbUri())
                        .apply(GlideStuff.getRequestOptions(useDiskCache))
                        .into(holder.binding.imageView);
            }
            holder.binding.txtName.setText(context.getString(R.string.gallery_adapter_folder_name, galleryFile.getNameWithPath(), galleryFile.getFileCount()));
        } else if (galleryFile.isText()) {
            holder.binding.imageView.setVisibility(View.GONE);
            holder.binding.textView.setText(galleryFile.getText() == null ? context.getString(R.string.loading) : galleryFile.getText());
            holder.binding.textView.setVisibility(View.VISIBLE);
            setItemFilename(holder, context, galleryFile);
            if (galleryFile.getText() == null) {
                readText(context, galleryFile, holder);
            }
        } else {
            //Log.e(TAG, "onBindViewHolder: load image, version " + galleryFile.getVersion() + ", " + galleryFile.getFileType().suffixPrefix);
            holder.binding.imageView.setVisibility(View.VISIBLE);
            if (galleryFile.getThumbUri() != null) {
                Glide.with(context)
                        .load(galleryFile.getThumbUri())
                        .apply(GlideStuff.getRequestOptions(useDiskCache))
                        .listener(new RequestListener<>() {
                            @Override
                            public boolean onLoadFailed(@Nullable GlideException e, Object model, @NonNull Target<Drawable> target, boolean isFirstResource) {
                                if (e != null) {
                                    for (Throwable t : e.getRootCauses()) {
                                        if (t instanceof InvalidPasswordException) {
                                            removeItem(holder.getBindingAdapterPosition());
                                            break;
                                        }
                                    }
                                }
                                return true;
                            }

                            @Override
                            public boolean onResourceReady(@NonNull Drawable resource, @NonNull Object model, Target<Drawable> target, @NonNull DataSource dataSource, boolean isFirstResource) {
                                return false;
                            }
                        })
                        .into(holder.binding.imageView);
            } else {
                Glide.with(context)
                        .load(R.drawable.outline_broken_image_24)
                        .centerInside()
                        .into(holder.binding.imageView);
            }
            setItemFilename(holder, context, galleryFile);
        }
        setClickListener(holder, context, galleryFile);
    }

    private void readText(FragmentActivity context, GalleryFile galleryFile, GalleryGridViewHolder holder) {
        new Thread(() -> {
            String text = Encryption.readEncryptedTextFromUri(galleryFile.getUri(), context, galleryFile.getVersion(), password.getPassword());
            galleryFile.setText(text);
            context.runOnUiThread(() -> {
                int pos = holder.getBindingAdapterPosition();
                if (pos >= 0) {
                    galleryViewModel.getOnAdapterItemChanged().onChanged(pos);
                }
            });
        }).start();
    }

    private void setItemFilename(@NonNull GalleryGridViewHolder holder, Context context, @NonNull GalleryFile galleryFile) {
        if (galleryFile.getSize() > 0) {
            holder.binding.txtName.setText(context.getString(R.string.gallery_adapter_file_name, galleryFile.getName(), StringStuff.bytesToReadableString(galleryFile.getSize())));
        } else {
            holder.binding.txtName.setText(galleryFile.getName());
        }
    }

    private void setClickListener(@NonNull GalleryGridViewHolder holder, FragmentActivity context, GalleryFile galleryFile) {
        holder.binding.layout.setOnClickListener(v -> {
            final int pos = holder.getBindingAdapterPosition();
            if (galleryFile.isAllFolder()) {
                if (!selectMode) {
                    Navigation.findNavController(holder.binding.layout).navigate(R.id.action_directory_to_directory_all);
                }
            } else if (selectMode) {
                if (isRootDir || !galleryFile.isDirectory()) {
                    if (!selectedFiles.contains(galleryFile)) {
                        selectedFiles.add(galleryFile);
                        lastSelectedPos = pos;
                    } else {
                        selectedFiles.remove(galleryFile);
                        if (selectedFiles.isEmpty()) {
                            setSelectMode(false);
                        }
                        lastSelectedPos = -1;
                    }
                    updateSelectedView(holder, galleryFile);
                }
            } else {
                if (galleryFile.isDirectory()) {
                    Bundle bundle = new Bundle();
                    if (isRootDir) {
                        bundle.putString(DirectoryFragment.ARGUMENT_DIRECTORY, DocumentFile.fromTreeUri(context, galleryFile.getUri()).getUri().toString());
                        bundle.putString(DirectoryFragment.ARGUMENT_NESTED_PATH, "/" + new File(galleryFile.getUri().getPath()).getName());
                    } else if (nestedPath != null) {
                        bundle.putString(DirectoryFragment.ARGUMENT_DIRECTORY, galleryFile.getUri().toString());
                        bundle.putString(DirectoryFragment.ARGUMENT_NESTED_PATH, nestedPath + "/" + new File(galleryFile.getUri().getPath()).getName());
                    } else {
                        bundle.putString(DirectoryFragment.ARGUMENT_DIRECTORY, galleryFile.getUri().toString());
                    }
                    galleryViewModel.setClickedDirectoryUri(galleryFile.getUri());
                    Navigation.findNavController(holder.binding.layout).navigate(R.id.action_directory_self, bundle);
                } else {
                    if (onFileCLicked != null) {
                        onFileCLicked.onClick(pos);
                    }
                }
            }
        });
        holder.binding.layout.setOnLongClickListener(v -> {
            if (!galleryFile.isAllFolder() && (isRootDir || !galleryFile.isDirectory())) {
                int pos = holder.getBindingAdapterPosition();
                if (!selectMode) {
                    setSelectMode(true);
                    holder.binding.layout.performClick();
                } else {
                    if (lastSelectedPos >= 0 && !selectedFiles.contains(galleryFile)) {
                        int minPos = Math.min(pos, lastSelectedPos);
                        int maxPos = Math.max(pos, lastSelectedPos);
                        if (minPos >= 0 && maxPos < galleryFiles.size()) {
                            for (int i = minPos; i >= 0 && i <= maxPos && i < galleryFiles.size(); i++) {
                                GalleryFile gf = galleryFiles.get(i);
                                if (gf != null && !selectedFiles.contains(gf)) {
                                    selectedFiles.add(gf);
                                }
                            }
                            notifyItemRangeChanged(minPos, 1 + (maxPos - minPos), new Payload(Payload.TYPE_SELECT_ALL));
                        }
                        //if (context instanceof GalleryDirectoryActivity activity) {
                        //    activity.onSelectionChanged(selectedFiles.size());
                        //}
                    } else {
                        holder.binding.layout.performClick();
                    }
                }
                lastSelectedPos = pos;
            }
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
                    GalleryFile galleryFile = galleryFiles.get(position);
                    holder.binding.txtName.setVisibility(showFileNames || galleryFile.isDirectory() ? View.VISIBLE : View.GONE);
                    found = true;
                } else if (((Payload) o).type == Payload.TYPE_NEW_FILENAME) {
                    setItemFilename(holder, weakReference.get(), galleryFiles.get(holder.getBindingAdapterPosition()));
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
            lastSelectedPos = -1;
            selectedFiles.clear();
            notifyItemRangeChanged(0, galleryFiles.size(), new Payload(Payload.TYPE_SELECT_ALL));
        }
        if (onSelectionModeChanged != null) {
            onSelectionModeChanged.onSelectionModeChanged(selectMode);
        }
    }

    private void updateSelectedView(GalleryGridViewHolder holder, GalleryFile galleryFile) {
        if (!galleryFile.isAllFolder() && selectMode && (isRootDir || !galleryFile.isDirectory())) {
            holder.binding.checked.setVisibility(View.VISIBLE);
            holder.binding.checked.setChecked(selectedFiles.contains(galleryFile));
        } else {
            holder.binding.checked.setVisibility(View.GONE);
            holder.binding.checked.setChecked(false);
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
            if (isRootDir) {
                List<GalleryFile> filtered = new ArrayList<>(galleryFiles.size() - 1);
                for (GalleryFile f : galleryFiles) {
                    if (!f.isAllFolder()) {
                        filtered.add(f);
                    }
                }
                selectedFiles.addAll(filtered);
            } else {
                for (GalleryFile g : galleryFiles) {
                    if (!g.isDirectory()) {
                        selectedFiles.add(g);
                    }
                }
            }
            notifyItemRangeChanged(0, galleryFiles.size(), new Payload(Payload.TYPE_SELECT_ALL));
        }
        //if (weakReference.get() instanceof GalleryDirectoryActivity activity) {
        //    activity.onSelectionChanged(selectedFiles.size());
        //}
    }

    public boolean toggleFilenames() {
        showFileNames = !showFileNames;
        notifyItemRangeChanged(0, galleryFiles.size(), new Payload(Payload.TYPE_TOGGLE_FILENAME));
        return showFileNames;
    }

    @NonNull
    public List<GalleryFile> getSelectedFiles() {
        return selectedFiles;
    }
}
