package com.example.instagaram.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.instagaram.activities.UserAvailabilityChecker;
import com.example.instagaram.databinding.ListRecentConversionBinding;
import com.example.instagaram.listeners.ConversionListener;
import com.example.instagaram.models.ChatMessage;
import com.example.instagaram.models.User;
import com.example.instagaram.utilities.Constants;
import com.example.instagaram.utilities.DateFormats;
import com.example.instagaram.utilities.PreferenceManager;

import java.util.List;
import java.util.Objects;

public class RecentConversationsAdapter extends RecyclerView.Adapter<RecentConversationsAdapter.ConversionViewHolder> {

    private final List<ChatMessage> chatMessages;
    private final ConversionListener conversionListener;
    private final UserAvailabilityChecker availabilityChecker;
    private final PreferenceManager preferenceManager;

    public RecentConversationsAdapter(List<ChatMessage> chatMessages, ConversionListener conversionListener, PreferenceManager preferenceManager) {
        this.chatMessages = chatMessages;
        this.conversionListener = conversionListener;
        this.preferenceManager = preferenceManager;
        this.availabilityChecker = new UserAvailabilityChecker();
    }

    @NonNull
    @Override
    public ConversionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ConversionViewHolder(ListRecentConversionBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ConversionViewHolder holder, int position) {
        holder.setConversionData(chatMessages.get(position));
    }

    @Override
    public int getItemCount() {
        return chatMessages.size();
    }

    class ConversionViewHolder extends RecyclerView.ViewHolder {

        ListRecentConversionBinding binding;

        ConversionViewHolder(ListRecentConversionBinding listRecentConversionBinding) {
            super(listRecentConversionBinding.getRoot());
            binding = listRecentConversionBinding;
        }

        void setConversionData(ChatMessage chatMessage) {
            binding.txtUsernameUser.setText(chatMessage.getConversionUsername());
            binding.txtMessage.setText(chatMessage.getMessage());
            binding.txtTime.setText(DateFormats.likeTime(itemView.getContext(), chatMessage.getDateObject()));
            Glide.with(this.itemView.getContext()).load(chatMessage.getConversionImage()).placeholder(com.example.instagaram.R.drawable.avatar_default).error(com.example.instagaram.R.drawable.avatar_default).fallback(com.example.instagaram.R.drawable.avatar_default).into(binding.imgProfileUser);

            String conversionUserId;
            if (Objects.equals(chatMessage.getReceiverId(), preferenceManager.getString(Constants.KEY_USER_ID))) {
                conversionUserId = chatMessage.getSenderId();
            } else {
                conversionUserId = chatMessage.getReceiverId();
            }

            availabilityChecker.checkUserAvailability(conversionUserId, new UserAvailabilityChecker.OnUserAvailabilityListener() {
                @Override
                public void onAvailabilityChecked(boolean isAvailable) {
                    if (isAvailable) {
                        binding.imgOnline.setVisibility(View.VISIBLE);
                    } else {
                        binding.imgOnline.setVisibility(View.GONE);
                    }
                }
            });

            binding.getRoot().setOnClickListener(v -> {
                User user = new User();
                user.setId(chatMessage.getConversionId());
                user.setUsername(chatMessage.getConversionUsername());
                user.setImage(chatMessage.getConversionImage());
                conversionListener.onConversionClicked(user);
            });
        }
    }

}
