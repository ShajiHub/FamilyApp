package com.shajitha.familyapp.reminders;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.shajitha.familyapp.data.AppDatabase;
import com.shajitha.familyapp.data.Reminder;

/** Runs when a reminder alert is due: shows the notification and schedules the next alert. */
public class ReminderWorker extends Worker {

    public ReminderWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        long id = getInputData().getLong(ReminderScheduler.KEY_ID, -1);
        long at = getInputData().getLong(ReminderScheduler.KEY_AT, 0);
        Reminder r;
        try {
            r = AppDatabase.get(context).dao().reminderNow(id);
        } catch (RuntimeException e) {
            // Database unavailable (for example the recovery screen is showing): try again later.
            return Result.retry();
        }
        if (r == null || !Reminder.STATUS_ACTIVE.equals(r.status)) {
            return Result.success();
        }
        Notifications.showReminder(context, r);
        if (r.snoozedUntil != null && r.snoozedUntil <= ReminderScheduler.clock.now() + 60_000) {
            r.snoozedUntil = null; // the snooze has been used
            AppDatabase.get(context).dao().update(r);
        }
        ReminderScheduler.scheduleAfter(context, r, Math.max(at, ReminderScheduler.clock.now()) + 1);
        return Result.success();
    }
}
