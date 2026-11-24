package com.shajitha.familyapp.reminders;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.robolectric.Shadows.shadowOf;

import android.Manifest;
import android.app.AlarmManager;
import android.app.Application;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.work.Configuration;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;
import androidx.work.impl.WorkManagerImpl;
import androidx.work.impl.model.WorkSpec;
import androidx.work.testing.SynchronousExecutor;
import androidx.work.testing.WorkManagerTestInitHelper;

import com.shajitha.familyapp.data.AppDatabase;
import com.shajitha.familyapp.data.FamilyDao;
import com.shajitha.familyapp.data.FamilyMember;
import com.shajitha.familyapp.data.Recurrence;
import com.shajitha.familyapp.data.Reminder;
import com.shajitha.familyapp.data.ReminderHistory;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.shadows.ShadowAlarmManager;

import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;

/** Reminder alerts: scheduling, notifications (private by default), Done/Tomorrow, restarts, Timer alarm. */
@RunWith(AndroidJUnit4.class)
public class ReminderSchedulingTest {

    private static final TimeZone ZONE = TimeZone.getTimeZone("America/Toronto");
    private Context context;
    private FamilyDao dao;
    private TimeZone originalZone;
    private long now;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        originalZone = TimeZone.getDefault();
        TimeZone.setDefault(ZONE);
        AppDatabase.useDirectExecutorsForTests();
        AppDatabase.resetForTests();
        dao = AppDatabase.get(context).dao();
        WorkManagerTestInitHelper.initializeTestWorkManager(context, new Configuration.Builder()
                .setMinimumLoggingLevel(Log.ERROR).setExecutor(new SynchronousExecutor()).build());
        now = at("2027-03-01", "08:00");
        ReminderScheduler.clock = () -> now;
        shadowOf((Application) context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS);
        ReminderSettings.setAlertsEnabled(context, true);
    }

    @After
    public void tearDown() {
        ReminderScheduler.clock = System::currentTimeMillis;
        TimeZone.setDefault(originalZone);
        AppDatabase.resetForTests();
    }

    private static long at(String date, String time) {
        return Recurrence.alertMoment(Recurrence.Day.parse(date), 0, time, ZONE);
    }

    private long save(String title, String type, String due, String alertDays, String repeat) {
        Reminder r = Reminders.create(title, type, due, null);
        if (alertDays != null) {
            r.alertDays = alertDays;
        }
        r.repeatUnit = repeat;
        r.notes = "Private note: do not show";
        return Reminders.save(context, r);
    }

    private List<WorkInfo> work(long id) throws Exception {
        return WorkManager.getInstance(context).getWorkInfosForUniqueWork(ReminderScheduler.workName(id)).get();
    }

    private WorkInfo pending(long id) throws Exception {
        for (WorkInfo w : work(id)) {
            if (w.getState() == WorkInfo.State.ENQUEUED || w.getState() == WorkInfo.State.BLOCKED) {
                return w;
            }
        }
        return null;
    }

    private long delayOf(WorkInfo w) {
        WorkSpec spec = WorkManagerImpl.getInstance(context).getWorkDatabase().workSpecDao().getWorkSpec(w.getId().toString());
        return spec.initialDelay;
    }

    /** Runs the pending alert as if its time had come. */
    private void fire(long id) throws Exception {
        WorkInfo w = pending(id);
        assertNotNull("an alert is scheduled", w);
        now = now + delayOf(w);
        WorkManagerTestInitHelper.getTestDriver(context).setInitialDelayMet(w.getId());
    }

    private List<Notification> shown() {
        return shadowOf((NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE)).getAllNotifications();
    }

    // ---------- Scheduling ----------

    @Test
    public void defaultsAndAlertTiming() throws Exception {
        assertEquals("30,7", ReminderSettings.defaultAlertDays(Reminder.TYPE_RENEWAL));
        assertEquals("1", ReminderSettings.defaultAlertDays(Reminder.TYPE_APPOINTMENT));
        assertEquals("1", ReminderSettings.defaultAlertDays(Reminder.TYPE_VACCINATION));
        assertEquals("2", ReminderSettings.defaultAlertDays(Reminder.TYPE_LIBRARY));
        assertEquals("09:00", ReminderSettings.alertTime(context));

        long id = save("Passport renewal", Reminder.TYPE_RENEWAL, "2027-04-30", null, Recurrence.NONE);
        // 30 days before = 2027-03-31 at 9:00 (after the March clock change: still 9:00 local).
        assertEquals(at("2027-03-31", "09:00") - now, delayOf(pending(id)));

        // Several advance alerts, and a different time of day for this one reminder.
        Reminder r = dao.reminderNow(id);
        r.alertDays = "90,30,7,1,0";
        r.alertTime = "18:30";
        Reminders.save(context, r);
        assertEquals(at("2027-03-31", "18:30") - now, delayOf(pending(id)));
        assertEquals("only one alert is scheduled per reminder", 1, countPending(id));

        // Changing the app-wide alert time applies to reminders that use it.
        r.alertTime = null;
        Reminders.save(context, r);
        ReminderSettings.setAlertTime(context, "07:15");
        Reminders.rescheduleAll(context);
        assertEquals(at("2027-03-31", "07:15") - now, delayOf(pending(id)));
    }

    private int countPending(long id) throws Exception {
        int n = 0;
        for (WorkInfo w : work(id)) {
            if (w.getState() == WorkInfo.State.ENQUEUED) {
                n++;
            }
        }
        return n;
    }

    @Test
    public void alertShowsAPrivateNotificationAndSchedulesTheNextOne() throws Exception {
        FamilyMember mia = new FamilyMember();
        mia.name = "Mia";
        mia.avatar = "👧";
        long miaId = dao.insert(mia);
        Reminder r = Reminders.create("Dentist", Reminder.TYPE_APPOINTMENT, "2027-03-10", miaId);
        r.alertDays = "7,1";
        r.notes = "Private note: do not show";
        long id = Reminders.save(context, r);

        fire(id); // 7 days before
        List<Notification> list = shown();
        assertEquals(1, list.size());
        Notification n = list.get(0);
        assertEquals("Dentist", n.extras.getString(NotificationCompat.EXTRA_TITLE));
        assertEquals("Due in 7 days · for Mia", n.extras.getString(NotificationCompat.EXTRA_TEXT));
        assertEquals("details hidden on the lock screen", Notification.VISIBILITY_PRIVATE, n.visibility);
        assertNotNull(n.publicVersion);
        assertEquals("You have a reminder", n.publicVersion.extras.getString(NotificationCompat.EXTRA_TEXT));
        assertFalse(n.publicVersion.extras.toString().contains("Dentist"));
        assertFalse("notes never shown", n.extras.toString().contains("Private note"));
        assertEquals(2, n.actions.length);
        assertTrue(n.actions[0].actionIntent.isImmutable());

        // Next: 1 day before, at 9:00 on 2027-03-09.
        assertEquals(at("2027-03-09", "09:00"), now + delayOf(pending(id)));
    }

    @Test
    public void lockScreenDetailsCanBeTurnedOn() {
        ReminderSettings.setLockScreenDetails(context, true);
        Reminder r = Reminders.create("Library books", Reminder.TYPE_LIBRARY, "2027-03-03", null);
        Notification n = Notifications.buildReminder(context, r, null, now).build();
        assertEquals(Notification.VISIBILITY_PUBLIC, n.visibility);
        assertNull(n.publicVersion);
    }

    @Test
    public void nothingIsShownWhenAlertsAreOffOrNotAllowed() throws Exception {
        long id = save("Haircut", Reminder.TYPE_APPOINTMENT, "2027-03-05", "1,0", Recurrence.NONE);
        ReminderSettings.setAlertsEnabled(context, false);
        fire(id);
        assertTrue(shown().isEmpty());
        assertNotNull("still scheduled for later", pending(id));

        ReminderSettings.setAlertsEnabled(context, true);
        shadowOf((Application) context).denyPermissions(Manifest.permission.POST_NOTIFICATIONS);
        fire(id);
        assertTrue("permission refused: nothing shown", shown().isEmpty());
    }

    // ---------- Done, Tomorrow, reschedule ----------

    @Test
    public void doneOnARepeatingReminderKeepsHistoryAndMovesToTheNextDate() throws Exception {
        long id = save("Library card", Reminder.TYPE_RENEWAL, "2027-01-31", "0", Recurrence.MONTH);
        now = at("2027-01-31", "10:00");
        ReminderActionReceiver.handleNow(context, ReminderActionReceiver.ACTION_DONE, id);
        Reminder r = dao.reminderNow(id);
        assertEquals("2027-02-28", r.dueDate);
        assertEquals(Reminder.STATUS_ACTIVE, r.status);
        ReminderActionReceiver.handleNow(context, ReminderActionReceiver.ACTION_DONE, id);
        assertEquals("back to the 31st", "2027-03-31", dao.reminderNow(id).dueDate);
        List<ReminderHistory> history = dao.historyNow(id);
        assertEquals(2, history.size());
        assertEquals("2027-02-28", history.get(0).dueDate);
        assertEquals("2027-01-31", history.get(1).dueDate);
        assertEquals("Library card", history.get(0).title);
        assertNotNull(pending(id));
    }

    @Test
    public void doneOnAOneOffReminderKeepsIt() throws Exception {
        long id = save("Eye exam", Reminder.TYPE_APPOINTMENT, "2027-03-04", null, Recurrence.NONE);
        Reminders.complete(context, id);
        Reminder r = dao.reminderNow(id);
        assertEquals(Reminder.STATUS_COMPLETED, r.status);
        assertNotNull(r.completedAt);
        assertNull("no more alerts", pending(id));
        assertEquals(1, dao.historyNow(id).size());
        // Completing twice doesn't add history twice.
        Reminders.complete(context, id);
        assertEquals(1, dao.historyNow(id).size());
    }

    @Test
    public void tomorrowPostponesWithoutChangingTheDueDate() throws Exception {
        long id = save("Vaccination", Reminder.TYPE_VACCINATION, "2027-03-02", null, Recurrence.NONE);
        now = at("2027-03-01", "09:00"); // the alert just showed
        ReminderActionReceiver.handleNow(context, ReminderActionReceiver.ACTION_TOMORROW, id);
        Reminder r = dao.reminderNow(id);
        assertEquals("2027-03-02", r.dueDate);
        assertEquals(Long.valueOf(at("2027-03-02", "09:00")), r.snoozedUntil);
        assertEquals(at("2027-03-02", "09:00") - now, delayOf(pending(id)));

        // Postpone by any amount, e.g. 3 hours.
        Reminders.postpone(context, id, now + 3 * 3600_000L);
        assertEquals(3 * 3600_000L, delayOf(pending(id)));
    }

    @Test
    public void rescheduleMovesTheDueDateAndReactivates() throws Exception {
        long id = save("Health checkup", Reminder.TYPE_APPOINTMENT, "2027-03-04", "1", Recurrence.YEAR);
        Reminders.reschedule(context, id, "2028-02-29");
        Reminder r = dao.reminderNow(id);
        assertEquals("2028-02-29", r.dueDate);
        assertEquals(29, r.anchorDay);
        assertEquals(at("2028-02-28", "09:00") - now, delayOf(pending(id)));
        try {
            Reminders.reschedule(context, id, "2027-02-30");
            fail("invalid date refused");
        } catch (Reminders.InvalidReminderException expected) {
            assertEquals("2028-02-29", dao.reminderNow(id).dueDate);
        }
    }

    @Test
    public void archiveRestoreAndDeleteKeepHistory() throws Exception {
        long id = save("Swimming fees", Reminder.TYPE_OTHER, "2027-03-03", "0", Recurrence.WEEK);
        Reminders.complete(context, id);
        Reminders.archive(context, id);
        assertEquals(Reminder.STATUS_ARCHIVED, dao.reminderNow(id).status);
        assertNull("archived: no alerts", pending(id));
        Reminders.restore(context, id);
        assertNotNull(pending(id));
        Reminders.delete(context, id);
        assertNull(dao.reminderNow(id));
        assertNull(pending(id));
        assertEquals("history kept after delete", 1, dao.allHistoryNow().size());
    }

    @Test
    public void invalidRemindersAreRefused() {
        String[][] bad = {{"", "2027-03-01"}, {"x".repeat(81), "2027-03-01"}, {"Ok", "2027-02-29"}, {"Ok", "tomorrow"}};
        for (String[] b : bad) {
            try {
                Reminders.save(context, Reminders.create(b[0], Reminder.TYPE_OTHER, b[1], null));
                fail("should refuse " + b[0] + " " + b[1]);
            } catch (Reminders.InvalidReminderException expected) {
                // ok
            }
        }
        Reminder notes = Reminders.create("Ok", Reminder.TYPE_OTHER, "2027-03-01", null);
        notes.notes = "n".repeat(501);
        try {
            Reminders.save(context, notes);
            fail("notes too long");
        } catch (Reminders.InvalidReminderException expected) {
            // ok
        }
        assertTrue(dao.allRemindersNow().isEmpty());
    }

    // ---------- Restarts ----------

    @Test
    public void allAlertsComeBackAfterTheScheduleIsLost() throws Exception {
        List<Long> ids = new ArrayList<>();
        ids.add(save("A", Reminder.TYPE_RENEWAL, "2027-05-01", null, Recurrence.NONE));
        ids.add(save("B", Reminder.TYPE_LIBRARY, "2027-03-20", null, Recurrence.WEEK));
        long archived = save("C", Reminder.TYPE_OTHER, "2027-03-20", null, Recurrence.NONE);
        Reminders.archive(context, archived);
        WorkManager.getInstance(context).cancelAllWork(); // as if the schedule had been lost
        for (long id : ids) {
            assertNull(pending(id));
        }
        Reminders.rescheduleAll(context); // what FamilyApp does when it starts
        for (long id : ids) {
            assertNotNull(pending(id));
        }
        assertNull("archived stays quiet", pending(archived));
    }

    // ---------- Timer alarm ----------

    private void saveTimer(boolean running, long end) {
        SharedPreferences p = context.getSharedPreferences("timer", Context.MODE_PRIVATE);
        p.edit().putBoolean("state_running", running).putLong("state_end", end).putString("state_preset", "clean_up").commit();
    }

    private ShadowAlarmManager alarms() {
        return shadowOf((AlarmManager) context.getSystemService(Context.ALARM_SERVICE));
    }

    @Test
    public void timerAlarmOnlyWhenTurnedOnAndRestoredAfterRestart() {
        long end = System.currentTimeMillis() + 15 * 60_000L;
        TimerAlarm.schedule(context, end);
        assertNull("off by default", alarms().getNextScheduledAlarm());

        ReminderSettings.setTimerAlerts(context, true);
        ShadowAlarmManager.setCanScheduleExactAlarms(true);
        TimerAlarm.schedule(context, end);
        ShadowAlarmManager.ScheduledAlarm alarm = alarms().getNextScheduledAlarm();
        assertNotNull(alarm);
        assertEquals(end, alarm.getTriggerAtMs());
        assertTrue(TimerAlarm.canBeExact(context));

        // Without "Alarms & reminders" an approximate alarm is still set.
        ShadowAlarmManager.setCanScheduleExactAlarms(false);
        assertFalse(TimerAlarm.canBeExact(context));
        TimerAlarm.cancel(context);
        assertNull(alarms().getNextScheduledAlarm());
        TimerAlarm.schedule(context, end);
        assertEquals(end, alarms().getNextScheduledAlarm().getTriggerAtMs());

        // After a phone restart the alarm is set again for a running timer.
        TimerAlarm.cancel(context);
        saveTimer(true, end);
        new BootReceiver().onReceive(context, new Intent(Intent.ACTION_BOOT_COMPLETED));
        assertEquals(end, alarms().getNextScheduledAlarm().getTriggerAtMs());
        // Anything other than the restart message is ignored.
        TimerAlarm.cancel(context);
        new BootReceiver().onReceive(context, new Intent("evil.ACTION"));
        assertNull(alarms().getNextScheduledAlarm());
    }

    @Test
    public void timerFinishedNotification() {
        ReminderSettings.setTimerAlerts(context, true);
        saveTimer(true, System.currentTimeMillis() - 1000);
        TimerAlarm.TimerState.screenVisible = true;
        new TimerAlarm.Receiver().onReceive(context, new Intent(TimerAlarm.Receiver.ACTION_TIMER_DONE));
        assertTrue("the Timer screen rings itself", shown().isEmpty());
        TimerAlarm.TimerState.screenVisible = false;
        new TimerAlarm.Receiver().onReceive(context, new Intent(TimerAlarm.Receiver.ACTION_TIMER_DONE));
        assertEquals(1, shown().size());
        assertEquals("Clean Up", shown().get(0).extras.getString(NotificationCompat.EXTRA_TEXT));
        // A paused or reset timer doesn't notify.
        ((NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE)).cancelAll();
        saveTimer(false, System.currentTimeMillis() - 1000);
        new TimerAlarm.Receiver().onReceive(context, new Intent(TimerAlarm.Receiver.ACTION_TIMER_DONE));
        assertTrue(shown().isEmpty());
    }

    @Test
    public void notificationButtonsIgnoreUnexpectedMessages() {
        long id = save("Haircut", Reminder.TYPE_APPOINTMENT, "2027-03-05", null, Recurrence.NONE);
        ReminderActionReceiver receiver = new ReminderActionReceiver();
        receiver.onReceive(context, new Intent("evil.ACTION").putExtra(ReminderActionReceiver.EXTRA_ID, id));
        receiver.onReceive(context, new Intent(ReminderActionReceiver.ACTION_DONE)); // no id
        assertEquals(Reminder.STATUS_ACTIVE, dao.reminderNow(id).status);
        assertTrue(dao.historyNow(id).isEmpty());
    }
}
