package com.example.instagaram.firebase;

import androidx.annotation.NonNull;

import com.example.instagaram.utilities.Constants;
import com.example.instagaram.utilities.NotificationHelper;
import com.example.instagaram.utilities.PreferenceManager;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

public class MessagingService extends FirebaseMessagingService {

    /** Tokens rotate; store the new one so server/notify can still reach this device. */
    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        PreferenceManager preferenceManager = new PreferenceManager(getApplicationContext());
        if (!preferenceManager.getBoolean(Constants.KEY_IS_LOGGED_IN)) return;
        FirebaseFirestore.getInstance().collection(Constants.KEY_COLLECTION_USERS)
                .document(preferenceManager.getString(Constants.KEY_USER_ID))
                .update(Constants.KEY_FCM_TOKEN, token);
    }

    /** server/notify sends data-only messages; the app builds the localised notification. */
    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);
        if (!new PreferenceManager(getApplicationContext()).getBoolean(Constants.KEY_IS_LOGGED_IN)) return;
        if (remoteMessage.getData().isEmpty()) return;
        NotificationHelper.show(getApplicationContext(), remoteMessage.getData());
    }
}
