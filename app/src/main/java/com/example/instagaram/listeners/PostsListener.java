package com.example.instagaram.listeners;

import android.widget.ImageView;

import com.example.instagaram.models.Likes;
import com.example.instagaram.models.Posts;

public interface PostsListener {

    void onLikeClicked(Likes likes);

    void onDislikeClicked(Likes likes);

    void onShareClicked(ImageView imagePosts, Posts posts);

    /** The author's photo or username was tapped. */
    void onProfileClicked(String userId);

    /** The "N likes" text (not the heart) was tapped. */
    void onLikesCountClicked(String postId);
}
