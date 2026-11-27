package com.shajitha.familyapp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.Manifest;
import android.app.Application;
import android.app.DatePickerDialog;
import android.content.Context;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.work.Configuration;
import androidx.work.testing.SynchronousExecutor;
import androidx.work.testing.WorkManagerTestInitHelper;

import com.shajitha.familyapp.data.AppDatabase;
import com.shajitha.familyapp.data.DataSetup;
import com.shajitha.familyapp.data.FamilyDao;
import com.shajitha.familyapp.data.Recurrence;
import com.shajitha.familyapp.data.Reminder;
import com.shajitha.familyapp.reminders.Notifications;
import com.shajitha.familyapp.reminders.ReminderSettings;
import com.shajitha.familyapp.reminders.ReminderTemplates;
import com.shajitha.familyapp.reminders.Reminders;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.shadows.ShadowDialog;
import org.robolectric.shadows.ShadowPopupMenu;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;

/** Reminders &amp; Renewals: templates, the one form, gentle actions, privacy, Home and Schedule. */
@RunWith(AndroidJUnit4.class)
public class RemindersTest {

    private Context context;
    private FamilyDao dao;
    private String today;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        AppDatabase.useDirectExecutorsForTests();
        AppDatabase.resetForTests();
        dao = AppDatabase.get(context).dao();
        WorkManagerTestInitHelper.initializeTestWorkManager(context, new Configuration.Builder()
                .setMinimumLoggingLevel(Log.ERROR).setExecutor(new SynchronousExecutor()).build());
        today = DataSetup.today();
    }

    @After
    public void tearDown() {
        AppDatabase.resetForTests();
    }

    private static void idle() {
        shadowOf(Looper.getMainLooper()).idle();
    }

    private static AlertDialog dialog() {
        idle();
        AlertDialog d = (AlertDialog) ShadowDialog.getLatestDialog();
        assertNotNull(d);
        assertTrue(d.isShowing());
        return d;
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

    /** The adapter position of the reminder titled {@code title}. */
    private static int find(RecyclerView rv, String title) {
        for (int i = 0; i < rv.getAdapter().getItemCount(); i++) {
            TextView t = row(rv, i).itemView.findViewById(R.id.reminderTitle);
            if (t != null && t.getText().toString().equals(title)) {
                return i;
            }
        }
        throw new AssertionError("not shown: " + title);
    }

    private String day(int plus) {
        return Recurrence.Day.parse(today).plusDays(plus).toString();
    }

    private long save(String title, String type, String area, int inDays) {
        Reminder r = Reminders.create(title, type, day(inDays), null);
        r.category = area;
        return Reminders.save(context, r);
    }

    // ---------- Templates ----------

    @Test
    public void everyTemplateHasATitleAndValidSuggestions() {
        for (ReminderTemplates.Area area : ReminderTemplates.AREAS) {
            if (area.titles == 0) {
                continue;
            }
            int titles = context.getResources().getStringArray(area.titles).length;
            assertEquals(area.key, titles, area.suggestionCount());
            for (ReminderTemplates.Template t : area.templates(context)) {
                Reminder r = t.newReminder(today);
                assertTrue(t.title, ReminderEditor.repeatIndex(r) >= 0); // the form can show the repeat
                assertFalse(t.title, Reminders.looksLikeIdNumber(t.title));
                assertEquals(area.key, r.category);
                for (int d : Recurrence.parseAlertDays(t.alertDays)) {
                    boolean offered = false;
                    for (int option : ReminderEditor.ALERT_DAYS) {
                        offered |= option == d;
                    }
                    assertTrue(t.title + " alert " + d, offered);
                }
            }
        }
        // The suggested renewal alerts asked for.
        assertEquals("180,90,30", template(ReminderTemplates.DOCUMENTS, 0).alertDays);
        assertEquals("60,30", template(ReminderTemplates.DOCUMENTS, 1).alertDays);
        assertEquals("60,30", template(ReminderTemplates.DOCUMENTS, 2).alertDays);
        assertEquals("30,7", template(ReminderTemplates.DOCUMENTS, 6).alertDays);
        assertEquals("2", template(ReminderTemplates.LIBRARY, 0).alertDays);
        assertEquals("1", template(ReminderTemplates.HEALTH, 0).alertDays);
        assertEquals(Reminder.TYPE_VACCINATION, template(ReminderTemplates.HEALTH, 0).type);
    }

    private ReminderTemplates.Template template(String area, int index) {
        return ReminderTemplates.area(area).templates(context).get(index);
    }

    @Test
    public void idNumbersAreSpottedButDatesAndSmallNumbersAreFine() {
        assertFalse(Reminders.looksLikeIdNumber("Passport renewal"));
        assertFalse(Reminders.looksLikeIdNumber("Bring 2 pairs, room 12"));
        assertFalse(Reminders.looksLikeIdNumber("Renewed on 2026-03-14"));
        assertFalse(Reminders.looksLikeIdNumber("Due 14/03/2026"));
        assertFalse(Reminders.looksLikeIdNumber(null));
        assertTrue(Reminders.looksLikeIdNumber("Passport AB123456"));
        assertTrue(Reminders.looksLikeIdNumber("Card 1234-5678-9012"));
        assertTrue(Reminders.looksLikeIdNumber("Health card 1234 567 890"));
    }

    // ---------- Adding ----------

    @Test
    public void addFromATemplateStoresOnlyTitleDateAndSuggestions() {
        try (ActivityScenario<RemindersActivity> s = ActivityScenario.launch(RemindersActivity.class)) {
            s.onActivity(a -> {
                idle();
                RecyclerView rv = a.findViewById(R.id.screenList);
                row(rv, 0).itemView.findViewById(R.id.remindersAdd).performClick();
                AlertDialog areas = dialog();
                assertEquals(ReminderTemplates.AREAS.size(), areas.getListView().getAdapter().getCount());
                shadowOf(areas.getListView()).performItemClick(0); // Documents & cards
                AlertDialog templates = dialog();
                assertEquals(a.getString(R.string.reminders_blank), templates.getListView().getAdapter().getItem(0));
                shadowOf(templates.getListView()).performItemClick(1); // Passport renewal
                AlertDialog form = dialog();
                assertEquals("Passport renewal", ((EditText) form.findViewById(R.id.reminderTitleInput)).getText().toString());
                assertEquals(View.GONE, form.findViewById(R.id.reminderAdvanced).getVisibility());
                form.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
                idle();
            });
        }
        List<Reminder> all = dao.familyRemindersNow();
        assertEquals(1, all.size());
        Reminder r = all.get(0);
        assertEquals("Passport renewal", r.title);
        assertEquals(Reminder.TYPE_RENEWAL, r.type);
        assertEquals(ReminderTemplates.DOCUMENTS, r.category);
        assertEquals("180,90,30", r.alertDays);
        assertEquals(today, r.dueDate);
        assertNull(r.notes);
        assertNull(r.memberId);
        assertNull(r.dueTime);
    }

    @Test
    public void aPossibleIdNumberIsQuestionedBeforeSaving() {
        try (ActivityScenario<RemindersActivity> s = ActivityScenario.launch(RemindersActivity.class)) {
            s.onActivity(a -> {
                idle();
                Reminder r = ReminderTemplates.area(ReminderTemplates.DOCUMENTS).templates(a).get(1).newReminder(today);
                a.edit(r);
                AlertDialog form = dialog();
                ((EditText) form.findViewById(R.id.reminderTitleInput)).setText("Health card 1234 567 890");
                form.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
                AlertDialog warning = dialog();
                assertTrue(dao.familyRemindersNow().isEmpty());
                warning.getButton(AlertDialog.BUTTON_POSITIVE).performClick(); // Change it
                idle();
                assertTrue("the form stays open", form.isShowing());
                assertTrue(dao.familyRemindersNow().isEmpty());
                ((EditText) form.findViewById(R.id.reminderTitleInput)).setText("Health card renewal");
                form.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
                idle();
                assertEquals("Health card renewal", dao.familyRemindersNow().get(0).title);
            });
        }
    }

    @Test
    public void editingKeepsARepeatTheFormDoesNotList() {
        Reminder r = Reminders.create("Every 2 months", Reminder.TYPE_OTHER, today, null);
        r.repeatUnit = Recurrence.MONTH;
        r.repeatInterval = 2;
        long id = Reminders.save(context, r);
        try (ActivityScenario<RemindersActivity> s = ActivityScenario.launch(RemindersActivity.class)) {
            s.onActivity(a -> {
                idle();
                a.edit(dao.reminderNow(id));
                AlertDialog form = dialog();
                ((EditText) form.findViewById(R.id.reminderTitleInput)).setText("Every 2 months, renamed");
                form.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
                idle();
            });
        }
        assertEquals("Every 2 months, renamed", dao.reminderNow(id).title);
        assertEquals(Recurrence.MONTH, dao.reminderNow(id).repeatUnit);
        assertEquals(2, dao.reminderNow(id).repeatInterval);
    }

    // ---------- Actions ----------

    @Test
    public void renewedKeepsTheOldDateInTheHistory() {
        long id = save("Driver's licence renewal", Reminder.TYPE_RENEWAL, ReminderTemplates.DOCUMENTS, 20);
        try (ActivityScenario<RemindersActivity> s = ActivityScenario.launch(RemindersActivity.class)) {
            s.onActivity(a -> {
                idle();
                RecyclerView rv = a.findViewById(R.id.screenList);
                View card = row(rv, find(rv, "Driver's licence renewal")).itemView;
                Button action = card.findViewById(R.id.reminderAction);
                assertEquals(a.getString(R.string.reminders_renewed), action.getText().toString());
                action.performClick();
                idle();
                DatePickerDialog picker = (DatePickerDialog) ShadowDialog.getLatestDialog();
                picker.updateDate(2031, 4, 20); // May 20, 2031
                picker.getButton(DatePickerDialog.BUTTON_POSITIVE).performClick();
                idle();
            });
        }
        Reminder r = dao.reminderNow(id);
        assertEquals("2031-05-20", r.dueDate);
        assertEquals(Reminder.STATUS_ACTIVE, r.status);
        assertEquals(1, dao.historyNow(id).size());
        assertEquals(day(20), dao.historyNow(id).get(0).dueDate);
    }

    @Test
    public void libraryBooksAreReturnedOrRenewedAndKeptInHistory() {
        long returned = save("Return \"Matilda\"", Reminder.TYPE_LIBRARY, ReminderTemplates.LIBRARY, 2);
        long renewed = save("Return \"The Hobbit\"", Reminder.TYPE_LIBRARY, ReminderTemplates.LIBRARY, 1);
        try (ActivityScenario<RemindersActivity> s = ActivityScenario.launch(
                RemindersActivity.intent(context, ReminderTemplates.LIBRARY))) {
            s.onActivity(a -> {
                idle();
                assertEquals(a.getString(R.string.area_library), a.getTitle().toString());
                RecyclerView rv = a.findViewById(R.id.screenList);
                View matilda = row(rv, find(rv, "Return \"Matilda\"")).itemView;
                Button action = matilda.findViewById(R.id.reminderAction);
                assertEquals(a.getString(R.string.reminders_returned), action.getText().toString());
                action.performClick();
                idle();

                // Renewed (from the ⋮ menu) asks for the confirmed new due date.
                View hobbit = row(rv, find(rv, "Return \"The Hobbit\"")).itemView;
                hobbit.findViewById(R.id.reminderMore).performClick();
                ShadowPopupMenu popup = shadowOf(ShadowPopupMenu.getLatestPopupMenu());
                popup.getOnMenuItemClickListener().onMenuItemClick(ShadowPopupMenu.getLatestPopupMenu().getMenu().findItem(3));
                idle();
                DatePickerDialog picker = (DatePickerDialog) ShadowDialog.getLatestDialog();
                assertNotNull(picker);
                Recurrence.Day later = Recurrence.Day.parse(day(22));
                picker.updateDate(later.year, later.month - 1, later.day);
                picker.getButton(DatePickerDialog.BUTTON_POSITIVE).performClick();
                idle();

                // Returned books are kept, under "Show done and archived".
                int more = rv.getAdapter().getItemCount() - 1;
                Button showAll = (Button) row(rv, more).itemView;
                assertEquals(a.getString(R.string.reminders_show_done, 1), showAll.getText().toString());
                showAll.performClick();
                idle();
                find(rv, "Return \"Matilda\"");
            });
        }
        assertEquals(Reminder.STATUS_COMPLETED, dao.reminderNow(returned).status);
        assertEquals(1, dao.historyNow(returned).size());
        assertEquals(day(22), dao.reminderNow(renewed).dueDate);
        assertEquals(1, dao.historyNow(renewed).size());
    }

    @Test
    public void remindLaterArchiveRestoreAndDeleteWithConfirmation() {
        long id = save("Dental visit", Reminder.TYPE_APPOINTMENT, ReminderTemplates.HEALTH, 3);
        try (ActivityScenario<RemindersActivity> s = ActivityScenario.launch(RemindersActivity.class)) {
            s.onActivity(a -> {
                idle();
                RecyclerView rv = a.findViewById(R.id.screenList);
                View card = row(rv, find(rv, "Dental visit")).itemView;
                card.findViewById(R.id.reminderMore).performClick();
                ShadowPopupMenu.getLatestPopupMenu();
                shadowOf(ShadowPopupMenu.getLatestPopupMenu()).getOnMenuItemClickListener()
                        .onMenuItemClick(ShadowPopupMenu.getLatestPopupMenu().getMenu().findItem(1)); // tomorrow
                idle();
                assertNotNull("the next alert waits until tomorrow", dao.reminderNow(id).snoozedUntil);
                assertEquals("the date itself is unchanged", day(3), dao.reminderNow(id).dueDate);

                card = row(rv, find(rv, "Dental visit")).itemView;
                card.findViewById(R.id.reminderMore).performClick();
                shadowOf(ShadowPopupMenu.getLatestPopupMenu()).getOnMenuItemClickListener()
                        .onMenuItemClick(ShadowPopupMenu.getLatestPopupMenu().getMenu().findItem(7)); // archive
                idle();
                assertEquals(Reminder.STATUS_ARCHIVED, dao.reminderNow(id).status);

                int more = rv.getAdapter().getItemCount() - 1;
                row(rv, more).itemView.performClick();
                idle();
                card = row(rv, find(rv, "Dental visit")).itemView;
                ((Button) card.findViewById(R.id.reminderAction)).performClick(); // Restore
                idle();
                assertEquals(Reminder.STATUS_ACTIVE, dao.reminderNow(id).status);

                card = row(rv, find(rv, "Dental visit")).itemView;
                card.findViewById(R.id.reminderMore).performClick();
                shadowOf(ShadowPopupMenu.getLatestPopupMenu()).getOnMenuItemClickListener()
                        .onMenuItemClick(ShadowPopupMenu.getLatestPopupMenu().getMenu().findItem(8)); // delete
                dialog().getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
                idle();
                assertNotNull(dao.reminderNow(id));
                card = row(rv, find(rv, "Dental visit")).itemView;
                card.findViewById(R.id.reminderMore).performClick();
                shadowOf(ShadowPopupMenu.getLatestPopupMenu()).getOnMenuItemClickListener()
                        .onMenuItemClick(ShadowPopupMenu.getLatestPopupMenu().getMenu().findItem(8));
                dialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick();
                idle();
                assertNull(dao.reminderNow(id));
            });
        }
    }

    @Test
    public void filtersAndGentleSections() {
        save("Passport renewal", Reminder.TYPE_RENEWAL, ReminderTemplates.DOCUMENTS, 120);
        save("Test smoke alarms", Reminder.TYPE_OTHER, ReminderTemplates.HOME, -3);
        save("Make the bed", Reminder.TYPE_CHORE, null, 0); // chores live in Chores, not here
        try (ActivityScenario<RemindersActivity> s = ActivityScenario.launch(RemindersActivity.class)) {
            s.onActivity(a -> {
                idle();
                RecyclerView rv = a.findViewById(R.id.screenList);
                // header, "Whenever you're ready", smoke alarms, "Later on", passport
                assertEquals(5, rv.getAdapter().getItemCount());
                assertEquals(a.getString(R.string.reminders_section_ready), ((TextView) row(rv, 1).itemView).getText().toString());
                String details = ((TextView) row(rv, 2).itemView.findViewById(R.id.reminderDetails)).getText().toString();
                assertTrue(details, details.contains(a.getString(R.string.notify_overdue)));
                // Filter: Home & safety shows its safety note and only its reminders.
                com.google.android.material.chip.ChipGroup chips = row(rv, 0).itemView.findViewById(R.id.remindersFilter);
                for (int i = 0; i < chips.getChildCount(); i++) {
                    if (ReminderTemplates.HOME.equals(chips.getChildAt(i).getTag())) {
                        chips.getChildAt(i).performClick();
                    }
                }
                idle();
                assertEquals(3, rv.getAdapter().getItemCount());
                TextView note = row(rv, 0).itemView.findViewById(R.id.remindersNote);
                assertEquals(View.VISIBLE, note.getVisibility());
                assertEquals(a.getString(R.string.area_home_note), note.getText().toString());
            });
        }
    }

    @Test
    public void wordingIsGentle() {
        assertEquals(context.getString(R.string.notify_overdue),
                Notifications.dueText(context, day(-5), System.currentTimeMillis()));
        assertEquals("Whenever you're ready", context.getString(R.string.notify_overdue));
        // No harsh words anywhere in the app's text.
        for (Field f : R.string.class.getFields()) {
            try {
                String text = context.getString(f.getInt(null)).toLowerCase(java.util.Locale.ROOT);
                for (String harsh : new String[]{"you failed", "you missed", "urgent", "overdue"}) {
                    assertFalse(f.getName() + ": " + text, text.contains(harsh));
                }
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }
    }

    // ---------- Alerts ----------

    @Test
    public void areaAlertsCanBeTurnedOffWithoutHidingTheReminders() {
        shadowOf((Application) context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS);
        ReminderSettings.setAlertsEnabled(context, true);
        Reminder bill = dao.reminderNow(save("Internet and phone bills", Reminder.TYPE_OTHER, ReminderTemplates.BILLS, 1));
        Reminder passport = dao.reminderNow(save("Passport renewal", Reminder.TYPE_RENEWAL, ReminderTemplates.DOCUMENTS, 1));
        Reminder old = Reminders.create("Old renewal", Reminder.TYPE_RENEWAL, day(1), null); // made before Phase 4: no area
        old = dao.reminderNow(Reminders.save(context, old));
        assertTrue(Notifications.showReminder(context, bill));
        ReminderSettings.setQuietAreas(context, Collections.singleton(ReminderTemplates.BILLS));
        assertFalse("Bills are quiet", Notifications.showReminder(context, bill));
        assertTrue(Notifications.showReminder(context, passport));
        ReminderSettings.setQuietAreas(context, Collections.singleton(ReminderTemplates.DOCUMENTS));
        assertFalse("older renewals count as Documents", Notifications.showReminder(context, old));
        assertEquals("still listed", 3, dao.familyRemindersNow().size());
    }

    @Test
    public void settingsChooseQuietAreas() {
        try (ActivityScenario<SettingsActivity> s = ActivityScenario.launch(SettingsActivity.class)) {
            s.onActivity(a -> {
                idle();
                assertEquals(a.getString(R.string.settings_areas_all_on),
                        ((TextView) a.findViewById(R.id.settingAreasValue)).getText().toString());
                // The dialog lists every area, all switched on; Save without changes keeps them all on.
                a.findViewById(R.id.settingAreas).performClick();
                AlertDialog d = dialog();
                assertEquals(ReminderTemplates.AREAS.size(), d.getListView().getAdapter().getCount());
                d.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
                idle();
                assertTrue(ReminderSettings.quietAreas(a).isEmpty());

                // A quiet area is shown unticked, and named in the summary.
                ReminderSettings.setQuietAreas(a, Collections.singleton(ReminderTemplates.PETS));
                a.findViewById(R.id.settingAreas).performClick();
                AlertDialog again = dialog();
                again.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
                idle();
                assertEquals(Collections.singleton(ReminderTemplates.PETS), ReminderSettings.quietAreas(a));
                assertEquals(a.getString(R.string.settings_areas_some_off, a.getString(R.string.area_pets)),
                        ((TextView) a.findViewById(R.id.settingAreasValue)).getText().toString());
            });
        }
    }

    // ---------- Home and Schedule ----------

    @Test
    public void homeShowsOneGentleLineForTheWeekAhead() {
        save("Make the bed", Reminder.TYPE_CHORE, null, 0);
        save("Passport renewal", Reminder.TYPE_RENEWAL, ReminderTemplates.DOCUMENTS, 60);
        try (ActivityScenario<MainActivity> s = ActivityScenario.launch(MainActivity.class)) {
            s.onActivity(a -> {
                idle();
                assertNull("nothing this week: no line", a.findViewById(R.id.today_reminders));
            });
        }
        save("Dental visit", Reminder.TYPE_APPOINTMENT, ReminderTemplates.HEALTH, 2);
        save("Return \"Matilda\"", Reminder.TYPE_LIBRARY, ReminderTemplates.LIBRARY, 5);
        try (ActivityScenario<MainActivity> s = ActivityScenario.launch(MainActivity.class)) {
            s.onActivity(a -> {
                idle();
                View line = a.findViewById(R.id.today_reminders);
                assertNotNull(line);
                assertEquals("2 reminders coming up · first: Dental visit",
                        ((TextView) line.findViewById(R.id.todayText)).getText().toString());
                line.performClick();
                assertEquals(RemindersActivity.class.getName(),
                        shadowOf(a).getNextStartedActivity().getComponent().getClassName());
            });
        }
    }

    @Test
    public void scheduleShowsRemindersOnTheirDay() {
        save("Test smoke alarms", Reminder.TYPE_OTHER, ReminderTemplates.HOME, 0);
        try (ActivityScenario<viewCalendar> s = ActivityScenario.launch(viewCalendar.class)) {
            s.onActivity(a -> {
                idle();
                LinearLayout box = a.findViewById(R.id.scheduleReminders);
                assertEquals(1, box.getChildCount());
                assertEquals("Test smoke alarms", ((TextView) box.getChildAt(0).findViewById(R.id.todayText)).getText().toString());
                assertEquals(View.VISIBLE, a.findViewById(R.id.scheduleRemindersTitle).getVisibility());
            });
        }
    }

    @Test
    public void hubShortcutsOpenTheirArea() {
        int found = 0;
        for (Features.Feature f : Features.ALL) {
            if (f.area != null) {
                assertEquals(RemindersActivity.class, f.screen);
                assertTrue(ReminderTemplates.isArea(f.area));
                found++;
            }
        }
        assertEquals("Library, Hobby Reminders and Family Time", 3, found);
        try (ActivityScenario<HubActivity> s = ActivityScenario.launch(HubActivity.intent(context, Features.Category.FAMILY_PLANNER))) {
            s.onActivity(hub -> {
                idle();
                RecyclerView grid = hub.findViewById(R.id.hubGrid);
                for (int i = 1; i < grid.getAdapter().getItemCount(); i++) {
                    View tile = row(grid, i).itemView;
                    if (tile.getId() == R.id.feat_library) {
                        tile.performClick();
                        android.content.Intent next = shadowOf(hub).getNextStartedActivity();
                        assertEquals(ReminderTemplates.LIBRARY, next.getStringExtra(RemindersActivity.EXTRA_AREA));
                        return;
                    }
                }
                throw new AssertionError("Library tile not found");
            });
        }
    }
}
