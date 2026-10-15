package com.example.instagaram.activities.fragments;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.Toast;

import com.example.instagaram.R;

import com.example.instagaram.activities.DirectMessageActivity;
import com.example.instagaram.activities.UserProfileActivity;
import com.example.instagaram.adapters.PostsHomeAdapter;
import com.example.instagaram.databinding.FragmentHomeBinding;
import com.example.instagaram.listeners.PostsListener;
import com.example.instagaram.models.Likes;
import com.example.instagaram.models.Posts;
import com.example.instagaram.utilities.Constants;
import com.example.instagaram.utilities.DateFormats;
import com.example.instagaram.utilities.FeedPager;
import com.example.instagaram.utilities.FollowManager;
import com.example.instagaram.utilities.LikeManager;
import com.example.instagaram.utilities.PreferenceManager;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldPath;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class HomeFragment extends Fragment implements PostsListener {

    private FragmentHomeBinding binding;
    private PreferenceManager preferenceManager;
    private PostsHomeAdapter adapter;
    private FirebaseFirestore database;
    // Survive leaving the tab or opening another screen, so Home comes back where it was.
    private final List<Posts> posts = new ArrayList<>();
    private Parcelable listState;
    private Set<String> loadedFollowing = new HashSet<>();
    private FeedPager pager;
    private boolean loadingPage;
    private static final int PAGE_SIZE = 10;
    private static final String TAG = "HomeFragment";

    public HomeFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        View view = binding.getRoot();

        database = FirebaseFirestore.getInstance();
        preferenceManager = new PreferenceManager(requireContext());

        // Show what we already have immediately; onResume refreshes it in place.
        adapter = new PostsHomeAdapter(posts, this, preferenceManager);
        binding.listPostHome.setAdapter(adapter);
        if (!posts.isEmpty()) {
            showFeed(true);
            restoreScroll();
        }

        // Tapping the logo jumps back to the top and reloads, like Instagram.
        binding.txtInstagaram.setOnClickListener(v -> {
            binding.listPostHome.scrollToPosition(0);
            refreshFeed(true);
        });

        // Infinite scroll: fetch the next page when the user nears the end of what is loaded.
        binding.listPostHome.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
                if (dy > 0 && layoutManager != null
                        && layoutManager.findLastVisibleItemPosition() >= posts.size() - 3) {
                    loadNextPage();
                }
            }
        });

        binding.imgDM.setOnClickListener(v -> {
            Intent intent = new Intent(getActivity(), DirectMessageActivity.class);
            startActivity(intent);
        });

        return view;
    }

    /**
     * The feed only shows posts from followed accounts, newest first, PAGE_SIZE at a time.
     * Coming back to Home keeps the loaded pages (and scroll position) unless the follow list changed.
     */
    private void refreshFeed(boolean forceReload) {
        FollowManager.getFollowing(preferenceManager.getString(Constants.KEY_USER_ID))
                .addOnCompleteListener(followTask -> {
                    if (binding == null) return;
                    if (!followTask.isSuccessful() || followTask.getResult() == null) {
                        Toast.makeText(requireContext(), R.string.error_connection, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    Set<String> followingIds = new HashSet<>();
                    for (QueryDocumentSnapshot follow : followTask.getResult()) {
                        followingIds.add(follow.getString(Constants.KEY_FOLLOWING_ID));
                    }
                    if (!forceReload && followingIds.equals(loadedFollowing) && !posts.isEmpty()) {
                        refreshLoadedPosts();
                    } else {
                        startFeed(followingIds);
                    }
                });
    }

    private void startFeed(Set<String> followingIds) {
        loadedFollowing = followingIds;
        posts.clear();
        adapter.notifyDataSetChanged();
        loadingPage = false;
        if (followingIds.isEmpty()) {
            pager = null;
            showFeed(false);
            return;
        }
        pager = new FeedPager(followingIds, PAGE_SIZE);
        loadNextPage();
    }

    private void loadNextPage() {
        if (pager == null || loadingPage || !pager.hasMore()) return;
        loadingPage = true;
        FeedPager requestedFrom = pager;
        pager.nextPage().addOnCompleteListener(task -> {
            if (binding == null || requestedFrom != pager) return;
            if (!task.isSuccessful() || task.getResult() == null) {
                loadingPage = false;
                // A missing composite index shows up here with a link to create it.
                Log.e(TAG, "Feed page failed", task.getException());
                Toast.makeText(requireContext(), R.string.error_connection, Toast.LENGTH_SHORT).show();
                showFeed(!posts.isEmpty());
                return;
            }
            List<Posts> page = new ArrayList<>();
            List<String> ids = new ArrayList<>();
            for (DocumentSnapshot document : task.getResult()) {
                page.add(toPost(document));
                ids.add(document.getId());
            }
            // One query tells which posts of this page the current user liked.
            LikeManager.likedPostIds(preferenceManager.getString(Constants.KEY_USER_ID), ids)
                    .addOnCompleteListener(likedTask -> {
                        loadingPage = false;
                        if (binding == null || requestedFrom != pager) return;
                        Set<String> liked = likedTask.isSuccessful() ? likedTask.getResult() : new HashSet<>();
                        for (Posts post : page) post.setLiked(liked.contains(post.getPostId()));
                        int start = posts.size();
                        posts.addAll(page);
                        adapter.notifyItemRangeInserted(start, page.size());
                        restoreScroll();
                        showFeed(!posts.isEmpty());
                    });
        });
    }

    private Posts toPost(DocumentSnapshot document) {
        Posts post = new Posts();
        post.setUsername(document.getString(Constants.KEY_USERNAME));
        post.setImage(document.getString(Constants.KEY_IMAGE));
        post.setImagePosts(document.getString(Constants.KEY_POSTS_IMAGE));
        post.setCaption(document.getString(Constants.KEY_POSTS_CAPTION));
        post.setDateTime(DateFormats.postDate(requireContext(), document.getDate(Constants.KEY_TIMESTAMP)));
        post.setDateObject(document.getDate(Constants.KEY_TIMESTAMP));
        post.setPostId(document.getId());
        post.setUserId(document.getString(Constants.KEY_USER_ID));
        post.setTotalLikes(LikeManager.likeCount(document));
        return post;
    }

    /** Re-reads like counts / liked state of the posts already shown, without touching the scroll. */
    private void refreshLoadedPosts() {
        List<String> ids = new ArrayList<>();
        for (Posts post : posts) ids.add(post.getPostId());
        List<Task<QuerySnapshot>> chunks = new ArrayList<>();
        for (int i = 0; i < ids.size(); i += 30) {
            chunks.add(database.collection(Constants.KEY_COLLECTION_POSTS)
                    .whereIn(FieldPath.documentId(), ids.subList(i, Math.min(i + 30, ids.size())))
                    .get());
        }
        Task<Set<String>> likedTask = LikeManager.likedPostIds(preferenceManager.getString(Constants.KEY_USER_ID), ids);
        Tasks.whenAllComplete(Tasks.whenAllSuccess(chunks), likedTask).addOnCompleteListener(done -> {
            if (binding == null || !likedTask.isSuccessful()) return;
            Map<String, Integer> counts = new HashMap<>();
            for (Task<QuerySnapshot> chunk : chunks) {
                if (!chunk.isSuccessful()) return;
                for (DocumentSnapshot document : chunk.getResult()) {
                    counts.put(document.getId(), LikeManager.likeCount(document));
                }
            }
            // Posts that no longer exist (deleted by their author) drop out of the feed.
            posts.removeIf(post -> !counts.containsKey(post.getPostId()));
            for (Posts post : posts) {
                post.setTotalLikes(counts.get(post.getPostId()));
                post.setLiked(likedTask.getResult().contains(post.getPostId()));
            }
            adapter.notifyDataSetChanged();
            showFeed(!posts.isEmpty());
        });
    }

    private void showFeed(boolean hasPosts) {
        binding.listPostHome.setVisibility(hasPosts ? View.VISIBLE : View.GONE);
        binding.txtNoPosts.setVisibility(hasPosts ? View.GONE : View.VISIBLE);
    }

    private void restoreScroll() {
        if (listState != null && binding.listPostHome.getLayoutManager() != null) {
            binding.listPostHome.getLayoutManager().onRestoreInstanceState(listState);
            listState = null;
        }
    }

    // The adapter already updated the heart and count optimistically; undo it if the write fails.
    @Override
    public void onLikeClicked(Likes likes) {
        LikeManager.like(likes.getPostId(), likes.getUserId())
                .addOnFailureListener(e -> revertLike(likes.getPostId(), false, R.string.error_like));
    }

    @Override
    public void onDislikeClicked(Likes likes) {
        LikeManager.unlike(likes.getPostId(), likes.getUserId())
                .addOnFailureListener(e -> revertLike(likes.getPostId(), true, R.string.error_dislike));
    }

    private void revertLike(String postId, boolean liked, int message) {
        for (int i = 0; i < posts.size(); i++) {
            Posts post = posts.get(i);
            if (!post.getPostId().equals(postId)) continue;
            post.setLiked(liked);
            post.setTotalLikes(Math.max(0, post.getTotalLikes() + (liked ? 1 : -1)));
            adapter.notifyItemChanged(i);
        }
        if (getContext() != null) Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onProfileClicked(String userId) {
        if (userId == null) return;
        Intent intent = new Intent(requireContext(), UserProfileActivity.class);
        intent.putExtra(Constants.KEY_PROFILE_USER_ID, userId);
        startActivity(intent);
    }

    @Override
    public void onLikesCountClicked(String postId) {
        UserListBottomSheet.openLikes(getChildFragmentManager(), postId);
    }

    @SuppressLint("QueryPermissionsNeeded")
    @Override
    public void onShareClicked(ImageView imagePosts, Posts posts) {
        Bitmap bitmap = ((BitmapDrawable) imagePosts.getDrawable()).getBitmap();
        String text = getString(R.string.share_text, posts.getCaption(), posts.getUsername(), posts.getDateTime());

        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("image/*");

        Uri uri = getImageUri(requireContext(), bitmap);
        if (uri != null) {
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.putExtra(Intent.EXTRA_TEXT, text);
        } else {
            Toast.makeText(requireContext(), R.string.error_no_image_to_share, Toast.LENGTH_SHORT).show();
        }

        if (intent.resolveActivity(requireContext().getPackageManager()) != null) {
            startActivity(Intent.createChooser(intent, getString(R.string.share_via)));
        } else {
            Toast.makeText(requireContext(), R.string.error_no_share_app, Toast.LENGTH_SHORT).show();
        }
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


    @Override
    public void onResume() {
        super.onResume();
        refreshFeed(false);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (binding.listPostHome.getLayoutManager() != null) {
            listState = binding.listPostHome.getLayoutManager().onSaveInstanceState();
        }
        binding = null;
    }
}
