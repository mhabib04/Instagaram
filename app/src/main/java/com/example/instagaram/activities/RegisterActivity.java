package com.example.instagaram.activities;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;
import com.example.instagaram.R;
import com.example.instagaram.databinding.ActivityRegisterBinding;
import com.example.instagaram.utilities.Constants;
import com.example.instagaram.utilities.CredentialSaver;
import com.example.instagaram.utilities.ImageCompressor;
import com.example.instagaram.utilities.NewPasswordInput;
import com.example.instagaram.utilities.PreferenceManager;
import com.example.instagaram.utilities.UsernameChecker;
import com.example.instagaram.network.CloudinaryUploader;
import com.google.firebase.firestore.FirebaseFirestore;

import org.mindrot.jbcrypt.BCrypt;

import java.util.HashMap;

public class RegisterActivity extends AppCompatActivity {

    private ActivityRegisterBinding binding;
    private String imageUrl;
    private Uri imageUri;
    private PreferenceManager preferenceManager;
    private UsernameChecker usernameChecker;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityRegisterBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        // A brand-new password must be typed, not filled from a password manager.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getWindow().getDecorView().setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        }

        preferenceManager = new PreferenceManager(this);
        usernameChecker = new UsernameChecker(binding.etUsername, binding.txtUsernameStatus, null, null);
        NewPasswordInput.apply(binding.etPassword, binding.etConfirmPassword);

        binding.txtLogin.setOnClickListener(v -> onBackPressed());

        binding.btnRegister.setOnClickListener(v -> {
            if (isValidRegisterDetails()) {
                checkUsername();
            }
        });

        // System photo picker limited to images: videos and other files are never listed.
        binding.imgProfile.setOnClickListener(v -> pickImage.launch(
                new PickVisualMediaRequest.Builder()
                        .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                        .build()));

        TextWatcher passwordWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) { }

            @Override
            public void afterTextChanged(Editable s) {
                updatePasswordRule();
                updatePasswordMatch();
            }
        };
        binding.etConfirmPassword.addTextChangedListener(passwordWatcher);
        binding.etPassword.addTextChangedListener(passwordWatcher);
    }

    /** Warns under the password field while it is shorter than the minimum. */
    private void updatePasswordRule() {
        boolean tooShort = NewPasswordInput.isTooShort(binding.etPassword.getText().toString().trim());
        binding.txtPasswordRule.setVisibility(tooShort ? View.VISIBLE : View.GONE);
        if (tooShort) {
            binding.txtPasswordRule.setText(R.string.error_password_too_short);
            binding.txtPasswordRule.setTextColor(ContextCompat.getColor(this, R.color.like_red));
        }
    }

    /** Shows match/mismatch under the confirmation field as soon as it is typed into. */
    private void updatePasswordMatch() {
        String confirm = binding.etConfirmPassword.getText().toString().trim();
        if (confirm.isEmpty()) {
            binding.txtPasswordMatch.setVisibility(View.GONE);
            return;
        }
        boolean matches = confirm.equals(binding.etPassword.getText().toString().trim());
        binding.txtPasswordMatch.setVisibility(View.VISIBLE);
        binding.txtPasswordMatch.setText(matches ? R.string.password_match : R.string.password_mismatch);
        binding.txtPasswordMatch.setTextColor(ContextCompat.getColor(this,
                matches ? R.color.online : R.color.like_red));
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    /** Usernames identify accounts (login, search), so a taken one cannot be registered again. */
    private void checkUsername() {
        loading(true);
        FirebaseFirestore.getInstance().collection(Constants.KEY_COLLECTION_USERS)
                .whereEqualTo(Constants.KEY_USERNAME, binding.etUsername.getText().toString().trim())
                .get()
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful() || task.getResult() == null) {
                        loading(false);
                        showToast(getString(R.string.error_connection));
                    } else if (!task.getResult().isEmpty()) {
                        loading(false);
                        showToast(getString(R.string.error_username_registered));
                    } else {
                        register();
                    }
                });
    }

    private void register() {
        loading(true);
        if (imageUri == null) {
            // No photo chosen: the account uses avatar_default until one is added from the profile.
            imageUrl = null;
            registerData();
            return;
        }
        CloudinaryUploader.uploadImage(this, imageUri, "instagaram/profiles", ImageCompressor.PROFILE_MAX_SIDE,
                new CloudinaryUploader.Callback() {
                    @Override
                    public void onSuccess(@NonNull String secureUrl) {
                        imageUrl = secureUrl;
                        registerData();
                    }

                    @Override
                    public void onError(@NonNull String message) {
                        loading(false);
                        showToast(message);
                    }
                });
    }

    private void registerData() {
        FirebaseFirestore database = FirebaseFirestore.getInstance();
        HashMap<String, Object> user = new HashMap<>();
        user.put(Constants.KEY_NAME, binding.etName.getText().toString());
        user.put(Constants.KEY_USERNAME, binding.etUsername.getText().toString());

        // Hash the password before storing it
        String hashedPassword = BCrypt.hashpw(binding.etPassword.getText().toString(), BCrypt.gensalt());
        user.put(Constants.KEY_PASSWORD, hashedPassword);

        if (imageUrl != null) user.put(Constants.KEY_IMAGE, imageUrl);

        database.collection(Constants.KEY_COLLECTION_USERS)
                .add(user)
                .addOnSuccessListener(documentReference -> {
                    loading(false);
                    preferenceManager.putBoolean(Constants.KEY_IS_LOGGED_IN, true);
                    preferenceManager.putString(Constants.KEY_USER_ID, documentReference.getId());
                    preferenceManager.putString(Constants.KEY_NAME, binding.etName.getText().toString().trim());
                    preferenceManager.putString(Constants.KEY_USERNAME, binding.etUsername.getText().toString().trim());
                    preferenceManager.putString(Constants.KEY_IMAGE, imageUrl);
                    // Same values the account was created with, so the saved login works as is.
                    CredentialSaver.offerToSave(this, binding.etUsername.getText().toString().trim(),
                            binding.etPassword.getText().toString(), this::openHome);
                })
                .addOnFailureListener(exception -> {
                    loading(false);
                    showToast(exception.getMessage());
                });
    }


    private void openHome() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }

    private final ActivityResultLauncher<PickVisualMediaRequest> pickImage = registerForActivityResult(
            new ActivityResultContracts.PickVisualMedia(),
            uri -> {
                if (uri == null) return;
                // Real file size; big photos are fine because the uploader shrinks them.
                if (ImageCompressor.getSize(this, uri) > ImageCompressor.MAX_INPUT_BYTES) {
                    showToast(getString(R.string.error_image_too_large));
                    return;
                }
                imageUri = uri;
                showAddImagePlaceholder(false);
                // Glide decodes at view size instead of the full-resolution setImageURI.
                Glide.with(this).load(uri).into(binding.imgProfile);
            }
    );

    private void showAddImagePlaceholder(boolean show) {
        int visibility = show ? View.VISIBLE : View.GONE;
        binding.txtAddImage.setVisibility(visibility);
        binding.txtAddImageSize.setVisibility(visibility);
        binding.imgAddImage.setVisibility(visibility);
    }


    private boolean isValidRegisterDetails() {
        if (binding.etName.getText().toString().trim().isEmpty()) {
            showToast(getString(R.string.error_enter_name));
            return false;
        } else if (binding.etUsername.getText().toString().trim().isEmpty()) {
            showToast(getString(R.string.error_enter_username));
            return false;
        } else if (binding.etUsername.getText().toString().trim().length() < UsernameChecker.MIN_LENGTH) {
            binding.etUsername.requestFocus();
            showToast(getString(R.string.error_username_too_short));
            return false;
        } else if (usernameChecker.getState() == UsernameChecker.State.TAKEN) {
            binding.etUsername.requestFocus();
            showToast(getString(R.string.error_username_registered));
            return false;
        } else if (binding.etPassword.getText().toString().trim().isEmpty()) {
            showToast(getString(R.string.error_enter_password));
            return false;
        } else if (binding.etPassword.getText().toString().trim().length() < NewPasswordInput.MIN_LENGTH) {
            binding.etPassword.requestFocus();
            showToast(getString(R.string.error_password_too_short));
            return false;
        } else if (binding.etConfirmPassword.getText().toString().trim().isEmpty()) {
            showToast(getString(R.string.error_confirm_password));
            return false;
        } else if (!binding.etPassword.getText().toString().trim().equals(binding.etConfirmPassword.getText().toString().trim())) {
            // Already shown live under the field; focus it so the user sees the hint.
            binding.etConfirmPassword.requestFocus();
            updatePasswordMatch();
            return false;
        } else {
            return true;
        }
    }

    private void loading(Boolean isLoading) {
        if (isLoading) {
            binding.btnRegister.setVisibility(View.INVISIBLE);
            binding.progressBar.setVisibility(View.VISIBLE);
        } else {
            binding.btnRegister.setVisibility(View.VISIBLE);
            binding.progressBar.setVisibility(View.INVISIBLE);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        usernameChecker.cancel();
    }

    @Override
    public void onBackPressed() {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        super.onBackPressed();
    }
}
