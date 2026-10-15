package com.example.instagaram.activities.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.instagaram.R;
import com.example.instagaram.activities.UserProfileActivity;
import com.example.instagaram.adapters.SearchUsersAdapter;
import com.example.instagaram.databinding.FragmentSearchBinding;
import com.example.instagaram.listeners.UserListener;
import com.example.instagaram.models.User;
import com.example.instagaram.utilities.Constants;
import com.example.instagaram.utilities.PreferenceManager;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

/** "Pencarian" tab: find accounts by username prefix and open their profile. */
public class SearchFragment extends Fragment implements UserListener {

    private static final long SEARCH_DELAY_MS = 300;
    private static final int MAX_RESULTS = 20;
    private static final int MIN_QUERY_LENGTH = 4;

    private FragmentSearchBinding binding;
    private PreferenceManager preferenceManager;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable pendingSearch;
    // Ignores responses from searches that were superseded by newer typing.
    private String latestQuery = "";

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentSearchBinding.inflate(inflater, container, false);
        preferenceManager = new PreferenceManager(requireContext());
        // Leaving the tab destroys this view; never restore the old query when coming back.
        binding.etSearch.setSaveEnabled(false);
        latestQuery = "";

        binding.etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) { }

            @Override
            public void afterTextChanged(Editable s) {
                scheduleSearch(s.toString());
            }
        });
        return binding.getRoot();
    }

    /** Debounces typing so Firestore is queried once the user pauses. */
    private void scheduleSearch(String rawQuery) {
        if (pendingSearch != null) handler.removeCallbacks(pendingSearch);
        String query = rawQuery.trim().replaceFirst("^@", "");
        latestQuery = query;
        if (query.isEmpty()) {
            showState(R.string.search_prompt);
            return;
        }
        // Only search once the query is specific enough (also saves Firestore reads); until then
        // the screen simply stays empty.
        if (query.length() < MIN_QUERY_LENGTH) {
            binding.listSearchUsers.setVisibility(View.GONE);
            binding.txtSearchState.setVisibility(View.GONE);
            return;
        }
        pendingSearch = () -> search(query);
        handler.postDelayed(pendingSearch, SEARCH_DELAY_MS);
    }

    private void search(String query) {
        String myId = preferenceManager.getString(Constants.KEY_USER_ID);
        // Prefix match: every username that starts with the query.
        FirebaseFirestore.getInstance().collection(Constants.KEY_COLLECTION_USERS)
                .orderBy(Constants.KEY_USERNAME)
                .startAt(query)
                .endAt(query + "")
                .limit(MAX_RESULTS)
                .get()
                .addOnCompleteListener(task -> {
                    if (binding == null || !query.equals(latestQuery)) return;
                    if (!task.isSuccessful() || task.getResult() == null) {
                        showState(R.string.error_connection);
                        return;
                    }
                    List<User> users = new ArrayList<>();
                    for (QueryDocumentSnapshot document : task.getResult()) {
                        if (document.getId().equals(myId)) continue;
                        User user = new User();
                        user.setId(document.getId());
                        user.setUsername(document.getString(Constants.KEY_USERNAME));
                        user.setName(document.getString(Constants.KEY_NAME));
                        user.setImage(document.getString(Constants.KEY_IMAGE));
                        users.add(user);
                    }
                    if (users.isEmpty()) {
                        showState(R.string.search_no_results);
                    } else {
                        binding.txtSearchState.setVisibility(View.GONE);
                        binding.listSearchUsers.setAdapter(new SearchUsersAdapter(users, this));
                        binding.listSearchUsers.setVisibility(View.VISIBLE);
                    }
                });
    }

    private void showState(int messageRes) {
        binding.listSearchUsers.setVisibility(View.GONE);
        binding.txtSearchState.setText(messageRes);
        binding.txtSearchState.setVisibility(View.VISIBLE);
    }

    @Override
    public void onUserClicked(User user) {
        Intent intent = new Intent(requireContext(), UserProfileActivity.class);
        intent.putExtra(Constants.KEY_PROFILE_USER_ID, user.getId());
        startActivity(intent);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (pendingSearch != null) handler.removeCallbacks(pendingSearch);
        binding = null;
    }
}
