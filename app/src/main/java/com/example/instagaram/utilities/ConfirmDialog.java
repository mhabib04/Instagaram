package com.example.instagaram.utilities;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.LayoutInflater;
import android.view.Window;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;

import com.example.instagaram.databinding.DialogConfirmBinding;

/** Rounded confirmation dialog with an icon, used for destructive actions. */
public final class ConfirmDialog {

    private ConfirmDialog() { }

    public static void show(@NonNull Context context, @DrawableRes int icon, @StringRes int title,
                            @StringRes int message, @StringRes int confirmText, @NonNull Runnable onConfirm) {
        DialogConfirmBinding binding = DialogConfirmBinding.inflate(LayoutInflater.from(context));
        binding.imgDialogIcon.setImageResource(icon);
        binding.txtDialogTitle.setText(title);
        binding.txtDialogMessage.setText(message);
        binding.btnDialogConfirm.setText(confirmText);

        AlertDialog dialog = new AlertDialog.Builder(context).setView(binding.getRoot()).create();
        binding.btnDialogCancel.setOnClickListener(v -> dialog.dismiss());
        binding.btnDialogConfirm.setOnClickListener(v -> {
            dialog.dismiss();
            onConfirm.run();
        });

        // Let bg_dialog's rounded corners show instead of the default square window.
        Window window = dialog.getWindow();
        if (window != null) window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        dialog.show();
    }
}
