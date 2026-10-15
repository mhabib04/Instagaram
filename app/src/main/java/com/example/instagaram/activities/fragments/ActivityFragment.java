package com.example.instagaram.activities.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.instagaram.R;
import com.example.instagaram.activities.DetailPostActivity;
import com.example.instagaram.activities.UserProfileActivity;
import com.example.instagaram.adapters.ActivityAdapter;
import com.example.instagaram.databinding.FragmentActivityBinding;
import com.example.instagaram.models.ActivityItem;
import com.example.instagaram.utilities.Constants;
import com.example.instagaram.utilities.FollowManager;
import com.example.instagaram.utilities.PreferenceManager;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldPath;
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
 * "Aktivitas" tab: who liked the current user's posts and who started following them, newest first.
 * Built from the likes and follows themselves, so an unlike / unfollow removes the row on the next load.
 */
public class ActivityFragment extends Fragment implements ActivityAdapter.Listener {

    // Firestore "in" queries accept at most 30 values.
    private static final int MAX_IN_VALUES = 30;

    private FragmentActivityBinding binding;
    private final List<ActivityItem> items = new ArrayList<>();
    private ActivityAdapter adapter;
    private FirebaseFirestore database;
    private String myId;
    // Ignores results of a load that was superseded by a newer one.
    private int loadGeneration;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentActivityBinding.inflate(inflater, container, false);
        database = FirebaseFirestore.getInstance();
        myId = new PreferenceManager(requireContext()).getString(Constants.KEY_USER_ID);
        adapter = new ActivityAdapter(items, this);
        binding.listActivity.setAdapter(adapter);
        if (!items.isEmpty()) show(true);
        return binding.getRoot();
    }

    @Override
    public void onResume() {
        super.onResume();
        load();
    }

    /** (My posts -> likes on them) + my followers, newest first -> those users' profiles. */
    private void load() {
        int generation = ++loadGeneration;
        Task<List<ActivityItem>> likes = database.collection(Constants.KEY_COLLECTION_POSTS)
                .whereEqualTo(Constants.KEY_USER_ID, myId)
                .get()
                .continueWithTask(postsTask -> {
                    Map<String, String> postImages = new HashMap<>();
                    for (DocumentSnapshot post : postsTask.getResult()) {
                        postImages.put(post.getId(), post.getString(Constants.KEY_POSTS_IMAGE));
                    }
                    return likesOn(postImages);
                });
        Task<List<ActivityItem>> follows = FollowManager.getFollowers(myId).continueWith(task -> {
            List<ActivityItem> result = new ArrayList<>();
            for (DocumentSnapshot follow : task.getResult()) {
                String followerId = follow.getString(Constants.KEY_FOLLOWER_ID);
                if (followerId == null || followerId.equals(myId)) continue;
                result.add(ActivityItem.follow(followerId, follow.getDate(Constants.KEY_TIMESTAMP)));
            }
            return result;
        });
        Tasks.<List<ActivityItem>>whenAllSuccess(likes, follows)
                .continueWithTask(task -> {
                    List<ActivityItem> all = new ArrayList<>();
                    for (List<ActivityItem> part : task.getResult()) all.addAll(part);
                    sortNewestFirst(all);
                    return withProfiles(all);
                })
                .addOnCompleteListener(task -> {
                    if (binding == null || generation != loadGeneration) return;
                    if (!task.isSuccessful()) {
                        Toast.makeText(requireContext(), R.string.error_connection, Toast.LENGTH_SHORT).show();
                        show(!items.isEmpty());
                        return;
                    }
                    items.clear();
                    items.addAll(task.getResult());
                    adapter.notifyDataSetChanged();
                    show(!items.isEmpty());
                });
    }

    /** Likes on the given posts, excluding my own likes. */
    private Task<List<ActivityItem>> likesOn(Map<String, String> postImages) {
        List<String> postIds = new ArrayList<>(postImages.keySet());
        List<Task<QuerySnapshot>> chunks = new ArrayList<>();
        for (int i = 0; i < postIds.size(); i += MAX_IN_VALUES) {
            chunks.add(database.collection(Constants.KEY_COLLECTION_LIKES)
                    .whereIn(Constants.KEY_POST_ID, postIds.subList(i, Math.min(i + MAX_IN_VALUES, postIds.size())))
                    .get());
        }
        return Tasks.whenAllSuccess(chunks).continueWith(task -> {
            List<ActivityItem> result = new ArrayList<>();
            for (Object snapshot : task.getResult()) {
                for (DocumentSnapshot like : ((QuerySnapshot) snapshot).getDocuments()) {
                    String likerId = like.getString(Constants.KEY_USER_ID);
                    String postId = like.getString(Constants.KEY_POST_ID);
                    if (likerId == null || likerId.equals(myId)) continue;
                    result.add(ActivityItem.like(likerId, postId, postImages.get(postId),
                            like.getDate(Constants.KEY_TIMESTAMP)));
                }
            }
            return result;
        });
    }

    /** Newest first; old likes without a timestamp go to the bottom. */
    private static void sortNewestFirst(List<ActivityItem> activity) {
        activity.sort((a, b) -> {
            Date ta = a.at, tb = b.at;
            if (ta == null && tb == null) return 0;
            if (ta == null) return 1;
            if (tb == null) return -1;
            return tb.compareTo(ta);
        });
    }

    /** Fills username + photo of each liker / follower, one query per 30 distinct users. */
    private Task<List<ActivityItem>> withProfiles(List<ActivityItem> activity) {
        Set<String> ids = new HashSet<>();
        for (ActivityItem item : activity) ids.add(item.userId);
        List<String> userIds = new ArrayList<>(ids);
        List<Task<QuerySnapshot>> chunks = new ArrayList<>();
        for (int i = 0; i < userIds.size(); i += MAX_IN_VALUES) {
            chunks.add(database.collection(Constants.KEY_COLLECTION_USERS)
                    .whereIn(FieldPath.documentId(), userIds.subList(i, Math.min(i + MAX_IN_VALUES, userIds.size())))
                    .get());
        }
        return Tasks.whenAllSuccess(chunks).continueWith(task -> {
            Map<String, DocumentSnapshot> users = new HashMap<>();
            for (Object snapshot : task.getResult()) {
                for (DocumentSnapshot user : ((QuerySnapshot) snapshot).getDocuments()) users.put(user.getId(), user);
            }
            List<ActivityItem> result = new ArrayList<>();
            for (ActivityItem item : activity) {
                DocumentSnapshot user = users.get(item.userId);
                if (user == null) continue; // deleted account
                item.username = user.getString(Constants.KEY_USERNAME);
                item.userImage = user.getString(Constants.KEY_IMAGE);
                result.add(item);
            }
            return result;
        });
    }

    private void show(boolean hasItems) {
        binding.listActivity.setVisibility(hasItems ? View.VISIBLE : View.GONE);
        binding.txtNoActivity.setVisibility(hasItems ? View.GONE : View.VISIBLE);
    }

    @Override
    public void onUserClicked(String userId) {
        Intent intent = new Intent(requireContext(), UserProfileActivity.class);
        intent.putExtra(Constants.KEY_PROFILE_USER_ID, userId);
        startActivity(intent);
    }

    @Override
    public void onPostClicked(String postId) {
        Intent intent = new Intent(requireContext(), DetailPostActivity.class);
        intent.putExtra(Constants.KEY_POST_ID, postId);
        startActivity(intent);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
