package com.shajitha.familyapp;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Patterns;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

/**
 * Contact Us: contact details (when set), a message form that opens the phone's own email app,
 * and the credits from the original page. Nothing is sent by the app itself, and no paid service is used.
 */
public class ContactUs extends BaseActivity {

    private static final long SLIDE_INTERVAL_MS = 1500;
    static final String DRAFT_PREFS = "contact_draft";

    private final int[] imgArray1 = {R.drawable.a1, R.drawable.a2, R.drawable.a3, R.drawable.a4, R.drawable.a5,
            R.drawable.a6, R.drawable.a7, R.drawable.a8, R.drawable.a9, R.drawable.a10};
    private final int[] imgArray2 = {R.drawable.b1, R.drawable.b2, R.drawable.b3};

    private final Handler handler = new Handler(Looper.getMainLooper());
    private ImageView img1, img2;
    private int i = 0;
    private int j = 0;

    private TextInputEditText name, from, subject, message;
    private TextInputLayout nameLayout, fromLayout, subjectLayout, messageLayout;
    private TextView status;
    private SharedPreferences draft;

    private final Runnable slideshow = new Runnable() {
        @Override
        public void run() {
            img1.setImageResource(imgArray1[i]);
            i = (i + 1) % imgArray1.length;
            img2.setImageResource(imgArray2[j]);
            j = (j + 1) % imgArray2.length;
            handler.postDelayed(this, SLIDE_INTERVAL_MS);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_contact_us);
        img1 = findViewById(R.id.img1);
        img2 = findViewById(R.id.img2);

        // Contact details only appear when they have been filled in (strings.xml).
        String email = getString(R.string.contact_email_address).trim();
        String phone = getString(R.string.contact_phone_number).trim();
        if (!email.isEmpty()) {
            ((TextView) findViewById(R.id.contactEmail)).setText(email);
            View card = findViewById(R.id.contactEmailCard);
            card.setVisibility(View.VISIBLE);
            card.setOnClickListener(v -> openEmailApp(email, "", ""));
        }
        if (!phone.isEmpty()) {
            ((TextView) findViewById(R.id.contactPhone)).setText(phone);
            View card = findViewById(R.id.contactPhoneCard);
            card.setVisibility(View.VISIBLE);
            card.setOnClickListener(v -> dial(phone));
        }

        name = findViewById(R.id.contactName);
        from = findViewById(R.id.contactFrom);
        subject = findViewById(R.id.contactSubject);
        message = findViewById(R.id.contactMessage);
        nameLayout = findViewById(R.id.contactNameLayout);
        fromLayout = findViewById(R.id.contactFromLayout);
        subjectLayout = findViewById(R.id.contactSubjectLayout);
        messageLayout = findViewById(R.id.contactMessageLayout);
        status = findViewById(R.id.contactStatus);

        // The unsent message is kept, even if the app is closed.
        draft = getSharedPreferences(DRAFT_PREFS, Context.MODE_PRIVATE);
        restoreDraft();
        for (TextInputEditText field : new TextInputEditText[]{name, from, subject, message}) {
            field.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int a, int b, int c) {
                }

                @Override
                public void onTextChanged(CharSequence s, int a, int b, int c) {
                }

                @Override
                public void afterTextChanged(Editable s) {
                    saveDraft();
                }
            });
        }

        findViewById(R.id.contactSend).setOnClickListener(v -> send());
        findViewById(R.id.contactClear).setOnClickListener(v -> new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(R.string.contact_clear_title)
                .setMessage(R.string.contact_clear_message)
                .setPositiveButton(R.string.contact_clear, (d, w) -> clearForm())
                .setNegativeButton(R.string.cancel, null)
                .show());
    }

    private void restoreDraft() {
        name.setText(draft.getString("name", ""));
        from.setText(draft.getString("from", ""));
        subject.setText(draft.getString("subject", ""));
        message.setText(draft.getString("message", ""));
    }

    private void saveDraft() {
        draft.edit()
                .putString("name", Dialogs.text(name))
                .putString("from", Dialogs.text(from))
                .putString("subject", Dialogs.text(subject))
                .putString("message", Dialogs.text(message))
                .apply();
    }

    private void clearForm() {
        for (TextInputEditText field : new TextInputEditText[]{name, from, subject, message}) {
            field.setText("");
        }
        for (TextInputLayout layout : new TextInputLayout[]{nameLayout, fromLayout, subjectLayout, messageLayout}) {
            layout.setError(null);
        }
        status.setVisibility(View.GONE);
    }

    /** Checks every field; shows an error under each one that needs fixing. */
    boolean validate() {
        boolean ok = true;
        ok &= require(nameLayout, Dialogs.text(name).isEmpty() ? getString(R.string.error_required) : null);
        String email = Dialogs.text(from);
        ok &= require(fromLayout, email.isEmpty() ? getString(R.string.error_required)
                : !Patterns.EMAIL_ADDRESS.matcher(email).matches() ? getString(R.string.contact_email_invalid) : null);
        ok &= require(subjectLayout, Dialogs.text(subject).isEmpty() ? getString(R.string.error_required) : null);
        String body = Dialogs.text(message);
        ok &= require(messageLayout, body.isEmpty() ? getString(R.string.error_required)
                : body.length() < 10 ? getString(R.string.contact_message_short) : null);
        return ok;
    }

    private boolean require(TextInputLayout layout, String error) {
        layout.setError(error);
        return error == null;
    }

    private void send() {
        status.setVisibility(View.GONE);
        if (!validate()) {
            return;
        }
        String body = Dialogs.text(message) + "\n\n— " + Dialogs.text(name) + " (" + Dialogs.text(from) + ")";
        openEmailApp(getString(R.string.contact_email_address).trim(), Dialogs.text(subject), body);
    }

    /**
     * Opens the phone's email app with the message filled in. The app can't know whether the email
     * is actually sent, so it never claims it was.
     */
    private void openEmailApp(String to, String subjectText, String body) {
        Intent intent = new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + Uri.encode(to)));
        if (!to.isEmpty()) {
            intent.putExtra(Intent.EXTRA_EMAIL, new String[]{to});
        }
        intent.putExtra(Intent.EXTRA_SUBJECT, subjectText);
        intent.putExtra(Intent.EXTRA_TEXT, body);
        try {
            startActivity(intent);
            showStatus(R.string.contact_opened_email_app);
        } catch (ActivityNotFoundException e) {
            showStatus(R.string.contact_no_email_app);
        }
    }

    private void dial(String phone) {
        try {
            startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(phone))));
        } catch (ActivityNotFoundException e) {
            showStatus(R.string.contact_no_phone_app);
        }
    }

    private void showStatus(int text) {
        status.setText(text);
        status.setVisibility(View.VISIBLE);
    }

    // The slideshow only runs while the screen is visible, and stops when you leave it.
    @Override
    protected void onStart() {
        super.onStart();
        handler.postDelayed(slideshow, 500);
    }

    @Override
    protected void onStop() {
        super.onStop();
        handler.removeCallbacks(slideshow);
    }
}
