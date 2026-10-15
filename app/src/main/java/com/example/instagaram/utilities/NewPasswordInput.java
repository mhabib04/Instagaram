package com.example.instagaram.utilities;

import android.os.Build;
import android.text.InputType;
import android.text.method.PasswordTransformationMethod;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;

import androidx.annotation.NonNull;

/**
 * Fields where a brand-new password is created (register, change password). Password managers
 * and keyboards (some with their own password vault) offer saved logins on anything typed as a
 * password, including "visible password" fields, and then treat the username above it as a login
 * too. So these are plain text fields that are only masked on screen: nothing marks them as a
 * password, the keyboard is told not to suggest or learn what is typed, and autofill is off.
 */
public final class NewPasswordInput {

    public static final int MIN_LENGTH = 8;

    private NewPasswordInput() { }

    public static void apply(@NonNull EditText... fields) {
        for (EditText field : fields) {
            field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            field.setImeOptions(field.getImeOptions() | EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);
            // setInputType resets the masking, so it has to come after.
            field.setTransformationMethod(PasswordTransformationMethod.getInstance());
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                field.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
            }
        }
    }

    /** True when something was typed but it is shorter than {@link #MIN_LENGTH}; blank is "not typed yet". */
    public static boolean isTooShort(@NonNull String password) {
        return !password.isEmpty() && password.length() < MIN_LENGTH;
    }
}
