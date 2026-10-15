package com.example.instagaram.utilities;

import android.os.Build;
import android.text.InputType;
import android.text.method.PasswordTransformationMethod;
import android.view.View;
import android.widget.EditText;

import androidx.annotation.NonNull;

/**
 * Fields where a brand-new password is created (register, change password). Keyboards and
 * password managers recognise "textPassword" fields and offer saved passwords there, which makes
 * no sense for a new one. A visible-password field that is masked by hand still shows dots but
 * is not treated as a login field.
 */
public final class NewPasswordInput {

    public static final int MIN_LENGTH = 8;

    private NewPasswordInput() { }

    public static void apply(@NonNull EditText... fields) {
        for (EditText field : fields) {
            field.setInputType(InputType.TYPE_CLASS_TEXT
                    | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                    | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
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
