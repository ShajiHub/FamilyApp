package com.shajitha.familyapp;

import androidx.appcompat.app.AppCompatActivity;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;
import android.provider.CalendarContract;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class AddEvents extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_events);
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        TextView dateToday = findViewById(R.id.datetoday);
        SimpleDateFormat dateFormat = new SimpleDateFormat("EEE, MMM d, ''yy", Locale.getDefault());
        dateToday.setText(dateFormat.format(Calendar.getInstance().getTime()));
    }

    /** Opens the phone's calendar app with a new one-hour event starting at the next full hour. */
    public void AddEvent(View view) {
        Calendar start = Calendar.getInstance();
        start.add(Calendar.HOUR_OF_DAY, 1);
        start.set(Calendar.MINUTE, 0);
        start.set(Calendar.SECOND, 0);
        start.set(Calendar.MILLISECOND, 0);
        long begin = start.getTimeInMillis();

        Intent event = new Intent(Intent.ACTION_INSERT)
                .setData(CalendarContract.Events.CONTENT_URI)
                .putExtra(CalendarContract.Events.TITLE, getString(R.string.event_default_title))
                .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin)
                .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, begin + 60 * 60 * 1000);
        try {
            startActivity(event);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.no_calendar_app, Toast.LENGTH_SHORT).show();
        }
    }

    public void addCalendar(View v) {
        startActivity(new Intent(this, viewCalendar.class));
    }
}
