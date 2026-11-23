package com.shajitha.familyapp;

import androidx.appcompat.app.AppCompatActivity;

import android.graphics.Color;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.SystemClock;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

public class FamAppTimer extends AppCompatActivity {

    private static final long GET_READY_MILLIS = 15 * 60 * 1000L;
    private static final long WATCH_MEDIA_MILLIS = 60 * 60 * 1000L;
    private static final long CLEAN_UP_MILLIS = 30 * 60 * 1000L;

    private static final String PRESET_COLOR = "#E8D46D";

    // Keys for keeping the timer going when the phone is rotated.
    private static final String STATE_REMAINING = "remaining";
    private static final String STATE_RUNNING = "running";
    private static final String STATE_END_TIME = "endTime";
    private static final String STATE_LABEL = "label";
    private static final String STATE_PRESET = "preset";

    TextView timertxt, timer1, startLabel;
    ImageView startstop_timer, reset_timer;
    Button getready, watchmedia, cleanup;

    private CountDownTimer countdownTimer;
    private long remainingMillis = 0;
    private long endTimeMillis = 0;
    private boolean isTimerOn = false;
    private int selectedPresetId = View.NO_ID;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fam_app_timer);
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        timertxt = findViewById(R.id.timertext);
        timer1 = findViewById(R.id.timer1);
        startLabel = findViewById(R.id.textView11); // only in the portrait layout
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
        endTimeMillis = SystemClock.elapsedRealtime() + remainingMillis;
        countdownTimer = new CountDownTimer(remainingMillis, 250) {
            @Override
            public void onTick(long millisUntilFinished) {
                remainingMillis = millisUntilFinished;
                updateTimer();
            }

            @Override
            public void onFinish() {
                remainingMillis = 0;
                onTimeUp(true);
            }
        }.start();
        isTimerOn = true;
        updateButtons();
    }

    public void pauseTimer() {
        cancelCountdown();
        isTimerOn = false;
        updateButtons();
    }

    public void resetTimer() {
        cancelCountdown();
        isTimerOn = false;
        remainingMillis = 0;
        selectedPresetId = View.NO_ID;
        timertxt.setText("");
        updateTimer();
        updateButtons();
    }

    private void onTimeUp(boolean playSound) {
        isTimerOn = false;
        countdownTimer = null;
        selectedPresetId = View.NO_ID;
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
        if (countdownTimer != null) {
            countdownTimer.cancel();
            countdownTimer = null;
        }
    }

    public void updateTimer() {
        // Round up so the display shows 00:00:01 until the very end.
        long totalSeconds = (remainingMillis + 999) / 1000;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds / 60) % 60;
        long seconds = totalSeconds % 60;
        timer1.setText(String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds));
    }

    /** Highlights the chosen preset, and shows a pause icon while running and a start icon otherwise. */
    private void updateButtons() {
        for (Button preset : new Button[]{getready, watchmedia, cleanup}) {
            boolean selected = preset.getId() == selectedPresetId;
            preset.setBackgroundColor(selected ? Color.LTGRAY : Color.parseColor(PRESET_COLOR));
        }
        startstop_timer.setImageResource(isTimerOn ? R.drawable.pause : R.drawable.starttimer);
        if (startLabel != null) {
            startLabel.setText(isTimerOn ? R.string.timer_pause : R.string.timer_start);
        }
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
