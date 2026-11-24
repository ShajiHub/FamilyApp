package com.shajitha.familyapp;

import android.app.TimePickerDialog;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CalendarView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.shajitha.familyapp.data.AppDatabase;
import com.shajitha.familyapp.data.FamilyDao;
import com.shajitha.familyapp.data.ScheduleEvent;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Monthly Schedule: pick a day and add, edit or delete its events (title, optional time, notes). */
public class viewCalendar extends BaseActivity {

    private static final String STATE_DATE = "selectedDate";

    private final Calendar selected = Calendar.getInstance();
    private final MutableLiveData<String> selectedDay = new MutableLiveData<>();
    private FamilyDao dao;
    private TextView dateText, monthInfo, empty;
    private LinearLayout list;
    private int monthCount = 0;
    private List<String> monthDays = new ArrayList<>();
    private List<com.shajitha.familyapp.data.FamilyMember> members = new ArrayList<>();
    private List<ScheduleEvent> shownEvents = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_view_calendar);
        dao = AppDatabase.get(this).dao();

        CalendarView calendar = findViewById(R.id.calender);
        dateText = findViewById(R.id.scheduleDate);
        monthInfo = findViewById(R.id.scheduleMonthCount);
        empty = findViewById(R.id.scheduleEmpty);
        list = findViewById(R.id.scheduleList);
        Button add = findViewById(R.id.scheduleAdd);

        if (savedInstanceState != null) {
            selected.setTimeInMillis(savedInstanceState.getLong(STATE_DATE));
        }
        calendar.setDate(selected.getTimeInMillis(), false, true);
        calendar.setOnDateChangeListener((view, year, month, dayOfMonth) -> {
            selected.set(year, month, dayOfMonth);
            selectDay();
        });
        add.setOnClickListener(v -> showEventDialog(null));

        dao.allMembers().observe(this, all -> {
            members = all;
            showEvents(shownEvents);
        });
        LiveData<List<ScheduleEvent>> events = Transformations.switchMap(selectedDay, dao::eventsOn);
        events.observe(this, this::showEvents);
        LiveData<String> month = Transformations.distinctUntilChanged(
                Transformations.map(selectedDay, day -> day.substring(0, 8)));
        Transformations.switchMap(month, dao::eventCountInMonth).observe(this, count -> {
            monthCount = count == null ? 0 : count;
            showMonthInfo();
        });
        Transformations.switchMap(month, dao::daysWithEvents).observe(this, days -> {
            monthDays = days;
            showMonthInfo();
        });
        selectDay();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putLong(STATE_DATE, selected.getTimeInMillis());
    }

    private void selectDay() {
        dateText.setText(java.text.DateFormat.getDateInstance(java.text.DateFormat.FULL).format(selected.getTime()));
        selectedDay.setValue(dayKey(selected));
    }

    private void showMonthInfo() {
        String text = getResources().getQuantityString(R.plurals.schedule_month_count, monthCount, monthCount);
        if (!monthDays.isEmpty()) {
            List<Integer> days = new ArrayList<>();
            for (String d : monthDays) {
                days.add(Integer.parseInt(d.substring(8)));
            }
            Collections.sort(days);
            StringBuilder sb = new StringBuilder();
            for (int d : days) {
                sb.append(sb.length() == 0 ? "" : ", ").append(d);
            }
            text = getString(R.string.schedule_month_days, text, sb.toString());
        }
        monthInfo.setText(text);
    }

    private void showEvents(List<ScheduleEvent> events) {
        shownEvents = events;
        list.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        for (ScheduleEvent event : events) {
            View card = inflater.inflate(R.layout.item_event, list, false);
            ((TextView) card.findViewById(R.id.eventTime)).setText(
                    event.time == null ? getString(R.string.event_all_day) : formatTime(event.time));
            ((TextView) card.findViewById(R.id.eventTitle)).setText(event.title);
            com.shajitha.familyapp.data.FamilyMember member = Members.find(members, event.memberId);
            TextView who = card.findViewById(R.id.eventMember);
            who.setText(member == null ? "" : Members.label(member));
            who.setVisibility(member == null ? View.GONE : View.VISIBLE);
            TextView notes = card.findViewById(R.id.eventNotes);
            notes.setText(event.notes);
            notes.setVisibility(event.notes == null || event.notes.isEmpty() ? View.GONE : View.VISIBLE);
            card.setOnClickListener(v -> showEventDialog(event));
            card.findViewById(R.id.eventEdit).setOnClickListener(v -> showEventDialog(event));
            card.findViewById(R.id.eventDelete).setOnClickListener(v -> Dialogs.confirmDelete(this,
                    getString(R.string.event_delete_title), event.title,
                    () -> AppDatabase.write(() -> dao.delete(event))));
            list.addView(card);
        }
        empty.setVisibility(events.isEmpty() ? View.VISIBLE : View.GONE);
    }

    /** Shows "17:30" or "5:30 PM", following the phone's 12/24-hour setting. */
    private String formatTime(String hhmm) {
        try {
            SimpleDateFormat in = new SimpleDateFormat("HH:mm", Locale.US);
            return DateFormat.getTimeFormat(this).format(in.parse(hhmm));
        } catch (Exception e) {
            return hhmm;
        }
    }

    /** Adds a new event (event == null) or edits an existing one. */
    void showEventDialog(ScheduleEvent event) {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_event, null);
        TextInputLayout titleLayout = view.findViewById(R.id.eventTitleLayout);
        TextInputEditText title = view.findViewById(R.id.eventTitleInput);
        TextInputEditText time = view.findViewById(R.id.eventTimeInput);
        TextInputEditText notes = view.findViewById(R.id.eventNotesInput);
        com.google.android.material.textfield.MaterialAutoCompleteTextView who = view.findViewById(R.id.eventMember);
        Members.attachPicker(who, members, event == null ? null : event.memberId);
        final String[] chosenTime = {event == null ? null : event.time};
        if (event != null) {
            title.setText(event.title);
            notes.setText(event.notes);
        }
        if (chosenTime[0] != null) {
            time.setText(formatTime(chosenTime[0]));
        }
        time.setOnClickListener(v -> {
            int hour = 9, minute = 0;
            if (chosenTime[0] != null) {
                hour = Integer.parseInt(chosenTime[0].substring(0, 2));
                minute = Integer.parseInt(chosenTime[0].substring(3, 5));
            }
            new TimePickerDialog(this, (picker, h, m) -> {
                chosenTime[0] = String.format(Locale.US, "%02d:%02d", h, m);
                time.setText(formatTime(chosenTime[0]));
            }, hour, minute, DateFormat.is24HourFormat(this)).show();
        });
        // The clear (x) button on the time field removes the time.
        time.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {
                if (s.length() == 0) {
                    chosenTime[0] = null;
                }
            }
        });

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(event == null ? getString(R.string.event_add_title, dateText.getText()) : getString(R.string.event_edit_title))
                .setView(view)
                .setPositiveButton(R.string.save, null)
                .setNegativeButton(R.string.cancel, null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String titleText = Dialogs.text(title);
            if (titleText.isEmpty()) {
                titleLayout.setError(getString(R.string.error_required));
                return;
            }
            ScheduleEvent e = new ScheduleEvent();
            if (event != null) {
                e.id = event.id;
                e.date = event.date;
                e.createdAt = event.createdAt;
            } else {
                e.date = dayKey(selected);
                e.createdAt = System.currentTimeMillis();
            }
            e.title = titleText;
            e.memberId = Members.selected(who);
            e.time = chosenTime[0];
            String notesText = Dialogs.text(notes);
            e.notes = notesText.isEmpty() ? null : notesText;
            AppDatabase.write(() -> {
                if (event == null) {
                    dao.insert(e);
                } else {
                    dao.update(e);
                }
            });
            dialog.dismiss();
        }));
        Dialogs.showWithKeyboard(dialog);
    }

    static String dayKey(Calendar day) {
        return String.format(Locale.US, "%04d-%02d-%02d",
                day.get(Calendar.YEAR), day.get(Calendar.MONTH) + 1, day.get(Calendar.DAY_OF_MONTH));
    }
}
