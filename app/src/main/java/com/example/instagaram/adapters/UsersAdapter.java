package com.example.instagaram.adapters;

import android.annotation.SuppressLint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.instagaram.activities.UserAvailabilityChecker;
import com.example.instagaram.databinding.ListUserBinding;
import com.example.instagaram.listeners.UserListener;
import com.example.instagaram.models.User;

import java.util.List;

public class UsersAdapter extends RecyclerView.Adapter<UsersAdapter.UsersViewHolder> {

    private final List<User> users;
    private final UserListener userListener;
    private final UserAvailabilityChecker availabilityChecker;

    public UsersAdapter(List<User> users, UserListener userListener, UserAvailabilityChecker availabilityChecker) {
        this.users = users;
        this.userListener = userListener;
        this.availabilityChecker = availabilityChecker;
    }

    @NonNull
    @Override
    public UsersViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ListUserBinding binding = ListUserBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new UsersViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull UsersViewHolder holder, int position) {
        holder.setUserData(users.get(position));
    }

    @Override
    public int getItemCount() {
        return users.size();
    }

    class UsersViewHolder extends RecyclerView.ViewHolder {

        ListUserBinding binding;

        UsersViewHolder(ListUserBinding listUserBinding) {
            super(listUserBinding.getRoot());
            binding = listUserBinding;
        }

        @SuppressLint("SetTextI18n")
        void setUserData(User user) {
            binding.txtUsernameUser.setText("@" + user.getUsername());
            binding.txtNameUser.setText(user.getName());
            Glide.with(this.itemView.getContext()).load(user.getImage()).placeholder(com.example.instagaram.R.drawable.avatar_default).error(com.example.instagaram.R.drawable.avatar_default).fallback(com.example.instagaram.R.drawable.avatar_default).into(binding.imgProfileUser);

            availabilityChecker.checkUserAvailability(user.getId(), new UserAvailabilityChecker.OnUserAvailabilityListener() {
                @Override
                public void onAvailabilityChecked(boolean isAvailable) {
                    if (isAvailable) {
                        binding.imgOnline.setVisibility(View.VISIBLE);
                    } else {
                        binding.imgOnline.setVisibility(View.GONE);
                    }
                }
            });

            binding.getRoot().setOnClickListener(v -> userListener.onUserClicked(user));
        }
    }

}
