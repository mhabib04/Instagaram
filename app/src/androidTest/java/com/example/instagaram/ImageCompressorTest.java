package com.example.instagaram;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.ExifInterface;
import android.net.Uri;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.instagaram.utilities.ImageCompressor;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Random;

/** Runs ImageCompressor on generated photos on a real device; no network or Firestore. */
@RunWith(AndroidJUnit4.class)
public class ImageCompressorTest {

    private static final String TAG = "ImageCompressorTest";
    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();

    /** Fine random detail compresses poorly, like a sharp high-megapixel phone photo. */
    private File writeNoisyJpeg(int width, int height, int quality) throws IOException {
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Random random = new Random(42);
        int[] row = new int[width];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) row[x] = 0xFF000000 | random.nextInt(0xFFFFFF);
            bitmap.setPixels(row, 0, width, 0, y, width, 1);
        }
        File file = new File(context.getCacheDir(), "test_" + width + "x" + height + ".jpg");
        try (FileOutputStream out = new FileOutputStream(file)) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out);
        }
        bitmap.recycle();
        return file;
    }

    private static int[] dimensions(File file) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getPath(), o);
        return new int[]{o.outWidth, o.outHeight};
    }

    @Test
    public void largePhoto_isShrunkForPosts() throws IOException {
        File original = writeNoisyJpeg(4000, 3000, 100);
        File result = ImageCompressor.compressForUpload(context, Uri.fromFile(original), ImageCompressor.POST_MAX_SIDE);
        assertNotNull(result);
        int[] size = dimensions(result);
        Log.i(TAG, "post: " + original.length() + " bytes 4000x3000 -> " + result.length()
                + " bytes " + size[0] + "x" + size[1]);
        assertEquals(2560, size[0]);
        assertEquals(1920, size[1]);
        assertTrue(result.length() < original.length());
        original.delete();
        result.delete();
    }

    @Test
    public void largePhoto_isShrunkMoreForProfiles() throws IOException {
        File original = writeNoisyJpeg(4000, 3000, 100);
        File result = ImageCompressor.compressForUpload(context, Uri.fromFile(original), ImageCompressor.PROFILE_MAX_SIDE);
        assertNotNull(result);
        int[] size = dimensions(result);
        Log.i(TAG, "profile: " + original.length() + " bytes -> " + result.length() + " bytes " + size[0] + "x" + size[1]);
        assertEquals(1080, size[0]);
        original.delete();
        result.delete();
    }

    @Test
    public void exifRotation_isApplied() throws IOException {
        File original = writeNoisyJpeg(4000, 3000, 90);
        ExifInterface exif = new ExifInterface(original.getPath());
        exif.setAttribute(ExifInterface.TAG_ORIENTATION, String.valueOf(ExifInterface.ORIENTATION_ROTATE_90));
        exif.saveAttributes();
        File result = ImageCompressor.compressForUpload(context, Uri.fromFile(original), ImageCompressor.POST_MAX_SIDE);
        assertNotNull(result);
        int[] size = dimensions(result);
        // A landscape sensor image tagged "rotate 90" must come out portrait.
        assertEquals(1920, size[0]);
        assertEquals(2560, size[1]);
        original.delete();
        result.delete();
    }

    @Test
    public void smallJpeg_isUploadedUntouched() throws IOException {
        File original = writeNoisyJpeg(400, 300, 85);
        assertTrue(original.length() < 1536L * 1024);
        assertNull(ImageCompressor.compressForUpload(context, Uri.fromFile(original), ImageCompressor.POST_MAX_SIDE));
        original.delete();
    }
}
