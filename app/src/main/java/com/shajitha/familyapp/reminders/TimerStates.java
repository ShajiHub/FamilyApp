package com.shajitha.familyapp.reminders;

import android.content.Context;

import androidx.annotation.NonNull;

/** Public access to the Timer's saved state for other screens. */
public final class TimerStates {
    private TimerStates() {
    }

    @NonNull
    public static TimerAlarm.TimerState read(@NonNull Context context) {
        return TimerAlarm.TimerState.read(context);
    }
}
