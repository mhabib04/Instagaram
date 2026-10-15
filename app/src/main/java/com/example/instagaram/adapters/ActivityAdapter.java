package com.example.instagaram.adapters;

import android.graphics.Typeface;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.instagaram.R;
import com.example.instagaram.databinding.ListActivityBinding;
import com.example.instagaram.models.ActivityItem;
import com.example.instagaram.utilities.DateFormats;

import java.util.List;

/**
 * Rows of the Activity tab. Like: photo opens the liker's profile, text and thumbnail open the post.
 * Follow: no thumbnail, photo and text open the follower's profile.
 */
public class ActivityAdapter extends RecyclerView.Adapter<ActivityAdapter.ActivityViewHolder> {

    public interface Listener {
        void onUserClicked(String userId);

        void onPostClicked(String postId);
    }

    private final List<ActivityItem> items;
    private final Listener listener;

    public ActivityAdapter(List<ActivityItem> items, Listener listener) {
        this.items = items;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ActivityViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ActivityViewHolder(ListActivityBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ActivityViewHolder holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    class ActivityViewHolder extends RecyclerView.ViewHolder {

        private final ListActivityBinding binding;

        ActivityViewHolder(ListActivityBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(ActivityItem item) {
            boolean isFollow = item.type == ActivityItem.Type.FOLLOW;
            String username = item.username == null ? "" : item.username;
            String sentence = itemView.getContext().getString(
                    isFollow ? R.string.activity_started_following : R.string.activity_liked_post, username);
            String time = DateFormats.likeTime(itemView.getContext(), item.at);
            String text = time.isEmpty() ? sentence : sentence + "  " + time;
            SpannableString styled = new SpannableString(text);
            // Bold the username, wherever the translation places it.
            int start = sentence.indexOf(username);
            if (!username.isEmpty() && start >= 0) {
                styled.setSpan(new StyleSpan(Typeface.BOLD), start, start + username.length(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            // The like time trails the sentence, smaller and muted.
            if (!time.isEmpty()) {
                int timeStart = text.length() - time.length();
                styled.setSpan(new ForegroundColorSpan(ContextCompat.getColor(itemView.getContext(), R.color.text_muted)),
                        timeStart, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                styled.setSpan(new RelativeSizeSpan(0.85f), timeStart, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            binding.txtActivity.setText(styled);

            Glide.with(itemView.getContext()).load(item.userImage)
                    .placeholder(R.drawable.avatar_default).error(R.drawable.avatar_default).fallback(R.drawable.avatar_default)
                    .into(binding.imgLiker);
            binding.imgLiker.setOnClickListener(v -> listener.onUserClicked(item.userId));

            if (isFollow) {
                // A follow has no post: the whole row opens the follower's profile.
                Glide.with(itemView.getContext()).clear(binding.imgActivityPost);
                binding.imgActivityPost.setVisibility(View.GONE);
                binding.txtActivity.setOnClickListener(v -> listener.onUserClicked(item.userId));
            } else {
                binding.imgActivityPost.setVisibility(View.VISIBLE);
                Glide.with(itemView.getContext()).load(item.postImage).into(binding.imgActivityPost);
                binding.txtActivity.setOnClickListener(v -> listener.onPostClicked(item.postId));
                binding.imgActivityPost.setOnClickListener(v -> listener.onPostClicked(item.postId));
            }
        }
    }
}
