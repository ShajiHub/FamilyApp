package com.shajitha.familyapp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Environment;
import android.os.Looper;
import android.provider.MediaStore;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

import java.io.File;
import java.io.FileOutputStream;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Opens every screen on a simulated phone (Robolectric), taps its buttons and rotates it,
 * to check that nothing crashes and that saved data comes back.
 */
@RunWith(AndroidJUnit4.class)
public class ScreensTest {

    private static void idle(long millis) {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(millis));
    }

    private static Context context() {
        return ApplicationProvider.getApplicationContext();
    }

    // ---------- Home ----------

    @Test
    public void homeButtonsOpenTheirScreens() {
        int[] buttons = {R.id.button, R.id.button2, R.id.button3, R.id.button4, R.id.button5,
                R.id.button6, R.id.button7, R.id.button8, R.id.button9};
        Class<?>[] screens = {AddEvents.class, MealPlanner.class, ToDoList.class, CheckList.class,
                ActivitiesFun.class, viewCalendar.class, PhotoGallery.class, FamAppTimer.class, ContactUs.class};
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                for (int i = 0; i < buttons.length; i++) {
                    activity.findViewById(buttons[i]).performClick();
                    Intent next = shadowOf(activity).getNextStartedActivity();
                    assertEquals(screens[i].getName(), next.getComponent().getClassName());
                }
            });
        }
    }

    // ---------- Timer ----------

    @Test
    public void timerPauseAndResetBeforeStartingDoNotCrash() {
        try (ActivityScenario<FamAppTimer> scenario = ActivityScenario.launch(FamAppTimer.class)) {
            scenario.onActivity(activity -> {
                activity.findViewById(R.id.resettimer).performClick();
                activity.findViewById(R.id.startstoptimer).performClick(); // nothing chosen: shows a hint
                activity.findViewById(R.id.resettimer).performClick();
                assertEquals("00:00:00", ((TextView) activity.findViewById(R.id.timer1)).getText().toString());
            });
        }
    }

    @Test
    public void timerCountsDownPausesSurvivesRotationAndResets() {
        try (ActivityScenario<FamAppTimer> scenario = ActivityScenario.launch(FamAppTimer.class)) {
            scenario.onActivity(activity -> {
                activity.findViewById(R.id.getready).performClick();
                assertEquals("00:15:00", ((TextView) activity.findViewById(R.id.timer1)).getText().toString());
                activity.findViewById(R.id.startstoptimer).performClick();
                idle(2_000);
                assertEquals("00:14:58", ((TextView) activity.findViewById(R.id.timer1)).getText().toString());
            });

            scenario.recreate(); // rotate the phone
            scenario.onActivity(activity -> {
                idle(1_000);
                String shown = ((TextView) activity.findViewById(R.id.timer1)).getText().toString();
                assertEquals("still counting after rotation", "00:14:57", shown);
                activity.findViewById(R.id.startstoptimer).performClick(); // pause
                idle(5_000);
                assertEquals("00:14:57", ((TextView) activity.findViewById(R.id.timer1)).getText().toString());
                activity.findViewById(R.id.resettimer).performClick();
                assertEquals("00:00:00", ((TextView) activity.findViewById(R.id.timer1)).getText().toString());
            });
        }
    }

    @Test
    public void timerShowsTimeUp() {
        try (ActivityScenario<FamAppTimer> scenario = ActivityScenario.launch(FamAppTimer.class)) {
            scenario.onActivity(activity -> {
                activity.findViewById(R.id.cleanup).performClick();
                activity.findViewById(R.id.startstoptimer).performClick();
                idle(31 * 60 * 1000);
                assertEquals(activity.getString(R.string.timer_time_up),
                        ((TextView) activity.findViewById(R.id.timertext)).getText().toString());
                assertEquals("00:00:00", ((TextView) activity.findViewById(R.id.timer1)).getText().toString());
            });
        }
    }

    // ---------- To-do ----------

    @Test
    public void todoItemsAreSavedAndRemovedWhenTicked() {
        try (ActivityScenario<ToDoList> scenario = ActivityScenario.launch(ToDoList.class)) {
            scenario.onActivity(activity -> {
                ((EditText) activity.findViewById(R.id.items)).setText("Buy milk");
                activity.findViewById(R.id.addButton).performClick();
                ((EditText) activity.findViewById(R.id.items)).setText("   ");
                activity.findViewById(R.id.addButton).performClick(); // blank: ignored
                assertEquals(1, ((ListView) activity.findViewById(R.id.Listitems)).getCount());
            });

            scenario.recreate();
            scenario.onActivity(activity -> {
                ListView list = activity.findViewById(R.id.Listitems);
                assertEquals("item kept after rotation", 1, list.getCount());
                list.performItemClick(null, 0, 0);
                idle(1_100);
                assertEquals(0, list.getCount());
            });
        }
        assertTrue(FamilyStore.getStringList(context(), "todo_items").isEmpty());
    }

    // ---------- Checklist ----------

    @Test
    public void checklistTicksAreSaved() {
        try (ActivityScenario<CheckList> scenario = ActivityScenario.launch(CheckList.class)) {
            scenario.onActivity(activity -> {
                RecyclerView rv = activity.findViewById(R.id.rView);
                rv.measure(0, 0);
                rv.layout(0, 0, 1080, 4000);
                RecyclerView.ViewHolder holder = rv.findViewHolderForAdapterPosition(1); // "Winter boots"
                assertNotNull(holder);
                holder.itemView.performClick();
                assertTrue(((CheckBox) holder.itemView.findViewById(R.id.cbSelect)).isChecked());
            });
        }
        assertTrue(FamilyStore.getStringSet(context(), "checklist_ticked").contains("Winter Checklist|Winter boots"));

        try (ActivityScenario<CheckList> scenario = ActivityScenario.launch(CheckList.class)) {
            scenario.onActivity(activity -> {
                RecyclerView rv = activity.findViewById(R.id.rView);
                rv.measure(0, 0);
                rv.layout(0, 0, 1080, 4000);
                RecyclerView.ViewHolder boots = rv.findViewHolderForAdapterPosition(1);
                RecyclerView.ViewHolder thermal = rv.findViewHolderForAdapterPosition(2);
                assertTrue(((CheckBox) boots.itemView.findViewById(R.id.cbSelect)).isChecked());
                assertFalse(((CheckBox) thermal.itemView.findViewById(R.id.cbSelect)).isChecked());
            });
        }
    }

    // ---------- Meal planner ----------

    /** Slice borders on foodspinner.png, degrees clockwise from the top; slice i runs from BORDERS[i]. */
    private static final float[] BORDERS = {331.3f, 15.7f, 62.4f, 106.3f, 150.7f, 194.7f, 241.1f, 285.9f};

    private static int sliceUnderPointer(float rotation) {
        float angle = ((-rotation) % 360 + 360) % 360; // part of the image now at the top
        for (int i = 0; i < BORDERS.length; i++) {
            float from = BORDERS[i];
            float to = BORDERS[(i + 1) % BORDERS.length];
            boolean inside = from < to ? (angle >= from && angle < to) : (angle >= from || angle < to);
            if (inside) {
                return i;
            }
        }
        return -1;
    }

    @Test
    public void mealWheelShowsTheMealUnderThePointer() {
        try (ActivityScenario<MealPlanner> scenario = ActivityScenario.launch(MealPlanner.class)) {
            scenario.onActivity(activity -> {
                idle(100);
                assertTrue("opening the screen must not pick a meal by itself",
                        FamilyStore.getString(activity, "meal_today") == null);
                String[] meals = activity.getResources().getStringArray(R.array.foodmenu);
                for (int spin = 0; spin < 25; spin++) {
                    activity.findViewById(R.id.spin).performClick();
                    assertFalse(activity.findViewById(R.id.spin).isEnabled());
                    idle(3_500);
                    ImageView wheel = activity.findViewById(R.id.spinBoard);
                    int chosen = ((Spinner) activity.findViewById(R.id.mealspinner1)).getSelectedItemPosition();
                    assertEquals("pointer lands on the announced meal", chosen, sliceUnderPointer(wheel.getRotation()));
                    assertEquals(activity.getString(R.string.meal_result, meals[chosen]),
                            ((TextView) activity.findViewById(R.id.spintext)).getText().toString());
                    assertTrue(activity.findViewById(R.id.spin).isEnabled());
                }
            });
            scenario.recreate();
            scenario.onActivity(activity -> {
                idle(100);
                assertTrue(((TextView) activity.findViewById(R.id.spintext)).getText().toString()
                        .startsWith("Today's lunch:"));
            });
        }
    }

    @Test
    public void choosingFromTheMenuSetsLunch() {
        try (ActivityScenario<MealPlanner> scenario = ActivityScenario.launch(MealPlanner.class)) {
            scenario.onActivity(activity -> {
                idle(100);
                Spinner menu = activity.findViewById(R.id.mealspinner1);
                menu.setSelection(6); // Sushi
                idle(100);
                assertEquals(activity.getString(R.string.meal_result, "Sushi"),
                        ((TextView) activity.findViewById(R.id.spintext)).getText().toString());
                assertEquals(6, sliceUnderPointer(activity.findViewById(R.id.spinBoard).getRotation()));
            });
        }
    }

    // ---------- Monthly schedule ----------

    @Test
    public void schedulePlansAreSavedPerDay() {
        try (ActivityScenario<viewCalendar> scenario = ActivityScenario.launch(viewCalendar.class)) {
            scenario.onActivity(activity -> {
                ((EditText) activity.findViewById(R.id.scheduleInput)).setText("Swimming class 5pm");
                activity.findViewById(R.id.scheduleAdd).performClick();
                assertEquals(1, ((ListView) activity.findViewById(R.id.scheduleList)).getCount());
                assertEquals("1 plan this month",
                        ((TextView) activity.findViewById(R.id.scheduleMonthCount)).getText().toString());
            });
            scenario.recreate();
            scenario.onActivity(activity ->
                    assertEquals(1, ((ListView) activity.findViewById(R.id.scheduleList)).getCount()));
        }
    }

    // ---------- Events ----------

    @Test
    public void addEventOpensTheCalendarApp() {
        try (ActivityScenario<AddEvents> scenario = ActivityScenario.launch(AddEvents.class)) {
            scenario.onActivity(activity -> {
                activity.findViewById(R.id.button10).performClick();
                Intent event = shadowOf(activity).getNextStartedActivity();
                assertEquals(Intent.ACTION_INSERT, event.getAction());
                assertFalse("events should not repeat", event.hasExtra("rrule"));
                activity.findViewById(R.id.button11).performClick();
                assertEquals(viewCalendar.class.getName(),
                        shadowOf(activity).getNextStartedActivity().getComponent().getClassName());
            });
        }
    }

    // ---------- Capture moments ----------

    @Test
    public void cancellingTheCameraDoesNotCrashAndTakenPhotosAreKept() throws Exception {
        try (ActivityScenario<PhotoGallery> scenario = ActivityScenario.launch(PhotoGallery.class)) {
            scenario.onActivity(activity -> {
                TextView count = activity.findViewById(R.id.photosCount);
                assertEquals(activity.getString(R.string.photos_empty), count.getText().toString());

                // Cancel the camera.
                activity.findViewById(R.id.button1).performClick();
                Intent camera = shadowOf(activity).getNextStartedActivityForResult().intent;
                assertEquals(MediaStore.ACTION_IMAGE_CAPTURE, camera.getAction());
                shadowOf(activity).receiveResult(camera, Activity.RESULT_CANCELED, null);
                assertEquals(0, photoFiles(activity).size());

                // Take a photo: pretend the camera app wrote the file.
                activity.findViewById(R.id.button1).performClick();
                Intent camera2 = shadowOf(activity).getNextStartedActivityForResult().intent;
                android.net.Uri output = camera2.getParcelableExtra(MediaStore.EXTRA_OUTPUT);
                File written = new File(activity.getExternalFilesDir(Environment.DIRECTORY_PICTURES),
                        output.getLastPathSegment());
                writeJpeg(written);
                shadowOf(activity).receiveResult(camera2, Activity.RESULT_OK, null);
                assertEquals("1 photo", count.getText().toString());
                assertEquals(android.view.View.VISIBLE, activity.findViewById(R.id.msg).getVisibility());
            });
            scenario.recreate();
            scenario.onActivity(activity -> assertEquals("1 photo",
                    ((TextView) activity.findViewById(R.id.photosCount)).getText().toString()));
        }
    }

    private static List<File> photoFiles(Activity activity) {
        File[] files = activity.getExternalFilesDir(Environment.DIRECTORY_PICTURES).listFiles();
        return files == null ? List.of() : Arrays.asList(files);
    }

    private static void writeJpeg(File file) {
        try (FileOutputStream out = new FileOutputStream(file)) {
            Bitmap.createBitmap(40, 30, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.JPEG, 90, out);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ---------- Activities and reading ----------

    @Test
    public void activityIdeasAndReadingLog() {
        try (ActivityScenario<ActivitiesFun> scenario = ActivityScenario.launch(ActivitiesFun.class)) {
            scenario.onActivity(activity -> {
                TextView idea = activity.findViewById(R.id.textView8);
                activity.findViewById(R.id.outdoorIdea).performClick();
                List<String> outdoor = Arrays.asList(activity.getResources().getStringArray(R.array.outdoor_activities));
                assertTrue(outdoor.contains(idea.getText().toString()));
                String first = idea.getText().toString();
                activity.findViewById(R.id.outdoorIdea).performClick();
                assertNotEquals("no repeat", first, idea.getText().toString());
                activity.findViewById(R.id.readingLog).performClick();
                assertEquals(Reading.class.getName(),
                        shadowOf(activity).getNextStartedActivity().getComponent().getClassName());
            });
        }

        AtomicReference<String> summary = new AtomicReference<>();
        try (ActivityScenario<Reading> scenario = ActivityScenario.launch(Reading.class)) {
            scenario.onActivity(activity -> {
                ((EditText) activity.findViewById(R.id.readingBook)).setText("Matilda");
                ((EditText) activity.findViewById(R.id.readingPages)).setText("240");
                activity.findViewById(R.id.readingAdd).performClick();
                ((EditText) activity.findViewById(R.id.readingBook)).setText("The Gruffalo");
                activity.findViewById(R.id.readingAdd).performClick(); // no pages: still added
            });
            scenario.recreate();
            scenario.onActivity(activity -> {
                summary.set(((TextView) activity.findViewById(R.id.readingSummary)).getText().toString());
                assertEquals(2, ((ListView) activity.findViewById(R.id.readingList)).getCount());
            });
        }
        assertEquals("Books: 2 · Pages read: 240", summary.get());
    }

    // ---------- Contact us ----------

    @Test
    public void contactSlideshowStopsWhenLeaving() {
        ActivityScenario<ContactUs> scenario = ActivityScenario.launch(ContactUs.class);
        idle(5_000);
        scenario.close();
        idle(5_000); // would crash or keep running if the slideshow were not stopped
        assertTrue(shadowOf(Looper.getMainLooper()).isIdle());
    }

    @Test
    public void everyScreenSurvivesRotation() {
        Class<?>[] screens = {MainActivity.class, AddEvents.class, viewCalendar.class, MealPlanner.class,
                FamAppTimer.class, ToDoList.class, CheckList.class, PhotoGallery.class,
                ActivitiesFun.class, Reading.class, ContactUs.class};
        for (Class<?> screen : screens) {
            @SuppressWarnings("unchecked")
            Class<Activity> cls = (Class<Activity>) screen;
            try (ActivityScenario<Activity> scenario = ActivityScenario.launch(cls)) {
                scenario.recreate();
                idle(500);
            }
        }
    }

    @Test
    @Config(qualifiers = "land")
    public void everyScreenOpensInLandscape() {
        Class<?>[] screens = {MainActivity.class, AddEvents.class, viewCalendar.class, MealPlanner.class,
                FamAppTimer.class, ToDoList.class, CheckList.class, PhotoGallery.class,
                ActivitiesFun.class, Reading.class, ContactUs.class};
        for (Class<?> screen : screens) {
            @SuppressWarnings("unchecked")
            Class<Activity> cls = (Class<Activity>) screen;
            try (ActivityScenario<Activity> scenario = ActivityScenario.launch(cls)) {
                idle(500);
            }
        }
        // Timer in landscape has no "Start Timer" label: starting and pausing must still work.
        try (ActivityScenario<FamAppTimer> scenario = ActivityScenario.launch(FamAppTimer.class)) {
            scenario.onActivity(activity -> {
                activity.findViewById(R.id.watchmedia).performClick();
                activity.findViewById(R.id.startstoptimer).performClick();
                idle(1_000);
                assertEquals("00:59:59", ((TextView) activity.findViewById(R.id.timer1)).getText().toString());
            });
        }
    }
}
