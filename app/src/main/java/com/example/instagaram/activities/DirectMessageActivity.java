package com.example.instagaram.activities;

import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.View;

import androidx.core.graphics.drawable.DrawableCompat;

import com.example.instagaram.R;
import com.example.instagaram.adapters.RecentConversationsAdapter;
import com.example.instagaram.databinding.ActivityDirectMessageBinding;
import com.example.instagaram.listeners.ConversionListener;
import com.example.instagaram.models.ChatMessage;
import com.example.instagaram.models.User;
import com.example.instagaram.utilities.Constants;
import com.example.instagaram.utilities.PreferenceManager;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;

public class DirectMessageActivity extends BaseActivity implements ConversionListener {

    private ActivityDirectMessageBinding binding;
    private PreferenceManager preferenceManager;
    private List<ChatMessage> conversations;
    private RecentConversationsAdapter recentConversationsAdapter;
    private FirebaseFirestore database;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityDirectMessageBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        preferenceManager = new PreferenceManager(this);

        setSupportActionBar(binding.toolbarDM);
        getSupportActionBar().setDisplayShowHomeEnabled(true);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowTitleEnabled(false);


        Drawable upArrow = binding.toolbarDM.getNavigationIcon();
        if (upArrow != null) {
            upArrow = DrawableCompat.wrap(upArrow);
            DrawableCompat.setTint(upArrow, getResources().getColor(R.color.black));
            getSupportActionBar().setHomeAsUpIndicator(upArrow);
        }

        loadUserDetails();
        init();
        listenConversion();

        binding.fabNewChat.setOnClickListener(v -> {
            Intent intent = new Intent(this, UsersActivity.class);
            startActivity(intent);
        });

        binding.listConversations.setVisibility(View.GONE);
        binding.txtNoConversation.setVisibility(View.GONE);

    }

    private void init() {
        conversations = new ArrayList<>();
        recentConversationsAdapter = new RecentConversationsAdapter(conversations, this, preferenceManager);
        binding.listConversations.setAdapter(recentConversationsAdapter);
        database = FirebaseFirestore.getInstance();
    }

    private void loadUserDetails() {
        binding.txtUsername.setText(preferenceManager.getString(Constants.KEY_USERNAME));
    }

    private void listenConversion() {
        database.collection(Constants.KEY_COLLECTION_CONVERSATIONS)
                .whereEqualTo(Constants.KEY_SENDER_ID, preferenceManager.getString(Constants.KEY_USER_ID))
                .addSnapshotListener(eventListener);
        database.collection(Constants.KEY_COLLECTION_CONVERSATIONS)
                .whereEqualTo(Constants.KEY_RECEIVER_ID, preferenceManager.getString(Constants.KEY_USER_ID))
                .addSnapshotListener(eventListener);
    }

    /**
     * One row per person. Older data can hold two conversation documents for the same pair;
     * only the most recent one is shown. Rows are matched by document id (kept in messageId).
     */
    private final EventListener<QuerySnapshot> eventListener = (value, error) -> {
        if (error != null || value == null) {
            return;
        }
        for (DocumentChange documentChange : value.getDocumentChanges()) {
            DocumentSnapshot document = documentChange.getDocument();
            if (documentChange.getType() == DocumentChange.Type.REMOVED) {
                conversations.removeIf(c -> document.getId().equals(c.getMessageId()));
                continue;
            }
            ChatMessage conversation = toConversation(document);
            if (conversation.getMessage() == null || conversation.getMessage().isEmpty()) {
                // Emptied before empty conversations were deleted: not a real conversation any more.
                conversations.removeIf(c -> document.getId().equals(c.getMessageId()));
                continue;
            }
            int index = indexOfPartner(conversation.getConversionId());
            if (index < 0) {
                conversations.add(conversation);
            } else {
                ChatMessage shown = conversations.get(index);
                if (document.getId().equals(shown.getMessageId()) || isNewer(conversation, shown)) {
                    conversations.set(index, conversation);
                }
            }
        }
        conversations.sort((a, b) -> isNewer(a, b) ? -1 : isNewer(b, a) ? 1 : 0);
        recentConversationsAdapter.notifyDataSetChanged();
        if (conversations.isEmpty()) {
            binding.listConversations.setVisibility(View.GONE);
            binding.txtNoConversation.setVisibility(View.VISIBLE);
        } else {
            binding.listConversations.setVisibility(View.VISIBLE);
            binding.txtNoConversation.setVisibility(View.GONE);
            binding.listConversations.smoothScrollToPosition(0);
        }
    };

    /** A conversation document as seen from the signed-in user: the other person's name and photo. */
    private ChatMessage toConversation(DocumentSnapshot document) {
        String senderId = document.getString(Constants.KEY_SENDER_ID);
        String receiverId = document.getString(Constants.KEY_RECEIVER_ID);
        boolean iSent = preferenceManager.getString(Constants.KEY_USER_ID).equals(senderId);
        ChatMessage conversation = new ChatMessage();
        conversation.setMessageId(document.getId());
        conversation.setSenderId(senderId);
        conversation.setReceiverId(receiverId);
        conversation.setConversionId(iSent ? receiverId : senderId);
        conversation.setConversionUsername(document.getString(iSent ? Constants.KEY_RECEIVER_USERNAME : Constants.KEY_SENDER_USERNAME));
        conversation.setConversionImage(document.getString(iSent ? Constants.KEY_RECEIVER_IMAGE : Constants.KEY_SENDER_IMAGE));
        conversation.setMessage(document.getString(Constants.KEY_LAST_MESSAGE));
        conversation.setDateObject(document.getDate(Constants.KEY_TIMESTAMP));
        return conversation;
    }

    private int indexOfPartner(String partnerId) {
        for (int i = 0; i < conversations.size(); i++) {
            if (Objects.equals(conversations.get(i).getConversionId(), partnerId)) return i;
        }
        return -1;
    }

    private static boolean isNewer(ChatMessage a, ChatMessage b) {
        Date da = a.getDateObject(), db = b.getDateObject();
        return da != null && (db == null || da.after(db));
    }

    @Override
    public void onBackPressed() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        super.onBackPressed();
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    @Override
    public void onConversionClicked(User user) {
        Intent intent = new Intent(this, ChatRoomActivity.class);
        intent.putExtra(Constants.KEY_USER, user);
        startActivity(intent);
    }
}