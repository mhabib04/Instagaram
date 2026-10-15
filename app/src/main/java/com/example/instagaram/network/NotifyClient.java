package com.example.instagaram.network;

import android.util.Log;

import androidx.annotation.NonNull;

import com.example.instagaram.BuildConfig;

import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Tells the notification server (server/notify) that a chat message, like or follow was just written.
 * The server reads the document itself and pushes it to the recipient. Fire-and-forget: a
 * failure here never affects sending the message or the like.
 */
public final class NotifyClient {

    private static final String TAG = "NotifyClient";
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    private NotifyClient() { }

    public static void chatSent(@NonNull String chatId) {
        post("chat", chatId);
    }

    public static void likeAdded(@NonNull String likeId) {
        post("like", likeId);
    }

    public static void followAdded(@NonNull String followId) {
        post("follow", followId);
    }

    private static void post(String type, String id) {
        if (BuildConfig.NOTIFY_URL.trim().isEmpty()) return; // notify.properties not configured
        EXECUTOR.execute(() -> {
            HttpURLConnection connection = null;
            try {
                byte[] body = new JSONObject().put("type", type).put("id", id).toString()
                        .getBytes(StandardCharsets.UTF_8);
                connection = (HttpURLConnection) new URL(BuildConfig.NOTIFY_URL).openConnection();
                connection.setRequestMethod("POST");
                connection.setDoOutput(true);
                connection.setConnectTimeout(10_000);
                // The server may need a few seconds on a cold start.
                connection.setReadTimeout(20_000);
                connection.setRequestProperty("Content-Type", "application/json");
                try (OutputStream out = connection.getOutputStream()) {
                    out.write(body);
                }
                int code = connection.getResponseCode();
                if (code != 200) Log.w(TAG, "notify " + type + " " + id + " -> HTTP " + code);
            } catch (Exception e) {
                Log.w(TAG, "notify " + type + " " + id + " failed", e);
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }
}
