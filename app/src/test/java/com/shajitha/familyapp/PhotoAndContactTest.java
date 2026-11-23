package com.shajitha.familyapp;

import static com.shajitha.familyapp.ScreensTest.idle;
import static com.shajitha.familyapp.ScreensTest.waitFor;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.provider.MediaStore;
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
import org.robolectric.shadows.ShadowToast;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;

/** Capture Moments (camera, import, share, delete) and Contact Us (form and email app). */
@RunWith(AndroidJUnit4.class)
public class PhotoAndContactTest {

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

    private static byte[] jpeg() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Bitmap.createBitmap(40, 30, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.JPEG, 90, out);
        return out.toByteArray();
    }

    // ---------- Capture Moments ----------

    @Test
    public void cameraCancelThenTakePhotoThenShareAndDelete() {
        try (ActivityScenario<PhotoGallery> scenario = ActivityScenario.launch(PhotoGallery.class)) {
            scenario.onActivity(a -> {
                assertEquals(a.getString(R.string.photos_empty), text(a, R.id.photosCount));
                assertEquals(View.GONE, a.findViewById(R.id.photoActions).getVisibility());

                // Cancel the camera: nothing saved, no crash.
                a.findViewById(R.id.button1).performClick();
                Intent camera = shadowOf(a).getNextStartedActivityForResult().intent;
                assertEquals(MediaStore.ACTION_IMAGE_CAPTURE, camera.getAction());
                shadowOf(a).receiveResult(camera, Activity.RESULT_CANCELED, null);
                assertTrue(PhotoStore.list(a).isEmpty());

                // Take a photo: the camera app writes the full-size file.
                a.findViewById(R.id.button1).performClick();
                Intent camera2 = shadowOf(a).getNextStartedActivityForResult().intent;
                Uri output = camera2.getParcelableExtra(MediaStore.EXTRA_OUTPUT);
                File written = new File(PhotoStore.folder(a), output.getLastPathSegment());
                try (FileOutputStream out = new FileOutputStream(written)) {
                    out.write(jpeg());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                shadowOf(a).receiveResult(camera2, Activity.RESULT_OK, null);
                assertEquals("1 photo", text(a, R.id.photosCount));
                assertEquals(View.VISIBLE, a.findViewById(R.id.msg).getVisibility());
                assertEquals(View.VISIBLE, a.findViewById(R.id.photoActions).getVisibility());

                // Share uses the Android share sheet with the photo attached.
                a.findViewById(R.id.photoShare).performClick();
                Intent chooser = shadowOf(a).getNextStartedActivity();
                assertEquals(Intent.ACTION_CHOOSER, chooser.getAction());
                Intent send = chooser.getParcelableExtra(Intent.EXTRA_INTENT);
                assertEquals(Intent.ACTION_SEND, send.getAction());
                assertNotNull(send.getParcelableExtra(Intent.EXTRA_STREAM));

                // Delete asks first: Cancel keeps the photo.
                a.findViewById(R.id.photoDelete).performClick();
                idle();
                ((AlertDialog) ShadowDialog.getLatestDialog()).getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
                idle();
                assertEquals(1, PhotoStore.list(a).size());
            });
            scenario.recreate();
            scenario.onActivity(a -> assertEquals("kept after rotation", "1 photo", text(a, R.id.photosCount)));
        }
        // Reopen the app: the photo is still there; now delete it.
        try (ActivityScenario<PhotoGallery> scenario = ActivityScenario.launch(PhotoGallery.class)) {
            scenario.onActivity(a -> {
                assertEquals("1 photo", text(a, R.id.photosCount));
                a.findViewById(R.id.photoDelete).performClick();
                idle();
                ((AlertDialog) ShadowDialog.getLatestDialog()).getButton(AlertDialog.BUTTON_POSITIVE).performClick();
                idle();
                assertTrue(PhotoStore.list(a).isEmpty());
                assertEquals(View.GONE, a.findViewById(R.id.photoActions).getVisibility());
            });
        }
    }

    @Test
    public void importPhotosFromThePhone() {
        try (ActivityScenario<PhotoGallery> scenario = ActivityScenario.launch(PhotoGallery.class)) {
            scenario.onActivity(a -> {
                Uri first = Uri.parse("content://photos.test/1");
                Uri second = Uri.parse("content://photos.test/2");
                Uri broken = Uri.parse("content://photos.test/missing");
                shadowOf(a.getContentResolver()).registerInputStream(first, new ByteArrayInputStream(jpeg()));
                shadowOf(a.getContentResolver()).registerInputStream(second, new ByteArrayInputStream(jpeg()));

                a.findViewById(R.id.importPhotos).performClick();
                Intent picker = shadowOf(a).getNextStartedActivityForResult().intent;
                assertNotNull("photo picker opened", picker);

                Intent result = new Intent();
                ClipData clip = ClipData.newRawUri("photos", first);
                clip.addItem(new ClipData.Item(second));
                clip.addItem(new ClipData.Item(broken));
                result.setClipData(clip);
                shadowOf(a).receiveResult(picker, Activity.RESULT_OK, result);
                waitFor(() -> text(a, R.id.photosCount).equals("2 photos"));
                assertEquals(a.getString(R.string.photos_import_some_failed, 2, 1), ShadowToast.getTextOfLatestToast());
                assertEquals(View.VISIBLE, a.findViewById(R.id.photoActions).getVisibility());

                // Choosing nothing changes nothing.
                a.findViewById(R.id.importPhotos).performClick();
                Intent picker2 = shadowOf(a).getNextStartedActivityForResult().intent;
                shadowOf(a).receiveResult(picker2, Activity.RESULT_CANCELED, null);
                idle();
                assertEquals(2, PhotoStore.list(a).size());

                // Save to gallery: no crash, and a message only after the result is known.
                a.findViewById(R.id.photoExport).performClick();
                waitFor(() -> {
                    CharSequence t = ShadowToast.getTextOfLatestToast();
                    return t != null && (t.equals(a.getString(R.string.photos_exported))
                            || t.equals(a.getString(R.string.photos_export_failed)));
                });
            });
        }
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
