package com.shajitha.familyapp.reminders;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The "Done" and "Tomorrow" buttons on a reminder notification. Not exported: only FamilyApp's own
 * (immutable) notification buttons can reach it.
 */
public class ReminderActionReceiver extends BroadcastReceiver {

    static final String ACTION_DONE = "com.shajitha.familyapp.action.REMINDER_DONE";
    static final String ACTION_TOMORROW = "com.shajitha.familyapp.action.REMINDER_TOMORROW";
    static final String EXTRA_ID = "reminderId";
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    static PendingIntent pendingIntent(Context context, String action, long reminderId) {
        Intent intent = new Intent(context, ReminderActionReceiver.class).setAction(action).putExtra(EXTRA_ID, reminderId);
        int requestCode = (int) (reminderId * 2 + (ACTION_DONE.equals(action) ? 0 : 1));
        return PendingIntent.getBroadcast(context, requestCode, intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        long id = intent.getLongExtra(EXTRA_ID, -1);
        if (id < 0 || (!ACTION_DONE.equals(action) && !ACTION_TOMORROW.equals(action))) {
            return; // ignore anything unexpected
        }
        Context app = context.getApplicationContext();
        PendingResult result = goAsync();
        EXECUTOR.execute(() -> {
            try {
                if (ACTION_DONE.equals(action)) {
                    Reminders.complete(app, id);
                } else {
                    Reminders.postponeToTomorrow(app, id);
                }
                Notifications.cancelReminder(app, id);
            } catch (RuntimeException ignored) {
                // Database unavailable: leave the notification so the user can try again.
            } finally {
                result.finish();
            }
        });
    }

    /** Tests: run actions on the calling thread. */
    static void handleNow(Context context, String action, long id) {
        if (ACTION_DONE.equals(action)) {
            Reminders.complete(context, id);
        } else if (ACTION_TOMORROW.equals(action)) {
            Reminders.postponeToTomorrow(context, id);
        }
        Notifications.cancelReminder(context, id);
    }
}
