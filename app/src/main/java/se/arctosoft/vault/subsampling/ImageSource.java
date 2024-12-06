/*
 * Valv-Android
 * Copyright (c) 2024 Arctosoft AB.
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
 * You should have received a copy of the GNU General Public License along with this program.  If not, see https://www.gnu.org/licenses/.
 */

package se.arctosoft.vault.subsampling;

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.net.Uri;

import androidx.annotation.NonNull;

/**
 * Helper class used to set the source and additional attributes from a variety of sources. Supports
 * use of a bitmap, asset, resource, external file or any other URI.
 * <p>
 * When you are using a preview image, you must set the dimensions of the full size image on the
 * ImageSource object for the full size image using the {@link #dimensions(int, int)} method.
 */
@SuppressWarnings({"unused", "WeakerAccess"})
public final class ImageSource {

    static final String FILE_SCHEME = "file:///";
    static final String ASSET_SCHEME = "file:///android_asset/";

    private final Uri uri;
    private final Bitmap bitmap;
    private final Integer resource;
    private final char[] password;
    private final int version;
    private boolean tile;
    private int sWidth;
    private int sHeight;
    private Rect sRegion;
    private boolean cached;

    private ImageSource(@NonNull Uri uri, char[] password, int version) {
        this.bitmap = null;
        this.uri = uri;
        this.resource = null;
        this.tile = true;
        this.password = password;
        this.version = version;
    }

    /**
     * Create an instance from a URI.
     *
     * @param uri      image URI.
     * @param password
     * @param version
     * @return an {@link ImageSource} instance.
     */
    @NonNull
    public static ImageSource uri(@NonNull Uri uri, char[] password, int version) {
        //noinspection ConstantConditions
        if (uri == null) {
            throw new NullPointerException("Uri must not be null");
        }
        return new ImageSource(uri, password, version);
    }

    /**
     * Enable tiling of the image. This does not apply to preview images which are always loaded as a single bitmap.,
     * and tiling cannot be disabled when displaying a region of the source image.
     *
     * @return this instance for chaining.
     */
    @NonNull
    public ImageSource tilingEnabled() {
        return tiling(true);
    }

    /**
     * Disable tiling of the image. This does not apply to preview images which are always loaded as a single bitmap,
     * and tiling cannot be disabled when displaying a region of the source image.
     *
     * @return this instance for chaining.
     */
    @NonNull
    public ImageSource tilingDisabled() {
        return tiling(false);
    }

    /**
     * Enable or disable tiling of the image. This does not apply to preview images which are always loaded as a single bitmap,
     * and tiling cannot be disabled when displaying a region of the source image.
     *
     * @param tile whether tiling should be enabled.
     * @return this instance for chaining.
     */
    @NonNull
    public ImageSource tiling(boolean tile) {
        this.tile = tile;
        return this;
    }

    /**
     * Use a region of the source image. Region must be set independently for the full size image and the preview if
     * you are using one.
     *
     * @param sRegion the region of the source image to be displayed.
     * @return this instance for chaining.
     */
    @NonNull
    public ImageSource region(Rect sRegion) {
        this.sRegion = sRegion;
        setInvariants();
        return this;
    }

    /**
     * Declare the dimensions of the image. This is only required for a full size image, when you are specifying a URI
     * and also a preview image. When displaying a bitmap object, or not using a preview, you do not need to declare
     * the image dimensions. Note if the declared dimensions are found to be incorrect, the view will reset.
     *
     * @param sWidth  width of the source image.
     * @param sHeight height of the source image.
     * @return this instance for chaining.
     */
    @NonNull
    public ImageSource dimensions(int sWidth, int sHeight) {
        if (bitmap == null) {
            this.sWidth = sWidth;
            this.sHeight = sHeight;
        }
        setInvariants();
        return this;
    }

    private void setInvariants() {
        if (this.sRegion != null) {
            this.tile = true;
            this.sWidth = this.sRegion.width();
            this.sHeight = this.sRegion.height();
        }
    }

    public char[] getPassword() {
        return password;
    }

    public int getVersion() {
        return version;
    }

    protected final Uri getUri() {
        return uri;
    }

    protected final Bitmap getBitmap() {
        return bitmap;
    }

    protected final Integer getResource() {
        return resource;
    }

    protected final boolean getTile() {
        return tile;
    }

    protected final int getSWidth() {
        return sWidth;
    }

    protected final int getSHeight() {
        return sHeight;
    }

    protected final Rect getSRegion() {
        return sRegion;
    }

    protected final boolean isCached() {
        return cached;
    }
}
