package com.example.instagaram.activities;

import android.annotation.SuppressLint;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.PopupMenu;
import android.widget.Toast;


import androidx.core.graphics.drawable.DrawableCompat;

import com.example.instagaram.R;
import com.example.instagaram.adapters.ChatAdapter;
import com.example.instagaram.databinding.ActivityChatRoomBinding;
import com.example.instagaram.models.ChatMessage;
import com.example.instagaram.models.User;
import com.example.instagaram.network.NotifyClient;
import com.example.instagaram.utilities.ConfirmDialog;
import com.example.instagaram.utilities.Constants;
import com.example.instagaram.utilities.PreferenceManager;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

public class ChatRoomActivity extends BaseActivity {

    private static final long DELETE_WINDOW_MS = 24L * 60 * 60 * 1000;

    private ActivityChatRoomBinding binding;
    private User receiverUser;
    private List<ChatMessage> chatMessages;
    private ChatAdapter chatAdapter;
    private PreferenceManager preferenceManager;
    private FirebaseFirestore database;
    private String conversionId = null;
    private UserAvailabilityChecker availabilityChecker;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityChatRoomBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        loadReceiverDetails();
        init();
        listenMessages();
        // Known up front, even when every message was deleted and the room opens empty.
        findConversation().addOnSuccessListener(id -> {
            if (id != null) conversionId = id;
        });

        availabilityChecker = new UserAvailabilityChecker();

        binding.btnSend.setOnClickListener(v -> {
            sendMessage();
        });
        // Send is only possible once the message has some non-blank text.
        updateSendButton();
        binding.etChat.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) { }

            @Override
            public void afterTextChanged(Editable s) {
                updateSendButton();
            }
        });

        setSupportActionBar(binding.toolbarChatRoom);
        getSupportActionBar().setDisplayShowHomeEnabled(true);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowTitleEnabled(false);

        Drawable upArrow = binding.toolbarChatRoom.getNavigationIcon();
        if (upArrow != null) {
            upArrow = DrawableCompat.wrap(upArrow);
            DrawableCompat.setTint(upArrow, getResources().getColor(R.color.black));
            getSupportActionBar().setHomeAsUpIndicator(upArrow);
        }
    }

    private void init() {
        preferenceManager = new PreferenceManager(this);
        chatMessages = new ArrayList<>();
        chatAdapter = new ChatAdapter(chatMessages, receiverUser.getImage(),
                preferenceManager.getString(Constants.KEY_USER_ID), this::onMessageLongClicked);
        binding.listChat.setAdapter(chatAdapter);
        database = FirebaseFirestore.getInstance();
    }

    private void updateSendButton() {
        boolean hasText = !binding.etChat.getText().toString().trim().isEmpty();
        binding.btnSend.setEnabled(hasText);
        binding.btnSend.setAlpha(hasText ? 1f : 0.4f);
    }

    private void sendMessage() {
        if (binding.etChat.getText().toString().trim().isEmpty()) return;
        HashMap<String, Object> message = new HashMap<>();
        message.put(Constants.KEY_SENDER_ID, preferenceManager.getString(Constants.KEY_USER_ID));
        message.put(Constants.KEY_RECEIVER_ID, receiverUser.getId());
        message.put(Constants.KEY_MESSAGE, binding.etChat.getText().toString());
        message.put(Constants.KEY_TIMESTAMP, new Date());
        database.collection(Constants.KEY_COLLECTION_CHAT).add(message)
                .addOnSuccessListener(chat -> NotifyClient.chatSent(chat.getId()));
        String text = binding.etChat.getText().toString();
        if (conversionId != null) {
            updateConversion(text);
        } else {
            // Not known yet (lookup still running or failed): check again before creating one,
            // otherwise the DM list would get a second row for the same person.
            findConversation().addOnCompleteListener(task -> {
                String existing = task.isSuccessful() ? task.getResult() : null;
                if (existing != null) {
                    conversionId = existing;
                    updateConversion(text);
                } else {
                    createConversion(text);
                }
            });
        }
        binding.etChat.setText(null);
    }

    public void listenAvailabilityOfReceiver() {
        availabilityChecker.checkUserAvailability(receiverUser.getId(), new UserAvailabilityChecker.OnUserAvailabilityListener() {
            @Override
            public void onAvailabilityChecked(boolean isAvailable) {
                if (isAvailable) {
                    binding.txtStatus.setVisibility(View.VISIBLE);
                } else {
                    binding.txtStatus.setVisibility(View.GONE);
                }
            }
        });
    }

    private void listenMessages() {
        database.collection(Constants.KEY_COLLECTION_CHAT)
                .whereEqualTo(Constants.KEY_SENDER_ID, preferenceManager.getString(Constants.KEY_USER_ID))
                .whereEqualTo(Constants.KEY_RECEIVER_ID, receiverUser.getId())
                .addSnapshotListener(messageListener);
        database.collection(Constants.KEY_COLLECTION_CHAT)
                .whereEqualTo(Constants.KEY_SENDER_ID, receiverUser.getId())
                .whereEqualTo(Constants.KEY_RECEIVER_ID, preferenceManager.getString(Constants.KEY_USER_ID))
                .addSnapshotListener(messageListener);;
    }

    @SuppressLint("NotifyDataSetChanged")
    private final EventListener<QuerySnapshot> messageListener = (value, error) -> {
        if (error != null) {
            return;
        }
        if (value != null) {
            int count = chatMessages.size();
            for (DocumentChange documentChange : value.getDocumentChanges()) {
                if (documentChange.getType() == DocumentChange.Type.REMOVED) {
                    String removedId = documentChange.getDocument().getId();
                    chatMessages.removeIf(m -> removedId.equals(m.getMessageId()));
                } else if (documentChange.getType() == DocumentChange.Type.ADDED) {
                    ChatMessage chatMessage = new ChatMessage();
                    chatMessage.setMessageId(documentChange.getDocument().getId());
                    chatMessage.setSenderId(documentChange.getDocument().getString(Constants.KEY_SENDER_ID));
                    chatMessage.setReceiverId(documentChange.getDocument().getString(Constants.KEY_RECEIVER_ID));
                    chatMessage.setMessage(documentChange.getDocument().getString(Constants.KEY_MESSAGE));
                    chatMessage.setDateObject(documentChange.getDocument().getDate(Constants.KEY_TIMESTAMP));
                    chatMessages.add(chatMessage);
                }
            }
            chatMessages.sort((obj1, obj2) -> obj1.getDateObject().compareTo(obj2.getDateObject()));
            // Full refresh: a new message can change the date/time labels of its neighbours.
            chatAdapter.notifyDataSetChanged();
            if (count != 0 && !chatMessages.isEmpty()) {
                binding.listChat.smoothScrollToPosition(chatMessages.size() - 1);
            }
            binding.listChat.setVisibility(View.VISIBLE);
        }
    };

    /** Own messages can be deleted (for both people) until 24 hours after sending. */
    private void onMessageLongClicked(ChatMessage message, View bubble) {
        if (message.getMessageId() == null || message.getDateObject() == null) return;
        long age = System.currentTimeMillis() - message.getDateObject().getTime();
        if (age > DELETE_WINDOW_MS) {
            Toast.makeText(this, R.string.chat_delete_expired, Toast.LENGTH_SHORT).show();
            return;
        }
        PopupMenu menu = new PopupMenu(this, bubble);
        menu.getMenu().add(R.string.chat_delete);
        menu.setOnMenuItemClickListener(item -> {
            // Close the menu first so it cannot linger behind (or reappear after) the dialog.
            menu.dismiss();
            ConfirmDialog.show(this, R.drawable.ic_delete, R.string.chat_delete_confirm,
                    R.string.chat_delete_message, R.string.action_delete, () -> deleteMessage(message));
            return true;
        });
        menu.show();
    }

    private void deleteMessage(ChatMessage message) {
        database.collection(Constants.KEY_COLLECTION_CHAT).document(message.getMessageId())
                .delete()
                .addOnSuccessListener(unused -> updateConversionAfterDelete(message))
                .addOnFailureListener(e -> Toast.makeText(this, R.string.chat_delete_failed, Toast.LENGTH_SHORT).show());
    }

    /**
     * If the newest message was deleted, the conversation preview (text and time) falls back to
     * the one before it. When no message is left, the conversation leaves the DM list entirely.
     */
    private void updateConversionAfterDelete(ChatMessage deleted) {
        ChatMessage latest = null;
        for (ChatMessage m : chatMessages) {
            if (m.getMessageId() != null && m.getMessageId().equals(deleted.getMessageId())) continue;
            if (latest == null || m.getDateObject().after(latest.getDateObject())) latest = m;
        }
        if (latest == null) {
            deleteConversations();
            return;
        }
        if (conversionId == null || latest.getDateObject().after(deleted.getDateObject())) return;
        database.collection(Constants.KEY_COLLECTION_CONVERSATIONS).document(conversionId)
                .update(Constants.KEY_LAST_MESSAGE, latest.getMessage(),
                        Constants.KEY_TIMESTAMP, latest.getDateObject());
    }

    /** Removes every conversation document of this pair (older data may hold two). */
    private void deleteConversations() {
        conversionId = null;
        String myId = preferenceManager.getString(Constants.KEY_USER_ID);
        String otherId = receiverUser.getId();
        deleteAll(conversationsBetween(myId, otherId));
        deleteAll(conversationsBetween(otherId, myId));
    }

    private static void deleteAll(Task<QuerySnapshot> query) {
        query.addOnSuccessListener(snapshot -> {
            for (DocumentSnapshot document : snapshot) document.getReference().delete();
        });
    }

    private void loadReceiverDetails() {
        receiverUser = (User) getIntent().getSerializableExtra(Constants.KEY_USER);
        binding.txtUsername.setText(receiverUser.getUsername());
    }

    /**
     * New conversations get a fixed id built from both user ids, so two sends racing to create
     * it write the same document instead of two. (Older ones keep their random ids.)
     */
    private void createConversion(String message) {
        String myId = preferenceManager.getString(Constants.KEY_USER_ID);
        String otherId = receiverUser.getId();
        String id = myId.compareTo(otherId) < 0 ? myId + "_" + otherId : otherId + "_" + myId;
        HashMap<String, Object> conversion = new HashMap<>();
        conversion.put(Constants.KEY_SENDER_ID, myId);
        conversion.put(Constants.KEY_SENDER_USERNAME, preferenceManager.getString(Constants.KEY_USERNAME));
        conversion.put(Constants.KEY_SENDER_IMAGE, preferenceManager.getString(Constants.KEY_IMAGE));
        conversion.put(Constants.KEY_RECEIVER_ID, otherId);
        conversion.put(Constants.KEY_RECEIVER_USERNAME, receiverUser.getUsername());
        conversion.put(Constants.KEY_RECEIVER_IMAGE, receiverUser.getImage());
        conversion.put(Constants.KEY_LAST_MESSAGE, message);
        conversion.put(Constants.KEY_TIMESTAMP, new Date());
        conversionId = id;
        database.collection(Constants.KEY_COLLECTION_CONVERSATIONS).document(id).set(conversion);
    }

    private void updateConversion(String message) {
        DocumentReference documentReference = database.collection(Constants.KEY_COLLECTION_CONVERSATIONS).document(conversionId);
        // The other person may have emptied the chat meanwhile, which deletes the conversation:
        // then start a fresh one instead of losing it from the DM list.
        documentReference.update(Constants.KEY_LAST_MESSAGE, message, Constants.KEY_TIMESTAMP, new Date())
                .addOnFailureListener(e -> {
                    if (e instanceof FirebaseFirestoreException
                            && ((FirebaseFirestoreException) e).getCode() == FirebaseFirestoreException.Code.NOT_FOUND) {
                        createConversion(message);
                    }
                });
    }

    /** Id of the conversation between the two users (either direction), or null if none yet. */
    private Task<String> findConversation() {
        String myId = preferenceManager.getString(Constants.KEY_USER_ID);
        String otherId = receiverUser.getId();
        Task<QuerySnapshot> mine = conversationsBetween(myId, otherId);
        Task<QuerySnapshot> theirs = conversationsBetween(otherId, myId);
        return Tasks.whenAllSuccess(mine, theirs).continueWith(task -> {
            for (Object result : task.getResult()) {
                QuerySnapshot snapshot = (QuerySnapshot) result;
                if (!snapshot.isEmpty()) return snapshot.getDocuments().get(0).getId();
            }
            return null;
        });
    }

    private Task<QuerySnapshot> conversationsBetween(String senderId, String receiverId) {
        return database.collection(Constants.KEY_COLLECTION_CONVERSATIONS)
                .whereEqualTo(Constants.KEY_SENDER_ID, senderId)
                .whereEqualTo(Constants.KEY_RECEIVER_ID, receiverId)
                .get();
    }

    /** The user whose chat is on screen; NotificationHelper skips banners for this chat. */
    public static volatile String activeChatUserId;

    @Override
    protected void onResume() {
        super.onResume();
        activeChatUserId = receiverUser.getId();
        listenAvailabilityOfReceiver();
    }

    @Override
    protected void onPause() {
        super.onPause();
        activeChatUserId = null;
    }

    // Back returns to whatever opened the chat (DM list or a profile).
    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}