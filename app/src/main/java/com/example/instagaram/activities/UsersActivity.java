package com.example.instagaram.activities;

import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.View;

import androidx.core.graphics.drawable.DrawableCompat;

import com.example.instagaram.R;
import com.example.instagaram.adapters.UsersAdapter;
import com.example.instagaram.databinding.ActivityUsersBinding;
import com.example.instagaram.listeners.UserListener;
import com.example.instagaram.models.User;
import com.example.instagaram.utilities.Constants;
import com.example.instagaram.utilities.FollowManager;
import com.example.instagaram.utilities.PreferenceManager;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class UsersActivity extends BaseActivity implements UserListener {

    private ActivityUsersBinding binding;
    private PreferenceManager preferenceManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityUsersBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        preferenceManager = new PreferenceManager(this);

        getUsers();

        setSupportActionBar(binding.toolbarUsers);
        getSupportActionBar().setDisplayShowHomeEnabled(true);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowTitleEnabled(false);

        Drawable upArrow = binding.toolbarUsers.getNavigationIcon();
        if (upArrow != null) {
            // Mengubah warna drawable
            upArrow = DrawableCompat.wrap(upArrow);
            DrawableCompat.setTint(upArrow, getResources().getColor(R.color.black));
            getSupportActionBar().setHomeAsUpIndicator(upArrow);
        }
    }

    /** "Suggested" accounts for a new message are the accounts the user follows. */
    private void getUsers() {
        FollowManager.getFollowing(preferenceManager.getString(Constants.KEY_USER_ID))
                .addOnCompleteListener(followTask -> {
                    if (!followTask.isSuccessful() || followTask.getResult() == null) {
                        binding.txtNoUser.setText(R.string.error_connection);
                        binding.txtNoUser.setVisibility(View.VISIBLE);
                        return;
                    }
                    Set<String> followingIds = new HashSet<>();
                    for (QueryDocumentSnapshot follow : followTask.getResult()) {
                        followingIds.add(follow.getString(Constants.KEY_FOLLOWING_ID));
                    }
                    if (followingIds.isEmpty()) {
                        showErrorMessage();
                        return;
                    }
                    getFollowedUsers(followingIds);
                });
    }

    private void getFollowedUsers(Set<String> followingIds) {
        FirebaseFirestore database = FirebaseFirestore.getInstance();
        database.collection(Constants.KEY_COLLECTION_USERS)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null) {
                        List<User> users = new ArrayList<>();
                        for (QueryDocumentSnapshot queryDocumentSnapshot : task.getResult()) {
                            if (!followingIds.contains(queryDocumentSnapshot.getId())) {
                                continue;
                            }
                            User user = new User();
                            user.setName(queryDocumentSnapshot.getString(Constants.KEY_NAME));
                            user.setUsername(queryDocumentSnapshot.getString(Constants.KEY_USERNAME));
                            user.setImage(queryDocumentSnapshot.getString(Constants.KEY_IMAGE));
                            user.setToken(queryDocumentSnapshot.getString(Constants.KEY_FCM_TOKEN));
                            user.setId(queryDocumentSnapshot.getId());
                            users.add(user);
                        }
                        if (users.size() > 0) {
                            UsersAdapter adapter = new UsersAdapter(users, this, new UserAvailabilityChecker());
                            binding.listUsers.setAdapter(adapter);
                            binding.listUsers.setVisibility(View.VISIBLE);
                        } else {
                            showErrorMessage();
                        }
                    }
                });
    }

    private void showErrorMessage() {
        binding.txtNoUser.setText(R.string.empty_following_users);
        binding.txtNoUser.setVisibility(View.VISIBLE);
    }


    @Override
    public void onBackPressed() {
        Intent intent = new Intent(this, DirectMessageActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        super.onBackPressed();
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    @Override
    public void onUserClicked(User user) {
        Intent intent = new Intent(this, ChatRoomActivity.class);
        intent.putExtra(Constants.KEY_USER, user);
        startActivity(intent);
        finish();
    }
}