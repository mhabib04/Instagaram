package com.example.instagaram.utilities;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.TaskStackBuilder;

import com.bumptech.glide.Glide;
import com.example.instagaram.R;
import com.example.instagaram.activities.ChatRoomActivity;
import com.example.instagaram.activities.DetailPostActivity;
import com.example.instagaram.activities.MainActivity;
import com.example.instagaram.activities.UserProfileActivity;
import com.example.instagaram.models.User;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Builds chat / like / follow notifications from the data sent by server/notify. */
public final class NotificationHelper {

    // A channel's sound is fixed once created, so the chat tone needs its own channel id;
    // the old "chat" channel (default sound) is deleted in ensureChannels.
    public static final String CHANNEL_CHAT = "chat_tone";
    private static final String OLD_CHANNEL_CHAT = "chat";
    public static final String CHANNEL_LIKES = "likes";
    public static final String CHANNEL_FOLLOWERS = "followers";

    private NotificationHelper() { }

    /** Separate channels so users can mute likes or followers but keep chats (Android settings → Notifications). */
    public static void ensureChannels(@NonNull Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        NotificationChannel chat = new NotificationChannel(CHANNEL_CHAT,
                context.getString(R.string.channel_chat_name), NotificationManager.IMPORTANCE_HIGH);
        chat.setDescription(context.getString(R.string.channel_chat_description));
        chat.setSound(chatTone(context), new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_INSTANT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build());
        NotificationChannel likes = new NotificationChannel(CHANNEL_LIKES,
                context.getString(R.string.channel_likes_name), NotificationManager.IMPORTANCE_DEFAULT);
        likes.setDescription(context.getString(R.string.channel_likes_description));
        NotificationChannel followers = new NotificationChannel(CHANNEL_FOLLOWERS,
                context.getString(R.string.channel_followers_name), NotificationManager.IMPORTANCE_DEFAULT);
        followers.setDescription(context.getString(R.string.channel_followers_description));
        manager.deleteNotificationChannel(OLD_CHANNEL_CHAT);
        manager.createNotificationChannel(chat);
        manager.createNotificationChannel(likes);
        manager.createNotificationChannel(followers);
    }

    /** Called on the FCM background thread, so the image downloads below may block briefly. */
    public static void show(@NonNull Context context, @NonNull Map<String, String> data) {
        ensureChannels(context);
        NotificationCompat.Builder builder;
        int id;
        if ("chat".equals(data.get("type"))) {
            String senderId = data.get("senderId");
            // No banner for the chat that is open on screen right now.
            if (senderId == null || senderId.equals(ChatRoomActivity.activeChatUserId)) return;
            User sender = new User();
            sender.setId(senderId);
            sender.setUsername(data.get("senderUsername"));
            sender.setImage(data.get("senderImage"));
            Intent open = new Intent(context, ChatRoomActivity.class).putExtra(Constants.KEY_USER, sender);
            builder = new NotificationCompat.Builder(context, CHANNEL_CHAT)
                    .setSmallIcon(R.drawable.ic_messages)
                    .setContentTitle(sender.getUsername())
                    .setContentText(data.get("message"))
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(data.get("message")))
                    .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    // Android 7 has no channels; there the tone is set on the notification itself.
                    .setSound(chatTone(context))
                    .setLargeIcon(loadBitmap(context, sender.getImage(), true))
                    .setContentIntent(pendingIntent(context, open, senderId.hashCode()));
            // One notification per sender: a newer message replaces the previous one.
            id = ("chat_" + senderId).hashCode();
        } else if ("like".equals(data.get("type"))) {
            String postId = data.get("postId");
            if (postId == null) return;
            Intent open = new Intent(context, DetailPostActivity.class).putExtra(Constants.KEY_POST_ID, postId);
            builder = new NotificationCompat.Builder(context, CHANNEL_LIKES)
                    .setSmallIcon(R.drawable.ic_heart)
                    .setContentTitle(context.getString(R.string.app_name))
                    .setContentText(context.getString(R.string.activity_liked_post, data.get("likerUsername")))
                    .setCategory(NotificationCompat.CATEGORY_SOCIAL)
                    .setLargeIcon(loadBitmap(context, data.get("postImage"), false))
                    .setContentIntent(pendingIntent(context, open, postId.hashCode()));
            // One notification per post: likes on the same post replace each other.
            id = ("like_" + postId).hashCode();
        } else if ("follow".equals(data.get("type"))) {
            String followerId = data.get("followerId");
            if (followerId == null) return;
            Intent open = new Intent(context, UserProfileActivity.class)
                    .putExtra(Constants.KEY_PROFILE_USER_ID, followerId);
            builder = new NotificationCompat.Builder(context, CHANNEL_FOLLOWERS)
                    .setSmallIcon(R.drawable.ic_person)
                    .setContentTitle(context.getString(R.string.app_name))
                    .setContentText(context.getString(R.string.activity_started_following, data.get("followerUsername")))
                    .setCategory(NotificationCompat.CATEGORY_SOCIAL)
                    .setLargeIcon(loadBitmap(context, data.get("followerImage"), true))
                    .setContentIntent(pendingIntent(context, open, followerId.hashCode()));
            id = ("follow_" + followerId).hashCode();
        } else {
            return;
        }
        builder.setAutoCancel(true).setColor(context.getColor(R.color.primary));
        try {
            NotificationManagerCompat.from(context).notify(id, builder.build());
        } catch (SecurityException notificationsNotAllowed) {
            // User declined POST_NOTIFICATIONS; nothing to show.
        }
    }

    /** res/raw/chat_tone: played for new chat messages only; likes and follows keep the system sound. */
    private static Uri chatTone(Context context) {
        return Uri.parse(ContentResolver.SCHEME_ANDROID_RESOURCE + "://" + context.getPackageName() + "/" + R.raw.chat_tone);
    }

    /** Opens the target screen with Home underneath, so Back stays inside the app. */
    private static PendingIntent pendingIntent(Context context, Intent target, int requestCode) {
        return TaskStackBuilder.create(context)
                .addNextIntent(new Intent(context, MainActivity.class))
                .addNextIntent(target)
                .getPendingIntent(requestCode, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    @Nullable
    private static Bitmap loadBitmap(Context context, @Nullable String url, boolean circle) {
        if (url == null || url.isEmpty()) return null;
        try {
            if (circle) {
                return Glide.with(context.getApplicationContext()).asBitmap().load(url).circleCrop()
                        .submit(192, 192).get(5, TimeUnit.SECONDS);
            }
            return Glide.with(context.getApplicationContext()).asBitmap().load(url).centerCrop()
                    .submit(192, 192).get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            return null; // the notification is still useful without the picture
        }
    }
}
