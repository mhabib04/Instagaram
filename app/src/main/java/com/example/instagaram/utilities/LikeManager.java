package com.example.instagaram.utilities;

import androidx.annotation.NonNull;

import com.example.instagaram.network.NotifyClient;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Likes, with a denormalised {@code likeCount} on each post so screens never count like documents.
 *
 * New likes use the id {@code postId_userId}; likes written by older app versions have random ids,
 * so "is it liked" and "unlike" look them up by postId + userId, which matches both kinds.
 */
public final class LikeManager {

    // Firestore "in" queries accept at most 30 values.
    private static final int MAX_IN_VALUES = 30;

    private LikeManager() { }

    private static FirebaseFirestore db() {
        return FirebaseFirestore.getInstance();
    }

    /** Adds the like and increments likeCount atomically; a second like by the same user is a no-op. */
    public static Task<Void> like(@NonNull String postId, @NonNull String userId) {
        DocumentReference post = db().collection(Constants.KEY_COLLECTION_POSTS).document(postId);
        DocumentReference like = db().collection(Constants.KEY_COLLECTION_LIKES).document(postId + "_" + userId);
        Task<Boolean> created = db().runTransaction(transaction -> {
            if (transaction.get(like).exists()) return false;
            Map<String, Object> data = new HashMap<>();
            data.put(Constants.KEY_POST_ID, postId);
            data.put(Constants.KEY_USER_ID, userId);
            data.put(Constants.KEY_TIMESTAMP, new Date());
            transaction.set(like, data);
            transaction.update(post, Constants.KEY_LIKE_COUNT, FieldValue.increment(1));
            return true;
        });
        // Push to the post owner only when this call actually added the like.
        created.addOnSuccessListener(isNew -> {
            if (Boolean.TRUE.equals(isNew)) NotifyClient.likeAdded(like.getId());
        });
        return created.continueWith(task -> {
            if (!task.isSuccessful()) throw task.getException();
            return null;
        });
    }

    /** Removes every like by this user on the post (old or new id) and decrements likeCount by that many. */
    public static Task<Void> unlike(@NonNull String postId, @NonNull String userId) {
        return db().collection(Constants.KEY_COLLECTION_LIKES)
                .whereEqualTo(Constants.KEY_POST_ID, postId)
                .whereEqualTo(Constants.KEY_USER_ID, userId)
                .get()
                .continueWithTask(task -> {
                    List<DocumentReference> likes = new ArrayList<>();
                    for (DocumentSnapshot document : task.getResult()) likes.add(document.getReference());
                    // The deterministic id may not be in the query result yet (e.g. just written offline).
                    DocumentReference newStyle = db().collection(Constants.KEY_COLLECTION_LIKES).document(postId + "_" + userId);
                    if (!likes.contains(newStyle)) likes.add(newStyle);
                    DocumentReference post = db().collection(Constants.KEY_COLLECTION_POSTS).document(postId);
                    return db().runTransaction(transaction -> {
                        // Re-read inside the transaction so only likes that still exist are counted.
                        List<DocumentReference> existing = new ArrayList<>();
                        for (DocumentReference ref : likes) if (transaction.get(ref).exists()) existing.add(ref);
                        if (existing.isEmpty()) return null;
                        for (DocumentReference ref : existing) transaction.delete(ref);
                        transaction.update(post, Constants.KEY_LIKE_COUNT, FieldValue.increment(-existing.size()));
                        return null;
                    });
                });
    }

    /** Which of these posts the user has liked: one query per 30 posts. */
    public static Task<Set<String>> likedPostIds(@NonNull String userId, @NonNull List<String> postIds) {
        if (postIds.isEmpty()) return Tasks.forResult(new HashSet<>());
        List<Task<QuerySnapshot>> chunks = new ArrayList<>();
        for (int i = 0; i < postIds.size(); i += MAX_IN_VALUES) {
            chunks.add(db().collection(Constants.KEY_COLLECTION_LIKES)
                    .whereEqualTo(Constants.KEY_USER_ID, userId)
                    .whereIn(Constants.KEY_POST_ID, postIds.subList(i, Math.min(i + MAX_IN_VALUES, postIds.size())))
                    .get());
        }
        return Tasks.whenAllSuccess(chunks).continueWith(task -> {
            Set<String> liked = new HashSet<>();
            for (Object result : task.getResult()) {
                for (DocumentSnapshot like : ((QuerySnapshot) result).getDocuments()) {
                    liked.add(like.getString(Constants.KEY_POST_ID));
                }
            }
            return liked;
        });
    }

    /** likeCount of a post document; posts without the field count as 0. */
    public static int likeCount(@NonNull DocumentSnapshot post) {
        Long count = post.getLong(Constants.KEY_LIKE_COUNT);
        return count == null ? 0 : (int) Math.max(0, count);
    }
}
