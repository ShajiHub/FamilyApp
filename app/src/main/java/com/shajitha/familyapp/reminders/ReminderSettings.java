package com.shajitha.familyapp.reminders;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import com.shajitha.familyapp.data.Reminder;

/** Notification settings (Home ⋮ → Settings). Everything is off until the family turns it on. */
public final class ReminderSettings {

    static final String PREFS = "settings";
    static final String KEY_ALERTS = "reminder_alerts";
    static final String KEY_ALERT_TIME = "alert_time";
    static final String KEY_LOCK_DETAILS = "lock_screen_details";
    static final String KEY_TIMER_ALERTS = "timer_alerts";
    static final String KEY_TIMER_SOUND = "timer_sound";
    static final String KEY_REMINDER_SOUND = "reminder_sound";
    static final String KEY_QUIET_AREAS = "quiet_areas";
    public static final String DEFAULT_ALERT_TIME = "09:00";

    private ReminderSettings() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static boolean alertsEnabled(Context context) {
        return prefs(context).getBoolean(KEY_ALERTS, false);
    }

    public static void setAlertsEnabled(Context context, boolean on) {
        prefs(context).edit().putBoolean(KEY_ALERTS, on).apply();
    }

    /** Time of day for reminder alerts (HH:mm). Default 9:00. */
    @NonNull
    public static String alertTime(Context context) {
        String t = prefs(context).getString(KEY_ALERT_TIME, DEFAULT_ALERT_TIME);
        return t == null || !t.matches("\\d{2}:\\d{2}") ? DEFAULT_ALERT_TIME : t;
    }

    public static void setAlertTime(Context context, String hhmm) {
        prefs(context).edit().putString(KEY_ALERT_TIME, hhmm).apply();
    }

    /** Whether reminder titles may show on the lock screen. Off by default (private). */
    public static boolean lockScreenDetails(Context context) {
        return prefs(context).getBoolean(KEY_LOCK_DETAILS, false);
    }

    public static void setLockScreenDetails(Context context, boolean on) {
        prefs(context).edit().putBoolean(KEY_LOCK_DETAILS, on).apply();
    }

    /** Whether the Timer alerts when it finishes while FamilyApp is closed. */
    public static boolean timerAlerts(Context context) {
        return prefs(context).getBoolean(KEY_TIMER_ALERTS, false);
    }

    public static void setTimerAlerts(Context context, boolean on) {
        prefs(context).edit().putBoolean(KEY_TIMER_ALERTS, on).apply();
    }

    /** Gentle chime when the Timer finishes (on by default; can be turned off). */
    public static boolean timerSound(Context context) {
        return prefs(context).getBoolean(KEY_TIMER_SOUND, true);
    }

    public static void setTimerSound(Context context, boolean on) {
        prefs(context).edit().putBoolean(KEY_TIMER_SOUND, on).apply();
    }

    /** Gentle chime with reminder notifications (on by default; off = silent notifications). */
    public static boolean reminderSound(Context context) {
        return prefs(context).getBoolean(KEY_REMINDER_SOUND, true);
    }

    public static void setReminderSound(Context context, boolean on) {
        prefs(context).edit().putBoolean(KEY_REMINDER_SOUND, on).apply();
    }

    /**
     * Areas (Documents, Bills, Pets, ...) whose alerts are turned off. Their reminders stay in the
     * list; they just don't send notifications.
     */
    @NonNull
    public static java.util.Set<String> quietAreas(Context context) {
        java.util.Set<String> saved = prefs(context).getStringSet(KEY_QUIET_AREAS, null);
        return saved == null ? new java.util.HashSet<>() : new java.util.HashSet<>(saved);
    }

    public static void setQuietAreas(Context context, @NonNull java.util.Set<String> areas) {
        prefs(context).edit().putStringSet(KEY_QUIET_AREAS, new java.util.HashSet<>(areas)).apply();
    }

    /** Whether reminders of this area may send alerts (chores and habits always may). */
    public static boolean areaAlerts(Context context, @NonNull Reminder r) {
        if (Reminder.TYPE_CHORE.equals(r.type) || Reminder.TYPE_HABIT.equals(r.type)) {
            return true;
        }
        return !quietAreas(context).contains(ReminderTemplates.areaOf(r));
    }

    /** Suggested advance alerts (days before) for a new reminder of this type; always editable. */
    @NonNull
    public static String defaultAlertDays(@NonNull String type) {
        switch (type) {
            case Reminder.TYPE_RENEWAL:
                return "30,7";
            case Reminder.TYPE_APPOINTMENT:
            case Reminder.TYPE_VACCINATION:
                return "1";
            case Reminder.TYPE_LIBRARY:
                return "2";
            default:
                return "0";
        }
    }
}
