package com.example.instagaram.models;

import androidx.annotation.Nullable;

import java.util.Date;

/** One row on the Activity tab: someone liked one of the current user's posts, or followed them. */
public class ActivityItem {

    public enum Type { LIKE, FOLLOW }

    public final Type type;
    /** Who liked or followed. */
    public final String userId;
    /** The liked post; null for a follow. */
    @Nullable public final String postId;
    @Nullable public final String postImage;
    /** When it happened; null for likes written before likes carried a timestamp. */
    @Nullable public final Date at;
    public String username;
    public String userImage;

    private ActivityItem(Type type, String userId, @Nullable String postId, @Nullable String postImage, @Nullable Date at) {
        this.type = type;
        this.userId = userId;
        this.postId = postId;
        this.postImage = postImage;
        this.at = at;
    }

    public static ActivityItem like(String likerId, String postId, @Nullable String postImage, @Nullable Date at) {
        return new ActivityItem(Type.LIKE, likerId, postId, postImage, at);
    }

    public static ActivityItem follow(String followerId, @Nullable Date at) {
        return new ActivityItem(Type.FOLLOW, followerId, null, null, at);
    }
}
