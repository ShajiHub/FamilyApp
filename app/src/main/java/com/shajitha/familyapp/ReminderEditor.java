package com.shajitha.familyapp;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.shajitha.familyapp.data.AppDatabase;
import com.shajitha.familyapp.data.FamilyMember;
import com.shajitha.familyapp.data.Recurrence;
import com.shajitha.familyapp.data.Reminder;
import com.shajitha.familyapp.reminders.ReminderSettings;
import com.shajitha.familyapp.reminders.ReminderTemplates;
import com.shajitha.familyapp.reminders.Reminders;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The one add/edit form for every kind of reminder: title, date and optional time, repeat, who it's
 * for, and whether to notify. Alert days, alert time, area and a note are under "More options".
 */
final class ReminderEditor {

    /** Repeat choices: {unit, interval}. */
    static final String[][] REPEATS = {{Recurrence.NONE, "1"}, {Recurrence.DAY, "1"}, {Recurrence.WEEK, "1"},
            {Recurrence.WEEK, "2"}, {Recurrence.MONTH, "1"}, {Recurrence.MONTH, "3"}, {Recurrence.MONTH, "6"},
            {Recurrence.YEAR, "1"}};
    static final int[] REPEAT_LABELS = {R.string.repeat_once, R.string.repeat_daily, R.string.repeat_weekly,
            R.string.repeat_fortnightly, R.string.repeat_monthly, R.string.repeat_quarterly,
            R.string.repeat_half_yearly, R.string.repeat_yearly};

    /** Alert choices, in days before the date. */
    static final int[] ALERT_DAYS = {0, 1, 2, 3, 7, 14, 30, 60, 90, 180};
    static final int[] ALERT_LABELS = {R.string.alert_same_day, R.string.alert_1_day, R.string.alert_2_days,
            R.string.alert_3_days, R.string.alert_1_week, R.string.alert_2_weeks, R.string.alert_30_days,
            R.string.alert_60_days, R.string.alert_3_months, R.string.alert_6_months};

    private ReminderEditor() {
    }

    static int repeatIndex(@NonNull Reminder r) {
        for (int i = 0; i < REPEATS.length; i++) {
            if (REPEATS[i][0].equals(r.repeatUnit) && Integer.parseInt(REPEATS[i][1]) == r.repeatInterval) {
                return i;
            }
        }
        return Recurrence.NONE.equals(r.repeatUnit) ? 0 : -1;
    }

    /** "Repeats every 3 months", or "" for a one-off. */
    static String repeatText(Context context, Reminder r) {
        int i = repeatIndex(r);
        if (i == 0) {
            return "";
        }
        return i > 0 ? context.getString(REPEAT_LABELS[i])
                : context.getString(R.string.repeat_custom, r.repeatInterval);
    }

    /** "1 day before · 9:00 AM" */
    static String alertSummary(Context context, String alertDays, @Nullable String alertTime) {
        List<Integer> days = Recurrence.parseAlertDays(alertDays);
        if (days.isEmpty()) {
            return context.getString(R.string.reminder_no_alerts);
        }
        StringBuilder sb = new StringBuilder();
        for (int d : days) {
            sb.append(sb.length() == 0 ? "" : ", ").append(alertLabel(context, d));
        }
        String time = alertTime != null ? alertTime : ReminderSettings.alertTime(context);
        return context.getString(R.string.reminder_alert_summary, sb.toString(), RoutineEditor.formatTime(context, time));
    }

    static String alertLabel(Context context, int days) {
        for (int i = 0; i < ALERT_DAYS.length; i++) {
            if (ALERT_DAYS[i] == days) {
                return context.getString(ALERT_LABELS[i]);
            }
        }
        return context.getResources().getQuantityString(R.plurals.alert_days_before, days, days);
    }

    /**
     * Shows the form. {@code r} is either a saved reminder (edit) or a new one (id 0) made from a
     * template or blank; nothing is saved until Save.
     */
    static AlertDialog show(@NonNull Context context, @NonNull Reminder r, @Nullable List<FamilyMember> members) {
        boolean isNew = r.id == 0;
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_reminder, null);
        TextInputLayout titleLayout = view.findViewById(R.id.reminderTitleLayout);
        TextInputEditText title = view.findViewById(R.id.reminderTitleInput);
        TextInputEditText date = view.findViewById(R.id.reminderDate);
        TextInputEditText time = view.findViewById(R.id.reminderTime);
        MaterialAutoCompleteTextView repeat = view.findViewById(R.id.reminderRepeat);
        MaterialAutoCompleteTextView who = view.findViewById(R.id.reminderMember);
        MaterialSwitch notify = view.findViewById(R.id.reminderNotify);
        TextView summary = view.findViewById(R.id.reminderAlertSummary);
        Button moreOptions = view.findViewById(R.id.reminderMoreOptions);
        View advanced = view.findViewById(R.id.reminderAdvanced);
        ChipGroup alertChips = view.findViewById(R.id.reminderAlertDays);
        TextInputEditText alertTimeField = view.findViewById(R.id.reminderAlertTime);
        MaterialAutoCompleteTextView area = view.findViewById(R.id.reminderArea);
        TextInputEditText note = view.findViewById(R.id.reminderNote);

        title.setText(r.title);
        title.setSelection(title.length());
        Members.attachPicker(who, members, r.memberId);
        note.setText(r.notes);

        // Date and optional time
        final String[] day = {r.dueDate};
        final String[] dueTime = {r.dueTime};
        date.setText(RoutineEditor.formatDay(context, day[0]));
        date.setOnClickListener(v -> {
            Recurrence.Day d = Recurrence.Day.parse(day[0]);
            new DatePickerDialog(context, (picker, y, m, dd) -> {
                day[0] = new Recurrence.Day(y, m + 1, dd).toString();
                date.setText(RoutineEditor.formatDay(context, day[0]));
            }, d.year, d.month - 1, d.day).show();
        });
        time.setText(dueTime[0] == null ? "" : RoutineEditor.formatTime(context, dueTime[0]));
        time.setOnClickListener(v -> {
            int[] t = dueTime[0] == null ? new int[]{9, 0} : Recurrence.parseTime(dueTime[0]);
            new TimePickerDialog(context, (picker, h, m) -> {
                dueTime[0] = String.format(Locale.US, "%02d:%02d", h, m);
                time.setText(RoutineEditor.formatTime(context, dueTime[0]));
            }, t[0], t[1], DateFormat.is24HourFormat(context)).show();
        });
        time.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (s.length() == 0) {
                    dueTime[0] = null; // cleared with ✕
                }
            }
        });

        // Repeat
        String[] repeatLabels = new String[REPEATS.length];
        for (int i = 0; i < REPEATS.length; i++) {
            repeatLabels[i] = context.getString(REPEAT_LABELS[i]);
        }
        repeat.setSimpleItems(repeatLabels);
        // A repeat the list doesn't offer (from an older version) is kept unless the person changes it.
        boolean customRepeat = repeatIndex(r) < 0;
        final boolean[] repeatChanged = {false};
        final int[] repeatIndex = {Math.max(0, repeatIndex(r))};
        repeat.setText(customRepeat ? repeatText(context, r) : repeatLabels[repeatIndex[0]], false);
        repeat.setOnItemClickListener((p, v, position, id) -> {
            repeatIndex[0] = position;
            repeatChanged[0] = true;
        });

        // Alerts: on/off, which days, what time
        List<Integer> chosen = new ArrayList<>(Recurrence.parseAlertDays(r.alertDays));
        final String[] alertTime = {r.alertTime};
        notify.setChecked(!chosen.isEmpty());
        for (int i = 0; i < ALERT_DAYS.length; i++) {
            Chip chip = new Chip(context, null, com.google.android.material.R.attr.chipStyle);
            chip.setCheckable(true);
            chip.setText(ALERT_LABELS[i]);
            chip.setChecked(chosen.contains(ALERT_DAYS[i]));
            chip.setTag(ALERT_DAYS[i]);
            alertChips.addView(chip);
        }
        Runnable refresh = () -> {
            List<Integer> days = checkedDays(alertChips);
            summary.setText(notify.isChecked() ? alertSummary(context, Recurrence.formatAlertDays(days), alertTime[0])
                    : context.getString(R.string.reminder_no_alerts));
            alertChips.setEnabled(notify.isChecked());
            for (int i = 0; i < alertChips.getChildCount(); i++) {
                alertChips.getChildAt(i).setEnabled(notify.isChecked());
            }
        };
        for (int i = 0; i < alertChips.getChildCount(); i++) {
            ((Chip) alertChips.getChildAt(i)).setOnCheckedChangeListener((b, c) -> refresh.run());
        }
        notify.setOnCheckedChangeListener((b, on) -> {
            if (on && checkedDays(alertChips).isEmpty()) {
                ((Chip) alertChips.getChildAt(1)).setChecked(true); // the day before
            }
            refresh.run();
        });
        alertTimeField.setText(RoutineEditor.formatTime(context,
                alertTime[0] != null ? alertTime[0] : ReminderSettings.alertTime(context)));
        alertTimeField.setOnClickListener(v -> {
            int[] t = Recurrence.parseTime(alertTime[0] != null ? alertTime[0] : ReminderSettings.alertTime(context));
            new TimePickerDialog(context, (picker, h, m) -> {
                alertTime[0] = String.format(Locale.US, "%02d:%02d", h, m);
                alertTimeField.setText(RoutineEditor.formatTime(context, alertTime[0]));
                refresh.run();
            }, t[0], t[1], DateFormat.is24HourFormat(context)).show();
        });
        refresh.run();

        // Area
        String[] areaLabels = new String[ReminderTemplates.AREAS.size()];
        for (int i = 0; i < areaLabels.length; i++) {
            areaLabels[i] = context.getString(ReminderTemplates.AREAS.get(i).name);
        }
        area.setSimpleItems(areaLabels);
        final int[] areaIndex = {ReminderTemplates.AREAS.indexOf(ReminderTemplates.area(ReminderTemplates.areaOf(r)))};
        area.setText(areaLabels[areaIndex[0]], false);
        area.setOnItemClickListener((p, v, position, id) -> areaIndex[0] = position);

        moreOptions.setOnClickListener(v -> {
            boolean show = advanced.getVisibility() != View.VISIBLE;
            advanced.setVisibility(show ? View.VISIBLE : View.GONE);
            moreOptions.setText(show ? R.string.reminder_fewer_options : R.string.reminder_more_options);
        });

        AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                .setTitle(isNew ? R.string.reminder_add_title : R.string.reminder_edit_title)
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
            if (text.length() > Reminder.MAX_TITLE_LENGTH) {
                text = text.substring(0, Reminder.MAX_TITLE_LENGTH);
            }
            String noteText = Dialogs.text(note);
            r.title = text;
            r.notes = noteText.isEmpty() ? null : noteText;
            if (!day[0].equals(r.dueDate) || isNew) {
                r.dueDate = day[0];
                Recurrence.Day chosenDay = Recurrence.Day.parse(day[0]);
                r.anchorDay = chosenDay == null ? 0 : chosenDay.day;
            }
            r.dueTime = dueTime[0];
            if (!customRepeat || repeatChanged[0]) {
                r.repeatUnit = REPEATS[repeatIndex[0]][0];
                r.repeatInterval = Integer.parseInt(REPEATS[repeatIndex[0]][1]);
            }
            r.memberId = Members.selected(who);
            r.alertDays = notify.isChecked() ? Recurrence.formatAlertDays(checkedDays(alertChips)) : "";
            r.alertTime = alertTime[0];
            r.category = ReminderTemplates.AREAS.get(areaIndex[0]).key;
            Runnable save = () -> {
                Context app = context.getApplicationContext();
                AppDatabase.write(() -> Reminders.save(app, r));
                if (!r.alertDays.isEmpty() && !ReminderSettings.alertsEnabled(context)) {
                    Toast.makeText(context, R.string.reminder_alerts_off_hint, Toast.LENGTH_LONG).show();
                }
                dialog.dismiss();
            };
            if (Reminders.looksLikeIdNumber(r.title) || Reminders.looksLikeIdNumber(r.notes)) {
                new MaterialAlertDialogBuilder(context)
                        .setTitle(R.string.reminder_number_title)
                        .setMessage(R.string.reminder_number_message)
                        .setPositiveButton(R.string.reminder_number_edit, null)
                        .setNegativeButton(R.string.reminder_number_save, (x, w) -> save.run())
                        .show();
            } else {
                save.run();
            }
        }));
        Dialogs.showWithKeyboard(dialog);
        return dialog;
    }

    private static List<Integer> checkedDays(ChipGroup group) {
        List<Integer> days = new ArrayList<>();
        for (int i = 0; i < group.getChildCount(); i++) {
            Chip chip = (Chip) group.getChildAt(i);
            if (chip.isChecked()) {
                days.add((Integer) chip.getTag());
            }
        }
        return days;
    }
}
