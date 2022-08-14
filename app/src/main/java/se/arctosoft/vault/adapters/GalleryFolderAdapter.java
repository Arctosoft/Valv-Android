package se.arctosoft.vault.adapters;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.lang.ref.WeakReference;
import java.util.List;

import se.arctosoft.vault.R;
import se.arctosoft.vault.adapters.viewholders.GalleryFolderViewHolder;
import se.arctosoft.vault.data.GalleryDirectory;
import se.arctosoft.vault.data.GalleryFile;

public class GalleryFolderAdapter extends RecyclerView.Adapter<GalleryFolderViewHolder> {
    private static final String TAG = "GalleryFolderAdapter";
    private final WeakReference<FragmentActivity> weakReference;
    private final List<GalleryDirectory> galleryDirectories;

    public GalleryFolderAdapter(FragmentActivity context, @NonNull List<GalleryDirectory> galleryDirectories) {
        this.weakReference = new WeakReference<>(context);
        this.galleryDirectories = galleryDirectories;
    }

    @NonNull
    @Override
    public GalleryFolderViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        CardView v = (CardView) LayoutInflater.from(parent.getContext()).inflate(R.layout.adapter_gallery_folder, parent, false);
        return new GalleryFolderViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull GalleryFolderViewHolder holder, int position) {
        Context context = weakReference.get();
        GalleryDirectory dir = galleryDirectories.get(position);
        GalleryFile firstFile = dir.getFirstFile();
        Log.e(TAG, "onBindViewHolder: " + position + " " + firstFile);
        if (firstFile != null) {
            Glide.with(context)
                    .load(firstFile.getThumbUri())
                    .into(holder.imageView);
            Log.e(TAG, "onBindViewHolder: glide load " + firstFile.getThumbUri());
        }
        holder.txtName.setText(context.getString(R.string.gallery_adapter_folder_name, dir.getDirectoryName(), dir.getFileCount()));
        holder.imageView.setOnClickListener(v -> {

        });
    }

    @Override
    public int getItemCount() {
        return galleryDirectories.size();
    }

}
