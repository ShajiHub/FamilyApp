package com.shajitha.familyapp.reminders;

import android.content.ContentResolver;
import android.content.Context;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;

import androidx.annotation.NonNull;

import com.shajitha.familyapp.R;

/** FamilyApp's gentle chime (res/raw/gentle_chime.wav, made for FamilyApp, no licence needed). */
public final class Sounds {

    private Sounds() {
    }

    public static Uri chimeUri(@NonNull Context context) {
        return Uri.parse(ContentResolver.SCHEME_ANDROID_RESOURCE + "://" + context.getPackageName() + "/" + R.raw.gentle_chime);
    }

    /** Plays the chime once (used when the Timer finishes on screen). Never throws. */
    public static void playChime(@NonNull Context context) {
        try {
            MediaPlayer player = new MediaPlayer();
            player.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
            player.setDataSource(context.getApplicationContext(), chimeUri(context));
            player.setOnCompletionListener(MediaPlayer::release);
            player.setOnErrorListener((mp, what, extra) -> {
                mp.release();
                return true;
            });
            player.setOnPreparedListener(MediaPlayer::start);
            player.prepareAsync();
        } catch (Exception ignored) {
            // No sound is fine; the screen still shows "Time Up".
        }
    }
}
