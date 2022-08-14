package se.arctosoft.vault.adapters.viewholders;

import android.view.View;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import se.arctosoft.vault.R;

public class GalleryItemViewHolder extends RecyclerView.ViewHolder {
    public final ImageView imageView;

    public GalleryItemViewHolder(@NonNull View itemView) {
        super(itemView);
        imageView = itemView.findViewById(R.id.imageView);
    }
}
