package com.shajitha.familyapp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputLayout;
import com.shajitha.familyapp.data.AppDatabase;
import com.shajitha.familyapp.data.Book;
import com.shajitha.familyapp.data.ChecklistItem;
import com.shajitha.familyapp.data.DataSetup;
import com.shajitha.familyapp.data.FamilyDao;
import com.shajitha.familyapp.data.Food;
import com.shajitha.familyapp.data.TodoTask;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowDialog;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

/**
 * Opens every screen on a simulated phone (Robolectric), uses it like a person would, rotates it,
 * closes and reopens it, and checks that nothing crashes and nothing is lost.
 */
@RunWith(AndroidJUnit4.class)
public class ScreensTest {

    @Before
    public void setUp() {
        // The timer follows the simulated phone's clock, which the tests fast-forward.
        FamAppTimer.clock = android.os.SystemClock::elapsedRealtime;
        AppDatabase.useDirectExecutorsForTests();
        AppDatabase.resetForTests();
    }

    @After
    public void tearDown() {
        AppDatabase.resetForTests();
    }

    // ---------- helpers ----------

    static void idle(long millis) {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(millis));
    }

    static void idle() {
        shadowOf(Looper.getMainLooper()).idle();
    }

    /** Waits for lists that update in the background (RecyclerView ListAdapter). */
    static void waitFor(java.util.function.BooleanSupplier condition) {
        long end = System.currentTimeMillis() + 3000;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > end) {
                throw new AssertionError("timed out waiting");
            }
            idle();
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
        idle();
    }

    private static Context context() {
        return ApplicationProvider.getApplicationContext();
    }

    private static FamilyDao dao() {
        return AppDatabase.get(context()).dao();
    }

    private static String text(Activity a, int id) {
        return ((TextView) a.findViewById(id)).getText().toString();
    }

    private static AlertDialog dialog() {
        idle();
        AlertDialog dialog = (AlertDialog) ShadowDialog.getLatestDialog();
        assertNotNull("a dialog is shown", dialog);
        assertTrue(dialog.isShowing());
        return dialog;
    }

    /** Types into the first text field of the latest dialog and taps Save. */
    private static AlertDialog typeAndSave(String text) {
        AlertDialog d = dialog();
        ((EditText) d.findViewById(R.id.dialogInput)).setText(text);
        d.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        idle();
        return d;
    }

    private static void confirm(boolean yes) {
        AlertDialog d = dialog();
        d.getButton(yes ? AlertDialog.BUTTON_POSITIVE : AlertDialog.BUTTON_NEGATIVE).performClick();
        idle();
    }

    private static RecyclerView.ViewHolder row(RecyclerView rv, int position) {
        rv.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(20000, View.MeasureSpec.EXACTLY));
        rv.layout(0, 0, 1080, 20000);
        rv.scrollToPosition(position);
        idle();
        RecyclerView.ViewHolder holder = rv.findViewHolderForAdapterPosition(position);
        assertNotNull("row " + position, holder);
        return holder;
    }

    // ---------- Home ----------

    @Test
    public void homeTilesOpenTheirScreens() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                RecyclerView grid = activity.findViewById(R.id.homeGrid);
                assertEquals(MainActivity.TILES.size() + 1, grid.getAdapter().getItemCount());
                for (int i = 0; i < MainActivity.TILES.size(); i++) {
                    MainActivity.Tile tile = MainActivity.TILES.get(i);
                    RecyclerView.ViewHolder holder = row(grid, i + 1);
                    assertEquals(tile.id, holder.itemView.getId());
                    holder.itemView.performClick();
                    Intent next = shadowOf(activity).getNextStartedActivity();
                    assertEquals(tile.screen.getName(), next.getComponent().getClassName());
                }
            });
        }
    }

    // ---------- Timer ----------

    @Test
    public void timerPauseAndResetBeforeStartingDoNotCrash() {
        try (ActivityScenario<FamAppTimer> scenario = ActivityScenario.launch(FamAppTimer.class)) {
            scenario.onActivity(a -> {
                a.findViewById(R.id.resettimer).performClick();
                a.findViewById(R.id.startstoptimer).performClick(); // nothing chosen: shows a hint
                assertEquals("00:00:00", text(a, R.id.timer1));
                assertEquals(a.getString(R.string.timer_start), text(a, R.id.startstoptimer));
            });
        }
    }

    @Test
    public void timerStartPauseResumeResetAndRotation() {
        try (ActivityScenario<FamAppTimer> scenario = ActivityScenario.launch(FamAppTimer.class)) {
            scenario.onActivity(a -> {
                a.findViewById(R.id.getready).performClick();
                assertEquals("00:15:00", text(a, R.id.timer1));
                a.findViewById(R.id.startstoptimer).performClick();
                assertEquals(a.getString(R.string.timer_pause), text(a, R.id.startstoptimer));
                idle(2_100);
                assertEquals("00:14:58", text(a, R.id.timer1));
            });
            scenario.recreate(); // rotate the phone
            scenario.onActivity(a -> {
                idle(1_000);
                assertEquals("still counting after rotation", "00:14:57", text(a, R.id.timer1));
                a.findViewById(R.id.startstoptimer).performClick(); // pause
                idle(5_000);
                assertEquals("00:14:57", text(a, R.id.timer1));
                assertEquals(a.getString(R.string.timer_resume), text(a, R.id.startstoptimer));
                a.findViewById(R.id.startstoptimer).performClick(); // resume
                idle(1_000);
                assertEquals("00:14:56", text(a, R.id.timer1));
                a.findViewById(R.id.resettimer).performClick();
                assertEquals("00:00:00", text(a, R.id.timer1));
            });
        }
    }

    @Test
    public void timerKeepsRunningWhenTheAppIsClosedAndFinishesWhileClosed() {
        try (ActivityScenario<FamAppTimer> scenario = ActivityScenario.launch(FamAppTimer.class)) {
            scenario.onActivity(a -> {
                a.findViewById(R.id.cleanup).performClick();
                a.findViewById(R.id.startstoptimer).performClick();
            });
        }
        idle(60_000); // app closed for a minute
        try (ActivityScenario<FamAppTimer> scenario = ActivityScenario.launch(FamAppTimer.class)) {
            scenario.onActivity(a -> {
                idle(100);
                assertEquals("00:29:00", text(a, R.id.timer1));
                assertEquals(a.getString(R.string.timer_clean_up), text(a, R.id.timertext));
            });
        }
        idle(30 * 60_000); // closed until after it finished
        try (ActivityScenario<FamAppTimer> scenario = ActivityScenario.launch(FamAppTimer.class)) {
            scenario.onActivity(a -> {
                assertEquals(a.getString(R.string.timer_time_up), text(a, R.id.timertext));
                assertEquals("00:00:00", text(a, R.id.timer1));
            });
        }
    }

    @Test
    public void timerShowsTimeUp() {
        try (ActivityScenario<FamAppTimer> scenario = ActivityScenario.launch(FamAppTimer.class)) {
            scenario.onActivity(a -> {
                a.findViewById(R.id.cleanup).performClick();
                a.findViewById(R.id.startstoptimer).performClick();
                idle(31 * 60 * 1000);
                assertEquals(a.getString(R.string.timer_time_up), text(a, R.id.timertext));
                assertEquals("00:00:00", text(a, R.id.timer1));
            });
        }
    }

    @Test
    public void timerTimesCanBeEditedAndAreSaved() {
        try (ActivityScenario<FamAppTimer> scenario = ActivityScenario.launch(FamAppTimer.class)) {
            scenario.onActivity(a -> {
                a.findViewById(R.id.getready).performClick();
                a.findViewById(R.id.editTimes).performClick();
                AlertDialog d = dialog();
                ((EditText) d.findViewById(R.id.minutesGetReady)).setText("0"); // not allowed
                d.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
                assertTrue("invalid value keeps the dialog open", d.isShowing());
                ((EditText) d.findViewById(R.id.minutesGetReady)).setText("20");
                ((EditText) d.findViewById(R.id.minutesCleanUp)).setText("45");
                d.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
                idle();
                assertFalse(d.isShowing());
                assertEquals("00:20:00", text(a, R.id.timer1));
                assertEquals("Get Ready · 20 min", text(a, R.id.getready));
            });
        }
        try (ActivityScenario<FamAppTimer> scenario = ActivityScenario.launch(FamAppTimer.class)) {
            scenario.onActivity(a -> assertEquals("Clean Up · 45 min", text(a, R.id.cleanup)));
        }
    }

    // ---------- To-do ----------

    @Test
    public void todoAddEditTickDeleteAndKeep() {
        try (ActivityScenario<ToDoList> scenario = ActivityScenario.launch(ToDoList.class)) {
            scenario.onActivity(a -> {
                a.findViewById(R.id.addButton).performClick(); // blank: error, nothing added
                assertNotNull(((TextInputLayout) a.findViewById(R.id.itemsLayout)).getError());
                ((EditText) a.findViewById(R.id.items)).setText("Buy milk");
                a.findViewById(R.id.addButton).performClick();
                ((EditText) a.findViewById(R.id.items)).setText("Call grandma");
                a.findViewById(R.id.addButton).performClick();
                RecyclerView rv = a.findViewById(R.id.Listitems);
                waitFor(() -> rv.getAdapter().getItemCount() == 2);

                // Tick: stays on the list, marked done.
                row(rv, 0).itemView.performClick();
                waitFor(() -> text(a, R.id.todoSummary).equals("1 of 2 done"));
                assertEquals(2, rv.getAdapter().getItemCount());
                assertEquals("1 of 2 done", text(a, R.id.todoSummary));

                // Edit the remaining task (now first, since done tasks go last).
                waitFor(() -> !((CheckBox) row(rv, 0).itemView.findViewById(R.id.rowCheck)).isChecked());
                row(rv, 0).itemView.findViewById(R.id.rowEdit).performClick();
                typeAndSave("Call grandma tonight");
                assertEquals("Call grandma tonight", dao().tasksNow().get(0).title);

                // Delete: Cancel keeps it, Delete removes it.
                row(rv, 0).itemView.findViewById(R.id.rowDelete).performClick();
                confirm(false);
                assertEquals(2, dao().tasksNow().size());
                row(rv, 0).itemView.findViewById(R.id.rowDelete).performClick();
                confirm(true);
                assertEquals(1, dao().tasksNow().size());
            });
        }
        // Reopen the app: the done task is still there and still done.
        try (ActivityScenario<ToDoList> scenario = ActivityScenario.launch(ToDoList.class)) {
            scenario.onActivity(a -> {
                RecyclerView rv = a.findViewById(R.id.Listitems);
                waitFor(() -> rv.getAdapter().getItemCount() == 1);
                assertTrue(((CheckBox) row(rv, 0).itemView.findViewById(R.id.rowCheck)).isChecked());
                // Clear completed asks first.
                a.findViewById(R.id.clearDone).performClick();
                confirm(true);
                assertTrue(dao().tasksNow().isEmpty());
            });
        }
    }

    // ---------- Checklist ----------

    @Test
    public void checklistTicksAndItemsAreSaved() {
        try (ActivityScenario<CheckList> scenario = ActivityScenario.launch(CheckList.class)) {
            scenario.onActivity(a -> {
                idle();
                RecyclerView rv = a.findViewById(R.id.rView);
                // Row 0 is the Winter heading, row 1 "Winter boots".
                assertEquals("Winter", ((TextView) row(rv, 0).itemView.findViewById(R.id.sectionTitle)).getText());
                row(rv, 1).itemView.performClick();
                idle();
                assertTrue(((CheckBox) row(rv, 1).itemView.findViewById(R.id.rowCheck)).isChecked());
                assertEquals("1 of 7 packed", ((TextView) row(rv, 0).itemView.findViewById(R.id.sectionProgress)).getText());

                // Add an item to Winter.
                row(rv, 0).itemView.findViewById(R.id.sectionAdd).performClick();
                typeAndSave("Hand warmers");
                assertEquals("1 of 8 packed", ((TextView) row(rv, 0).itemView.findViewById(R.id.sectionProgress)).getText());

                // Rename and delete "Thermal wear" (row 2).
                row(rv, 2).itemView.findViewById(R.id.rowEdit).performClick();
                typeAndSave("Thermal underwear");
                row(rv, 2).itemView.findViewById(R.id.rowDelete).performClick();
                confirm(true);
                assertFalse(dao().checklistItemsNow().stream().anyMatch(i -> i.title.startsWith("Thermal")));
            });
        }
        try (ActivityScenario<CheckList> scenario = ActivityScenario.launch(CheckList.class)) {
            scenario.onActivity(a -> {
                idle();
                RecyclerView rv = a.findViewById(R.id.rView);
                assertTrue("tick kept after reopening",
                        ((CheckBox) row(rv, 1).itemView.findViewById(R.id.rowCheck)).isChecked());
                // Untick all asks first, and keeps the items.
                row(rv, 0).itemView.findViewById(R.id.sectionReset).performClick();
                confirm(true);
                assertEquals(0, dao().checklistItemsNow().stream().filter(i -> i.checked).count());
                assertEquals(17, dao().checklistItemsNow().size());

                // A new checklist of the family's own.
                int footer = rv.getAdapter().getItemCount() - 1;
                row(rv, footer).itemView.findViewById(R.id.newChecklist).performClick();
                typeAndSave("Camping");
                typeAndSave("Tent");
                assertTrue(dao().checklistItemsNow().stream().anyMatch(i -> i.section.equals("Camping") && i.title.equals("Tent")));
            });
        }
    }

    // ---------- Meal planner ----------

    @Test
    public void mealWheelLandsOnTheAnnouncedMealForAnyMenuSize() {
        try (ActivityScenario<MealPlanner> scenario = ActivityScenario.launch(MealPlanner.class)) {
            scenario.onActivity(a -> {
                idle();
                FoodWheelView wheel = a.findViewById(R.id.spinBoard);
                assertEquals("default menu", 8, wheel.getFoodCount());
                assertNull("opening the screen doesn't pick a meal", dao().mealOnNow(DataSetup.today()));
                for (int foodsOnMenu : new int[]{8, 9, 3, 2}) {
                    while (dao().foodsNow().size() > foodsOnMenu) {
                        List<Food> foods = dao().foodsNow();
                        dao().delete(foods.get(foods.size() - 1));
                    }
                    while (dao().foodsNow().size() < foodsOnMenu) {
                        Food f = new Food();
                        f.name = "Extra " + dao().foodsNow().size();
                        f.position = dao().nextFoodPosition();
                        dao().insert(f);
                    }
                    idle();
                    assertEquals(foodsOnMenu, wheel.getFoodCount());
                    for (int spin = 0; spin < 15; spin++) {
                        a.findViewById(R.id.spin).performClick();
                        assertFalse(a.findViewById(R.id.spin).isEnabled());
                        idle(3_500);
                        String saved = dao().mealOnNow(DataSetup.today()).food;
                        List<Food> foods = dao().foodsNow();
                        assertEquals("pointer is on the saved meal (" + foodsOnMenu + " foods)",
                                saved, foods.get(wheel.indexUnderPointer()).name);
                        assertEquals(a.getString(R.string.meal_result, saved), text(a, R.id.spintext));
                        assertTrue(a.findViewById(R.id.spin).isEnabled());
                    }
                }
            });
        }
    }

    @Test
    public void mealMenuCanBeEditedAndIsKept() {
        try (ActivityScenario<MealPlanner> scenario = ActivityScenario.launch(MealPlanner.class)) {
            scenario.onActivity(a -> {
                idle();
                LinearLayout list = a.findViewById(R.id.foodList);
                assertEquals(8, list.getChildCount());

                a.findViewById(R.id.addFood).performClick();
                AlertDialog d = typeAndSave("pizza"); // already on the menu
                assertTrue("duplicate is refused", d.isShowing());
                ((EditText) d.findViewById(R.id.dialogInput)).setText("Tacos");
                d.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
                idle();
                assertEquals(9, list.getChildCount());
                assertEquals(9, ((FoodWheelView) a.findViewById(R.id.spinBoard)).getFoodCount());

                // Choose today's lunch from the dropdown: the wheel turns to it.
                MaterialAutoCompleteTextView choice = a.findViewById(R.id.mealspinner1);
                ListAdapter adapter = choice.getAdapter();
                assertEquals(9, adapter.getCount());
                choice.setListSelection(6);
                choice.performCompletion();
                choice.getOnItemClickListener().onItemClick(null, null, 6, 6);
                idle();
                assertEquals("Sushi", dao().mealOnNow(DataSetup.today()).food);
                assertEquals(6, ((FoodWheelView) a.findViewById(R.id.spinBoard)).indexUnderPointer());

                // Rename Sushi: today's lunch follows.
                ((ViewGroup) list.getChildAt(6)).findViewById(R.id.rowEdit).performClick();
                typeAndSave("Sushi rolls");
                assertEquals("Sushi rolls", dao().mealOnNow(DataSetup.today()).food);
                assertEquals(a.getString(R.string.meal_result, "Sushi rolls"), text(a, R.id.spintext));

                // Delete asks first.
                list.getChildAt(0).findViewById(R.id.rowDelete).performClick();
                confirm(false);
                assertEquals(9, dao().foodsNow().size());
                list.getChildAt(0).findViewById(R.id.rowDelete).performClick();
                confirm(true);
                assertEquals(8, dao().foodsNow().size());
            });
        }
        try (ActivityScenario<MealPlanner> scenario = ActivityScenario.launch(MealPlanner.class)) {
            scenario.onActivity(a -> {
                idle();
                assertEquals("menu kept after reopening", 8, ((LinearLayout) a.findViewById(R.id.foodList)).getChildCount());
                assertEquals(a.getString(R.string.meal_result, "Sushi rolls"), text(a, R.id.spintext));
                // Down to one food: spinning is disabled with a hint.
                List<Food> foods = dao().foodsNow();
                for (int i = 1; i < foods.size(); i++) {
                    dao().delete(foods.get(i));
                }
                idle();
                assertFalse(a.findViewById(R.id.spin).isEnabled());
                assertEquals(a.getString(R.string.meal_need_more), text(a, R.id.spintext));
            });
        }
    }

    // ---------- Monthly schedule ----------

    @Test
    public void scheduleEventsCanBeAddedEditedDeletedAndAreKept() {
        try (ActivityScenario<viewCalendar> scenario = ActivityScenario.launch(viewCalendar.class)) {
            scenario.onActivity(a -> {
                idle();
                assertEquals(View.VISIBLE, a.findViewById(R.id.scheduleEmpty).getVisibility());
                a.findViewById(R.id.scheduleAdd).performClick();
                AlertDialog d = dialog();
                d.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
                assertTrue("title is required", d.isShowing());
                ((EditText) d.findViewById(R.id.eventTitleInput)).setText("Swimming class");
                ((EditText) d.findViewById(R.id.eventNotesInput)).setText("Bring goggles");
                d.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
                idle();
                LinearLayout list = a.findViewById(R.id.scheduleList);
                assertEquals(1, list.getChildCount());
                assertEquals("Bring goggles", ((TextView) list.getChildAt(0).findViewById(R.id.eventNotes)).getText());
                assertTrue(text(a, R.id.scheduleMonthCount).startsWith("1 event this month"));
            });
            scenario.recreate();
            scenario.onActivity(a -> {
                idle();
                LinearLayout list = a.findViewById(R.id.scheduleList);
                assertEquals(1, list.getChildCount());
                list.getChildAt(0).findViewById(R.id.eventEdit).performClick();
                AlertDialog d = dialog();
                ((EditText) d.findViewById(R.id.eventTitleInput)).setText("Swimming lesson");
                d.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
                idle();
                assertEquals("Swimming lesson", dao().allEventsNow().get(0).title);
                assertEquals("Bring goggles", dao().allEventsNow().get(0).notes);
            });
        }
        try (ActivityScenario<viewCalendar> scenario = ActivityScenario.launch(viewCalendar.class)) {
            scenario.onActivity(a -> {
                idle();
                LinearLayout list = a.findViewById(R.id.scheduleList);
                assertEquals("kept after reopening", 1, list.getChildCount());
                list.getChildAt(0).findViewById(R.id.eventDelete).performClick();
                confirm(true);
                assertTrue(dao().allEventsNow().isEmpty());
                assertEquals(View.VISIBLE, a.findViewById(R.id.scheduleEmpty).getVisibility());
            });
        }
    }

    // ---------- Events ----------

    @Test
    public void addEventOpensTheCalendarApp() {
        try (ActivityScenario<AddEvents> scenario = ActivityScenario.launch(AddEvents.class)) {
            scenario.onActivity(a -> {
                a.findViewById(R.id.button10).performClick();
                Intent event = shadowOf(a).getNextStartedActivity();
                assertEquals(Intent.ACTION_INSERT, event.getAction());
                assertFalse("events should not repeat", event.hasExtra("rrule"));
                a.findViewById(R.id.button11).performClick();
                assertEquals(viewCalendar.class.getName(),
                        shadowOf(a).getNextStartedActivity().getComponent().getClassName());
            });
        }
    }

    // ---------- Activities and reading ----------

    @Test
    public void activityIdeas() {
        try (ActivityScenario<ActivitiesFun> scenario = ActivityScenario.launch(ActivitiesFun.class)) {
            scenario.onActivity(a -> {
                a.findViewById(R.id.outdoorIdea).performClick();
                List<String> outdoor = Arrays.asList(a.getResources().getStringArray(R.array.outdoor_activities));
                String first = text(a, R.id.textView8);
                assertTrue(outdoor.contains(first));
                assertEquals(a.getString(R.string.activities_outdoor_label), text(a, R.id.ideaCategory));
                a.findViewById(R.id.outdoorIdea).performClick();
                assertNotEquals("no repeat", first, text(a, R.id.textView8));
                a.findViewById(R.id.readingLog).performClick();
                assertEquals(Reading.class.getName(),
                        shadowOf(a).getNextStartedActivity().getComponent().getClassName());
            });
        }
    }

    @Test
    public void readingLogTracksProgress() {
        try (ActivityScenario<Reading> scenario = ActivityScenario.launch(Reading.class)) {
            scenario.onActivity(a -> {
                a.findViewById(R.id.readingAdd).performClick();
                AlertDialog d = dialog();
                d.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
                assertTrue("title required", d.isShowing());
                ((EditText) d.findViewById(R.id.bookTitleInput)).setText("Matilda");
                ((EditText) d.findViewById(R.id.bookReadInput)).setText("300");
                ((EditText) d.findViewById(R.id.bookTotalInput)).setText("240");
                d.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
                assertTrue("can't read more pages than the book has", d.isShowing());
                ((EditText) d.findViewById(R.id.bookReadInput)).setText("40");
                d.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
                idle();

                RecyclerView rv = a.findViewById(R.id.readingList);
                waitFor(() -> rv.getAdapter().getItemCount() == 1);
                View card = row(rv, 0).itemView;
                assertEquals("40 of 240 pages · 16%", ((TextView) card.findViewById(R.id.bookStatus)).getText());
                card.findViewById(R.id.bookUpdate).performClick();
                typeAndSave("240");
                waitFor(() -> row(rv, 0).itemView.findViewById(R.id.bookFinished).getVisibility() == View.VISIBLE);
                card = row(rv, 0).itemView;
                assertEquals(View.VISIBLE, card.findViewById(R.id.bookFinished).getVisibility());
                assertEquals("1", ((TextView) a.findViewById(R.id.statFinished).findViewById(R.id.statValue)).getText());
            });
            scenario.recreate();
            scenario.onActivity(a -> {
                idle();
                assertEquals("Books: 1 · Pages read: 240", text(a, R.id.readingSummary));
                RecyclerView rv = a.findViewById(R.id.readingList);
                waitFor(() -> rv.getAdapter().getItemCount() == 1);
                row(rv, 0).itemView.findViewById(R.id.bookDelete).performClick();
                confirm(true);
                assertTrue(dao().booksNow().isEmpty());
            });
        }
    }

    // ---------- Capture moments ----------

    @Test
    public void capturePhotoScreenOpens() {
        try (ActivityScenario<PhotoGallery> scenario = ActivityScenario.launch(PhotoGallery.class)) {
            scenario.onActivity(a -> assertEquals(a.getString(R.string.photos_empty), text(a, R.id.photosCount)));
        }
    }

    // ---------- Every screen ----------

    static final Class<?>[] SCREENS = {MainActivity.class, AddEvents.class, viewCalendar.class, MealPlanner.class,
            FamAppTimer.class, ToDoList.class, CheckList.class, PhotoGallery.class,
            ActivitiesFun.class, Reading.class, ContactUs.class};

    @Test
    public void everyScreenSurvivesRotation() {
        for (Class<?> screen : SCREENS) {
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
        for (Class<?> screen : SCREENS) {
            @SuppressWarnings("unchecked")
            Class<Activity> cls = (Class<Activity>) screen;
            try (ActivityScenario<Activity> scenario = ActivityScenario.launch(cls)) {
                idle(500);
            }
        }
    }

    @Test
    @Config(qualifiers = "w320dp-h480dp")
    public void everyScreenOpensOnASmallPhone() {
        for (Class<?> screen : SCREENS) {
            @SuppressWarnings("unchecked")
            Class<Activity> cls = (Class<Activity>) screen;
            try (ActivityScenario<Activity> scenario = ActivityScenario.launch(cls)) {
                idle(500);
            }
        }
    }

    @Test
    @Config(qualifiers = "sw720dp-w1280dp-h800dp-land")
    public void everyScreenOpensOnATablet() {
        for (Class<?> screen : SCREENS) {
            @SuppressWarnings("unchecked")
            Class<Activity> cls = (Class<Activity>) screen;
            try (ActivityScenario<Activity> scenario = ActivityScenario.launch(cls)) {
                idle(500);
            }
        }
    }
}
