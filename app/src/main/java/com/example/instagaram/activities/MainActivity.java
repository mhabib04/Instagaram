package com.example.instagaram.activities;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.Toast;

import com.example.instagaram.R;
import com.example.instagaram.activities.fragments.ActivityFragment;
import com.example.instagaram.activities.fragments.HomeFragment;
import com.example.instagaram.activities.fragments.ProfileFragment;
import com.example.instagaram.activities.fragments.SearchFragment;
import com.example.instagaram.activities.fragments.UploadFragment;
import com.example.instagaram.utilities.Constants;
import com.example.instagaram.utilities.NotificationHelper;
import com.example.instagaram.utilities.PreferenceManager;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.example.instagaram.databinding.ActivityMainBinding;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.messaging.FirebaseMessaging;

public class MainActivity extends BaseActivity implements BottomNavigationView.OnNavigationItemSelectedListener{

    private ActivityMainBinding binding;
    private ProfileFragment profileFragment = new ProfileFragment();
    private UploadFragment uploadFragment = new UploadFragment();
    private HomeFragment homeFragment = new HomeFragment();
    private SearchFragment searchFragment = new SearchFragment();
    private ActivityFragment activityFragment = new ActivityFragment();
    private PreferenceManager preferenceManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        verifyAccountExists();
        getToken();
        NotificationHelper.ensureChannels(this);
        requestNotificationPermission();

        binding.bottomView.setOnNavigationItemSelectedListener(this);
        // Set fragment homeFragment sebagai default saat pertama kali dibuka
        getSupportFragmentManager().beginTransaction().replace(R.id.flFragment, homeFragment).commit();

        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        if (intent != null && "ProfileFragment".equals(intent.getStringExtra("navigate_to"))) {
            navigateToProfileFragment();
        }
    }

    private void navigateToProfileFragment() {
        openProfileTab();
    }

    /** Switches to the Profile tab via the bottom nav so the highlighted icon matches. */
    public void openProfileTab() {
        binding.bottomView.setSelectedItemId(R.id.navigation_profile);
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.navigation_home) {
            getSupportFragmentManager().beginTransaction().replace(R.id.flFragment, homeFragment).commit();
            return true;
        } else if (item.getItemId() == R.id.navigation_search) {
            getSupportFragmentManager().beginTransaction().replace(R.id.flFragment, searchFragment).commit();
            return true;
        } else if (item.getItemId() == R.id.navigation_upload) {
            getSupportFragmentManager().beginTransaction().replace(R.id.flFragment, uploadFragment).commit();
            return true;
        } else if (item.getItemId() == R.id.navigation_activity) {
            getSupportFragmentManager().beginTransaction().replace(R.id.flFragment, activityFragment).commit();
            return true;
        } else if (item.getItemId() == R.id.navigation_profile) {
            getSupportFragmentManager().beginTransaction().replace(R.id.flFragment, profileFragment).commit();
            return true;
        }
        return false;
    }

    private final ActivityResultLauncher<String> notificationPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> { });

    /** Android 13+ blocks notifications until the user allows them; ask once after login. */
    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS);
        }
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    /** If the stored account was deleted from the database, sign out instead of running as a ghost. */
    private void verifyAccountExists() {
        PreferenceManager prefs = new PreferenceManager(this);
        String userId = prefs.getString(Constants.KEY_USER_ID);
        if (userId == null) return;
        FirebaseFirestore.getInstance().collection(Constants.KEY_COLLECTION_USERS).document(userId).get()
                .addOnSuccessListener(user -> {
                    // Only a successful read that finds nothing counts; offline errors keep the session.
                    if (user.exists() || user.getMetadata().isFromCache()) return;
                    prefs.clear();
                    Intent intent = new Intent(this, LoginActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                });
    }

    private void getToken() {
        FirebaseMessaging.getInstance().getToken().addOnSuccessListener(this::updateToken);
    }

    private void updateToken(String token) {
        FirebaseFirestore database = FirebaseFirestore.getInstance();
        preferenceManager = new PreferenceManager(this);
        DocumentReference documentReference =
                database.collection(Constants.KEY_COLLECTION_USERS)
                        .document(preferenceManager.getString(Constants.KEY_USER_ID));
        documentReference.update(Constants.KEY_FCM_TOKEN, token)
                .addOnFailureListener(e -> showToast(getString(R.string.error_update_token)));
    }
}