package com.example.instagaram.utilities;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.provider.OpenableColumns;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

/**
 * Shrinks photos before upload. Phones show images at ~1080px wide, so a 50MP photo
 * re-encoded at 2560px / JPEG 90 looks the same on screen while being a fraction of the size.
 */
public final class ImageCompressor {

    /** Longest side kept for feed posts (room for zooming on high-density screens). */
    public static final int POST_MAX_SIDE = 2560;
    /** Profile photos are only ever shown small. */
    public static final int PROFILE_MAX_SIDE = 1080;
    /** Upper bound for a picked file; anything bigger is rejected before decoding. */
    public static final long MAX_INPUT_BYTES = 50L * 1024 * 1024;

    private static final int JPEG_QUALITY = 90;
    /** Small JPEGs are uploaded untouched to avoid a needless re-encode. */
    private static final long KEEP_ORIGINAL_BYTES = 1536L * 1024;

    private ImageCompressor() { }

    /** Real file size from the content provider, or -1 if unknown. */
    public static long getSize(@NonNull Context context, @NonNull Uri uri) {
        try (Cursor cursor = context.getContentResolver()
                .query(uri, new String[]{OpenableColumns.SIZE}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst() && !cursor.isNull(0)) return cursor.getLong(0);
        } catch (RuntimeException ignored) {
            // Fall through to the file descriptor.
        }
        try (AssetFileDescriptor fd = context.getContentResolver().openAssetFileDescriptor(uri, "r")) {
            if (fd != null) return fd.getLength();
        } catch (IOException | RuntimeException ignored) {
            // Unknown size.
        }
        return -1;
    }

    /**
     * Returns a temporary JPEG no larger than {@code maxSide} on its longest edge, or null when
     * the original is already small enough to upload as-is. The caller deletes the file.
     */
    @Nullable
    public static File compressForUpload(@NonNull Context context, @NonNull Uri uri, int maxSide) throws IOException {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream input = context.getContentResolver().openInputStream(uri)) {
            BitmapFactory.decodeStream(input, null, bounds);
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw new IOException("Not an image: " + uri);

        int longest = Math.max(bounds.outWidth, bounds.outHeight);
        long size = getSize(context, uri);
        boolean isJpeg = "image/jpeg".equals(bounds.outMimeType);
        if (isJpeg && longest <= maxSide && size >= 0 && size <= KEEP_ORIGINAL_BYTES) return null;

        // Decode at the smallest power-of-two step that is still >= maxSide, to bound memory.
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = 1;
        while (longest / (options.inSampleSize * 2) >= maxSide) options.inSampleSize *= 2;
        Bitmap decoded;
        try (InputStream input = context.getContentResolver().openInputStream(uri)) {
            decoded = BitmapFactory.decodeStream(input, null, options);
        }
        if (decoded == null) throw new IOException("Could not decode: " + uri);

        Bitmap result = scaleAndRotate(decoded, maxSide, readRotation(context, uri));
        if (result.hasAlpha()) result = flattenOnWhite(result);

        File out = new File(context.getCacheDir(), "upload_" + UUID.randomUUID() + ".jpg");
        try (OutputStream stream = new FileOutputStream(out)) {
            result.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, stream);
        } finally {
            result.recycle();
        }
        return out;
    }

    /** Camera photos store their orientation in EXIF; decoding ignores it, so apply it here. */
    private static int readRotation(Context context, Uri uri) {
        try (InputStream input = context.getContentResolver().openInputStream(uri)) {
            if (input == null) return 0;
            switch (new ExifInterface(input).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                case ExifInterface.ORIENTATION_ROTATE_90: return 90;
                case ExifInterface.ORIENTATION_ROTATE_180: return 180;
                case ExifInterface.ORIENTATION_ROTATE_270: return 270;
                default: return 0;
            }
        } catch (IOException | RuntimeException e) {
            return 0;
        }
    }

    private static Bitmap scaleAndRotate(Bitmap source, int maxSide, int rotation) {
        float scale = Math.min(1f, (float) maxSide / Math.max(source.getWidth(), source.getHeight()));
        if (scale == 1f && rotation == 0) return source;
        Matrix matrix = new Matrix();
        matrix.postScale(scale, scale);
        matrix.postRotate(rotation);
        Bitmap result = Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), matrix, true);
        if (result != source) source.recycle();
        return result;
    }

    /** JPEG has no transparency; draw transparent PNGs on white instead of black. */
    private static Bitmap flattenOnWhite(Bitmap source) {
        Bitmap flat = Bitmap.createBitmap(source.getWidth(), source.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(flat);
        canvas.drawColor(Color.WHITE);
        canvas.drawBitmap(source, 0, 0, null);
        source.recycle();
        return flat;
    }
}
