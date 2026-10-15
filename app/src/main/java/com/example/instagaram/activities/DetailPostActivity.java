package com.example.instagaram.activities;

import static android.content.ContentValues.TAG;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.Toast;

import androidx.core.content.FileProvider;
import androidx.core.graphics.drawable.DrawableCompat;

import com.bumptech.glide.Glide;
import com.example.instagaram.R;
import com.example.instagaram.activities.fragments.UserListBottomSheet;
import com.example.instagaram.databinding.ActivityDetailPostBinding;
import com.example.instagaram.utilities.ConfirmDialog;
import com.example.instagaram.utilities.Constants;
import com.example.instagaram.utilities.DateFormats;
import com.example.instagaram.utilities.LikeManager;
import com.example.instagaram.utilities.PreferenceManager;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.Objects;

public class DetailPostActivity extends BaseActivity {

    private ListenerRegistration likeCountListener;

    private ActivityDetailPostBinding binding;
    private FirebaseFirestore database;
    private PreferenceManager preferenceManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityDetailPostBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        database = FirebaseFirestore.getInstance();
        preferenceManager = new PreferenceManager(this);

        String postId = getIntent().getStringExtra(Constants.KEY_POST_ID);
        getPosts(postId);
        String userId = preferenceManager.getString(Constants.KEY_USER_ID);
        getPostLikes(postId, userId);


        setSupportActionBar(binding.toolbarPost);
        getSupportActionBar().setDisplayShowHomeEnabled(true);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowTitleEnabled(false);

        Drawable upArrow = binding.toolbarPost.getNavigationIcon();
        if (upArrow != null) {
            upArrow = DrawableCompat.wrap(upArrow);
            DrawableCompat.setTint(upArrow, getResources().getColor(R.color.black));
            getSupportActionBar().setHomeAsUpIndicator(upArrow);
        }

        binding.btnOption.setOnClickListener(v -> showPopupMenu());
        binding.btnShare.setOnClickListener(v -> sharePosts(binding.imgPost));
        binding.txtTotalLikes.setOnClickListener(v -> UserListBottomSheet.openLikes(getSupportFragmentManager(), postId));
        binding.btnLike.setOnClickListener(v -> likePost(postId, userId));
        binding.btnDisLike.setOnClickListener(v -> dislikePost(postId, userId));
        listenLikesCount(postId);

    }

    @SuppressLint("SetTextI18n")
    private void getPosts(String postId) {
        database.collection(Constants.KEY_COLLECTION_POSTS)
                .document(postId)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null) {
                        DocumentSnapshot documentSnapshot = task.getResult();
                        binding.txtUsernamePost.setText("@" + documentSnapshot.getString(Constants.KEY_USERNAME));
                        // Only the author may delete: hide the options menu on other people's posts.
                        boolean isOwnPost = Objects.equals(documentSnapshot.getString(Constants.KEY_USER_ID),
                                preferenceManager.getString(Constants.KEY_USER_ID));
                        binding.btnOption.setVisibility(isOwnPost ? View.VISIBLE : View.GONE);
                        if (Objects.equals(documentSnapshot.getString(Constants.KEY_POSTS_CAPTION), "")) {
                            binding.txtCaptionPost.setVisibility(View.GONE);
                        } else {
                            binding.txtCaptionPost.setVisibility(View.VISIBLE);
                            binding.txtCaptionPost.setText(documentSnapshot.getString(Constants.KEY_POSTS_CAPTION));
                        }
                        Glide.with(this).load(documentSnapshot.getString(Constants.KEY_POSTS_IMAGE)).into(binding.imgPost);
                        Glide.with(this).load(documentSnapshot.getString(Constants.KEY_IMAGE)).placeholder(com.example.instagaram.R.drawable.avatar_default).error(com.example.instagaram.R.drawable.avatar_default).fallback(com.example.instagaram.R.drawable.avatar_default).into(binding.imgProfilePost);
                        binding.txtDatePost.setText(DateFormats.postDate(this, documentSnapshot.getDate(Constants.KEY_TIMESTAMP)));
                        showLikes(LikeManager.likeCount(documentSnapshot));
                    } else {
                        Toast.makeText(this, R.string.error_get_posts, Toast.LENGTH_SHORT).show();
                    }
                });
    }

    /** Whether the current user already liked this post (old or new like ids). */
    private void getPostLikes(String postId, String userId) {
        LikeManager.likedPostIds(userId, Collections.singletonList(postId))
                .addOnSuccessListener(liked -> showLiked(liked.contains(postId)));
    }

    private void showLiked(boolean liked) {
        binding.btnLike.setVisibility(liked ? View.GONE : View.VISIBLE);
        binding.btnDisLike.setVisibility(liked ? View.VISIBLE : View.GONE);
    }

    // The heart flips immediately; the count follows from the post's likeCount listener below.
    private void likePost(String postId, String userId) {
        showLiked(true);
        LikeManager.like(postId, userId).addOnFailureListener(e -> {
            showLiked(false);
            Toast.makeText(this, R.string.error_like, Toast.LENGTH_SHORT).show();
        });
    }

    private void dislikePost(String postId, String userId) {
        showLiked(false);
        LikeManager.unlike(postId, userId).addOnFailureListener(e -> {
            showLiked(true);
            Toast.makeText(this, R.string.error_dislike, Toast.LENGTH_SHORT).show();
        });
    }

    /** Live like count: listens to this one post document instead of all of its likes. */
    private void listenLikesCount(String postId) {
        likeCountListener = database.collection(Constants.KEY_COLLECTION_POSTS)
                .document(postId)
                .addSnapshotListener((post, e) -> {
                    if (e != null) {
                        Log.w(TAG, "Listen failed.", e);
                        return;
                    }
                    if (post != null && post.exists()) showLikes(LikeManager.likeCount(post));
                });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (likeCountListener != null) likeCountListener.remove();
    }

    private void showPopupMenu() {
        PopupMenu popupMenu = new PopupMenu(this, binding.btnOption);
        popupMenu.getMenuInflater().inflate(R.menu.menu, popupMenu.getMenu());
        popupMenu.setGravity(Gravity.END);
        popupMenu.show();

        popupMenu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.delete) {
                // Close the menu first so it cannot linger behind the dialog (same as deleting a message).
                popupMenu.dismiss();
                ConfirmDialog.show(this, R.drawable.ic_delete, R.string.dialog_delete_post_title,
                        R.string.dialog_delete_post_message, R.string.action_delete,
                        () -> deletePosts(getIntent().getStringExtra(Constants.KEY_POST_ID)));
                return true;
            }
            return false;
        });
    }

    private void deletePosts(String postId) {
        database.collection(Constants.KEY_COLLECTION_POSTS)
                .document(postId)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null) {
                        DocumentSnapshot documentSnapshot = task.getResult();
                        // Cloudinary's unsigned upload API deliberately cannot delete assets.
                        // Delete the Firestore post and its likes; server-side cleanup can delete
                        // Cloudinary assets later using the Admin API and its secret.
                        database.collection(Constants.KEY_COLLECTION_POSTS)
                                .document(postId)
                                .delete()
                                .addOnSuccessListener(aVoid -> database.collection(Constants.KEY_COLLECTION_LIKES)
                                        .whereEqualTo(Constants.KEY_POST_ID, postId)
                                        .get()
                                        .addOnCompleteListener(likesTask -> {
                                            if (likesTask.isSuccessful() && likesTask.getResult() != null) {
                                                for (DocumentSnapshot document : likesTask.getResult()) {
                                                    database.collection(Constants.KEY_COLLECTION_LIKES).document(document.getId()).delete();
                                                }
                                            }
                                            navigateToProfileFragment();
                                        }))
                                .addOnFailureListener(e -> Toast.makeText(this, R.string.error_delete_post, Toast.LENGTH_SHORT).show());
                    } else {
                        Toast.makeText(this, R.string.error_get_post, Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void navigateToProfileFragment() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra("navigate_to", "ProfileFragment");
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
    }

    @SuppressLint("QueryPermissionsNeeded")
    private void sharePosts(ImageView imagePosts) {
        Bitmap bitmap = ((BitmapDrawable) imagePosts.getDrawable()).getBitmap();
        // txtUsernamePost already shows "@username"; the template adds its own "@".
        String text = getString(R.string.share_text, binding.txtCaptionPost.getText().toString(),
                binding.txtUsernamePost.getText().toString().replaceFirst("^@", ""),
                binding.txtDatePost.getText().toString());

        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("image/*");

        Uri uri = getImageUri(this, bitmap);
        if (uri != null) {
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.putExtra(Intent.EXTRA_TEXT, text);
        } else {
            Toast.makeText(this, R.string.error_no_image_to_share, Toast.LENGTH_SHORT).show();
        }

        if (intent.resolveActivity(getPackageManager()) != null) {
            startActivity(Intent.createChooser(intent, getString(R.string.share_via)));
        } else {
            Toast.makeText(this, R.string.error_no_share_app, Toast.LENGTH_SHORT).show();
        }
    }

    private void showLikes(int count) {
        binding.txtTotalLikes.setText(getResources().getQuantityString(R.plurals.likes_count, count, count));
    }

    private Uri getImageUri(Context context, Bitmap bitmap) {
        Uri uri = null;
        try {
            File cachePath = new File(context.getCacheDir(), "images");
            cachePath.mkdirs();
            File tempFile = new File(cachePath, "temp_image.png");
            FileOutputStream out = new FileOutputStream(tempFile);
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
            out.close();

            uri = FileProvider.getUriForFile(context, "com.example.instagaram.fileprovider", tempFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return uri;
    }

    // Back returns to whichever screen opened this post (own profile, a visited profile, ...).
    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

}
