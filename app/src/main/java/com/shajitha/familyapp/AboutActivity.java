package com.shajitha.familyapp;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

/** About FamilyApp: Shajis Creative Labs branding, version, purpose, privacy and credits. */
public class AboutActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);
        ((TextView) findViewById(R.id.aboutVersion)).setText(getString(R.string.about_version, BuildConfig.VERSION_NAME));

        // Developer contact details appear only when they have been filled in (strings.xml).
        String email = getString(R.string.contact_email_address).trim();
        String phone = getString(R.string.contact_phone_number).trim();
        StringBuilder details = new StringBuilder();
        if (!email.isEmpty()) {
            details.append(email);
        }
        if (!phone.isEmpty()) {
            details.append(details.length() > 0 ? "\n" : "").append(phone);
        }
        TextView contact = findViewById(R.id.aboutContactDetails);
        if (details.length() > 0) {
            contact.setText(details);
            contact.setVisibility(View.VISIBLE);
        }
        findViewById(R.id.aboutContact).setOnClickListener(v -> startActivity(new Intent(this, ContactUs.class)));
    }
}
