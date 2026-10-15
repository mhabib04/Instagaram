package com.example.instagaram.utilities;

import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.example.instagaram.R;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

/**
 * Live username check while typing: a green check at the end of the field when the name is free,
 * a red cross when it is too short or taken, with the reason in the hint line below.
 */
public final class UsernameChecker {

    public static final int MIN_LENGTH = 4;
    private static final long DELAY_MS = 400;

    public enum State { EMPTY, UNCHANGED, TOO_SHORT, CHECKING, AVAILABLE, TAKEN, ERROR }

    public interface Listener {
        void onStateChanged(@NonNull State state);
    }

    private final EditText input;
    private final TextView hint;
    @Nullable private final String ownerId;
    @Nullable private final Listener listener;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable pending;
    private String unchangedName;
    private String latest = "";
    private State state = State.EMPTY;

    /**
     * @param ownerId the signed-in user's id when editing a profile, so their own current
     *                username counts as free; null when registering.
     */
    public UsernameChecker(@NonNull EditText input, @NonNull TextView hint, @Nullable String ownerId,
                           @Nullable Listener listener) {
        this.input = input;
        this.hint = hint;
        this.ownerId = ownerId;
        this.listener = listener;
        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) { }

            @Override
            public void afterTextChanged(Editable s) {
                onTyped(s.toString().trim());
            }
        });
    }

    /** The username already stored for this account; typing it back shows no icon. */
    public void setUnchangedName(@Nullable String name) {
        unchangedName = name;
        onTyped(input.getText().toString().trim());
    }

    @NonNull
    public State getState() {
        return state;
    }

    private void onTyped(String name) {
        if (pending != null) handler.removeCallbacks(pending);
        latest = name;
        if (name.isEmpty()) {
            show(State.EMPTY);
        } else if (name.equals(unchangedName)) {
            show(State.UNCHANGED);
        } else if (name.length() < MIN_LENGTH) {
            show(State.TOO_SHORT);
        } else {
            show(State.CHECKING);
            // Wait for a pause in typing so Firestore is queried once, not per keystroke.
            pending = () -> query(name);
            handler.postDelayed(pending, DELAY_MS);
        }
    }

    private void query(String name) {
        FirebaseFirestore.getInstance().collection(Constants.KEY_COLLECTION_USERS)
                .whereEqualTo(Constants.KEY_USERNAME, name)
                .get()
                .addOnCompleteListener(task -> {
                    if (!name.equals(latest)) return; // the user kept typing
                    if (!task.isSuccessful() || task.getResult() == null) {
                        show(State.ERROR);
                        return;
                    }
                    boolean taken = false;
                    for (DocumentSnapshot user : task.getResult()) {
                        if (!user.getId().equals(ownerId)) taken = true;
                    }
                    show(taken ? State.TAKEN : State.AVAILABLE);
                });
    }

    private void show(State newState) {
        state = newState;
        int icon = 0, message = 0, color = R.color.like_red;
        switch (newState) {
            case TOO_SHORT:
                icon = R.drawable.ic_cancel;
                message = R.string.error_username_too_short;
                break;
            case TAKEN:
                icon = R.drawable.ic_cancel;
                message = R.string.error_username_registered;
                break;
            case AVAILABLE:
                icon = R.drawable.ic_check_circle;
                message = R.string.username_available;
                color = R.color.online;
                break;
            default:
                // EMPTY, UNCHANGED, CHECKING and ERROR show nothing; an error is retried on submit.
                break;
        }
        input.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, icon, 0);
        if (message == 0) {
            hint.setVisibility(View.GONE);
        } else {
            hint.setText(message);
            hint.setTextColor(ContextCompat.getColor(hint.getContext(), color));
            hint.setVisibility(View.VISIBLE);
        }
        if (listener != null) listener.onStateChanged(newState);
    }

    /** Stops a pending check, e.g. when the screen is closed. */
    public void cancel() {
        if (pending != null) handler.removeCallbacks(pending);
    }
}
