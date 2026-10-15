package com.example.instagaram.activities;

import com.example.instagaram.utilities.Constants;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Objects;

public class UserAvailabilityChecker {

    public void checkUserAvailability(String userId, OnUserAvailabilityListener listener) {
        FirebaseFirestore.getInstance().collection(Constants.KEY_COLLECTION_USERS)
                .document(userId)
                .addSnapshotListener((value, error) -> {
                    if (error != null) {
                        listener.onAvailabilityChecked(false);
                        return;
                    }
                    if (value != null) {
                        if (value.getLong(Constants.KEY_AVAILABILITY) != null) {
                            int availability = Objects.requireNonNull(value.getLong(Constants.KEY_AVAILABILITY)).intValue();
                            boolean isUserAvailable = availability == 1;
                            listener.onAvailabilityChecked(isUserAvailable);
                        }
                    }
                });
    }

    public interface OnUserAvailabilityListener {
        void onAvailabilityChecked(boolean isAvailable);
    }
}
