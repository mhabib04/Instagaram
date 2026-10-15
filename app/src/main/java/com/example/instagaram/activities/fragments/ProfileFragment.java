package com.example.instagaram.activities.fragments;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;

import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.example.instagaram.R;
import com.example.instagaram.activities.DetailPostActivity;
import com.example.instagaram.activities.EditProfileActivity;
import com.example.instagaram.activities.LoginActivity;
import com.example.instagaram.adapters.PostsProfileAdapter;
import com.example.instagaram.databinding.BottomSheetPhotoOptionsBinding;
import com.example.instagaram.databinding.FragmentProfileBinding;
import com.example.instagaram.listeners.PostsProfileListener;
import com.example.instagaram.models.Posts;
import com.example.instagaram.network.CloudinaryUploader;
import com.example.instagaram.utilities.ConfirmDialog;
import com.example.instagaram.utilities.Constants;
import com.example.instagaram.utilities.ImageCompressor;
import com.example.instagaram.utilities.FollowManager;
import com.example.instagaram.utilities.LikeManager;
import com.example.instagaram.utilities.PreferenceManager;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;


public class ProfileFragment extends Fragment implements PostsProfileListener {

    private FragmentProfileBinding binding;
    private PreferenceManager preferenceManager;
    private PostsProfileAdapter adapter;
    private FirebaseFirestore database;
    private ActivityResultLauncher<PickVisualMediaRequest> pickProfileImage;

    public ProfileFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Register while the Fragment is being created, before any view can launch it.
        pickProfileImage = registerForActivityResult(
                new ActivityResultContracts.PickVisualMedia(), uri -> {
                    if (uri != null) {
                        updateProfilePhoto(uri);
                    }
                });
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentProfileBinding.inflate(inflater, container, false);
        View view = binding.getRoot();

        database = FirebaseFirestore.getInstance();
        preferenceManager = new PreferenceManager(requireContext());

        getPosts();
        getUserPostStats();

        loadUserData();

        // Images only: the system photo picker hides videos and other files.
        binding.imgProfileUser.setOnClickListener(v -> showPhotoOptions());

        // Follower / Following lists are only browsable on your own profile.
        String myId = preferenceManager.getString(Constants.KEY_USER_ID);
        makeStatClickable(binding.stats.colFollowers,
                v -> UserListBottomSheet.openFollowers(getChildFragmentManager(), myId));
        makeStatClickable(binding.stats.colFollowing,
                v -> UserListBottomSheet.openFollowing(getChildFragmentManager(), myId));

        binding.btnEditProfile.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), EditProfileActivity.class)));

        binding.btnLogout.setOnClickListener(v -> ConfirmDialog.show(requireContext(), R.drawable.ic_logout,
                R.string.dialog_logout_title, R.string.dialog_logout_message, R.string.action_logout,
                this::logout));
        return view;
    }

    private void makeStatClickable(View column, View.OnClickListener listener) {
        TypedValue ripple = new TypedValue();
        requireContext().getTheme().resolveAttribute(android.R.attr.selectableItemBackground, ripple, true);
        column.setBackgroundResource(ripple.resourceId);
        column.setOnClickListener(listener);
    }

    private void showToast(String message) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
    }

    @SuppressLint("SetTextI18n")
    private void loadUserData(){
        binding.txtNameUser.setText(preferenceManager.getString(Constants.KEY_NAME));
        binding.txtUsernameUser.setText("@" + preferenceManager.getString(Constants.KEY_USERNAME));
        Glide.with(requireContext()).load(preferenceManager.getString(Constants.KEY_IMAGE)).placeholder(R.drawable.avatar_default).error(R.drawable.avatar_default).fallback(R.drawable.avatar_default).into(binding.imgProfileUser);
    }

    private void getPosts() {
        database.collection(Constants.KEY_COLLECTION_POSTS)
                .whereEqualTo(Constants.KEY_USER_ID, preferenceManager.getString(Constants.KEY_USER_ID))
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null) {
                        List<Posts> posts = new ArrayList<>();
                        for (QueryDocumentSnapshot queryDocumentSnapshot : task.getResult()) {
                            Posts postsData = new Posts();
                            postsData.setImagePosts(queryDocumentSnapshot.getString(Constants.KEY_POSTS_IMAGE));
                            postsData.setDateObject(queryDocumentSnapshot.getDate(Constants.KEY_TIMESTAMP));
                            postsData.setPostId(queryDocumentSnapshot.getId());
                            posts.add(postsData);
                        }
                        if (!posts.isEmpty()) {
                            posts.sort((post1, post2) -> post2.getDateObject().compareTo(post1.getDateObject()));
                            adapter = new PostsProfileAdapter(posts, this);
                            binding.recyclerView.setAdapter(adapter);
                            binding.recyclerView.setLayoutManager(new GridLayoutManager(requireContext(), 3));
                            binding.recyclerView.setVisibility(View.VISIBLE);
                        }
                    }
                });
    }

    private void getUserPostStats() {
        String myId = preferenceManager.getString(Constants.KEY_USER_ID);
        FollowManager.loadCounts(myId, binding.stats.totalFollowers, binding.stats.totalFollowing);
        // Total likes = sum of each post's likeCount; no per-post like queries.
        database.collection(Constants.KEY_COLLECTION_POSTS)
                .whereEqualTo(Constants.KEY_USER_ID, myId)
                .get()
                .addOnCompleteListener(task -> {
                    if (binding == null) return;
                    if (!task.isSuccessful() || task.getResult() == null) {
                        showToast(getString(R.string.error_get_user_posts));
                        return;
                    }
                    int totalLikes = 0;
                    for (QueryDocumentSnapshot document : task.getResult()) {
                        totalLikes += LikeManager.likeCount(document);
                    }
                    displayUserStats(task.getResult().size(), totalLikes);
                });
    }

    private void displayUserStats(int postCount, int totalLikes) {
        binding.stats.totalPosts.setText(String.valueOf(postCount));
        binding.stats.totalLikes.setText(String.valueOf(totalLikes));
    }

    private void updateProfilePhoto(@NonNull Uri imageUri) {
        setProfileUpdateLoading(true);
        CloudinaryUploader.uploadImage(requireContext(), imageUri, "instagaram/profiles", ImageCompressor.PROFILE_MAX_SIDE,
                new CloudinaryUploader.Callback() {
                    @Override
                    public void onSuccess(@NonNull String secureUrl) {
                        updateUserPhotoUrl(secureUrl);
                    }

                    @Override
                    public void onError(@NonNull String message) {
                        setProfileUpdateLoading(false);
                        showToast(message);
                    }
                });
    }

    private void updateUserPhotoUrl(@NonNull String secureUrl) {
        database.collection(Constants.KEY_COLLECTION_USERS)
                .document(preferenceManager.getString(Constants.KEY_USER_ID))
                .update(Constants.KEY_IMAGE, secureUrl)
                .addOnSuccessListener(unused -> {
                    preferenceManager.putString(Constants.KEY_IMAGE, secureUrl);
                    Glide.with(this).load(secureUrl).placeholder(R.drawable.avatar_default).error(R.drawable.avatar_default).fallback(R.drawable.avatar_default).into(binding.imgProfileUser);
                    propagateProfilePhoto(secureUrl);
                    setProfileUpdateLoading(false);
                })
                .addOnFailureListener(error -> {
                    setProfileUpdateLoading(false);
                    showToast(getString(R.string.profile_photo_update_failed));
                });
    }

    /** "Change photo" / "Delete photo" sheet; delete only when there is a photo to delete. */
    private void showPhotoOptions() {
        BottomSheetDialog sheet = new BottomSheetDialog(requireContext(), R.style.ThemeOverlay_App_BottomSheet);
        BottomSheetPhotoOptionsBinding options = BottomSheetPhotoOptionsBinding.inflate(getLayoutInflater());
        sheet.setContentView(options.getRoot());
        String currentPhoto = preferenceManager.getString(Constants.KEY_IMAGE);
        options.optDeletePhoto.setVisibility(currentPhoto == null || currentPhoto.isEmpty() ? View.GONE : View.VISIBLE);
        options.optChangePhoto.setOnClickListener(v -> {
            sheet.dismiss();
            pickProfileImage.launch(new PickVisualMediaRequest.Builder()
                    .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                    .build());
        });
        options.optDeletePhoto.setOnClickListener(v -> {
            sheet.dismiss();
            ConfirmDialog.show(requireContext(), R.drawable.ic_delete, R.string.dialog_delete_photo_title,
                    R.string.dialog_delete_photo_message, R.string.action_delete, this::removeProfilePhoto);
        });
        sheet.show();
    }

    /** Back to the default avatar: clear the photo on the account, its posts and its conversations. */
    private void removeProfilePhoto() {
        setProfileUpdateLoading(true);
        database.collection(Constants.KEY_COLLECTION_USERS)
                .document(preferenceManager.getString(Constants.KEY_USER_ID))
                .update(Constants.KEY_IMAGE, FieldValue.delete())
                .addOnSuccessListener(unused -> {
                    preferenceManager.putString(Constants.KEY_IMAGE, null);
                    if (binding != null) {
                        Glide.with(this).load((String) null).fallback(R.drawable.avatar_default).into(binding.imgProfileUser);
                    }
                    propagateProfilePhoto(null);
                    setProfileUpdateLoading(false);
                    showToast(getString(R.string.profile_photo_deleted));
                })
                .addOnFailureListener(error -> {
                    setProfileUpdateLoading(false);
                    showToast(getString(R.string.profile_photo_update_failed));
                });
    }

    /**
     * Posts and conversations keep a copy of the author's photo; update (or clear, when url is
     * null) every copy so feeds and the DM list show the current one.
     */
    private void propagateProfilePhoto(@Nullable String url) {
        String myId = preferenceManager.getString(Constants.KEY_USER_ID);
        Object value = url == null ? FieldValue.delete() : url;
        database.collection(Constants.KEY_COLLECTION_POSTS).whereEqualTo(Constants.KEY_USER_ID, myId).get()
                .addOnSuccessListener(posts -> {
                    for (QueryDocumentSnapshot post : posts) post.getReference().update(Constants.KEY_IMAGE, value);
                });
        database.collection(Constants.KEY_COLLECTION_CONVERSATIONS).whereEqualTo(Constants.KEY_SENDER_ID, myId).get()
                .addOnSuccessListener(chats -> {
                    for (QueryDocumentSnapshot chat : chats) chat.getReference().update(Constants.KEY_SENDER_IMAGE, value);
                });
        database.collection(Constants.KEY_COLLECTION_CONVERSATIONS).whereEqualTo(Constants.KEY_RECEIVER_ID, myId).get()
                .addOnSuccessListener(chats -> {
                    for (QueryDocumentSnapshot chat : chats) chat.getReference().update(Constants.KEY_RECEIVER_IMAGE, value);
                });
    }

    private void setProfileUpdateLoading(boolean isLoading) {
        binding.imgProfileUser.setEnabled(!isLoading);
        binding.imgProfileUser.setAlpha(isLoading ? 0.55f : 1f);
    }

    private void logout() {
        FirebaseFirestore database = FirebaseFirestore.getInstance();
        DocumentReference documentReference =
                database.collection(Constants.KEY_COLLECTION_USERS)
                        .document(preferenceManager.getString(Constants.KEY_USER_ID));
        HashMap<String, Object> updates = new HashMap<>();
        updates.put(Constants.KEY_FCM_TOKEN, FieldValue.delete());
        documentReference.update(updates)
                .addOnSuccessListener(unused -> {
                    moveToLogin();
                })
                .addOnFailureListener(e -> showToast(getString(R.string.error_logout)));
    }

    private void moveToLogin() {
        Intent intent = new Intent(requireContext(), LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        getActivity().finish();
        preferenceManager.clear();
    }


    @Override
    public void onPostClicked(String postId) {
        Intent intent = new Intent(getContext(), DetailPostActivity.class);
        intent.putExtra(Constants.KEY_POST_ID, postId);
        startActivity(intent);
    }

    @Override
    public void onResume() {
        super.onResume();
        // Picks up name/username changes made in EditProfileActivity.
        loadUserData();
        getPosts();
        getUserPostStats();
    }
}
