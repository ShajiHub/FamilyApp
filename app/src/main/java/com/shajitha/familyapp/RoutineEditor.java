package com.shajitha.familyapp;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.shajitha.familyapp.data.AppDatabase;
import com.shajitha.familyapp.data.DataSetup;
import com.shajitha.familyapp.data.FamilyMember;
import com.shajitha.familyapp.data.Recurrence;
import com.shajitha.familyapp.data.Reminder;
import com.shajitha.familyapp.reminders.ReminderSettings;
import com.shajitha.familyapp.reminders.Reminders;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

/** The add/edit dialog shared by Chores and Habits (both are reminders underneath). */
final class RoutineEditor {

    /** Repeat choices for chores: {unit, interval}. */
    static final String[][] REPEATS = {{Recurrence.NONE, "1"}, {Recurrence.DAY, "1"}, {Recurrence.WEEK, "1"},
            {Recurrence.WEEK, "2"}, {Recurrence.MONTH, "1"}};
    static final int[] REPEAT_LABELS = {R.string.repeat_once, R.string.repeat_daily, R.string.repeat_weekly,
            R.string.repeat_fortnightly, R.string.repeat_monthly};

    private RoutineEditor() {
    }

    static String repeatLabel(Context context, Reminder r) {
        for (int i = 0; i < REPEATS.length; i++) {
            if (REPEATS[i][0].equals(r.repeatUnit) && Integer.parseInt(REPEATS[i][1]) == r.repeatInterval) {
                return context.getString(REPEAT_LABELS[i]);
            }
        }
        return context.getString(R.string.repeat_once);
    }

    /**
     * Shows the dialog. {@code existing} null = new; {@code presetTitle} fills in a suggestion.
     * Habits (type HABIT) always repeat daily, so the repeat and date fields are hidden.
     */
    static AlertDialog show(@NonNull Context context, @NonNull String type, @Nullable Reminder existing,
                            @Nullable String presetTitle, @Nullable List<FamilyMember> members) {
        boolean habit = Reminder.TYPE_HABIT.equals(type);
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_routine, null);
        TextInputLayout titleLayout = view.findViewById(R.id.routineTitleLayout);
        TextInputEditText title = view.findViewById(R.id.routineTitleInput);
        MaterialAutoCompleteTextView who = view.findViewById(R.id.routineMember);
        MaterialAutoCompleteTextView repeat = view.findViewById(R.id.routineRepeat);
        TextInputEditText date = view.findViewById(R.id.routineDate);
        MaterialSwitch remind = view.findViewById(R.id.routineRemind);
        TextInputEditText time = view.findViewById(R.id.routineTime);
        View timeLayout = view.findViewById(R.id.routineTimeLayout);

        titleLayout.setHint(habit ? R.string.habits_title_hint : R.string.routine_title_hint);
        title.setText(existing != null ? existing.title : presetTitle);
        Members.attachPicker(who, members, existing == null ? null : existing.memberId);

        // Repeat
        String[] labels = new String[REPEATS.length];
        for (int i = 0; i < REPEATS.length; i++) {
            labels[i] = context.getString(REPEAT_LABELS[i]);
        }
        repeat.setSimpleItems(labels);
        final int[] repeatIndex = {2}; // every week by default
        if (existing != null) {
            for (int i = 0; i < REPEATS.length; i++) {
                if (REPEATS[i][0].equals(existing.repeatUnit) && Integer.parseInt(REPEATS[i][1]) == existing.repeatInterval) {
                    repeatIndex[0] = i;
                }
            }
        }
        repeat.setText(labels[repeatIndex[0]], false);
        repeat.setOnItemClickListener((p, v, position, id) -> repeatIndex[0] = position);

        // Date
        final String[] day = {existing != null ? existing.dueDate : DataSetup.today()};
        date.setText(formatDay(context, day[0]));
        date.setOnClickListener(v -> {
            Recurrence.Day d = Recurrence.Day.parse(day[0]);
            new DatePickerDialog(context, (picker, y, m, dd) -> {
                day[0] = new Recurrence.Day(y, m + 1, dd).toString();
                date.setText(formatDay(context, day[0]));
            }, d.year, d.month - 1, d.day).show();
        });
        view.findViewById(R.id.routineRepeatLayout).setVisibility(habit ? View.GONE : View.VISIBLE);
        view.findViewById(R.id.routineDateLayout).setVisibility(habit ? View.GONE : View.VISIBLE);

        // Reminder
        boolean reminderOn = existing != null && !existing.alertDays.isEmpty();
        final String[] alertTime = {existing != null && existing.alertTime != null ? existing.alertTime
                : ReminderSettings.alertTime(context)};
        remind.setChecked(reminderOn);
        timeLayout.setVisibility(reminderOn ? View.VISIBLE : View.GONE);
        remind.setOnCheckedChangeListener((b, on) -> timeLayout.setVisibility(on ? View.VISIBLE : View.GONE));
        time.setText(formatTime(context, alertTime[0]));
        time.setOnClickListener(v -> {
            int[] t = Recurrence.parseTime(alertTime[0]);
            new TimePickerDialog(context, (picker, h, m) -> {
                alertTime[0] = String.format(Locale.US, "%02d:%02d", h, m);
                time.setText(formatTime(context, alertTime[0]));
            }, t[0], t[1], DateFormat.is24HourFormat(context)).show();
        });

        int dialogTitle = habit ? (existing == null ? R.string.habits_add : R.string.habits_edit)
                : (existing == null ? R.string.chores_add : R.string.chores_edit);
        AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                .setTitle(dialogTitle)
                .setView(view)
                .setPositiveButton(R.string.save, null)
                .setNegativeButton(R.string.cancel, null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String text = Dialogs.text(title);
            if (text.isEmpty()) {
                titleLayout.setError(context.getString(R.string.error_required));
                return;
            }
            Reminder r = existing != null ? existing : Reminders.create(text, type, day[0], null);
            r.title = text.length() > Reminder.MAX_TITLE_LENGTH ? text.substring(0, Reminder.MAX_TITLE_LENGTH) : text;
            r.memberId = Members.selected(who);
            if (habit) {
                r.repeatUnit = Recurrence.DAY;
                r.repeatInterval = 1;
                if (existing == null) {
                    r.dueDate = DataSetup.today();
                }
            } else {
                r.repeatUnit = REPEATS[repeatIndex[0]][0];
                r.repeatInterval = Integer.parseInt(REPEATS[repeatIndex[0]][1]);
                if (existing == null || !day[0].equals(existing.dueDate)) {
                    r.dueDate = day[0];
                    Recurrence.Day chosen = Recurrence.Day.parse(day[0]);
                    r.anchorDay = chosen == null ? 0 : chosen.day;
                }
            }
            r.alertDays = remind.isChecked() ? "0" : "";
            r.alertTime = remind.isChecked() ? alertTime[0] : null;
            Context app = context.getApplicationContext();
            AppDatabase.write(() -> Reminders.save(app, r));
            dialog.dismiss();
        }));
        Dialogs.showWithKeyboard(dialog);
        return dialog;
    }

    static String formatDay(Context context, String yyyyMmDd) {
        try {
            return java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM)
                    .format(new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(yyyyMmDd));
        } catch (Exception e) {
            return yyyyMmDd;
        }
    }

    static String formatTime(Context context, String hhmm) {
        try {
            return DateFormat.getTimeFormat(context).format(new SimpleDateFormat("HH:mm", Locale.US).parse(hhmm));
        } catch (Exception e) {
            return hhmm;
        }
    }
}
