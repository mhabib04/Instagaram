package com.example.instagaram.adapters;

import android.annotation.SuppressLint;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.instagaram.databinding.ListPostProfileBinding;
import com.example.instagaram.listeners.PostsProfileListener;
import com.example.instagaram.models.Posts;

import java.util.List;

public class PostsProfileAdapter extends RecyclerView.Adapter<PostsProfileAdapter.PostsProfileViewHolder> {

    private final List<Posts> posts;
    private final PostsProfileListener postsProfileListener;

    public PostsProfileAdapter(List<Posts> posts, PostsProfileListener postsProfileListener) {
        this.posts = posts;
        this.postsProfileListener = postsProfileListener;
    }

    @NonNull
    @Override
    public PostsProfileViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ListPostProfileBinding binding = ListPostProfileBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new PostsProfileViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull PostsProfileViewHolder holder, int position) {
        holder.setPostData(posts.get(position));
    }

    @Override
    public int getItemCount() {
        return posts.size();
    }

    class PostsProfileViewHolder extends RecyclerView.ViewHolder {

        ListPostProfileBinding binding;

        PostsProfileViewHolder(ListPostProfileBinding listPostProfileBinding) {
            super(listPostProfileBinding.getRoot());
            binding = listPostProfileBinding;
        }

        @SuppressLint({"SetTextI18n", "DefaultLocale"})
        void setPostData(Posts posts) {

            Glide.with(this.itemView.getContext()).load(posts.getImagePosts()).into(binding.imgPostProfile);

            binding.getRoot().setOnClickListener(v -> {
                postsProfileListener.onPostClicked(posts.getPostId());
            });

        }
    }

}
