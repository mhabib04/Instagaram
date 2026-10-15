package com.example.instagaram.utilities;

import android.widget.TextView;

import androidx.annotation.NonNull;

import com.example.instagaram.network.NotifyClient;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.AggregateQuerySnapshot;
import com.google.firebase.firestore.AggregateSource;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.Date;
import java.util.HashMap;

/**
 * Follow relationships live in the "follows" collection. The document id is
 * followerId_followingId, so following twice is impossible and unfollowing is one delete.
 */
public final class FollowManager {

    private FollowManager() { }

    private static DocumentReference followDoc(@NonNull String followerId, @NonNull String followingId) {
        return FirebaseFirestore.getInstance()
                .collection(Constants.KEY_COLLECTION_FOLLOWS)
                .document(followerId + "_" + followingId);
    }

    public static Task<DocumentSnapshot> isFollowing(@NonNull String followerId, @NonNull String followingId) {
        return followDoc(followerId, followingId).get();
    }

    public static Task<Void> follow(@NonNull String followerId, @NonNull String followingId) {
        HashMap<String, Object> follow = new HashMap<>();
        follow.put(Constants.KEY_FOLLOWER_ID, followerId);
        follow.put(Constants.KEY_FOLLOWING_ID, followingId);
        follow.put(Constants.KEY_TIMESTAMP, new Date());
        DocumentReference doc = followDoc(followerId, followingId);
        // Push "x started following you"; the server notifies each follow document only once,
        // so unfollowing and following again does not ping the user a second time.
        return doc.set(follow).addOnSuccessListener(unused -> NotifyClient.followAdded(doc.getId()));
    }

    /** Everyone following the given user, for the Activity tab. */
    public static Task<QuerySnapshot> getFollowers(@NonNull String followingId) {
        return FirebaseFirestore.getInstance()
                .collection(Constants.KEY_COLLECTION_FOLLOWS)
                .whereEqualTo(Constants.KEY_FOLLOWING_ID, followingId)
                .get();
    }

    public static Task<Void> unfollow(@NonNull String followerId, @NonNull String followingId) {
        return followDoc(followerId, followingId).delete();
    }

    /** How many accounts follow the given user (server-side count, no documents downloaded). */
    public static Task<AggregateQuerySnapshot> countFollowers(@NonNull String userId) {
        return FirebaseFirestore.getInstance()
                .collection(Constants.KEY_COLLECTION_FOLLOWS)
                .whereEqualTo(Constants.KEY_FOLLOWING_ID, userId)
                .count()
                .get(AggregateSource.SERVER);
    }

    /** How many accounts the given user follows. */
    public static Task<AggregateQuerySnapshot> countFollowing(@NonNull String userId) {
        return FirebaseFirestore.getInstance()
                .collection(Constants.KEY_COLLECTION_FOLLOWS)
                .whereEqualTo(Constants.KEY_FOLLOWER_ID, userId)
                .count()
                .get(AggregateSource.SERVER);
    }

    /** Fills the Follower / Following columns of a profile's stats row. */
    public static void loadCounts(@NonNull String userId, @NonNull TextView followers, @NonNull TextView following) {
        countFollowers(userId).addOnSuccessListener(snapshot -> followers.setText(String.valueOf(snapshot.getCount())));
        countFollowing(userId).addOnSuccessListener(snapshot -> following.setText(String.valueOf(snapshot.getCount())));
    }

    /** Everyone the given user follows. */
    public static Task<QuerySnapshot> getFollowing(@NonNull String followerId) {
        return FirebaseFirestore.getInstance()
                .collection(Constants.KEY_COLLECTION_FOLLOWS)
                .whereEqualTo(Constants.KEY_FOLLOWER_ID, followerId)
                .get();
    }
}
