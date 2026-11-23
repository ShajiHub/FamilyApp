package com.shajitha.familyapp;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CalendarView;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/** Monthly Schedule: pick a day on the calendar and save plans for it. */
public class viewCalendar extends AppCompatActivity {

    private static final String KEY_PREFIX = "schedule_";
    private static final String STATE_DATE = "selectedDate";

    private final Calendar selected = Calendar.getInstance();
    private final List<String> plans = new ArrayList<>();
    private ArrayAdapter<String> adapter;
    private TextView dateText, monthCount;
    private EditText input;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_view_calendar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        CalendarView calendar = findViewById(R.id.calender);
        dateText = findViewById(R.id.scheduleDate);
        monthCount = findViewById(R.id.scheduleMonthCount);
        input = findViewById(R.id.scheduleInput);
        Button add = findViewById(R.id.scheduleAdd);
        ListView list = findViewById(R.id.scheduleList);

        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, plans);
        list.setAdapter(adapter);
        list.setEmptyView(findViewById(R.id.scheduleEmpty));

        if (savedInstanceState != null) {
            selected.setTimeInMillis(savedInstanceState.getLong(STATE_DATE));
        }
        calendar.setDate(selected.getTimeInMillis(), false, true);

        calendar.setOnDateChangeListener((view, year, month, dayOfMonth) -> {
            selected.set(year, month, dayOfMonth);
            showSelectedDay();
        });

        add.setOnClickListener(v -> addPlan());
        input.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                addPlan();
                return true;
            }
            return false;
        });

        // Long-press a plan to delete it.
        list.setOnItemLongClickListener((parent, view, position, id) -> {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.delete_item_title)
                    .setMessage(plans.get(position))
                    .setPositiveButton(R.string.delete, (dialog, which) -> {
                        plans.remove(position);
                        savePlans();
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
            return true;
        });

        showSelectedDay();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putLong(STATE_DATE, selected.getTimeInMillis());
    }

    private void addPlan() {
        String text = input.getText().toString().trim();
        if (text.isEmpty()) {
            return;
        }
        plans.add(text);
        input.setText("");
        savePlans();
    }

    private void savePlans() {
        String key = dayKey(selected);
        if (plans.isEmpty()) {
            FamilyStore.remove(this, key);
        } else {
            FamilyStore.putStringList(this, key, plans);
        }
        adapter.notifyDataSetChanged();
        updateMonthCount();
    }

    private void showSelectedDay() {
        dateText.setText(DateFormat.getDateInstance(DateFormat.FULL).format(selected.getTime()));
        plans.clear();
        plans.addAll(FamilyStore.getStringList(this, dayKey(selected)));
        adapter.notifyDataSetChanged();
        updateMonthCount();
    }

    /** Shows how many plans are saved in the selected day's month. */
    private void updateMonthCount() {
        String monthPrefix = String.format(Locale.US, "%s%04d-%02d-", KEY_PREFIX,
                selected.get(Calendar.YEAR), selected.get(Calendar.MONTH) + 1);
        int count = 0;
        for (String key : FamilyStore.keysWithPrefix(this, monthPrefix)) {
            count += FamilyStore.getStringList(this, key).size();
        }
        monthCount.setText(getResources().getQuantityString(R.plurals.schedule_month_count, count, count));
    }

    private static String dayKey(Calendar day) {
        return String.format(Locale.US, "%s%04d-%02d-%02d", KEY_PREFIX,
                day.get(Calendar.YEAR), day.get(Calendar.MONTH) + 1, day.get(Calendar.DAY_OF_MONTH));
    }
}
