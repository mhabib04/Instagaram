package com.example.instagaram.activities;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import com.example.instagaram.R;
import com.example.instagaram.databinding.ActivityChangePasswordBinding;
import com.example.instagaram.utilities.Constants;
import com.example.instagaram.utilities.CredentialSaver;
import com.example.instagaram.utilities.NewPasswordInput;
import com.example.instagaram.utilities.PreferenceManager;
import com.google.firebase.firestore.FirebaseFirestore;

import org.mindrot.jbcrypt.BCrypt;

/**
 * Two steps: confirm the current password against the stored bcrypt hash,
 * then choose and confirm a new one.
 */
public class ChangePasswordActivity extends BaseActivity {

    private ActivityChangePasswordBinding binding;
    private FirebaseFirestore database;
    private String myId;
    // Hash loaded during step 1, reused to reject "new password == current password".
    private String currentHash;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityChangePasswordBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        database = FirebaseFirestore.getInstance();
        myId = new PreferenceManager(this).getString(Constants.KEY_USER_ID);

        binding.btnBack.setOnClickListener(v -> finish());
        binding.btnNext.setOnClickListener(v -> verifyCurrentPassword());
        binding.btnSavePassword.setOnClickListener(v -> saveNewPassword());

        binding.etCurrentPassword.addTextChangedListener(new SimpleWatcher(() ->
                binding.btnNext.setEnabled(!binding.etCurrentPassword.getText().toString().isEmpty())));
        // The current password may come from a password manager; the new one is typed fresh.
        NewPasswordInput.apply(binding.etNewPassword, binding.etConfirmNewPassword);
        SimpleWatcher newPasswordWatcher = new SimpleWatcher(this::updateNewPasswordState);
        binding.etNewPassword.addTextChangedListener(newPasswordWatcher);
        binding.etConfirmNewPassword.addTextChangedListener(newPasswordWatcher);
    }

    private void verifyCurrentPassword() {
        String entered = binding.etCurrentPassword.getText().toString().trim();
        setLoading(binding.btnNext, binding.progressNext, true);
        database.collection(Constants.KEY_COLLECTION_USERS).document(myId).get()
                .addOnCompleteListener(task -> {
                    setLoading(binding.btnNext, binding.progressNext, false);
                    if (!task.isSuccessful() || task.getResult() == null) {
                        Toast.makeText(this, R.string.error_connection, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    String hash = task.getResult().getString(Constants.KEY_PASSWORD);
                    if (!matches(entered, hash)) {
                        Toast.makeText(this, R.string.error_wrong_current_password, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    currentHash = hash;
                    binding.etCurrentPassword.setText(null);
                    binding.stepCurrent.setVisibility(View.GONE);
                    binding.stepNew.setVisibility(View.VISIBLE);
                    binding.etNewPassword.requestFocus();
                });
    }

    /**
     * Live hints: minimum length under the new password, match under the confirmation.
     * Save needs a long enough password typed twice.
     */
    private void updateNewPasswordState() {
        String password = binding.etNewPassword.getText().toString().trim();
        String confirm = binding.etConfirmNewPassword.getText().toString().trim();
        boolean same = !confirm.isEmpty() && confirm.equals(password);
        boolean tooShort = NewPasswordInput.isTooShort(password);
        binding.txtPasswordRule.setVisibility(tooShort ? View.VISIBLE : View.GONE);
        if (tooShort) {
            binding.txtPasswordRule.setText(R.string.error_password_too_short);
            binding.txtPasswordRule.setTextColor(ContextCompat.getColor(this, R.color.like_red));
        }
        if (confirm.isEmpty()) {
            binding.txtPasswordMatch.setVisibility(View.GONE);
        } else {
            binding.txtPasswordMatch.setVisibility(View.VISIBLE);
            binding.txtPasswordMatch.setText(same ? R.string.password_match : R.string.password_mismatch);
            binding.txtPasswordMatch.setTextColor(ContextCompat.getColor(this, same ? R.color.online : R.color.like_red));
        }
        binding.btnSavePassword.setEnabled(password.length() >= NewPasswordInput.MIN_LENGTH && same);
    }

    private void saveNewPassword() {
        String password = binding.etNewPassword.getText().toString().trim();
        if (matches(password, currentHash)) {
            Toast.makeText(this, R.string.error_same_password, Toast.LENGTH_SHORT).show();
            return;
        }
        setLoading(binding.btnSavePassword, binding.progressSave, true);
        database.collection(Constants.KEY_COLLECTION_USERS).document(myId)
                .update(Constants.KEY_PASSWORD, BCrypt.hashpw(password, BCrypt.gensalt()))
                .addOnSuccessListener(unused -> {
                    Toast.makeText(this, R.string.password_changed, Toast.LENGTH_SHORT).show();
                    // Lets the password manager replace the old saved password with the new one.
                    String username = new PreferenceManager(this).getString(Constants.KEY_USERNAME);
                    if (username == null || username.isEmpty()) {
                        finish();
                    } else {
                        CredentialSaver.offerToSave(this, username, password, this::finish);
                    }
                })
                .addOnFailureListener(e -> {
                    setLoading(binding.btnSavePassword, binding.progressSave, false);
                    Toast.makeText(this, R.string.error_change_password, Toast.LENGTH_SHORT).show();
                });
    }

    private static boolean matches(String password, String hash) {
        if (hash == null) return false;
        try {
            return BCrypt.checkpw(password, hash);
        } catch (IllegalArgumentException invalidHash) {
            return false;
        }
    }

    private static void setLoading(View button, View progress, boolean loading) {
        button.setVisibility(loading ? View.INVISIBLE : View.VISIBLE);
        progress.setVisibility(loading ? View.VISIBLE : View.GONE);
    }

    /** TextWatcher that only cares about "text changed". */
    private static final class SimpleWatcher implements TextWatcher {
        private final Runnable onChanged;

        SimpleWatcher(Runnable onChanged) {
            this.onChanged = onChanged;
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) { }

        @Override
        public void afterTextChanged(Editable s) {
            onChanged.run();
        }
    }
}
