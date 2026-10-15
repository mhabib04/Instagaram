package com.example.instagaram.activities.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentManager;

import com.example.instagaram.R;
import com.example.instagaram.activities.UserProfileActivity;
import com.example.instagaram.adapters.SearchUsersAdapter;
import com.example.instagaram.databinding.BottomSheetUsersBinding;
import com.example.instagaram.models.User;
import com.example.instagaram.utilities.Constants;
import com.example.instagaram.utilities.DateFormats;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldPath;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Bottom sheet listing accounts: who liked a post, or a user's followers / following.
 * Tapping a row opens that profile.
 */
public class UserListBottomSheet extends BottomSheetDialogFragment {

    private static final String TAG = "UserListBottomSheet";
    private static final String ARG_MODE = "mode";
    private static final String ARG_ID = "id";
    private static final int MODE_LIKES = 0;
    private static final int MODE_FOLLOWERS = 1;
    private static final int MODE_FOLLOWING = 2;
    // Firestore "in" queries accept at most 30 values.
    private static final int MAX_IN_VALUES = 30;

    private BottomSheetUsersBinding binding;
    private final Map<String, Date> likedAt = new HashMap<>();

    /** Everyone who liked the post. */
    public static void openLikes(@NonNull FragmentManager fragmentManager, @NonNull String postId) {
        open(fragmentManager, MODE_LIKES, postId);
    }

    /** Accounts following the user. */
    public static void openFollowers(@NonNull FragmentManager fragmentManager, @NonNull String userId) {
        open(fragmentManager, MODE_FOLLOWERS, userId);
    }

    /** Accounts the user follows. */
    public static void openFollowing(@NonNull FragmentManager fragmentManager, @NonNull String userId) {
        open(fragmentManager, MODE_FOLLOWING, userId);
    }

    private static void open(FragmentManager fragmentManager, int mode, String id) {
        if (fragmentManager.findFragmentByTag(TAG) != null) return;
        UserListBottomSheet sheet = new UserListBottomSheet();
        Bundle args = new Bundle();
        args.putInt(ARG_MODE, mode);
        args.putString(ARG_ID, id);
        sheet.setArguments(args);
        sheet.show(fragmentManager, TAG);
    }

    @Override
    public int getTheme() {
        return R.style.ThemeOverlay_App_BottomSheet;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = BottomSheetUsersBinding.inflate(inflater, container, false);
        int mode = requireArguments().getInt(ARG_MODE);
        String id = requireArguments().getString(ARG_ID);

        FirebaseFirestore database = FirebaseFirestore.getInstance();
        Query query;
        String idField;
        int emptyText;
        if (mode == MODE_FOLLOWERS) {
            binding.txtSheetTitle.setText(R.string.title_followers);
            query = database.collection(Constants.KEY_COLLECTION_FOLLOWS).whereEqualTo(Constants.KEY_FOLLOWING_ID, id);
            idField = Constants.KEY_FOLLOWER_ID;
            emptyText = R.string.empty_followers;
        } else if (mode == MODE_FOLLOWING) {
            binding.txtSheetTitle.setText(R.string.title_following);
            query = database.collection(Constants.KEY_COLLECTION_FOLLOWS).whereEqualTo(Constants.KEY_FOLLOWER_ID, id);
            idField = Constants.KEY_FOLLOWING_ID;
            emptyText = R.string.empty_following;
        } else {
            binding.txtSheetTitle.setText(R.string.title_likes);
            query = database.collection(Constants.KEY_COLLECTION_LIKES).whereEqualTo(Constants.KEY_POST_ID, id);
            idField = Constants.KEY_USER_ID;
            emptyText = R.string.empty_likes;
        }
        loadUserIds(query, idField, emptyText);
        return binding.getRoot();
    }

    /** Reads the account ids out of the like/follow documents, then loads those accounts. */
    private void loadUserIds(Query query, String idField, int emptyText) {
        query.get().addOnCompleteListener(task -> {
            if (binding == null) return;
            if (!task.isSuccessful() || task.getResult() == null) {
                showMessage(R.string.error_connection);
                return;
            }
            Set<String> userIds = new LinkedHashSet<>();
            likedAt.clear();
            for (DocumentSnapshot document : task.getResult()) {
                String userId = document.getString(idField);
                if (userId == null) continue;
                userIds.add(userId);
                Date time = document.getDate(Constants.KEY_TIMESTAMP);
                if (time != null) likedAt.put(userId, time);
            }
            if (userIds.isEmpty()) {
                showMessage(emptyText);
                return;
            }
            loadUsers(new ArrayList<>(userIds));
        });
    }

    private void loadUsers(List<String> userIds) {
        FirebaseFirestore database = FirebaseFirestore.getInstance();
        List<Task<QuerySnapshot>> chunks = new ArrayList<>();
        for (int i = 0; i < userIds.size(); i += MAX_IN_VALUES) {
            chunks.add(database.collection(Constants.KEY_COLLECTION_USERS)
                    .whereIn(FieldPath.documentId(), userIds.subList(i, Math.min(i + MAX_IN_VALUES, userIds.size())))
                    .get());
        }
        Tasks.whenAllSuccess(chunks).addOnCompleteListener(task -> {
            if (binding == null) return;
            if (!task.isSuccessful() || task.getResult() == null) {
                showMessage(R.string.error_connection);
                return;
            }
            List<User> users = new ArrayList<>();
            for (Object result : task.getResult()) {
                for (DocumentSnapshot document : ((QuerySnapshot) result).getDocuments()) {
                    User user = new User();
                    user.setId(document.getId());
                    user.setUsername(document.getString(Constants.KEY_USERNAME));
                    user.setImage(document.getString(Constants.KEY_IMAGE));
                    users.add(user);
                }
            }
            // Likes: newest first, with the like time on the right; old likes without a time go last.
            Map<String, String> trailing = new HashMap<>();
            if (requireArguments().getInt(ARG_MODE) == MODE_LIKES) {
                users.sort((a, b) -> {
                    Date ta = likedAt.get(a.getId()), tb = likedAt.get(b.getId());
                    if (ta == null && tb == null) return 0;
                    if (ta == null) return 1;
                    if (tb == null) return -1;
                    return tb.compareTo(ta);
                });
                for (User user : users) {
                    trailing.put(user.getId(), DateFormats.likeTime(requireContext(), likedAt.get(user.getId())));
                }
            }
            binding.listUsers.setAdapter(new SearchUsersAdapter(users, user -> {
                Intent intent = new Intent(requireContext(), UserProfileActivity.class);
                intent.putExtra(Constants.KEY_PROFILE_USER_ID, user.getId());
                startActivity(intent);
                dismiss();
            }, trailing));
        });
    }

    private void showMessage(int messageRes) {
        binding.listUsers.setVisibility(View.GONE);
        binding.txtEmpty.setText(messageRes);
        binding.txtEmpty.setVisibility(View.VISIBLE);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
