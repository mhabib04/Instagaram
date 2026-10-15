package com.example.instagaram.adapters;

import android.content.Context;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.instagaram.R;
import com.example.instagaram.databinding.ListReceivedMessageBinding;
import com.example.instagaram.databinding.ListSentMessageBinding;
import com.example.instagaram.models.ChatMessage;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ChatAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    /** Long-press on one of the current user's own bubbles. */
    public interface OnMessageLongClickListener {
        void onMessageLongClicked(ChatMessage message, View bubble);
    }

    private final List<ChatMessage> chatMessages;
    private final String receiverProfileImage;
    private final String senderId;
    private final OnMessageLongClickListener longClickListener;

    public static final int VIEW_TYPE_SENT = 1;
    public static final int VIEW_TYPE_RECEIVED = 2;

    public ChatAdapter(List<ChatMessage> chatMessages, String receiverProfileImage, String senderId,
                       OnMessageLongClickListener longClickListener) {
        this.chatMessages = chatMessages;
        this.receiverProfileImage = receiverProfileImage;
        this.senderId = senderId;
        this.longClickListener = longClickListener;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == VIEW_TYPE_SENT) {
            return new SentMessageViewHolder(ListSentMessageBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));

        } else {
            return new ReceivedMessageViewHolder(ListReceivedMessageBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (getItemViewType(position) == VIEW_TYPE_SENT) {
            SentMessageViewHolder sent = (SentMessageViewHolder) holder;
            ChatMessage message = chatMessages.get(position);
            sent.binding.txtMessage.setText(message.getMessage());
            // Only your own messages can be deleted, so only sent bubbles react to a long-press.
            sent.binding.txtMessage.setOnLongClickListener(v -> {
                longClickListener.onMessageLongClicked(message, v);
                return true;
            });
            bindDateAndTime(position, sent.binding.txtDateHeader, sent.binding.txtDateTime);
        } else {
            ReceivedMessageViewHolder received = (ReceivedMessageViewHolder) holder;
            received.setMessageData(chatMessages.get(position), receiverProfileImage);
            bindDateAndTime(position, received.binding.txtDateHeader, received.binding.txtDateTime);
        }
    }

    /**
     * Date pill only above the first message of each day; the time only under the last
     * message of a run sent by the same person within the same minute.
     */
    private void bindDateAndTime(int position, TextView dateHeader, TextView time) {
        ChatMessage current = chatMessages.get(position);
        Date date = current.getDateObject();
        Context context = time.getContext();

        boolean firstOfDay = position == 0
                || !sameDay(chatMessages.get(position - 1).getDateObject(), date);
        dateHeader.setVisibility(firstOfDay ? View.VISIBLE : View.GONE);
        if (firstOfDay) dateHeader.setText(formatDay(context, date));

        boolean lastOfMinute = true;
        if (position + 1 < chatMessages.size()) {
            ChatMessage next = chatMessages.get(position + 1);
            lastOfMinute = !(next.getSenderId().equals(current.getSenderId())
                    && sameMinute(next.getDateObject(), date));
        }
        time.setVisibility(lastOfMinute ? View.VISIBLE : View.GONE);
        if (lastOfMinute) time.setText(DateFormat.getTimeFormat(context).format(date));
    }

    private static String formatDay(Context context, Date date) {
        Calendar day = Calendar.getInstance();
        day.setTime(date);
        Calendar today = Calendar.getInstance();
        if (sameDay(day, today)) return context.getString(R.string.chat_today);
        today.add(Calendar.DAY_OF_YEAR, -1);
        if (sameDay(day, today)) return context.getString(R.string.chat_yesterday);
        return new SimpleDateFormat("EEEE, d MMMM yyyy", Locale.getDefault()).format(date);
    }

    private static boolean sameDay(Date a, Date b) {
        Calendar ca = Calendar.getInstance();
        ca.setTime(a);
        Calendar cb = Calendar.getInstance();
        cb.setTime(b);
        return sameDay(ca, cb);
    }

    private static boolean sameDay(Calendar a, Calendar b) {
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR)
                && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
    }

    private static boolean sameMinute(Date a, Date b) {
        return a.getTime() / 60000 == b.getTime() / 60000;
    }

    @Override
    public int getItemCount() {
        return chatMessages.size();
    }

    @Override
    public int getItemViewType(int position) {
        if (chatMessages.get(position).getSenderId().equals(senderId)) {
            return VIEW_TYPE_SENT;
        } else {
            return VIEW_TYPE_RECEIVED;
        }
    }

    static class SentMessageViewHolder extends RecyclerView.ViewHolder {

        private final ListSentMessageBinding binding;

        SentMessageViewHolder(ListSentMessageBinding listSentMessageBinding) {
            super(listSentMessageBinding.getRoot());
            binding = listSentMessageBinding;
        }
    }

    static class ReceivedMessageViewHolder extends RecyclerView.ViewHolder {

        private final ListReceivedMessageBinding binding;

        ReceivedMessageViewHolder(ListReceivedMessageBinding listReceivedMessageBinding) {
            super(listReceivedMessageBinding.getRoot());
            binding = listReceivedMessageBinding;
        }

        void setMessageData(ChatMessage chatMessage, String receiverProfileImage) {
            binding.txtMessage.setText(chatMessage.getMessage());
            Glide.with(this.itemView.getContext()).load(receiverProfileImage).placeholder(R.drawable.avatar_default).error(R.drawable.avatar_default).fallback(R.drawable.avatar_default).into(binding.imgProfileUserReceived);
        }
    }

}
