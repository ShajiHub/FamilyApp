package com.shajitha.familyapp.reminders;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * After the phone restarts: sets the Timer alarm again if a timer is still running.
 * (Reminder alerts are restored automatically by WorkManager.)
 * Not exported: Android's own "phone finished starting" message still reaches it.
 */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            return;
        }
        TimerAlarm.TimerState state = TimerAlarm.TimerState.read(context);
        if (state.running) {
            // If it already ended while the phone was off, the alarm fires straight away.
            TimerAlarm.schedule(context, state.endMillis);
        }
    }
}
