package se.arctosoft.vault.adapters;

import android.net.Uri;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.RecyclerView;

import com.davemorrissey.labs.subscaleview.ImageSource;

import java.lang.ref.WeakReference;
import java.util.List;

import se.arctosoft.vault.R;
import se.arctosoft.vault.adapters.viewholders.GalleryFullscreenViewHolder;
import se.arctosoft.vault.data.GalleryFile;
import se.arctosoft.vault.encryption.Encryption;
import se.arctosoft.vault.utils.Settings;

public class GalleryFullscreenAdapter extends RecyclerView.Adapter<GalleryFullscreenViewHolder> {
    private static final String TAG = "GalleryFullscreenAdapter";
    private final WeakReference<FragmentActivity> weakReference;
    private final List<GalleryFile> galleryFiles;

    public GalleryFullscreenAdapter(FragmentActivity context, @NonNull List<GalleryFile> galleryFiles) {
        this.weakReference = new WeakReference<>(context);
        this.galleryFiles = galleryFiles;
    }

    @NonNull
    @Override
    public GalleryFullscreenViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.adapter_item_gallery_fullscreen, parent, false);
        return new GalleryFullscreenViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull GalleryFullscreenViewHolder holder, int position) {
        FragmentActivity context = weakReference.get();
        GalleryFile galleryFile = galleryFiles.get(position);
        if (galleryFile.isDirectory()) {
            GalleryFile firstFile = galleryFile.getFirstFile();
            Log.e(TAG, "onBindViewHolder: " + position + " " + firstFile);
            if (firstFile != null) {
                /*Glide.with(context)
                        .load(firstFile.getUri())
                        .apply(GlideStuff.getRequestOptions())
                        .into(holder.imageView);*/
            }
            holder.txtName.setText(context.getString(R.string.gallery_adapter_folder_name, galleryFile.getNameWithPath(), galleryFile.getFileCount()));
        } else {
            /*Glide.with(context)
                    .load(galleryFile.getUri())
                    .apply(GlideStuff.getRequestOptions())
                    .into(holder.imageView);*/

            new Thread(() -> Encryption.decryptToCache(context, galleryFile.getUri(), Settings.getInstance(context).getTempPassword(), new Encryption.IOnUriResult() {
                /*@Override
                public void onBytesResult(byte[] bytes) {
                    try {
                        Bitmap bitmap = Glide.with(context).asBitmap().load(bytes).submit().get();
                        context.runOnUiThread(() -> holder.imageView.setImage(ImageSource.bitmap(bitmap)));
                    } catch (ExecutionException | InterruptedException e) {
                        e.printStackTrace();
                    }
                }*/ // TODO make fragment instead of activity

                @Override
                public void onUriResult(Uri outputUri) {
                    context.runOnUiThread(() -> {
                        holder.imageView.setImage(ImageSource.uri(outputUri));
                    });
                }

                @Override
                public void onError(Exception e) {

                }
            })).start();
            holder.txtName.setText(galleryFile.getName());
        }
    }

    @Override
    public void onViewRecycled(@NonNull GalleryFullscreenViewHolder holder) {
        holder.imageView.recycle();
        super.onViewRecycled(holder);
    }

    @Override
    public int getItemCount() {
        return galleryFiles.size();
    }

}
