package com.shajitha.familyapp;

import static com.shajitha.familyapp.ScreensTest.idle;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.work.Configuration;
import androidx.work.testing.SynchronousExecutor;
import androidx.work.testing.WorkManagerTestInitHelper;

import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.shajitha.familyapp.data.AppDatabase;
import com.shajitha.familyapp.data.DataSetup;
import com.shajitha.familyapp.data.FamilyDao;
import com.shajitha.familyapp.data.FamilyMember;
import com.shajitha.familyapp.data.Recurrence;
import com.shajitha.familyapp.data.Reminder;
import com.shajitha.familyapp.reminders.Reminders;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.shadows.ShadowDialog;

/** Chores, Healthy Habits and the Home dashboard lines for them. */
@RunWith(AndroidJUnit4.class)
public class ChoresHabitsTest {

    private Context context;
    private FamilyDao dao;
    private String today, tomorrow;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        AppDatabase.useDirectExecutorsForTests();
        AppDatabase.resetForTests();
        dao = AppDatabase.get(context).dao();
        WorkManagerTestInitHelper.initializeTestWorkManager(context, new Configuration.Builder()
                .setMinimumLoggingLevel(Log.ERROR).setExecutor(new SynchronousExecutor()).build());
        today = DataSetup.today();
        tomorrow = Recurrence.Day.parse(today).plusDays(1).toString();
    }

    @After
    public void tearDown() {
        AppDatabase.resetForTests();
    }

    private long chore(String title, String repeat, Long memberId) {
        Reminder r = Reminders.create(title, Reminder.TYPE_CHORE, today, memberId);
        r.repeatUnit = repeat;
        r.alertDays = "";
        return Reminders.save(context, r);
    }

    private static View row(RecyclerView rv, int position) {
        rv.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(30000, View.MeasureSpec.EXACTLY));
        rv.layout(0, 0, 1080, 30000);
        idle();
        RecyclerView.ViewHolder h = rv.findViewHolderForAdapterPosition(position);
        assertNotNull("row " + position, h);
        return h.itemView;
    }

    private static AlertDialog dialog() {
        idle();
        return (AlertDialog) ShadowDialog.getLatestDialog();
    }

    // ---------- Chores ----------

    @Test
    public void choreEngineKeepsHistoryAndRepeats() {
        long weekly = chore("Vacuum", Recurrence.WEEK, null);
        Reminders.complete(context, weekly);
        assertEquals(Recurrence.Day.parse(today).plusDays(7).toString(), dao.reminderNow(weekly).dueDate);
        assertEquals(1, dao.historyNow(weekly).size());

        long once = chore("Clean the garage", Recurrence.NONE, null);
        Reminders.postponeOccurrence(context, once, tomorrow);
        assertEquals(tomorrow, dao.reminderNow(once).dueDate);
        assertTrue(dao.historyNow(once).isEmpty());
        Reminders.complete(context, once);
        assertEquals(Reminder.STATUS_COMPLETED, dao.reminderNow(once).status);

        Reminders.delete(context, weekly);
        assertNull(dao.reminderNow(weekly));
        assertEquals("history kept", 2, dao.allHistoryNow().size());
    }

    @Test
    public void choresScreen() {
        FamilyMember mia = new FamilyMember();
        mia.name = "Mia";
        mia.avatar = "👧";
        long miaId = dao.insert(mia);
        chore("Water the plants", Recurrence.WEEK, null);
        try (ActivityScenario<ChoresActivity> s = ActivityScenario.launch(ChoresActivity.class)) {
            s.onActivity(a -> {
                RecyclerView rv = a.findViewById(R.id.screenList);
                View header = row(rv, 0);
                // A suggestion fills in the dialog.
                ChipGroup ideas = header.findViewById(R.id.routineSuggestions);
                ideas.getChildAt(0).performClick(); // "Make the bed"
                AlertDialog d = dialog();
                assertEquals("Make the bed", ((EditText) d.findViewById(R.id.routineTitleInput)).getText().toString());
                MaterialAutoCompleteTextView who = d.findViewById(R.id.routineMember);
                who.getOnItemClickListener().onItemClick(null, null, 1, 1); // Mia
                d.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
                idle();
                assertEquals(2, dao.remindersOfTypeNow(Reminder.TYPE_CHORE).size());

                // Rows: header, "Today", the two chores.
                assertEquals(a.getString(R.string.chores_today), ((TextView) row(rv, 1)).getText());
                View bed = null;
                for (int i = 2; i <= 3; i++) {
                    View r = row(rv, i);
                    if ("Make the bed".contentEquals(((TextView) r.findViewById(R.id.choreTitle)).getText())) {
                        bed = r;
                    }
                }
                assertNotNull(bed);
                assertTrue(((TextView) bed.findViewById(R.id.choreDetails)).getText().toString().startsWith("👧 Mia · Every week"));

                // Tick it: a star for Mia, and the weekly chore moves to next week.
                ((CheckBox) bed.findViewById(R.id.choreDone)).setChecked(true);
                idle();
                assertEquals("⭐ This week: 👧 Mia 1", ((TextView) row(rv, 0).findViewById(R.id.routineStars)).getText().toString());

                // Filter by Mia: only her chores.
                ChipGroup filters = row(rv, 0).findViewById(R.id.routineFilter);
                filters.getChildAt(1).performClick();
                idle();
                for (int i = 1; i < rv.getAdapter().getItemCount(); i++) {
                    View r = row(rv, i);
                    TextView title = r.findViewById(R.id.choreTitle);
                    if (title != null) {
                        assertEquals("Make the bed", title.getText().toString());
                    }
                }

                // Delete asks first.
                filters = row(rv, 0).findViewById(R.id.routineFilter);
                filters.getChildAt(0).performClick();
                idle();
                View first = null;
                for (int i = 1; i < rv.getAdapter().getItemCount() && first == null; i++) {
                    if (row(rv, i).findViewById(R.id.choreDelete) != null) {
                        first = row(rv, i);
                    }
                }
                first.findViewById(R.id.choreDelete).performClick();
                dialog().getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
                idle();
                assertEquals(2, dao.remindersOfTypeNow(Reminder.TYPE_CHORE).size());
            });
        }
        assertEquals(Long.valueOf(miaId), dao.allHistoryNow().get(0).memberId);
    }

    // ---------- Habits ----------

    @Test
    public void habitCheckInsAndUndo() {
        Reminder r = Reminders.create("Drink water", Reminder.TYPE_HABIT, today, null);
        r.repeatUnit = Recurrence.DAY;
        r.alertDays = "0";
        r.alertTime = "18:00";
        long id = Reminders.save(context, r);
        Reminders.checkIn(context, id, today);
        Reminders.checkIn(context, id, today); // once per day
        assertEquals(1, dao.historyNow(id).size());
        assertEquals("next reminder tomorrow", tomorrow, dao.reminderNow(id).dueDate);
        Reminders.undoCheckIn(context, id, today);
        assertTrue(dao.historyNow(id).isEmpty());
        assertEquals(today, dao.reminderNow(id).dueDate);
    }

    @Test
    public void habitsScreenShowsTheWeek() {
        try (ActivityScenario<HabitsActivity> s = ActivityScenario.launch(HabitsActivity.class)) {
            s.onActivity(a -> {
                RecyclerView rv = a.findViewById(R.id.screenList);
                ((ChipGroup) row(rv, 0).findViewById(R.id.routineSuggestions)).getChildAt(2).performClick(); // Play outside
                AlertDialog d = dialog();
                assertEquals(View.GONE, d.findViewById(R.id.routineRepeatLayout).getVisibility());
                d.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
                idle();
                Reminder habit = dao.remindersOfTypeNow(Reminder.TYPE_HABIT).get(0);
                assertEquals(Recurrence.DAY, habit.repeatUnit);
                View card = row(rv, 1);
                assertEquals(7, ((ViewGroup) card.findViewById(R.id.habitWeek)).getChildCount());
                assertEquals("0 of the last 7 days", ((TextView) card.findViewById(R.id.habitProgress)).getText().toString());
                ((CheckBox) card.findViewById(R.id.habitToday)).setChecked(true);
                idle();
                assertEquals("1 of the last 7 days", ((TextView) row(rv, 1).findViewById(R.id.habitProgress)).getText().toString());
                assertEquals("🌱 Today: 1 of 1 habits done", ((TextView) row(rv, 0).findViewById(R.id.routineStars)).getText().toString());
            });
        }
    }

    // ---------- Home dashboard ----------

    @Test
    public void homeShowsChoresAndHabits() {
        chore("Feed the pet", Recurrence.DAY, null);
        long late = chore("Tidy toys", Recurrence.NONE, null);
        Reminders.postponeOccurrence(context, late, Recurrence.Day.parse(today).plusDays(-2).toString());
        Reminder h = Reminders.create("Read for 20 minutes", Reminder.TYPE_HABIT, today, null);
        h.repeatUnit = Recurrence.DAY;
        h.alertDays = "";
        long habit = Reminders.save(context, h);
        Reminder h2 = Reminders.create("Drink water", Reminder.TYPE_HABIT, today, null);
        h2.repeatUnit = Recurrence.DAY;
        h2.alertDays = "";
        Reminders.save(context, h2);
        Reminders.checkIn(context, habit, today);
        try (ActivityScenario<MainActivity> s = ActivityScenario.launch(MainActivity.class)) {
            s.onActivity(a -> {
                idle();
                assertEquals("2 chores to do (1 waiting from before)",
                        ((TextView) a.findViewById(R.id.today_chores).findViewById(R.id.todayText)).getText().toString());
                assertEquals("1 of 2 habits done today",
                        ((TextView) a.findViewById(R.id.today_habits).findViewById(R.id.todayText)).getText().toString());
            });
        }
    }

    @Test
    public void homeHidesChoresAndHabitsUntilUsed() {
        try (ActivityScenario<MainActivity> s = ActivityScenario.launch(MainActivity.class)) {
            s.onActivity(a -> {
                idle();
                assertNull(a.findViewById(R.id.today_chores));
                assertNull(a.findViewById(R.id.today_habits));
                assertFalse(a.isFinishing());
            });
        }
    }
}
