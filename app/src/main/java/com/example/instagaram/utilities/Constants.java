package com.example.instagaram.utilities;

public class Constants {

    public static final String KEY_COLLECTION_USERS = "users";
    public static final String KEY_NAME = "name";
    public static final String KEY_USERNAME = "username";
    public static final String KEY_PASSWORD = "password";
    public static final String KEY_PREFERENCE_NAME = "instagaramAppPreference";
    public static final String KEY_IS_LOGGED_IN = "isLoggedIn";
    public static final String KEY_USER_ID = "userId";
    public static final String KEY_IMAGE = "image";
    public static final String KEY_FCM_TOKEN = "fcmToken";
    public static final String KEY_USER = "user";
    public static final String KEY_COLLECTION_CHAT = "chats";
    public static final String KEY_SENDER_ID = "senderId";
    public static final String KEY_RECEIVER_ID = "receiverId";
    public static final String KEY_MESSAGE = "message";
    public static final String KEY_TIMESTAMP = "timestamp";
    public static final String KEY_COLLECTION_CONVERSATIONS = "conversations";
    public static final String KEY_SENDER_USERNAME = "senderUsername";
    public static final String KEY_RECEIVER_USERNAME = "receiverUsername";
    public static final String KEY_SENDER_IMAGE = "senderImage";
    public static final String KEY_RECEIVER_IMAGE = "receiverImage";
    public static final String KEY_LAST_MESSAGE = "lastMessage";
    public static final String KEY_AVAILABILITY = "availability";
    public static final String KEY_COLLECTION_POSTS = "posts";
    public static final String KEY_POST_ID = "postId";
    public static final String KEY_POSTS_CAPTION = "caption";
    public static final String KEY_POSTS_IMAGE = "imagePosts";
    public static final String KEY_COLLECTION_LIKES = "likes";
    // Denormalised number of likes on a post document (maintained by LikeManager)
    public static final String KEY_LIKE_COUNT = "likeCount";
    // One document per follow, id = followerId + "_" + followingId (see FollowManager)
    public static final String KEY_COLLECTION_FOLLOWS = "follows";
    public static final String KEY_FOLLOWER_ID = "followerId";
    public static final String KEY_FOLLOWING_ID = "followingId";
    public static final String KEY_PROFILE_USER_ID = "profileUserId";

}
