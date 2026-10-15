package com.example.instagaram.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.instagaram.R;
import com.example.instagaram.databinding.ListSearchUserBinding;
import com.example.instagaram.listeners.UserListener;
import com.example.instagaram.models.User;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Search results: profile photo + username only. */
public class SearchUsersAdapter extends RecyclerView.Adapter<SearchUsersAdapter.SearchUserViewHolder> {

    private final List<User> users;
    private final UserListener userListener;
    // Optional right-hand text per user id (e.g. like time); empty for plain lists.
    private final Map<String, String> trailingText;

    public SearchUsersAdapter(List<User> users, UserListener userListener) {
        this(users, userListener, Collections.emptyMap());
    }

    public SearchUsersAdapter(List<User> users, UserListener userListener, Map<String, String> trailingText) {
        this.users = users;
        this.userListener = userListener;
        this.trailingText = trailingText;
    }

    @NonNull
    @Override
    public SearchUserViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ListSearchUserBinding binding = ListSearchUserBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new SearchUserViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull SearchUserViewHolder holder, int position) {
        holder.setUserData(users.get(position));
    }

    @Override
    public int getItemCount() {
        return users.size();
    }

    class SearchUserViewHolder extends RecyclerView.ViewHolder {

        ListSearchUserBinding binding;

        SearchUserViewHolder(ListSearchUserBinding listSearchUserBinding) {
            super(listSearchUserBinding.getRoot());
            binding = listSearchUserBinding;
        }

        void setUserData(User user) {
            binding.txtUsernameUser.setText(user.getUsername());
            String trailing = trailingText.get(user.getId());
            binding.txtTrailing.setVisibility(trailing == null || trailing.isEmpty() ? View.GONE : View.VISIBLE);
            binding.txtTrailing.setText(trailing);
            Glide.with(itemView.getContext()).load(user.getImage())
                    .placeholder(R.drawable.avatar_default).error(R.drawable.avatar_default).fallback(R.drawable.avatar_default)
                    .into(binding.imgProfileUser);
            binding.getRoot().setOnClickListener(v -> userListener.onUserClicked(user));
        }
    }
}
