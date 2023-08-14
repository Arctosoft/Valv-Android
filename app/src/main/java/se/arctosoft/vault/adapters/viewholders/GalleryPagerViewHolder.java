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

import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.media3.ui.PlayerView;
import androidx.recyclerview.widget.RecyclerView;

import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView;

import se.arctosoft.vault.R;

public class GalleryPagerViewHolder extends RecyclerView.ViewHolder {
    public final TextView txtName;
    public final Button btnDelete, btnExport;
    public final LinearLayout lLButtons;
    public final View root;

    private GalleryPagerViewHolder(@NonNull View itemView) {
        super(itemView);
        root = itemView;
        txtName = itemView.findViewById(R.id.txtName);
        btnDelete = itemView.findViewById(R.id.btnDelete);
        btnExport = itemView.findViewById(R.id.btnExport);
        lLButtons = itemView.findViewById(R.id.lLButtons);
    }

    public static class GalleryPagerImageViewHolder extends GalleryPagerViewHolder {
        public final SubsamplingScaleImageView imageView;

        public GalleryPagerImageViewHolder(@NonNull View itemView) {
            super(itemView);
            imageView = itemView.findViewById(R.id.imageView);
        }
    }

    public static class GalleryPagerGifViewHolder extends GalleryPagerViewHolder {
        public final ImageView gifImageView;

        public GalleryPagerGifViewHolder(@NonNull View itemView) {
            super(itemView);
            gifImageView = itemView.findViewById(R.id.gifImageView);
        }
    }

    public static class GalleryPagerVideoViewHolder extends GalleryPagerViewHolder {
        public final PlayerView playerView;
        public final RelativeLayout rLPlay;
        public final ImageView imgThumb, imgFullscreen;

        public GalleryPagerVideoViewHolder(@NonNull View itemView) {
            super(itemView);
            playerView = itemView.findViewById(R.id.playerView);
            rLPlay = itemView.findViewById(R.id.rLPlay);
            imgThumb = itemView.findViewById(R.id.imgThumb);
            imgFullscreen = itemView.findViewById(R.id.imgFullscreen);
        }

    }
}
