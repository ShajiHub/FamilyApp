package com.shajitha.familyapp;

import android.content.Context;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

/** Dialogs shared by every screen, so deleting and editing always look and work the same. */
public final class Dialogs {

    private Dialogs() {
    }

    /** Asks before anything is permanently deleted. Nothing happens unless the user taps Delete. */
    public static AlertDialog confirmDelete(@NonNull Context context, @NonNull CharSequence title,
                                            @Nullable CharSequence message, @NonNull Runnable onDelete) {
        return new MaterialAlertDialogBuilder(context)
                .setTitle(title)
                .setMessage(message)
                .setIcon(R.drawable.ic_delete)
                .setPositiveButton(R.string.delete, (d, w) -> onDelete.run())
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /** Called with the trimmed text when the user saves. Return an error message to keep the dialog open. */
    public interface TextValidator {
        @Nullable
        String validate(@NonNull String text);
    }

    public interface OnText {
        void onSave(@NonNull String text);
    }

    /** One text field (add or rename something). Empty text is never saved. */
    public static AlertDialog editText(@NonNull Context context, @StringRes int title, @StringRes int hint,
                                       @Nullable String initial, @Nullable TextValidator validator,
                                       @NonNull OnText onSave) {
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_text_input, null);
        TextInputLayout layout = view.findViewById(R.id.dialogInputLayout);
        TextInputEditText input = view.findViewById(R.id.dialogInput);
        layout.setHint(hint);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        if (initial != null) {
            input.setText(initial);
            input.setSelection(initial.length());
        }
        AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                .setTitle(title)
                .setView(view)
                .setPositiveButton(R.string.save, null)
                .setNegativeButton(R.string.cancel, null)
                .create();
        dialog.setOnShowListener(d -> {
            Button save = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            save.setOnClickListener(v -> {
                String text = input.getText() == null ? "" : input.getText().toString().trim();
                String error = text.isEmpty() ? context.getString(R.string.error_required) : null;
                if (error == null && validator != null) {
                    error = validator.validate(text);
                }
                layout.setError(error);
                if (error == null) {
                    onSave.onSave(text);
                    dialog.dismiss();
                }
            });
            input.requestFocus();
        });
        showWithKeyboard(dialog);
        return dialog;
    }

    /** Shows a dialog with the keyboard open for its first field. */
    public static void showWithKeyboard(AlertDialog dialog) {
        if (dialog.getWindow() != null) {
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        }
        dialog.show();
    }

    /** The trimmed text of a field ("" when empty). */
    public static String text(TextInputEditText input) {
        return input.getText() == null ? "" : input.getText().toString().trim();
    }

    /** A whole number from a field, or the fallback when empty or invalid. */
    public static int number(TextInputEditText input, int fallback) {
        try {
            return Integer.parseInt(text(input));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
