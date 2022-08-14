package se.arctosoft.vault.adapters.viewholders;

import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import se.arctosoft.vault.R;

public class GalleryViewHolder extends RecyclerView.ViewHolder {
    public final ImageView imageView;
    public final TextView txtName;

    public GalleryViewHolder(@NonNull View itemView) {
        super(itemView);
        imageView = itemView.findViewById(R.id.imageView);
        txtName = itemView.findViewById(R.id.txtName);
    }
}
