package com.shajitha.familyapp.reminders;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.shajitha.familyapp.MainActivity;
import com.shajitha.familyapp.R;
import com.shajitha.familyapp.data.AppDatabase;
import com.shajitha.familyapp.data.FamilyMember;
import com.shajitha.familyapp.data.Recurrence;
import com.shajitha.familyapp.data.Reminder;

import java.util.Calendar;
import java.util.TimeZone;

/**
 * Local notifications (no internet, no server). Private by default: the lock screen only shows
 * "You have a reminder" unless the family turns on lock-screen details in Settings. Reminder notes
 * are never shown in a notification.
 */
public final class Notifications {

    public static final String CHANNEL_REMINDERS = "reminders";
    public static final String CHANNEL_TIMER = "timer";
    static final int TIMER_NOTIFICATION_ID = 1;
    private static final int REMINDER_ID_BASE = 10_000;

    private Notifications() {
    }

    /** Creates the two notification categories people can switch off in Android settings. */
    public static void ensureChannels(@NonNull Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        NotificationChannel reminders = new NotificationChannel(CHANNEL_REMINDERS,
                context.getString(R.string.channel_reminders), NotificationManager.IMPORTANCE_DEFAULT);
        reminders.setDescription(context.getString(R.string.channel_reminders_description));
        reminders.setLockscreenVisibility(NotificationCompat.VISIBILITY_PRIVATE);
        NotificationChannel timer = new NotificationChannel(CHANNEL_TIMER,
                context.getString(R.string.channel_timer), NotificationManager.IMPORTANCE_HIGH);
        timer.setDescription(context.getString(R.string.channel_timer_description));
        manager.createNotificationChannel(reminders);
        manager.createNotificationChannel(timer);
    }

    /** Whether FamilyApp may show notifications at all (permission on Android 13+ and the user's settings). */
    public static boolean allowed(@NonNull Context context) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled();
    }

    static int reminderNotificationId(long reminderId) {
        return REMINDER_ID_BASE + (int) (reminderId % 1_000_000);
    }

    /** Builds a reminder notification (separate from posting, so it can be tested). */
    @NonNull
    static NotificationCompat.Builder buildReminder(@NonNull Context context, @NonNull Reminder r,
                                                    @androidx.annotation.Nullable FamilyMember member, long now) {
        String when = dueText(context, r.dueDate, now);
        String text = member == null ? when : context.getString(R.string.notify_for_member, when, member.name);
        boolean details = ReminderSettings.lockScreenDetails(context);

        // What the lock screen shows when details are hidden.
        NotificationCompat.Builder publicVersion = new NotificationCompat.Builder(context, CHANNEL_REMINDERS)
                .setSmallIcon(R.drawable.ic_event)
                .setContentTitle(context.getString(R.string.app_name))
                .setContentText(context.getString(R.string.notify_private_text));

        Intent open = new Intent(context, MainActivity.class).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent openApp = PendingIntent.getActivity(context, 0, open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder b = new NotificationCompat.Builder(context, CHANNEL_REMINDERS)
                .setSmallIcon(R.drawable.ic_event)
                .setContentTitle(r.title) // notes are never included
                .setContentText(text)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(openApp)
                .setAutoCancel(true)
                .setVisibility(details ? NotificationCompat.VISIBILITY_PUBLIC : NotificationCompat.VISIBILITY_PRIVATE)
                .addAction(0, context.getString(R.string.notify_action_done),
                        ReminderActionReceiver.pendingIntent(context, ReminderActionReceiver.ACTION_DONE, r.id))
                .addAction(0, context.getString(R.string.notify_action_tomorrow),
                        ReminderActionReceiver.pendingIntent(context, ReminderActionReceiver.ACTION_TOMORROW, r.id));
        if (!details) {
            b.setPublicVersion(publicVersion.build());
        }
        return b;
    }

    /** Shows a reminder if alerts are turned on and allowed. Returns whether it was shown. */
    public static boolean showReminder(@NonNull Context context, @NonNull Reminder r) {
        if (!ReminderSettings.alertsEnabled(context) || !allowed(context)) {
            return false;
        }
        ensureChannels(context);
        FamilyMember member = r.memberId == null ? null : AppDatabase.get(context).dao().memberNow(r.memberId);
        try {
            NotificationManagerCompat.from(context).notify(reminderNotificationId(r.id),
                    buildReminder(context, r, member, ReminderScheduler.clock.now()).build());
            return true;
        } catch (SecurityException e) {
            return false; // permission was removed in the meantime
        }
    }

    public static void cancelReminder(@NonNull Context context, long reminderId) {
        NotificationManagerCompat.from(context).cancel(reminderNotificationId(reminderId));
    }

    /** "Time's up" when the Timer finishes while FamilyApp is closed. */
    public static boolean showTimerFinished(@NonNull Context context, @NonNull String routine) {
        if (!ReminderSettings.timerAlerts(context) || !allowed(context)) {
            return false;
        }
        ensureChannels(context);
        Intent open = new Intent(context, com.shajitha.familyapp.FamAppTimer.class)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent openTimer = PendingIntent.getActivity(context, 1, open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        NotificationCompat.Builder b = new NotificationCompat.Builder(context, CHANNEL_TIMER)
                .setSmallIcon(R.drawable.ic_timer)
                .setContentTitle(context.getString(R.string.timer_time_up))
                .setContentText(routine)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(openTimer)
                .setAutoCancel(true);
        try {
            NotificationManagerCompat.from(context).notify(TIMER_NOTIFICATION_ID, b.build());
            return true;
        } catch (SecurityException e) {
            return false;
        }
    }

    /** "Due today", "Due tomorrow", "Due in 7 days", "Overdue". */
    static String dueText(Context context, String dueDate, long now) {
        Recurrence.Day due = Recurrence.Day.parse(dueDate);
        if (due == null) {
            return "";
        }
        Calendar c = Calendar.getInstance(TimeZone.getDefault());
        c.setTimeInMillis(now);
        Recurrence.Day today = new Recurrence.Day(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
        int days = 0;
        Recurrence.Day d = today;
        int step = due.compareTo(today) >= 0 ? 1 : -1;
        while (!d.equals(due) && Math.abs(days) < 4000) {
            d = d.plusDays(step);
            days += step;
        }
        if (days < 0) {
            return context.getString(R.string.notify_overdue);
        } else if (days == 0) {
            return context.getString(R.string.notify_due_today);
        } else if (days == 1) {
            return context.getString(R.string.notify_due_tomorrow);
        }
        return context.getResources().getQuantityString(R.plurals.notify_due_in, days, days);
    }
}
