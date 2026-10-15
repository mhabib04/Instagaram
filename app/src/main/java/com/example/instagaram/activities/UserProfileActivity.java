package com.example.instagaram.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.example.instagaram.R;
import com.example.instagaram.adapters.PostsProfileAdapter;
import com.example.instagaram.databinding.ActivityUserProfileBinding;
import com.example.instagaram.listeners.PostsProfileListener;
import com.example.instagaram.models.Posts;
import com.example.instagaram.models.User;
import com.example.instagaram.utilities.Constants;
import com.example.instagaram.utilities.FollowManager;
import com.example.instagaram.utilities.LikeManager;
import com.example.instagaram.utilities.PreferenceManager;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

/** Another account's profile, opened from Search: stats, follow button and post grid. */
public class UserProfileActivity extends BaseActivity implements PostsProfileListener {

    private ActivityUserProfileBinding binding;
    private FirebaseFirestore database;
    private String myId;
    private String profileUserId;
    // Filled once the profile loads; needed to open the chat room from "Message".
    private User profileUser;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityUserProfileBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        database = FirebaseFirestore.getInstance();
        myId = new PreferenceManager(this).getString(Constants.KEY_USER_ID);
        profileUserId = getIntent().getStringExtra(Constants.KEY_PROFILE_USER_ID);
        if (profileUserId == null) {
            finish();
            return;
        }

        binding.btnBack.setOnClickListener(v -> finish());
        binding.btnFollow.setOnClickListener(v -> setFollowing(true));
        binding.btnUnfollow.setOnClickListener(v -> setFollowing(false));
        binding.btnMessage.setOnClickListener(v -> openChat());

        loadUser();
        loadPosts();
        loadFollowState();
        loadFollowCounts();
    }

    private void loadUser() {
        database.collection(Constants.KEY_COLLECTION_USERS).document(profileUserId).get()
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful() || task.getResult() == null || !task.getResult().exists()) {
                        Toast.makeText(this, R.string.error_get_user, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    profileUser = new User();
                    profileUser.setId(profileUserId);
                    profileUser.setUsername(task.getResult().getString(Constants.KEY_USERNAME));
                    profileUser.setName(task.getResult().getString(Constants.KEY_NAME));
                    profileUser.setImage(task.getResult().getString(Constants.KEY_IMAGE));
                    profileUser.setToken(task.getResult().getString(Constants.KEY_FCM_TOKEN));
                    binding.txtUsernameUser.setText("@" + task.getResult().getString(Constants.KEY_USERNAME));
                    binding.txtNameUser.setText(task.getResult().getString(Constants.KEY_NAME));
                    Glide.with(this).load(task.getResult().getString(Constants.KEY_IMAGE))
                            .placeholder(R.drawable.avatar_default).error(R.drawable.avatar_default).fallback(R.drawable.avatar_default)
                            .into(binding.imgProfileUser);
                });
    }

    private void loadPosts() {
        database.collection(Constants.KEY_COLLECTION_POSTS)
                .whereEqualTo(Constants.KEY_USER_ID, profileUserId)
                .get()
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful() || task.getResult() == null) return;
                    List<Posts> posts = new ArrayList<>();
                    int totalLikes = 0;
                    for (QueryDocumentSnapshot document : task.getResult()) {
                        Posts post = new Posts();
                        post.setImagePosts(document.getString(Constants.KEY_POSTS_IMAGE));
                        post.setDateObject(document.getDate(Constants.KEY_TIMESTAMP));
                        post.setPostId(document.getId());
                        posts.add(post);
                        totalLikes += LikeManager.likeCount(document);
                    }
                    binding.stats.totalPosts.setText(String.valueOf(posts.size()));
                    binding.stats.totalLikes.setText(String.valueOf(totalLikes));

                    if (posts.isEmpty()) {
                        binding.txtNoPosts.setVisibility(View.VISIBLE);
                        binding.recyclerView.setVisibility(View.GONE);
                        return;
                    }
                    posts.sort((a, b) -> b.getDateObject().compareTo(a.getDateObject()));
                    binding.recyclerView.setAdapter(new PostsProfileAdapter(posts, this));
                    binding.recyclerView.setVisibility(View.VISIBLE);
                    binding.txtNoPosts.setVisibility(View.GONE);
                });
    }

    private void loadFollowCounts() {
        FollowManager.loadCounts(profileUserId, binding.stats.totalFollowers, binding.stats.totalFollowing);
    }

    private void loadFollowState() {
        if (profileUserId.equals(myId)) {
            // Your own account: nothing to follow.
            binding.btnFollow.setVisibility(View.GONE);
            return;
        }
        FollowManager.isFollowing(myId, profileUserId).addOnCompleteListener(task -> {
            if (task.isSuccessful() && task.getResult() != null) {
                showFollowState(task.getResult().exists());
            } else {
                Toast.makeText(this, R.string.error_connection, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setFollowing(boolean follow) {
        // Keep the button in place but ignore taps until Firestore answers.
        binding.btnFollow.setEnabled(false);
        binding.btnUnfollow.setEnabled(false);
        Task<Void> request = follow
                ? FollowManager.follow(myId, profileUserId)
                : FollowManager.unfollow(myId, profileUserId);
        request.addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                showFollowState(follow);
                loadFollowCounts();
            } else {
                showFollowState(!follow);
                Toast.makeText(this, R.string.error_follow, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showFollowState(boolean following) {
        binding.btnFollow.setEnabled(true);
        binding.btnUnfollow.setEnabled(true);
        binding.btnFollow.setVisibility(following ? View.GONE : View.VISIBLE);
        binding.layoutFollowing.setVisibility(following ? View.VISIBLE : View.GONE);
    }

    private void openChat() {
        if (profileUser == null) return;
        Intent intent = new Intent(this, ChatRoomActivity.class);
        intent.putExtra(Constants.KEY_USER, profileUser);
        startActivity(intent);
    }

    @Override
    public void onPostClicked(String postId) {
        Intent intent = new Intent(this, DetailPostActivity.class);
        intent.putExtra(Constants.KEY_POST_ID, postId);
        startActivity(intent);
    }
}
