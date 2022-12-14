package se.arctosoft.vault.adapters.viewholders;

import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView;
import com.google.android.exoplayer2.ui.StyledPlayerView;

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
        public final StyledPlayerView playerView;
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
