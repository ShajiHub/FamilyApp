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

    // Channels with the gentle chime, and quiet ones for when sounds are turned off in Settings.
    public static final String CHANNEL_REMINDERS = "reminders_gentle";
    public static final String CHANNEL_REMINDERS_QUIET = "reminders_quiet";
    public static final String CHANNEL_TIMER = "timer_gentle";
    public static final String CHANNEL_TIMER_QUIET = "timer_quiet";
    /** Phase 2 channels (default sound), replaced by the gentle ones above. */
    private static final String[] OLD_CHANNELS = {"reminders", "timer"};
    static final int TIMER_NOTIFICATION_ID = 1;
    private static final int REMINDER_ID_BASE = 10_000;

    private Notifications() {
    }

    /** Creates the notification categories people can switch off in Android settings. */
    public static void ensureChannels(@NonNull Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        for (String old : OLD_CHANNELS) {
            manager.deleteNotificationChannel(old);
        }
        android.net.Uri chime = Sounds.chimeUri(context);
        android.media.AudioAttributes notificationAudio = new android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION).build();
        android.media.AudioAttributes alarmAudio = new android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION).build();

        NotificationChannel reminders = new NotificationChannel(CHANNEL_REMINDERS,
                context.getString(R.string.channel_reminders), NotificationManager.IMPORTANCE_DEFAULT);
        reminders.setDescription(context.getString(R.string.channel_reminders_description));
        reminders.setLockscreenVisibility(NotificationCompat.VISIBILITY_PRIVATE);
        reminders.setSound(chime, notificationAudio);

        NotificationChannel remindersQuiet = new NotificationChannel(CHANNEL_REMINDERS_QUIET,
                context.getString(R.string.channel_reminders_quiet), NotificationManager.IMPORTANCE_LOW);
        remindersQuiet.setDescription(context.getString(R.string.channel_reminders_description));
        remindersQuiet.setLockscreenVisibility(NotificationCompat.VISIBILITY_PRIVATE);
        remindersQuiet.setSound(null, null);

        NotificationChannel timer = new NotificationChannel(CHANNEL_TIMER,
                context.getString(R.string.channel_timer), NotificationManager.IMPORTANCE_HIGH);
        timer.setDescription(context.getString(R.string.channel_timer_description));
        timer.setSound(chime, alarmAudio);

        NotificationChannel timerQuiet = new NotificationChannel(CHANNEL_TIMER_QUIET,
                context.getString(R.string.channel_timer_quiet), NotificationManager.IMPORTANCE_DEFAULT);
        timerQuiet.setDescription(context.getString(R.string.channel_timer_description));
        timerQuiet.setSound(null, null);

        manager.createNotificationChannel(reminders);
        manager.createNotificationChannel(remindersQuiet);
        manager.createNotificationChannel(timer);
        manager.createNotificationChannel(timerQuiet);
    }

    static String reminderChannel(Context context) {
        return ReminderSettings.reminderSound(context) ? CHANNEL_REMINDERS : CHANNEL_REMINDERS_QUIET;
    }

    static String timerChannel(Context context) {
        return ReminderSettings.timerSound(context) ? CHANNEL_TIMER : CHANNEL_TIMER_QUIET;
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
        String channel = reminderChannel(context);
        NotificationCompat.Builder publicVersion = new NotificationCompat.Builder(context, channel)
                .setSmallIcon(R.drawable.ic_event)
                .setContentTitle(context.getString(R.string.app_name))
                .setContentText(context.getString(R.string.notify_private_text));

        Intent open = new Intent(context, MainActivity.class).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent openApp = PendingIntent.getActivity(context, 0, open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder b = new NotificationCompat.Builder(context, channel)
                .setSmallIcon(R.drawable.ic_event)
                .setContentTitle(r.title) // notes are never included
                .setContentText(text)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setPriority(ReminderSettings.reminderSound(context) ? NotificationCompat.PRIORITY_DEFAULT : NotificationCompat.PRIORITY_LOW)
                .setSilent(!ReminderSettings.reminderSound(context))
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
        if (!ReminderSettings.alertsEnabled(context) || !allowed(context) || !ReminderSettings.areaAlerts(context, r)) {
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
        NotificationCompat.Builder b = new NotificationCompat.Builder(context, timerChannel(context))
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

    /** "Today", "Tomorrow", "Coming up in 7 days", or (gently) "Whenever you're ready" once the date has passed. */
    public static String dueText(Context context, String dueDate, long now) {
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
