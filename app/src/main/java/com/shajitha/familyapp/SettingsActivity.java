package com.shajitha.familyapp;

import android.Manifest;
import android.app.TimePickerDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.format.DateFormat;
import android.view.View;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.google.android.material.materialswitch.MaterialSwitch;
import com.shajitha.familyapp.data.AppDatabase;
import com.shajitha.familyapp.data.Recurrence;
import com.shajitha.familyapp.reminders.Notifications;
import com.shajitha.familyapp.reminders.ReminderSettings;
import com.shajitha.familyapp.reminders.Reminders;
import com.shajitha.familyapp.reminders.TimerAlarm;

import java.text.SimpleDateFormat;
import java.util.Locale;

/**
 * Settings for alerts. Notification permission is only asked for when the family turns alerts on;
 * "Alarms & reminders" is only suggested for exact Timer alerts.
 */
public class SettingsActivity extends BaseActivity {

    private MaterialSwitch alerts, lockDetails, timerAlerts;
    /** Which switch asked for the notification permission. */
    private MaterialSwitch waitingForPermission;

    private final ActivityResultLauncher<String> notificationPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                MaterialSwitch which = waitingForPermission;
                waitingForPermission = null;
                if (which != null) {
                    apply(which, Boolean.TRUE.equals(granted) && Notifications.allowed(this));
                }
                refresh();
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        Notifications.ensureChannels(this);

        alerts = setUpSwitch(R.id.settingAlerts, R.string.settings_alerts, R.string.settings_alerts_summary);
        lockDetails = setUpSwitch(R.id.settingLockDetails, R.string.settings_lock_details, R.string.settings_lock_details_summary);
        timerAlerts = setUpSwitch(R.id.settingTimerAlerts, R.string.settings_timer_alerts, R.string.settings_timer_alerts_summary);

        alerts.setChecked(ReminderSettings.alertsEnabled(this));
        lockDetails.setChecked(ReminderSettings.lockScreenDetails(this));
        timerAlerts.setChecked(ReminderSettings.timerAlerts(this));

        alerts.setOnCheckedChangeListener((b, on) -> onSwitch(alerts, on));
        timerAlerts.setOnCheckedChangeListener((b, on) -> onSwitch(timerAlerts, on));
        lockDetails.setOnCheckedChangeListener((b, on) -> ReminderSettings.setLockScreenDetails(this, on));

        findViewById(R.id.settingAlertTime).setOnClickListener(v -> pickAlertTime());
        findViewById(R.id.exactAllow).setOnClickListener(v -> openExactAlarmSettings());
        findViewById(R.id.openNotificationSettings).setOnClickListener(v -> openNotificationSettings());

        // Debug builds only (never in a release): a real reminder one minute from now.
        if (BuildConfig.DEBUG) {
            findViewById(R.id.developerCard).setVisibility(View.VISIBLE);
            findViewById(R.id.testReminder).setOnClickListener(v -> sendTestReminder());
        }
    }

    private void sendTestReminder() {
        android.content.Context app = getApplicationContext();
        String title = getString(R.string.settings_test_reminder_title);
        AppDatabase.write(() -> {
            com.shajitha.familyapp.data.Reminder r = Reminders.create(title,
                    com.shajitha.familyapp.data.Reminder.TYPE_OTHER,
                    com.shajitha.familyapp.data.DataSetup.today(), null);
            r.alertDays = "0";
            r.snoozedUntil = System.currentTimeMillis() + 60_000L;
            Reminders.save(app, r);
        });
        android.widget.Toast.makeText(this, ReminderSettings.alertsEnabled(this) && Notifications.allowed(this)
                ? R.string.settings_test_reminder_sent : R.string.settings_test_reminder_off, android.widget.Toast.LENGTH_LONG).show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh(); // the person may have changed permissions in Android settings
    }

    private MaterialSwitch setUpSwitch(int rowId, int title, int summary) {
        View row = findViewById(rowId);
        ((TextView) row.findViewById(R.id.settingTitle)).setText(title);
        ((TextView) row.findViewById(R.id.settingSummary)).setText(summary);
        MaterialSwitch sw = row.findViewById(R.id.settingSwitch);
        sw.setContentDescription(getString(title));
        row.setOnClickListener(v -> sw.toggle());
        return sw;
    }

    /** Turning alerts on asks for notification permission first (Android 13+). */
    private void onSwitch(MaterialSwitch sw, boolean on) {
        if (on && !Notifications.allowed(this)) {
            if (Build.VERSION.SDK_INT >= 33) {
                waitingForPermission = sw;
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS);
                return;
            }
            // Notifications switched off for FamilyApp in Android settings.
            apply(sw, false);
            refresh();
            return;
        }
        apply(sw, on);
        refresh();
    }

    private void apply(MaterialSwitch sw, boolean on) {
        sw.setOnCheckedChangeListener(null);
        sw.setChecked(on);
        sw.setOnCheckedChangeListener((b, checked) -> onSwitch(sw, checked));
        if (sw == alerts) {
            ReminderSettings.setAlertsEnabled(this, on);
            if (on) {
                rescheduleReminders();
            }
        } else if (sw == timerAlerts) {
            ReminderSettings.setTimerAlerts(this, on);
            TimerAlarm.TimerState state = timerState();
            if (on && state.running) {
                TimerAlarm.schedule(this, state.endMillis);
            } else if (!on) {
                TimerAlarm.cancel(this);
            }
        }
    }

    private TimerAlarm.TimerState timerState() {
        // Read through the receiver's helper so both agree on what the Timer saved.
        return com.shajitha.familyapp.reminders.TimerStates.read(this);
    }

    private void rescheduleReminders() {
        android.content.Context app = getApplicationContext();
        AppDatabase.write(() -> Reminders.rescheduleAll(app));
    }

    private void refresh() {
        String time = ReminderSettings.alertTime(this);
        ((TextView) findViewById(R.id.alertTimeValue)).setText(formatTime(time));
        boolean blocked = (alerts.isChecked() || timerAlerts.isChecked() || Build.VERSION.SDK_INT < 33)
                && !Notifications.allowed(this);
        findViewById(R.id.notificationsBlocked).setVisibility(blocked ? View.VISIBLE : View.GONE);
        boolean exact = TimerAlarm.canBeExact(this);
        ((TextView) findViewById(R.id.exactStatus)).setText(exact ? R.string.settings_exact_on : R.string.settings_exact_off);
        findViewById(R.id.exactAllow).setVisibility(!exact && Build.VERSION.SDK_INT >= 31 ? View.VISIBLE : View.GONE);
    }

    private String formatTime(String hhmm) {
        try {
            return DateFormat.getTimeFormat(this).format(new SimpleDateFormat("HH:mm", Locale.US).parse(hhmm));
        } catch (Exception e) {
            return hhmm;
        }
    }

    private void pickAlertTime() {
        int[] t = Recurrence.parseTime(ReminderSettings.alertTime(this));
        new TimePickerDialog(this, (picker, h, m) -> {
            ReminderSettings.setAlertTime(this, String.format(Locale.US, "%02d:%02d", h, m));
            rescheduleReminders();
            refresh();
        }, t[0], t[1], DateFormat.is24HourFormat(this)).show();
    }

    private void openExactAlarmSettings() {
        if (Build.VERSION.SDK_INT >= 31) {
            try {
                startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                        Uri.parse("package:" + getPackageName())));
            } catch (ActivityNotFoundException ignored) {
                // Some phones don't have this page; the Timer then uses approximate alerts.
            }
        }
    }

    private void openNotificationSettings() {
        Intent intent;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
        } else {
            intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()));
        }
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException ignored) {
            // nothing else to offer
        }
    }
}
