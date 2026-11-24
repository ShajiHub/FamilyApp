package com.shajitha.familyapp;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.Locale;

/**
 * Timer for family routines. Routine lengths can be edited, and the timer's state is saved all the
 * time, so it keeps going after rotating the phone or closing and reopening the app.
 */
public class FamAppTimer extends BaseActivity {

    static final String PREFS = "timer";
    static final String KEY_GET_READY = "get_ready";
    static final String KEY_SCREEN_TIME = "screen_time";
    static final String KEY_CLEAN_UP = "clean_up";
    private static final int[] DEFAULT_MINUTES = {15, 60, 30};
    static final int MAX_MINUTES = 600;

    // Saved state
    private static final String S_PRESET = "state_preset";
    private static final String S_TOTAL = "state_total";
    private static final String S_REMAINING = "state_remaining";
    private static final String S_RUNNING = "state_running";
    private static final String S_END = "state_end";
    private static final String S_TIME_UP = "state_time_up";

    /** Source of the current time in milliseconds. */
    interface Clock {
        long now();
    }

    /** Wall-clock time, so a running timer keeps time while the app is closed. Tests replace it. */
    @androidx.annotation.VisibleForTesting
    static Clock clock = System::currentTimeMillis;

    private SharedPreferences prefs;
    TextView timertxt, timer1;
    MaterialButton startstop_timer, reset_timer;
    Button getready, watchmedia, cleanup;
    private MaterialButtonToggleGroup presetGroup;
    private CircularProgressIndicator progress;

    /** Which routine is chosen: one of the KEY_ values, or null. */
    private String preset;
    private long totalMillis = 0;
    private long remainingMillis = 0;
    /** When a running timer ends, in wall-clock time (survives the app being closed). */
    private long endTimeMillis = 0;
    private boolean isTimerOn = false;
    private boolean timeUp = false;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            remainingMillis = endTimeMillis - clock.now();
            if (remainingMillis <= 0) {
                remainingMillis = 0;
                onTimeUp(true);
                return;
            }
            updateTimer();
            // Wake up exactly when the shown second changes.
            long untilNextSecond = remainingMillis % 1000;
            handler.postDelayed(this, untilNextSecond == 0 ? 1000 : untilNextSecond);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fam_app_timer);
        prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE);

        timertxt = findViewById(R.id.timertext);
        timer1 = findViewById(R.id.timer1);
        getready = findViewById(R.id.getready);
        watchmedia = findViewById(R.id.watchmedia);
        cleanup = findViewById(R.id.cleanup);
        startstop_timer = findViewById(R.id.startstoptimer);
        reset_timer = findViewById(R.id.resettimer);
        presetGroup = findViewById(R.id.presetGroup);
        progress = findViewById(R.id.timerProgress);

        startstop_timer.setOnClickListener(v -> {
            if (isTimerOn) {
                pauseTimer();
            } else {
                startTimer();
            }
        });
        reset_timer.setOnClickListener(v -> resetTimer());
        findViewById(R.id.editTimes).setOnClickListener(v -> editTimes());

        updatePresetLabels();
        restoreState();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(ticker);
    }

    // ---------- For the Home screen ----------

    /** Whether a timer is counting down right now. */
    static boolean isRunning(SharedPreferences prefs) {
        return prefs.getBoolean(S_RUNNING, false) && prefs.getLong(S_END, 0) > clock.now();
    }

    /** Time left on a running or paused timer, or 0 when no timer is set. */
    static long remainingMillis(SharedPreferences prefs, long now) {
        if (prefs.getBoolean(S_RUNNING, false)) {
            return Math.max(0, prefs.getLong(S_END, 0) - now);
        }
        return prefs.getString(S_PRESET, null) == null ? 0 : prefs.getLong(S_REMAINING, 0);
    }

    /** 00:12:40 */
    static String format(long millis) {
        long totalSeconds = (millis + 999) / 1000;
        return String.format(Locale.getDefault(), "%02d:%02d:%02d",
                totalSeconds / 3600, (totalSeconds / 60) % 60, totalSeconds % 60);
    }

    // ---------- Settings ----------

    static int minutes(SharedPreferences prefs, String key) {
        int fallback = KEY_GET_READY.equals(key) ? DEFAULT_MINUTES[0]
                : KEY_SCREEN_TIME.equals(key) ? DEFAULT_MINUTES[1] : DEFAULT_MINUTES[2];
        return prefs.getInt("minutes_" + key, fallback);
    }

    private void updatePresetLabels() {
        getready.setText(getString(R.string.timer_preset_label, getString(R.string.timer_name_get_ready), minutes(prefs, KEY_GET_READY)));
        watchmedia.setText(getString(R.string.timer_preset_label, getString(R.string.timer_name_screen_time), minutes(prefs, KEY_SCREEN_TIME)));
        cleanup.setText(getString(R.string.timer_preset_label, getString(R.string.timer_name_clean_up), minutes(prefs, KEY_CLEAN_UP)));
    }

    private void editTimes() {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_timer_times, null);
        String[] keys = {KEY_GET_READY, KEY_SCREEN_TIME, KEY_CLEAN_UP};
        int[] inputs = {R.id.minutesGetReady, R.id.minutesScreenTime, R.id.minutesCleanUp};
        int[] layouts = {R.id.minutesGetReadyLayout, R.id.minutesScreenTimeLayout, R.id.minutesCleanUpLayout};
        for (int i = 0; i < keys.length; i++) {
            ((TextInputEditText) view.findViewById(inputs[i])).setText(String.valueOf(minutes(prefs, keys[i])));
        }
        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.timer_edit_times)
                .setView(view)
                .setPositiveButton(R.string.save, null)
                .setNegativeButton(R.string.cancel, null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            int[] values = new int[keys.length];
            boolean ok = true;
            for (int i = 0; i < keys.length; i++) {
                values[i] = Dialogs.number(view.findViewById(inputs[i]), -1);
                boolean valid = values[i] >= 1 && values[i] <= MAX_MINUTES;
                ((TextInputLayout) view.findViewById(layouts[i])).setError(valid ? null : getString(R.string.timer_minutes_error, MAX_MINUTES));
                ok &= valid;
            }
            if (!ok) {
                return;
            }
            SharedPreferences.Editor editor = prefs.edit();
            for (int i = 0; i < keys.length; i++) {
                editor.putInt("minutes_" + keys[i], values[i]);
            }
            editor.apply();
            updatePresetLabels();
            // A chosen routine that hasn't started yet gets its new length straight away.
            if (preset != null && !isTimerOn && remainingMillis == totalMillis) {
                totalMillis = remainingMillis = minutes(prefs, preset) * 60_000L;
                updateTimer();
                saveState();
            }
            dialog.dismiss();
        }));
        dialog.show();
    }

    // ---------- Saved state ----------

    private void saveState() {
        prefs.edit()
                .putString(S_PRESET, preset)
                .putLong(S_TOTAL, totalMillis)
                .putLong(S_REMAINING, remainingMillis)
                .putBoolean(S_RUNNING, isTimerOn)
                .putLong(S_END, endTimeMillis)
                .putBoolean(S_TIME_UP, timeUp)
                .apply();
    }

    private void restoreState() {
        preset = prefs.getString(S_PRESET, null);
        totalMillis = prefs.getLong(S_TOTAL, 0);
        remainingMillis = prefs.getLong(S_REMAINING, 0);
        endTimeMillis = prefs.getLong(S_END, 0);
        timeUp = prefs.getBoolean(S_TIME_UP, false);
        boolean running = prefs.getBoolean(S_RUNNING, false);
        if (running) {
            remainingMillis = endTimeMillis - clock.now();
            if (remainingMillis > 0) {
                showLabel();
                startTimer();
                return;
            }
            // It finished while the app was closed.
            remainingMillis = 0;
            onTimeUp(false);
            return;
        }
        showLabel();
        updateTimer();
        updateButtons();
    }

    private void showLabel() {
        if (timeUp) {
            timertxt.setText(R.string.timer_time_up);
        } else if (KEY_GET_READY.equals(preset)) {
            timertxt.setText(R.string.timer_get_ready);
        } else if (KEY_SCREEN_TIME.equals(preset)) {
            timertxt.setText(R.string.timer_watch_media);
        } else if (KEY_CLEAN_UP.equals(preset)) {
            timertxt.setText(R.string.timer_clean_up);
        } else {
            timertxt.setText(R.string.timer_choose_routine);
        }
    }

    // ---------- Timer ----------

    public void startTimer() {
        if (remainingMillis <= 0) {
            Toast.makeText(this, R.string.timer_choose_first, Toast.LENGTH_SHORT).show();
            return;
        }
        handler.removeCallbacks(ticker);
        // The end time is fixed; the remaining time is always worked out from the clock.
        endTimeMillis = clock.now() + remainingMillis;
        isTimerOn = true;
        timeUp = false;
        handler.post(ticker);
        updateButtons();
        saveState();
    }

    public void pauseTimer() {
        handler.removeCallbacks(ticker);
        remainingMillis = Math.max(0, endTimeMillis - clock.now());
        isTimerOn = false;
        updateTimer();
        updateButtons();
        saveState();
    }

    public void resetTimer() {
        handler.removeCallbacks(ticker);
        isTimerOn = false;
        timeUp = false;
        remainingMillis = 0;
        totalMillis = 0;
        preset = null;
        showLabel();
        updateTimer();
        updateButtons();
        saveState();
    }

    private void onTimeUp(boolean playSound) {
        handler.removeCallbacks(ticker);
        isTimerOn = false;
        timeUp = true;
        preset = null;
        totalMillis = 0;
        showLabel();
        updateTimer();
        updateButtons();
        saveState();
        if (playSound) {
            playAlert();
        }
    }

    private void playAlert() {
        Uri sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        Ringtone ringtone = RingtoneManager.getRingtone(this, sound);
        if (ringtone != null) {
            ringtone.play();
        }
    }

    public void updateTimer() {
        // Rounds up, so the display shows 00:00:01 until the very end.
        timer1.setText(format(remainingMillis));
        int fraction = totalMillis > 0 ? (int) (1000 * remainingMillis / totalMillis) : 0;
        progress.setProgress(fraction);
    }

    /** Highlights the chosen routine, and shows Pause while running and Start/Resume otherwise. */
    private void updateButtons() {
        int id = KEY_GET_READY.equals(preset) ? R.id.getready
                : KEY_SCREEN_TIME.equals(preset) ? R.id.watchmedia
                : KEY_CLEAN_UP.equals(preset) ? R.id.cleanup : View.NO_ID;
        if (id == View.NO_ID) {
            presetGroup.clearChecked();
        } else {
            presetGroup.check(id);
        }
        if (isTimerOn) {
            startstop_timer.setText(R.string.timer_pause);
            startstop_timer.setIconResource(R.drawable.ic_pause);
        } else {
            boolean paused = remainingMillis > 0 && remainingMillis < totalMillis;
            startstop_timer.setText(paused ? R.string.timer_resume : R.string.timer_start);
            startstop_timer.setIconResource(R.drawable.ic_play_arrow);
        }
        reset_timer.setEnabled(remainingMillis > 0 || isTimerOn || totalMillis > 0 || timeUp);
        if (isTimerOn) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    private void choosePreset(String key) {
        handler.removeCallbacks(ticker);
        isTimerOn = false;
        timeUp = false;
        preset = key;
        totalMillis = remainingMillis = minutes(prefs, key) * 60_000L;
        showLabel();
        updateTimer();
        updateButtons();
        saveState();
    }

    public void getready(View v) {
        choosePreset(KEY_GET_READY);
    }

    public void watchmedia(View v) {
        choosePreset(KEY_SCREEN_TIME);
    }

    public void cleanup(View v) {
        choosePreset(KEY_CLEAN_UP);
    }
}
