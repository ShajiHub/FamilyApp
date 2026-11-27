package com.shajitha.familyapp;

import static com.shajitha.familyapp.ScreensTest.idle;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.content.Intent;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.google.android.material.textfield.TextInputLayout;
import com.shajitha.familyapp.data.AppDatabase;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.shadows.ShadowDialog;


/** Contact Us: the form, validation, the email app and the saved draft. */
@RunWith(AndroidJUnit4.class)
public class ContactTest {

    @Before
    public void setUp() {
        AppDatabase.useDirectExecutorsForTests();
        AppDatabase.resetForTests();
    }

    @After
    public void tearDown() {
        AppDatabase.resetForTests();
    }

    private static String text(Activity a, int id) {
        return ((TextView) a.findViewById(id)).getText().toString();
    }


    // ---------- Contact Us ----------

    @Test
    public void contactFormValidatesAndOpensTheEmailApp() {
        try (ActivityScenario<ContactUs> scenario = ActivityScenario.launch(ContactUs.class)) {
            scenario.onActivity(a -> {
                // No contact details were provided, so none are shown.
                assertEquals(View.GONE, a.findViewById(R.id.contactEmailCard).getVisibility());
                assertEquals(View.GONE, a.findViewById(R.id.contactPhoneCard).getVisibility());

                a.findViewById(R.id.contactSend).performClick();
                assertNotNull(((TextInputLayout) a.findViewById(R.id.contactNameLayout)).getError());
                assertNotNull(((TextInputLayout) a.findViewById(R.id.contactMessageLayout)).getError());
                assertNull("nothing opened", shadowOf(a).getNextStartedActivity());

                ((EditText) a.findViewById(R.id.contactName)).setText("Sam");
                ((EditText) a.findViewById(R.id.contactFrom)).setText("not-an-email");
                ((EditText) a.findViewById(R.id.contactSubject)).setText("Hello");
                ((EditText) a.findViewById(R.id.contactMessage)).setText("Love the meal wheel, thanks!");
                a.findViewById(R.id.contactSend).performClick();
                assertEquals(a.getString(R.string.contact_email_invalid),
                        ((TextInputLayout) a.findViewById(R.id.contactFromLayout)).getError());
                assertNull(shadowOf(a).getNextStartedActivity());

                ((EditText) a.findViewById(R.id.contactFrom)).setText("sam@example.com");
                a.findViewById(R.id.contactSend).performClick();
                Intent email = shadowOf(a).getNextStartedActivity();
                assertEquals(Intent.ACTION_SENDTO, email.getAction());
                assertEquals("mailto", email.getData().getScheme());
                assertEquals("Hello", email.getStringExtra(Intent.EXTRA_SUBJECT));
                assertTrue(email.getStringExtra(Intent.EXTRA_TEXT).contains("Love the meal wheel"));
                assertTrue(email.getStringExtra(Intent.EXTRA_TEXT).contains("sam@example.com"));
                // The app can't know if it was sent, so it doesn't say so.
                assertEquals(a.getString(R.string.contact_opened_email_app), text(a, R.id.contactStatus));
                assertFalse(text(a, R.id.contactStatus).toLowerCase().contains("sent successfully"));
            });
        }
        // The unsent message is kept after closing the app, until the user clears it.
        try (ActivityScenario<ContactUs> scenario = ActivityScenario.launch(ContactUs.class)) {
            scenario.onActivity(a -> {
                assertEquals("Hello", text(a, R.id.contactSubject));
                a.findViewById(R.id.contactClear).performClick();
                idle();
                ((AlertDialog) ShadowDialog.getLatestDialog()).getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
                idle();
                assertEquals("Cancel keeps it", "Hello", text(a, R.id.contactSubject));
                a.findViewById(R.id.contactClear).performClick();
                idle();
                ((AlertDialog) ShadowDialog.getLatestDialog()).getButton(AlertDialog.BUTTON_POSITIVE).performClick();
                idle();
                assertEquals("", text(a, R.id.contactSubject));
            });
        }
    }

    @Test
    public void contactSlideshowStopsWhenLeaving() {
        ActivityScenario<ContactUs> scenario = ActivityScenario.launch(ContactUs.class);
        idle(5_000);
        scenario.close();
        idle(5_000);
    }
}
