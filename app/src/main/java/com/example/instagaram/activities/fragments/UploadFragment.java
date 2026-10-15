package com.example.instagaram.activities.fragments;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.example.instagaram.R;

import com.example.instagaram.activities.MainActivity;
import com.example.instagaram.databinding.FragmentUploadBinding;
import com.example.instagaram.utilities.Constants;
import com.example.instagaram.utilities.ImageCompressor;
import com.example.instagaram.utilities.PreferenceManager;
import com.example.instagaram.network.CloudinaryUploader;
import com.google.firebase.firestore.FirebaseFirestore;

import java.io.InputStream;
import java.util.Date;
import java.util.HashMap;


public class UploadFragment extends Fragment {

    private static final int REQUEST_GALLERY = 1;

    private Uri imageUri;
    private PreferenceManager preferenceManager;
    private FragmentUploadBinding binding;
    private String imageUrl;

    public UploadFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentUploadBinding.inflate(inflater, container, false);
        View view = binding.getRoot();

        preferenceManager = new PreferenceManager(requireContext());

        binding.btnUpload.setOnClickListener(v -> {
            if (imageUri == null) {
                showToast(getString(R.string.error_select_post_image));
                return;
            }
            upload();
        });

        binding.btnSelectImageUpload.setOnClickListener(v -> openGallery());
        binding.dropZone.setOnClickListener(v -> openGallery());

        return view;
    }

    private void showToast(String message) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
    }

    private void upload() {
        loading(true);
        CloudinaryUploader.uploadImage(requireContext(), imageUri, "instagaram/posts", ImageCompressor.POST_MAX_SIDE,
                new CloudinaryUploader.Callback() {
                    @Override
                    public void onSuccess(@NonNull String secureUrl) {
                        imageUrl = secureUrl;
                        uploadData();
                    }

                    @Override
                    public void onError(@NonNull String message) {
                        loading(false);
                        showToast(message);
                    }
                });
    }

    private void uploadData() {
        FirebaseFirestore database = FirebaseFirestore.getInstance();
        HashMap<String, Object> post = new HashMap<>();
        post.put(Constants.KEY_USER_ID, preferenceManager.getString(Constants.KEY_USER_ID));
        post.put(Constants.KEY_USERNAME, preferenceManager.getString(Constants.KEY_USERNAME));
        post.put(Constants.KEY_IMAGE, preferenceManager.getString(Constants.KEY_IMAGE));
        post.put(Constants.KEY_POSTS_CAPTION, binding.etUploadCaption.getText().toString());
        post.put(Constants.KEY_POSTS_IMAGE, imageUrl);
        post.put(Constants.KEY_TIMESTAMP, new Date());
        post.put(Constants.KEY_LIKE_COUNT, 0);

        database.collection(Constants.KEY_COLLECTION_POSTS)
                .add(post)
                .addOnSuccessListener(documentReference -> {
                    loading(false);
                    // This fragment instance is reused: start the next post from an empty form.
                    resetForm();
                    // Show the new post on the user's own profile.
                    if (getActivity() instanceof MainActivity) {
                        ((MainActivity) getActivity()).openProfileTab();
                    }
                })
                .addOnFailureListener(exception -> {
                    loading(false);
                    showToast(exception.getMessage());
                });
    }

    /** Back to the empty picker, so the shared photo and caption can't be posted twice. */
    private void resetForm() {
        imageUri = null;
        imageUrl = null;
        binding.etUploadCaption.setText(null);
        Glide.with(this).clear(binding.imgSelectImageUpload);
        binding.imgSelectImageUpload.setVisibility(View.GONE);
        binding.dropZone.setVisibility(View.VISIBLE);
    }

    private void openGallery() {
        Intent galleryIntent = new Intent();
        galleryIntent.setAction(Intent.ACTION_GET_CONTENT);
        galleryIntent.setType("image/*");
        startActivityForResult(galleryIntent, REQUEST_GALLERY);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == Activity.RESULT_OK && requestCode == REQUEST_GALLERY && data != null) {
            Uri picked = data.getData();
            // Real file size (InputStream.available() is only a buffer estimate). Large photos are
            // fine: CloudinaryUploader shrinks them before upload; this only rejects absurd files.
            long size = ImageCompressor.getSize(requireContext(), picked);
            if (size > ImageCompressor.MAX_INPUT_BYTES) {
                Toast.makeText(getContext(), R.string.error_image_too_large, Toast.LENGTH_SHORT).show();
                return;
            }
            imageUri = picked;
            // Swap the empty picker for a preview shaped like the post will be in the feed.
            binding.dropZone.setVisibility(View.GONE);
            binding.imgSelectImageUpload.setVisibility(View.VISIBLE);
            // Glide decodes at view size; setImageURI would decode a 50MP photo at full resolution.
            Glide.with(this).load(picked).into(binding.imgSelectImageUpload);
        }
    }


    private void loading(Boolean isLoading) {
        if (isLoading) {
            binding.btnUpload.setVisibility(View.INVISIBLE);
            binding.progressBar.setVisibility(View.VISIBLE);
        } else {
            binding.btnUpload.setVisibility(View.VISIBLE);
            binding.progressBar.setVisibility(View.INVISIBLE);
        }
    }

}
