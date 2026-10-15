package com.example.instagaram.network;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

import com.example.instagaram.BuildConfig;
import com.example.instagaram.R;
import com.example.instagaram.utilities.ImageCompressor;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Uploads images to Cloudinary using an unsigned preset and returns secure_url. */
public final class CloudinaryUploader {

    public interface Callback {
        void onSuccess(@NonNull String secureUrl);
        void onError(@NonNull String message);
    }

    private static final String TAG = "CloudinaryUploader";
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private CloudinaryUploader() { }

    /**
     * Shrinks the photo to {@code maxSide} on its longest edge (see ImageCompressor), uploads it
     * and returns the secure URL on the main thread.
     */
    public static void uploadImage(@NonNull Context context, @NonNull Uri imageUri,
                                   @NonNull String folder, int maxSide, @NonNull Callback callback) {
        if (BuildConfig.CLOUDINARY_CLOUD_NAME.trim().isEmpty()
                || BuildConfig.CLOUDINARY_UPLOAD_PRESET.trim().isEmpty()) {
            // Developer hint: fill CLOUDINARY_CLOUD_NAME and CLOUDINARY_UPLOAD_PRESET in cloudinary.properties.
            callback.onError(context.getString(R.string.upload_not_configured));
            return;
        }

        Context appContext = context.getApplicationContext();
        EXECUTOR.execute(() -> {
            File compressed = null;
            try {
                compressed = ImageCompressor.compressForUpload(appContext, imageUri, maxSide);
                Uri toUpload = compressed != null ? Uri.fromFile(compressed) : imageUri;
                Log.d(TAG, "Upload size: original=" + ImageCompressor.getSize(appContext, imageUri)
                        + " bytes, sent=" + (compressed != null ? compressed.length() + " bytes" : "original"));
                String url = upload(appContext.getContentResolver(), toUpload, folder);
                MAIN.post(() -> callback.onSuccess(url));
            } catch (Exception exception) {
                Log.e(TAG, "Upload failed", exception);
                String message = appContext.getString(R.string.upload_failed);
                MAIN.post(() -> callback.onError(message));
            } finally {
                if (compressed != null && !compressed.delete()) compressed.deleteOnExit();
            }
        });
    }

    @NonNull
    private static String upload(ContentResolver resolver, Uri uri, String folder) throws Exception {
        String boundary = "----Instagaram" + UUID.randomUUID();
        URL endpoint = new URL("https://api.cloudinary.com/v1_1/"
                + BuildConfig.CLOUDINARY_CLOUD_NAME + "/image/upload");
        HttpURLConnection connection = (HttpURLConnection) endpoint.openConnection();
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setConnectTimeout(20_000);
        connection.setReadTimeout(30_000);
        connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

        try (DataOutputStream output = new DataOutputStream(connection.getOutputStream());
             InputStream input = resolver.openInputStream(uri)) {
            if (input == null) throw new IOException("Gambar tidak dapat dibaca");
            writeField(output, boundary, "upload_preset", BuildConfig.CLOUDINARY_UPLOAD_PRESET);
            writeField(output, boundary, "folder", folder);
            output.writeBytes("--" + boundary + "\r\n");
            output.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"upload.jpg\"\r\n");
            output.writeBytes("Content-Type: " + mimeType(resolver, uri) + "\r\n\r\n");
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            output.writeBytes("\r\n--" + boundary + "--\r\n");
            output.flush();
        }

        int responseCode = connection.getResponseCode();
        InputStream response = responseCode >= 200 && responseCode < 300
                ? connection.getInputStream() : connection.getErrorStream();
        String body = read(response);
        connection.disconnect();
        JSONObject json = new JSONObject(body);
        if (responseCode < 200 || responseCode >= 300) {
            String error = json.optJSONObject("error") != null
                    ? json.optJSONObject("error").optString("message") : "Upload Cloudinary gagal";
            throw new IOException(error);
        }
        String secureUrl = json.optString("secure_url");
        if (secureUrl.isEmpty()) throw new IOException("Cloudinary tidak mengembalikan URL gambar");
        return secureUrl;
    }

    private static void writeField(DataOutputStream output, String boundary, String name, String value) throws IOException {
        output.writeBytes("--" + boundary + "\r\n");
        output.writeBytes("Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n");
        output.write(value.getBytes(StandardCharsets.UTF_8));
        output.writeBytes("\r\n");
    }

    private static String mimeType(ContentResolver resolver, Uri uri) {
        String type = resolver.getType(uri);
        return type == null ? "image/jpeg" : type;
    }

    private static String read(InputStream input) throws IOException {
        if (input == null) return "";
        StringBuilder result = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) result.append(line);
        }
        return result.toString();
    }
}
