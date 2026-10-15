package com.example.instagaram.activities;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.instagaram.R;
import com.example.instagaram.databinding.ActivityLoginBinding;
import com.example.instagaram.utilities.Constants;
import com.example.instagaram.utilities.PreferenceManager;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import org.mindrot.jbcrypt.BCrypt;

public class LoginActivity extends AppCompatActivity {

    private ActivityLoginBinding binding;
    private boolean isPasswordVisible = false;
    private PreferenceManager preferenceManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        preferenceManager = new PreferenceManager(this);

        binding.txtRegister.setOnClickListener(v -> {
            Intent intent = new Intent(this, RegisterActivity.class);
            startActivity(intent);
        });

        binding.btnLogin.setOnClickListener(v -> {
            if (isValidLoginDetails()) {
                login();
            }
        });

        binding.btnShowHidePassword.setOnClickListener(v -> {
            isPasswordVisible = !isPasswordVisible;
            if (isPasswordVisible) {
                binding.etPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
                binding.btnShowHidePassword.setImageResource(R.drawable.ic_eye_off);
            } else {
                binding.etPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                binding.btnShowHidePassword.setImageResource(R.drawable.ic_eye);
            }
            binding.etPassword.setSelection(binding.etPassword.getText().length());
        });

    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void login() {
        loading(true);
        FirebaseFirestore database = FirebaseFirestore.getInstance();
        String input = binding.etUsername.getText().toString().trim();
        String password = binding.etPassword.getText().toString().trim();

        // One lookup by username; every credential failure gets the same message
        // so the screen never reveals whether an account exists.
        database.collection(Constants.KEY_COLLECTION_USERS)
                .whereEqualTo(Constants.KEY_USERNAME, input)
                .limit(1)
                .get()
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful() || task.getResult() == null) {
                        loading(false);
                        showToast(getString(R.string.error_connection));
                        return;
                    }
                    DocumentSnapshot user = task.getResult().isEmpty()
                            ? null : task.getResult().getDocuments().get(0);
                    if (user != null && passwordMatches(password, user.getString(Constants.KEY_PASSWORD))) {
                        handleLoginSuccess(user);
                    } else {
                        if (user == null) {
                            // Spend the same bcrypt time as a real check to avoid a timing leak.
                            passwordMatches(password, dummyHash());
                        }
                        loading(false);
                        showToast(getString(R.string.error_login_invalid));
                    }
                });
    }

    private static String dummyHash;

    private static String dummyHash() {
        if (dummyHash == null) {
            dummyHash = BCrypt.hashpw("instagaram-dummy", BCrypt.gensalt());
        }
        return dummyHash;
    }

    private static boolean passwordMatches(String password, String hash) {
        if (hash == null) return false;
        try {
            return BCrypt.checkpw(password, hash);
        } catch (IllegalArgumentException invalidHash) {
            return false;
        }
    }

    private void handleLoginSuccess(DocumentSnapshot documentSnapshot) {
        preferenceManager.putBoolean(Constants.KEY_IS_LOGGED_IN, true);
        preferenceManager.putString(Constants.KEY_USER_ID, documentSnapshot.getId());
        preferenceManager.putString(Constants.KEY_USERNAME, documentSnapshot.getString(Constants.KEY_USERNAME));
        preferenceManager.putString(Constants.KEY_NAME, documentSnapshot.getString(Constants.KEY_NAME));
        preferenceManager.putString(Constants.KEY_IMAGE, documentSnapshot.getString(Constants.KEY_IMAGE));
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }

    private boolean isValidLoginDetails() {
        if (binding.etUsername.getText().toString().trim().isEmpty()) {
            showToast(getString(R.string.error_enter_username));
            return false;
        } else if (binding.etPassword.getText().toString().trim().isEmpty()) {
            showToast(getString(R.string.error_enter_password));
            return false;
        } else {
            return true;
        }
    }

    private void loading(Boolean isLoading) {
        if (isLoading) {
            binding.btnLogin.setVisibility(View.INVISIBLE);
            binding.progressBar.setVisibility(View.VISIBLE);
        } else {
            binding.btnLogin.setVisibility(View.VISIBLE);
            binding.progressBar.setVisibility(View.INVISIBLE);
        }
    }
}