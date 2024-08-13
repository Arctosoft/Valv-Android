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

package se.arctosoft.vault.adapters.viewholders;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import se.arctosoft.vault.databinding.AdapterGalleryViewpagerItemBinding;
import se.arctosoft.vault.databinding.AdapterGalleryViewpagerItemDirectoryBinding;
import se.arctosoft.vault.databinding.AdapterGalleryViewpagerItemGifBinding;
import se.arctosoft.vault.databinding.AdapterGalleryViewpagerItemImageBinding;
import se.arctosoft.vault.databinding.AdapterGalleryViewpagerItemTextBinding;
import se.arctosoft.vault.databinding.AdapterGalleryViewpagerItemVideoBinding;

public class GalleryPagerViewHolder extends RecyclerView.ViewHolder {
    public final AdapterGalleryViewpagerItemBinding parentBinding;

    private GalleryPagerViewHolder(AdapterGalleryViewpagerItemBinding parentBinding) {
        super(parentBinding.getRoot());
        this.parentBinding = parentBinding;
    }

    public static class GalleryPagerImageViewHolder extends GalleryPagerViewHolder {
        public final AdapterGalleryViewpagerItemImageBinding binding;

        public GalleryPagerImageViewHolder(AdapterGalleryViewpagerItemBinding parentBinding, AdapterGalleryViewpagerItemImageBinding binding) {
            super(parentBinding);
            this.binding = binding;
        }
    }

    public static class GalleryPagerGifViewHolder extends GalleryPagerViewHolder {
        public final AdapterGalleryViewpagerItemGifBinding binding;

        public GalleryPagerGifViewHolder(AdapterGalleryViewpagerItemBinding parentBinding, @NonNull AdapterGalleryViewpagerItemGifBinding binding) {
            super(parentBinding);
            this.binding = binding;
        }
    }

    public static class GalleryPagerVideoViewHolder extends GalleryPagerViewHolder {
        public final AdapterGalleryViewpagerItemVideoBinding binding;

        public GalleryPagerVideoViewHolder(AdapterGalleryViewpagerItemBinding parentBinding, @NonNull AdapterGalleryViewpagerItemVideoBinding binding) {
            super(parentBinding);
            this.binding = binding;
        }

    }

    public static class GalleryPagerTextViewHolder extends GalleryPagerViewHolder {
        public final AdapterGalleryViewpagerItemTextBinding binding;

        public GalleryPagerTextViewHolder(AdapterGalleryViewpagerItemBinding parentBinding, @NonNull AdapterGalleryViewpagerItemTextBinding binding) {
            super(parentBinding);
            this.binding = binding;
        }

    }

    public static class GalleryPagerDirectoryViewHolder extends GalleryPagerViewHolder {
        public final AdapterGalleryViewpagerItemDirectoryBinding binding;

        public GalleryPagerDirectoryViewHolder(AdapterGalleryViewpagerItemBinding parentBinding, @NonNull AdapterGalleryViewpagerItemDirectoryBinding binding) {
            super(parentBinding);
            this.binding = binding;
        }

    }
}
