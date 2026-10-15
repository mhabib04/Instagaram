package com.example.instagaram.adapters;

import android.annotation.SuppressLint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.instagaram.R;
import com.example.instagaram.databinding.ListPostHomeBinding;
import com.example.instagaram.listeners.PostsListener;
import com.example.instagaram.models.Likes;
import com.example.instagaram.models.Posts;
import com.example.instagaram.utilities.Constants;
import com.example.instagaram.utilities.PreferenceManager;

import java.util.List;

public class PostsHomeAdapter extends RecyclerView.Adapter<PostsHomeAdapter.PostsHomeViewHolder> {

    private final List<Posts> posts;
    private final PostsListener postsListener;
    private final PreferenceManager preferenceManager;

    public PostsHomeAdapter(List<Posts> posts, PostsListener postsListener, PreferenceManager preferenceManager) {
        this.posts = posts;
        this.postsListener = postsListener;
        this.preferenceManager = preferenceManager;
    }

    @NonNull
    @Override
    public PostsHomeViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ListPostHomeBinding binding = ListPostHomeBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new PostsHomeViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull PostsHomeViewHolder holder, int position) {
        holder.setPostData(posts.get(position));
    }

    @Override
    public int getItemCount() {
        return posts.size();
    }

    class PostsHomeViewHolder extends RecyclerView.ViewHolder {

        ListPostHomeBinding binding;

        PostsHomeViewHolder(ListPostHomeBinding listPostHomeBinding) {
            super(listPostHomeBinding.getRoot());
            binding = listPostHomeBinding;
        }

        private void showLikeState(Posts post) {
            binding.btnLike.setVisibility(post.isLiked() ? View.GONE : View.VISIBLE);
            binding.btnDisLike.setVisibility(post.isLiked() ? View.VISIBLE : View.GONE);
            binding.txtTotalLikes.setText(itemView.getResources().getQuantityString(
                    R.plurals.likes_count, post.getTotalLikes(), post.getTotalLikes()));
        }

        @SuppressLint({"SetTextI18n", "DefaultLocale"})
        void setPostData(Posts posts) {
            binding.txtUsernamePost.setText("@" + posts.getUsername());
            if (posts.getCaption().isEmpty()) {
                binding.txtCaptionPost.setVisibility(View.GONE);
            } else {
                binding.txtCaptionPost.setVisibility(View.VISIBLE);
                binding.txtCaptionPost.setText(posts.getCaption());
            }
            Glide.with(this.itemView.getContext()).load(posts.getImage()).placeholder(R.drawable.avatar_default).error(R.drawable.avatar_default).fallback(R.drawable.avatar_default).into(binding.imgProfilePost);
            Glide.with(this.itemView.getContext()).load(posts.getImagePosts()).into(binding.imgPost);
            binding.txtDatePost.setText(posts.getDateTime());
            binding.txtTotalLikes.setText(itemView.getResources().getQuantityString(
                    R.plurals.likes_count, posts.getTotalLikes(), posts.getTotalLikes()));

            if (posts.isLiked()) {
                binding.btnLike.setVisibility(View.GONE);
                binding.btnDisLike.setVisibility(View.VISIBLE);
            } else {
                binding.btnLike.setVisibility(View.VISIBLE);
                binding.btnDisLike.setVisibility(View.GONE);
            }

            // Update the heart and the count immediately; the listener reverts them if the write fails.
            binding.btnLike.setOnClickListener(v -> {
                Likes likes = new Likes();
                likes.setPostId(posts.getPostId());
                likes.setUserId(preferenceManager.getString(Constants.KEY_USER_ID));
                posts.setLiked(true);
                posts.setTotalLikes(posts.getTotalLikes() + 1);
                showLikeState(posts);
                postsListener.onLikeClicked(likes);
            });

            binding.btnDisLike.setOnClickListener(v -> {
                Likes likes = new Likes();
                likes.setPostId(posts.getPostId());
                likes.setUserId(preferenceManager.getString(Constants.KEY_USER_ID));
                posts.setLiked(false);
                posts.setTotalLikes(Math.max(0, posts.getTotalLikes() - 1));
                showLikeState(posts);
                postsListener.onDislikeClicked(likes);
            });

            binding.imgProfilePost.setOnClickListener(v -> postsListener.onProfileClicked(posts.getUserId()));
            binding.txtUsernamePost.setOnClickListener(v -> postsListener.onProfileClicked(posts.getUserId()));
            binding.txtTotalLikes.setOnClickListener(v -> postsListener.onLikesCountClicked(posts.getPostId()));

            binding.btnShare.setOnClickListener(v -> {
                Posts posts1 = new Posts();
                posts1.setUsername(posts.getUsername());
                posts1.setImagePosts(posts.getImagePosts());
                posts1.setCaption(posts.getCaption());
                posts1.setDateTime(posts.getDateTime());
                postsListener.onShareClicked(binding.imgPost, posts1);
            });

        }
    }

}
