package com.example.instagaram.activities;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import com.example.instagaram.R;
import com.example.instagaram.databinding.ActivityEditProfileBinding;
import com.example.instagaram.utilities.Constants;
import com.example.instagaram.utilities.PreferenceManager;
import com.example.instagaram.utilities.UsernameChecker;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.HashMap;
import java.util.Map;

/** Edit username and name; the Save button is only enabled when something changed. */
public class EditProfileActivity extends BaseActivity {

    private ActivityEditProfileBinding binding;
    private FirebaseFirestore database;
    private PreferenceManager preferenceManager;
    private String myId;
    // Values as currently stored, to detect changes.
    private String originalUsername = "", originalName = "";
    private UsernameChecker usernameChecker;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityEditProfileBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        database = FirebaseFirestore.getInstance();
        preferenceManager = new PreferenceManager(this);
        myId = preferenceManager.getString(Constants.KEY_USER_ID);

        binding.btnBack.setOnClickListener(v -> finish());
        binding.btnSave.setOnClickListener(v -> save());
        binding.txtChangePassword.setOnClickListener(v ->
                startActivity(new Intent(this, ChangePasswordActivity.class)));

        TextWatcher watcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) { }

            @Override
            public void afterTextChanged(Editable s) {
                updateSaveButton();
            }
        };
        setFieldsEnabled(false);
        // Own current username counts as free; the checker also refreshes the Save button.
        usernameChecker = new UsernameChecker(binding.etUsername, binding.txtUsernameStatus, myId,
                state -> updateSaveButton());
        binding.etName.addTextChangedListener(watcher);

        loadProfile();
    }

    private void loadProfile() {
        database.collection(Constants.KEY_COLLECTION_USERS).document(myId).get()
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful() || task.getResult() == null || !task.getResult().exists()) {
                        Toast.makeText(this, R.string.error_get_user, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    DocumentSnapshot user = task.getResult();
                    originalUsername = valueOf(user.getString(Constants.KEY_USERNAME));
                    originalName = valueOf(user.getString(Constants.KEY_NAME));
                    usernameChecker.setUnchangedName(originalUsername);
                    binding.etUsername.setText(originalUsername);
                    binding.etName.setText(originalName);
                    setFieldsEnabled(true);
                    updateSaveButton();
                });
    }

    private static String valueOf(String s) {
        return s == null ? "" : s;
    }

    private static String text(EditText editText) {
        return editText.getText().toString().trim();
    }

    private void setFieldsEnabled(boolean enabled) {
        binding.etUsername.setEnabled(enabled);
        binding.etName.setEnabled(enabled);
    }

    /**
     * Save is active only when a field differs from what is stored, none is blank, and a new
     * username is long enough and not known to be taken.
     */
    private void updateSaveButton() {
        if (binding == null || usernameChecker == null) return;
        String username = text(binding.etUsername), name = text(binding.etName);
        boolean changed = !username.equals(originalUsername) || !name.equals(originalName);
        boolean complete = !username.isEmpty() && !name.isEmpty();
        UsernameChecker.State state = usernameChecker.getState();
        boolean usernameOk = state != UsernameChecker.State.TOO_SHORT && state != UsernameChecker.State.TAKEN;
        binding.btnSave.setEnabled(changed && complete && usernameOk && binding.etUsername.isEnabled());
    }

    private void save() {
        String username = text(binding.etUsername), name = text(binding.etName);
        if (username.equals(originalUsername)) {
            writeProfile(username, name);
            return;
        }
        loading(true);
        // A new username must not belong to another account.
        Task<QuerySnapshot> usernameCheck = database.collection(Constants.KEY_COLLECTION_USERS)
                .whereEqualTo(Constants.KEY_USERNAME, username).get();
        usernameCheck.addOnCompleteListener(done -> {
            if (!usernameCheck.isSuccessful()) {
                loading(false);
                Toast.makeText(this, R.string.error_connection, Toast.LENGTH_SHORT).show();
            } else if (takenByOther(usernameCheck)) {
                loading(false);
                Toast.makeText(this, R.string.error_username_registered, Toast.LENGTH_SHORT).show();
            } else {
                writeProfile(username, name);
            }
        });
    }

    private boolean takenByOther(Task<QuerySnapshot> check) {
        if (check == null || check.getResult() == null) return false;
        for (DocumentSnapshot document : check.getResult()) {
            if (!document.getId().equals(myId)) return true;
        }
        return false;
    }

    private void writeProfile(String username, String name) {
        loading(true);
        Map<String, Object> updates = new HashMap<>();
        updates.put(Constants.KEY_USERNAME, username);
        updates.put(Constants.KEY_NAME, name);
        database.collection(Constants.KEY_COLLECTION_USERS).document(myId).update(updates)
                .addOnSuccessListener(unused -> {
                    preferenceManager.putString(Constants.KEY_USERNAME, username);
                    preferenceManager.putString(Constants.KEY_NAME, name);
                    if (!username.equals(originalUsername)) propagateUsername(username);
                    loading(false);
                    finish();
                })
                .addOnFailureListener(e -> {
                    loading(false);
                    Toast.makeText(this, R.string.error_update_profile, Toast.LENGTH_SHORT).show();
                });
    }

    /** Posts and conversations store a copy of the username; keep them in sync. */
    private void propagateUsername(String username) {
        database.collection(Constants.KEY_COLLECTION_POSTS).whereEqualTo(Constants.KEY_USER_ID, myId).get()
                .addOnSuccessListener(posts -> {
                    for (DocumentSnapshot post : posts) post.getReference().update(Constants.KEY_USERNAME, username);
                });
        database.collection(Constants.KEY_COLLECTION_CONVERSATIONS).whereEqualTo(Constants.KEY_SENDER_ID, myId).get()
                .addOnSuccessListener(conversations -> {
                    for (DocumentSnapshot c : conversations) c.getReference().update(Constants.KEY_SENDER_USERNAME, username);
                });
        database.collection(Constants.KEY_COLLECTION_CONVERSATIONS).whereEqualTo(Constants.KEY_RECEIVER_ID, myId).get()
                .addOnSuccessListener(conversations -> {
                    for (DocumentSnapshot c : conversations) c.getReference().update(Constants.KEY_RECEIVER_USERNAME, username);
                });
    }

    private void loading(boolean isLoading) {
        binding.btnSave.setVisibility(isLoading ? View.INVISIBLE : View.VISIBLE);
        binding.progressSave.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        setFieldsEnabled(!isLoading);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (usernameChecker != null) usernameChecker.cancel();
    }
}
