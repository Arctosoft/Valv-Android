package se.arctosoft.vault.adapters.viewholders;

import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.checkbox.MaterialCheckBox;

import se.arctosoft.vault.R;

public class GalleryGridViewHolder extends RecyclerView.ViewHolder {
    public final ImageView imageView, imgType;
    public final TextView txtName;
    public final MaterialCheckBox checked;

    public GalleryGridViewHolder(@NonNull View itemView) {
        super(itemView);
        imageView = itemView.findViewById(R.id.imageView);
        txtName = itemView.findViewById(R.id.txtName);
        checked = itemView.findViewById(R.id.checked);
        imgType = itemView.findViewById(R.id.imgType);
    }
}
