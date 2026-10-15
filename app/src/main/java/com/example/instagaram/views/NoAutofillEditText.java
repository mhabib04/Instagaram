package com.example.instagaram.views;

import android.content.Context;
import android.os.Build;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.widget.AppCompatEditText;

/**
 * An input that can never be autofilled, for creating new credentials (register, new password).
 *
 * importantForAutofill="no" is not enough: since Android 14 the system can still send fields that
 * look like a login (username next to a password) to the password manager, which then opens its
 * "use saved password" sheet on tap. A view whose autofill type is NONE is skipped before any of
 * those rules, so no autofill request or fill dialog is ever made for it.
 */
public class NoAutofillEditText extends AppCompatEditText {

    public NoAutofillEditText(@NonNull Context context) {
        super(context);
    }

    public NoAutofillEditText(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public NoAutofillEditText(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    // Autofill (and this method) only exists from Android 8.0; older versions never call it.
    @RequiresApi(Build.VERSION_CODES.O)
    @Override
    public int getAutofillType() {
        return View.AUTOFILL_TYPE_NONE;
    }
}
