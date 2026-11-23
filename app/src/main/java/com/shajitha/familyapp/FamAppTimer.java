package com.shajitha.familyapp;


import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.progressindicator.CircularProgressIndicator;

import java.util.Locale;

public class FamAppTimer extends BaseActivity {

    private static final long GET_READY_MILLIS = 15 * 60 * 1000L;
    private static final long WATCH_MEDIA_MILLIS = 60 * 60 * 1000L;
    private static final long CLEAN_UP_MILLIS = 30 * 60 * 1000L;

    // Keys for keeping the timer going when the phone is rotated.
    private static final String STATE_REMAINING = "remaining";
    private static final String STATE_RUNNING = "running";
    private static final String STATE_END_TIME = "endTime";
    private static final String STATE_LABEL = "label";
    private static final String STATE_PRESET = "preset";
    private static final String STATE_TOTAL = "total";

    TextView timertxt, timer1;
    MaterialButton startstop_timer, reset_timer;
    Button getready, watchmedia, cleanup;
    private MaterialButtonToggleGroup presetGroup;
    private CircularProgressIndicator progress;
    /** Length of the chosen routine, for the progress ring. */
    private long totalMillis = 0;

    /** Updates the display a few times a second while running. */
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            remainingMillis = endTimeMillis - SystemClock.elapsedRealtime();
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

    private long remainingMillis = 0;
    private long endTimeMillis = 0;
    private boolean isTimerOn = false;
    private int selectedPresetId = View.NO_ID;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fam_app_timer);

        timertxt = findViewById(R.id.timertext);
        timer1 = findViewById(R.id.timer1);
        presetGroup = findViewById(R.id.presetGroup);
        progress = findViewById(R.id.timerProgress);
        getready = findViewById(R.id.getready);
        watchmedia = findViewById(R.id.watchmedia);
        cleanup = findViewById(R.id.cleanup);
        startstop_timer = findViewById(R.id.startstoptimer);
        reset_timer = findViewById(R.id.resettimer);

        startstop_timer.setOnClickListener(v -> {
            if (isTimerOn) {
                pauseTimer();
            } else {
                startTimer();
            }
        });
        reset_timer.setOnClickListener(v -> resetTimer());

        if (savedInstanceState != null) {
            restoreState(savedInstanceState);
        }
        updateTimer();
        updateButtons();
    }

    private void restoreState(Bundle state) {
        remainingMillis = state.getLong(STATE_REMAINING);
        totalMillis = state.getLong(STATE_TOTAL);
        timertxt.setText(state.getCharSequence(STATE_LABEL));
        selectedPresetId = state.getInt(STATE_PRESET, View.NO_ID);
        if (state.getBoolean(STATE_RUNNING)) {
            // Work out how much time passed while the screen was being recreated.
            remainingMillis = state.getLong(STATE_END_TIME) - SystemClock.elapsedRealtime();
            if (remainingMillis > 0) {
                startTimer();
            } else {
                remainingMillis = 0;
                onTimeUp(false);
            }
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putLong(STATE_REMAINING, remainingMillis);
        outState.putBoolean(STATE_RUNNING, isTimerOn);
        outState.putLong(STATE_END_TIME, endTimeMillis);
        outState.putCharSequence(STATE_LABEL, timertxt.getText());
        outState.putInt(STATE_PRESET, selectedPresetId);
        outState.putLong(STATE_TOTAL, totalMillis);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cancelCountdown();
    }

    public void startTimer() {
        if (remainingMillis <= 0) {
            Toast.makeText(this, R.string.timer_choose_first, Toast.LENGTH_SHORT).show();
            return;
        }
        cancelCountdown();
        // The end time is fixed; the remaining time is always worked out from the clock,
        // so the display never drifts.
        endTimeMillis = SystemClock.elapsedRealtime() + remainingMillis;
        handler.post(ticker);
        isTimerOn = true;
        updateButtons();
    }

    public void pauseTimer() {
        cancelCountdown();
        remainingMillis = Math.max(0, endTimeMillis - SystemClock.elapsedRealtime());
        updateTimer();
        isTimerOn = false;
        updateButtons();
    }

    public void resetTimer() {
        cancelCountdown();
        isTimerOn = false;
        remainingMillis = 0;
        totalMillis = 0;
        selectedPresetId = View.NO_ID;
        timertxt.setText(R.string.timer_choose_routine);
        updateTimer();
        updateButtons();
    }

    private void onTimeUp(boolean playSound) {
        isTimerOn = false;
        cancelCountdown();
        selectedPresetId = View.NO_ID;
        totalMillis = 0;
        timertxt.setText(R.string.timer_time_up);
        updateTimer();
        updateButtons();
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

    private void cancelCountdown() {
        handler.removeCallbacks(ticker);
    }

    public void updateTimer() {
        // Round up so the display shows 00:00:01 until the very end.
        long totalSeconds = (remainingMillis + 999) / 1000;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds / 60) % 60;
        long seconds = totalSeconds % 60;
        timer1.setText(String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds));
        int fraction = totalMillis > 0 ? (int) (1000 * remainingMillis / totalMillis) : 0;
        progress.setProgress(fraction);
    }

    /** Highlights the chosen routine, and shows Pause while running and Start/Resume otherwise. */
    private void updateButtons() {
        if (selectedPresetId == View.NO_ID) {
            presetGroup.clearChecked();
        } else {
            presetGroup.check(selectedPresetId);
        }
        if (isTimerOn) {
            startstop_timer.setText(R.string.timer_pause);
            startstop_timer.setIconResource(R.drawable.ic_pause);
        } else {
            boolean paused = remainingMillis > 0 && remainingMillis < totalMillis;
            startstop_timer.setText(paused ? R.string.timer_resume : R.string.timer_start);
            startstop_timer.setIconResource(R.drawable.ic_play_arrow);
        }
        reset_timer.setEnabled(remainingMillis > 0 || isTimerOn || totalMillis > 0);
        if (isTimerOn) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    private void choosePreset(Button preset, int labelRes, long millis) {
        cancelCountdown();
        isTimerOn = false;
        selectedPresetId = preset.getId();
        timertxt.setText(labelRes);
        remainingMillis = millis;
        totalMillis = millis;
        updateTimer();
        updateButtons();
    }

    public void getready(View v) {
        choosePreset(getready, R.string.timer_get_ready, GET_READY_MILLIS);
    }

    public void watchmedia(View v) {
        choosePreset(watchmedia, R.string.timer_watch_media, WATCH_MEDIA_MILLIS);
    }

    public void cleanup(View v) {
        choosePreset(cleanup, R.string.timer_clean_up, CLEAN_UP_MILLIS);
    }
}
