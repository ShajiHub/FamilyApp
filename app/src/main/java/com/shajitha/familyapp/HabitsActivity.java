package com.shajitha.familyapp;

import android.content.Context;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.color.MaterialColors;
import com.shajitha.familyapp.data.AppDatabase;
import com.shajitha.familyapp.data.DataSetup;
import com.shajitha.familyapp.data.FamilyDao;
import com.shajitha.familyapp.data.FamilyMember;
import com.shajitha.familyapp.data.Recurrence;
import com.shajitha.familyapp.data.Reminder;
import com.shajitha.familyapp.data.ReminderHistory;
import com.shajitha.familyapp.reminders.Reminders;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Healthy Habits: small daily routines (water, outdoor play, reading, bedtime...). Tick today when
 * done; the last 7 days show progress. No medical advice or automatic recommendations.
 */
public class HabitsActivity extends BaseActivity {

    private FamilyDao dao;
    private List<Reminder> habits = new ArrayList<>();
    private List<FamilyMember> members = new ArrayList<>();
    /** "reminderId|yyyy-MM-dd" for every check-in in the last 7 days. */
    private final Set<String> checkIns = new HashSet<>();
    private long filter = ChoresActivity.ALL;
    private final HabitAdapter adapter = new HabitAdapter();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_list_screen);
        dao = AppDatabase.get(this).dao();
        if (savedInstanceState != null) {
            filter = savedInstanceState.getLong("filter", ChoresActivity.ALL);
        }
        RecyclerView list = findViewById(R.id.screenList);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);

        dao.remindersOfType(Reminder.TYPE_HABIT).observe(this, all -> {
            habits = all;
            adapter.rebuild();
        });
        dao.allMembers().observe(this, all -> {
            members = all;
            adapter.rebuild();
        });
        dao.historyOfTypeSince(Reminder.TYPE_HABIT, lastDays().get(0)).observe(this, history -> {
            checkIns.clear();
            for (ReminderHistory h : history) {
                checkIns.add(h.reminderId + "|" + h.dueDate);
            }
            adapter.rebuild();
        });
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putLong("filter", filter);
    }

    /** The last 7 days, oldest first, ending today (yyyy-MM-dd). */
    static List<String> lastDays() {
        Recurrence.Day today = Recurrence.Day.parse(DataSetup.today());
        List<String> days = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            days.add(today.plusDays(-i).toString());
        }
        return days;
    }

    boolean doneOn(Reminder habit, String day) {
        return checkIns.contains(habit.id + "|" + day);
    }

    /** How many of today's habits are done, for the Home summary: {done, total}. */
    static int[] todayProgress(List<Reminder> habits, List<ReminderHistory> history) {
        String today = DataSetup.today();
        Set<Long> doneToday = new HashSet<>();
        for (ReminderHistory h : history) {
            if (today.equals(h.dueDate)) {
                doneToday.add(h.reminderId);
            }
        }
        int done = 0, total = 0;
        for (Reminder r : habits) {
            if (Reminder.STATUS_ACTIVE.equals(r.status)) {
                total++;
                if (doneToday.contains(r.id)) {
                    done++;
                }
            }
        }
        return new int[]{done, total};
    }

    private void setDone(Reminder habit, boolean done) {
        Context app = getApplicationContext();
        String today = DataSetup.today();
        AppDatabase.write(() -> {
            if (done) {
                Reminders.checkIn(app, habit.id, today);
            } else {
                Reminders.undoCheckIn(app, habit.id, today);
            }
        });
    }

    private void delete(Reminder habit) {
        Context app = getApplicationContext();
        Dialogs.confirmDelete(this, getString(R.string.habits_delete_title), getString(R.string.habits_delete_message, habit.title),
                () -> AppDatabase.write(() -> Reminders.delete(app, habit.id)));
    }

    private final class HabitAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private static final int HEADER = 0, HABIT = 1;
        private final List<Reminder> shown = new ArrayList<>();

        void rebuild() {
            shown.clear();
            for (Reminder r : habits) {
                boolean match = filter == ChoresActivity.ALL
                        || (filter == ChoresActivity.UNASSIGNED ? r.memberId == null : r.memberId != null && r.memberId == filter);
                if (match && Reminder.STATUS_ACTIVE.equals(r.status)) {
                    shown.add(r);
                }
            }
            notifyDataSetChanged();
        }

        @Override
        public int getItemViewType(int position) {
            return position == 0 ? HEADER : HABIT;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            int layout = viewType == HEADER ? R.layout.item_routine_header : R.layout.item_habit;
            return new RecyclerView.ViewHolder(LayoutInflater.from(parent.getContext()).inflate(layout, parent, false)) {
            };
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            if (position == 0) {
                bindHeader(holder.itemView);
            } else {
                bindHabit(holder.itemView, shown.get(position - 1));
            }
        }

        private void bindHeader(View v) {
            ((TextView) v.findViewById(R.id.routineIntro)).setText(R.string.habits_intro);
            int[] progress = todayProgress(habits, currentHistory());
            ((TextView) v.findViewById(R.id.routineStars)).setText(progress[1] == 0
                    ? getString(R.string.habits_stars_none) : getString(R.string.habits_stars, progress[0], progress[1]));
            ((TextView) v.findViewById(R.id.routineAdd)).setText(R.string.habits_add);
            v.findViewById(R.id.routineAdd).setOnClickListener(x ->
                    RoutineEditor.show(HabitsActivity.this, Reminder.TYPE_HABIT, null, null, members));
            TextView empty = v.findViewById(R.id.routineEmpty);
            empty.setText(R.string.habits_empty);
            empty.setVisibility(habits.isEmpty() ? View.VISIBLE : View.GONE);

            ChipGroup filters = v.findViewById(R.id.routineFilter);
            filters.removeAllViews();
            addFilter(filters, ChoresActivity.ALL, getString(R.string.chores_filter_all));
            for (FamilyMember m : members) {
                if (!m.archived) {
                    addFilter(filters, m.id, Members.label(m));
                }
            }
            addFilter(filters, ChoresActivity.UNASSIGNED, getString(R.string.members_everyone));

            ChipGroup ideas = v.findViewById(R.id.routineSuggestions);
            ideas.removeAllViews();
            for (String idea : getResources().getStringArray(R.array.habit_ideas)) {
                Chip chip = new Chip(HabitsActivity.this);
                chip.setText(idea);
                chip.setOnClickListener(x -> RoutineEditor.show(HabitsActivity.this, Reminder.TYPE_HABIT, null, idea, members));
                ideas.addView(chip);
            }
        }

        private List<ReminderHistory> currentHistory() {
            List<ReminderHistory> list = new ArrayList<>();
            for (String key : checkIns) {
                ReminderHistory h = new ReminderHistory();
                h.reminderId = Long.parseLong(key.substring(0, key.indexOf('|')));
                h.dueDate = key.substring(key.indexOf('|') + 1);
                list.add(h);
            }
            return list;
        }

        private void addFilter(ChipGroup group, long value, String label) {
            Chip chip = new Chip(HabitsActivity.this, null, com.google.android.material.R.attr.chipStyle);
            chip.setCheckable(true);
            chip.setText(label);
            chip.setId(View.generateViewId());
            group.addView(chip);
            if (value == filter) {
                group.check(chip.getId());
            }
            chip.setOnClickListener(x -> {
                filter = value;
                rebuild();
                notifyItemChanged(0);
            });
        }

        private void bindHabit(View v, Reminder habit) {
            String today = DataSetup.today();
            ((TextView) v.findViewById(R.id.habitTitle)).setText(habit.title);
            FamilyMember m = Members.find(members, habit.memberId);
            String details = m == null ? getString(R.string.members_everyone) : Members.label(m);
            if (!habit.alertDays.isEmpty() && habit.alertTime != null) {
                details += " · 🔔 " + RoutineEditor.formatTime(HabitsActivity.this, habit.alertTime);
            }
            ((TextView) v.findViewById(R.id.habitDetails)).setText(details);
            CheckBox check = v.findViewById(R.id.habitToday);
            check.setOnCheckedChangeListener(null);
            check.setChecked(doneOn(habit, today));
            check.setContentDescription(getString(R.string.habits_done_today, habit.title));
            check.setOnCheckedChangeListener((b, on) -> setDone(habit, on));

            LinearLayout week = v.findViewById(R.id.habitWeek);
            week.removeAllViews();
            int done = 0;
            int filled = MaterialColors.getColor(v, com.google.android.material.R.attr.colorPrimary);
            int empty = MaterialColors.getColor(v, com.google.android.material.R.attr.colorSurfaceVariant);
            int onFilled = MaterialColors.getColor(v, com.google.android.material.R.attr.colorOnPrimary);
            int onEmpty = MaterialColors.getColor(v, com.google.android.material.R.attr.colorOnSurfaceVariant);
            float density = getResources().getDisplayMetrics().density;
            for (String day : lastDays()) {
                boolean isDone = doneOn(habit, day);
                if (isDone) {
                    done++;
                }
                TextView dot = new TextView(HabitsActivity.this);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, (int) (28 * density), 1f);
                lp.setMargins((int) (3 * density), 0, (int) (3 * density), 0);
                dot.setLayoutParams(lp);
                dot.setGravity(Gravity.CENTER);
                dot.setBackgroundResource(R.drawable.bg_day_dot);
                dot.setBackgroundTintList(ColorStateList.valueOf(isDone ? filled : empty));
                dot.setTextColor(isDone ? onFilled : onEmpty);
                dot.setTextSize(11);
                dot.setText(dayLetter(day));
                dot.setContentDescription(getString(isDone ? R.string.habits_day_done : R.string.habits_day_not_done,
                        RoutineEditor.formatDay(HabitsActivity.this, day)));
                week.addView(dot);
            }
            ((TextView) v.findViewById(R.id.habitProgress)).setText(getString(R.string.habits_progress, done));
            v.findViewById(R.id.habitEdit).setOnClickListener(x ->
                    RoutineEditor.show(HabitsActivity.this, Reminder.TYPE_HABIT, habit, null, members));
            v.findViewById(R.id.habitDelete).setOnClickListener(x -> delete(habit));
        }

        @Override
        public int getItemCount() {
            return shown.size() + 1;
        }
    }

    /** "M", "T", ... for a day. */
    private static String dayLetter(String yyyyMmDd) {
        Recurrence.Day d = Recurrence.Day.parse(yyyyMmDd);
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(d.year, d.month - 1, d.day);
        return c.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.SHORT, Locale.getDefault()).substring(0, 1);
    }
}
