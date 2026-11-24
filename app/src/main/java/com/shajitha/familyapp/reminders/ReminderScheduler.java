package com.shajitha.familyapp.reminders;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.shajitha.familyapp.data.Recurrence;
import com.shajitha.familyapp.data.Reminder;

import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Keeps exactly one scheduled alert per active reminder, using WorkManager (free, part of Android
 * Jetpack). WorkManager keeps scheduled work through phone restarts and app updates.
 */
public final class ReminderScheduler {

    static final String TAG = "reminder";
    static final String KEY_ID = "id";
    static final String KEY_AT = "at";

    /** Current time; tests replace it. */
    interface Clock {
        long now();
    }

    static Clock clock = System::currentTimeMillis;

    private ReminderScheduler() {
    }

    static String workName(long reminderId) {
        return "reminder-" + reminderId;
    }

    /** The next alert moment for this reminder, or -1 if there is none (or it isn't active). */
    public static long nextAlert(@NonNull Context context, @NonNull Reminder r, long now) {
        Recurrence.Day due = Recurrence.Day.parse(r.dueDate);
        if (due == null || !Reminder.STATUS_ACTIVE.equals(r.status)) {
            return -1;
        }
        String time = r.alertTime != null ? r.alertTime : ReminderSettings.alertTime(context);
        return Recurrence.nextAlert(due, Recurrence.parseAlertDays(r.alertDays), time, r.snoozedUntil, now,
                TimeZone.getDefault());
    }

    /** (Re)schedules a reminder's next alert, replacing any earlier one; cancels it if none is due. */
    public static void schedule(@NonNull Context context, @NonNull Reminder r) {
        enqueue(context, r, clock.now(), ExistingWorkPolicy.REPLACE);
    }

    /** Called by the worker after an alert: schedules the one after it. */
    static void scheduleAfter(@NonNull Context context, @NonNull Reminder r, long after) {
        enqueue(context, r, after, ExistingWorkPolicy.APPEND_OR_REPLACE);
    }

    private static void enqueue(Context context, Reminder r, long after, ExistingWorkPolicy policy) {
        long at = nextAlert(context, r, after);
        if (at < 0) {
            cancel(context, r.id);
            return;
        }
        long delay = Math.max(0, at - clock.now());
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(ReminderWorker.class)
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setInputData(new Data.Builder().putLong(KEY_ID, r.id).putLong(KEY_AT, at).build())
                .addTag(TAG)
                .build();
        WorkManager.getInstance(context).enqueueUniqueWork(workName(r.id), policy, request);
    }

    public static void cancel(@NonNull Context context, long reminderId) {
        WorkManager.getInstance(context).cancelUniqueWork(workName(reminderId));
    }
}
