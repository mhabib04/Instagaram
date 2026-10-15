package com.example.instagaram;

import android.app.Instrumentation;
import android.os.Bundle;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.instagaram.utilities.Constants;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * READ-ONLY overview of the Firestore data, to decide what to clean up. Writes nothing.
 *   adb shell am instrument -w -e class com.example.instagaram.FirestoreInventory \
 *       com.example.instagaram.test/androidx.test.runner.AndroidJUnitRunner
 */
@RunWith(AndroidJUnit4.class)
public class FirestoreInventory {

    private static void report(String line) {
        Bundle status = new Bundle();
        status.putString(Instrumentation.REPORT_KEY_STREAMRESULT, line + "\n");
        InstrumentationRegistry.getInstrumentation().sendStatus(0, status);
    }

    private static QuerySnapshot all(String collection) throws Exception {
        return Tasks.await(FirebaseFirestore.getInstance().collection(collection).get());
    }

    @Test
    public void inventory() throws Exception {
        QuerySnapshot users = all(Constants.KEY_COLLECTION_USERS);
        QuerySnapshot posts = all(Constants.KEY_COLLECTION_POSTS);
        QuerySnapshot likes = all(Constants.KEY_COLLECTION_LIKES);
        QuerySnapshot follows = all(Constants.KEY_COLLECTION_FOLLOWS);
        QuerySnapshot chats = all(Constants.KEY_COLLECTION_CHAT);
        QuerySnapshot conversations = all(Constants.KEY_COLLECTION_CONVERSATIONS);
        QuerySnapshot notifySent = all("notifySent");

        Set<String> userIds = new HashSet<>();
        Map<String, String> usernames = new HashMap<>();
        int withEmail = 0, withPhoto = 0;
        for (DocumentSnapshot u : users) {
            userIds.add(u.getId());
            usernames.put(u.getId(), u.getString(Constants.KEY_USERNAME));
            if (u.contains("email")) withEmail++;
            String image = u.getString(Constants.KEY_IMAGE);
            if (image != null && !image.isEmpty()) withPhoto++;
        }
        Set<String> postIds = new HashSet<>();
        Map<String, Integer> postsPerUser = new TreeMap<>();
        int orphanPosts = 0;
        for (DocumentSnapshot p : posts) {
            postIds.add(p.getId());
            String owner = p.getString(Constants.KEY_USER_ID);
            String name = owner == null ? "(no userId)" : usernames.getOrDefault(owner, "(deleted user " + owner + ")");
            postsPerUser.merge(name, 1, Integer::sum);
            if (owner == null || !userIds.contains(owner)) orphanPosts++;
        }
        int orphanLikes = 0, oldStyleLikes = 0;
        for (DocumentSnapshot l : likes) {
            if (!postIds.contains(l.getString(Constants.KEY_POST_ID)) || !userIds.contains(l.getString(Constants.KEY_USER_ID))) orphanLikes++;
            if (!l.getId().equals(l.getString(Constants.KEY_POST_ID) + "_" + l.getString(Constants.KEY_USER_ID))) oldStyleLikes++;
        }
        int orphanFollows = 0;
        for (DocumentSnapshot f : follows) {
            if (!userIds.contains(f.getString(Constants.KEY_FOLLOWER_ID)) || !userIds.contains(f.getString(Constants.KEY_FOLLOWING_ID))) orphanFollows++;
        }
        int orphanChats = 0, emptyChats = 0;
        for (DocumentSnapshot c : chats) {
            if (!userIds.contains(c.getString(Constants.KEY_SENDER_ID)) || !userIds.contains(c.getString(Constants.KEY_RECEIVER_ID))) orphanChats++;
            String message = c.getString(Constants.KEY_MESSAGE);
            if (message == null || message.trim().isEmpty()) emptyChats++;
        }
        int orphanConversations = 0;
        for (DocumentSnapshot c : conversations) {
            if (!userIds.contains(c.getString(Constants.KEY_SENDER_ID)) || !userIds.contains(c.getString(Constants.KEY_RECEIVER_ID))) orphanConversations++;
        }

        report("=== USERS: " + users.size() + " (with email field: " + withEmail + ", with photo: " + withPhoto + ")");
        for (DocumentSnapshot u : users) report("   - " + u.getString(Constants.KEY_USERNAME) + "  [" + u.getId() + "]");
        report("=== POSTS: " + posts.size() + " (orphans: " + orphanPosts + ")");
        for (Map.Entry<String, Integer> e : postsPerUser.entrySet()) report("   - " + e.getKey() + ": " + e.getValue());
        report("=== LIKES: " + likes.size() + " (orphans: " + orphanLikes + ", old random-id: " + oldStyleLikes + ")");
        report("=== FOLLOWS: " + follows.size() + " (orphans: " + orphanFollows + ")");
        report("=== CHATS (messages): " + chats.size() + " (orphans: " + orphanChats + ", empty text: " + emptyChats + ")");
        report("=== CONVERSATIONS (DM list rows): " + conversations.size() + " (orphans: " + orphanConversations + ")");
        report("=== notifySent (push dedupe records): " + notifySent.size());
    }
}
