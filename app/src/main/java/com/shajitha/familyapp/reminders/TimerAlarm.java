package com.shajitha.familyapp.reminders;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import androidx.annotation.NonNull;

/**
 * Wakes the phone when the Timer finishes while FamilyApp is closed. Uses an exact alarm only when
 * Android allows it ("Alarms & reminders" on Android 12+); otherwise an approximate one, which can
 * be up to about 10 minutes late.
 */
public final class TimerAlarm {

    private TimerAlarm() {
    }

    /** Whether exact timing is allowed (always true before Android 12). */
    public static boolean canBeExact(@NonNull Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return true;
        }
        AlarmManager am = context.getSystemService(AlarmManager.class);
        return am != null && am.canScheduleExactAlarms();
    }

    static PendingIntent pendingIntent(Context context) {
        Intent intent = new Intent(context, Receiver.class).setAction(Receiver.ACTION_TIMER_DONE);
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    /** Sets the alarm for {@code endMillis} (wall-clock time) if timer alerts are on; cancels it otherwise. */
    public static void schedule(@NonNull Context context, long endMillis) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) {
            return;
        }
        PendingIntent pi = pendingIntent(context);
        if (!ReminderSettings.timerAlerts(context)) {
            am.cancel(pi);
            return;
        }
        if (canBeExact(context)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endMillis, pi);
            } else {
                am.setExact(AlarmManager.RTC_WAKEUP, endMillis, pi);
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endMillis, pi);
        } else {
            am.set(AlarmManager.RTC_WAKEUP, endMillis, pi);
        }
    }

    public static void cancel(@NonNull Context context) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am != null) {
            am.cancel(pendingIntent(context));
        }
    }

    /** Receives the timer alarm. Not exported: only FamilyApp's own alarm can reach it. */
    public static class Receiver extends BroadcastReceiver {
        static final String ACTION_TIMER_DONE = "com.shajitha.familyapp.action.TIMER_DONE";

        @Override
        public void onReceive(Context context, Intent intent) {
            if (!ACTION_TIMER_DONE.equals(intent.getAction())) {
                return;
            }
            TimerState state = TimerState.read(context);
            // Only when a timer really finished, and FamilyApp's Timer screen isn't open (it rings itself).
            if (state.running && state.endMillis <= System.currentTimeMillis() + 5_000 && !TimerState.screenVisible) {
                Notifications.showTimerFinished(context, state.routineName(context));
            }
        }
    }

    /** What FamilyApp's Timer has saved (see FamAppTimer). */
    public static final class TimerState {
        /** True while the Timer screen is on screen. */
        public static volatile boolean screenVisible;

        public final boolean running;
        public final long endMillis;
        final String preset;

        private TimerState(boolean running, long endMillis, String preset) {
            this.running = running;
            this.endMillis = endMillis;
            this.preset = preset;
        }

        static TimerState read(Context context) {
            SharedPreferences p = context.getSharedPreferences("timer", Context.MODE_PRIVATE);
            return new TimerState(p.getBoolean("state_running", false), p.getLong("state_end", 0),
                    p.getString("state_preset", null));
        }

        String routineName(Context context) {
            if ("get_ready".equals(preset)) {
                return context.getString(com.shajitha.familyapp.R.string.timer_name_get_ready);
            } else if ("screen_time".equals(preset)) {
                return context.getString(com.shajitha.familyapp.R.string.timer_name_screen_time);
            } else if ("clean_up".equals(preset)) {
                return context.getString(com.shajitha.familyapp.R.string.timer_name_clean_up);
            }
            return context.getString(com.shajitha.familyapp.R.string.title_timer);
        }
    }
}
