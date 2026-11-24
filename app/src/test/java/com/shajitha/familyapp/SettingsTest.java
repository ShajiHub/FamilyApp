package com.shajitha.familyapp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.Manifest;
import android.app.Application;
import android.view.View;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.google.android.material.materialswitch.MaterialSwitch;
import com.shajitha.familyapp.data.AppDatabase;
import com.shajitha.familyapp.reminders.ReminderSettings;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.shadows.ShadowActivity;

/** Settings: everything private and off by default; permission only asked for when needed. */
@RunWith(AndroidJUnit4.class)
public class SettingsTest {

    @Before
    public void setUp() {
        AppDatabase.useDirectExecutorsForTests();
        AppDatabase.resetForTests();
    }

    @After
    public void tearDown() {
        AppDatabase.resetForTests();
    }

    private static MaterialSwitch sw(SettingsActivity a, int row) {
        return a.findViewById(row).findViewById(R.id.settingSwitch);
    }

    @Test
    public void defaultsArePrivateAndOff() {
        Application app = ApplicationProvider.getApplicationContext();
        assertFalse(ReminderSettings.alertsEnabled(app));
        assertFalse("lock screen details hidden by default", ReminderSettings.lockScreenDetails(app));
        assertFalse(ReminderSettings.timerAlerts(app));
        try (ActivityScenario<SettingsActivity> s = ActivityScenario.launch(SettingsActivity.class)) {
            s.onActivity(a -> {
                assertFalse(sw(a, R.id.settingAlerts).isChecked());
                assertFalse(sw(a, R.id.settingLockDetails).isChecked());
                assertFalse(sw(a, R.id.settingTimerAlerts).isChecked());
            });
        }
    }

    @Test
    public void turningAlertsOnAsksForPermissionOnlyThen() {
        Application app = ApplicationProvider.getApplicationContext();
        try (ActivityScenario<SettingsActivity> s = ActivityScenario.launch(SettingsActivity.class)) {
            s.onActivity(a -> {
                ShadowActivity shadow = shadowOf(a);
                assertTrue("nothing asked on opening", shadow.getLastRequestedPermission() == null);
                sw(a, R.id.settingAlerts).setChecked(true);
                ShadowActivity.PermissionsRequest request = shadow.getLastRequestedPermission();
                assertNotNull("asked when switched on", request);
                assertEquals(Manifest.permission.POST_NOTIFICATIONS, request.requestedPermissions[0]);
                assertFalse("not on until allowed", ReminderSettings.alertsEnabled(app));
            });
        }
        // With permission: switching on works straight away.
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS);
        try (ActivityScenario<SettingsActivity> s = ActivityScenario.launch(SettingsActivity.class)) {
            s.onActivity(a -> {
                sw(a, R.id.settingAlerts).setChecked(true);
                assertTrue(ReminderSettings.alertsEnabled(app));
                sw(a, R.id.settingLockDetails).setChecked(true);
                assertTrue(ReminderSettings.lockScreenDetails(app));
                sw(a, R.id.settingTimerAlerts).setChecked(true);
                assertTrue(ReminderSettings.timerAlerts(app));
                assertEquals(View.GONE, a.findViewById(R.id.notificationsBlocked).getVisibility());
            });
        }
    }
}
