package com.example.instagaram.utilities;

import android.app.Activity;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.credentials.CreateCredentialResponse;
import androidx.credentials.CreatePasswordRequest;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.exceptions.CreateCredentialException;

/**
 * Offers to save a new username + password in the user's password manager ("Save password?").
 *
 * The register and new-password fields are deliberately not autofillable (see NoAutofillEditText),
 * so the system no longer offers to save on its own; this asks for it explicitly instead.
 */
public final class CredentialSaver {

    private CredentialSaver() { }

    /**
     * Shows the save sheet, then runs {@code then} whatever the user chooses (save, dismiss,
     * or no password manager available).
     */
    public static void offerToSave(@NonNull Activity activity, @NonNull String username,
                                   @NonNull String password, @NonNull Runnable then) {
        CredentialManager.create(activity).createCredentialAsync(
                activity,
                new CreatePasswordRequest(username, password),
                null,
                ContextCompat.getMainExecutor(activity),
                new CredentialManagerCallback<CreateCredentialResponse, CreateCredentialException>() {
                    @Override
                    public void onResult(CreateCredentialResponse response) {
                        then.run();
                    }

                    @Override
                    public void onError(@NonNull CreateCredentialException e) {
                        // Declined, or nothing to save to: saving is optional, carry on.
                        then.run();
                    }
                });
    }
}
