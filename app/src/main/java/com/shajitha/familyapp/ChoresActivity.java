package com.shajitha.familyapp;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.shajitha.familyapp.data.AppDatabase;
import com.shajitha.familyapp.data.DataSetup;
import com.shajitha.familyapp.data.FamilyDao;
import com.shajitha.familyapp.data.FamilyMember;
import com.shajitha.familyapp.data.Recurrence;
import com.shajitha.familyapp.data.Reminder;
import com.shajitha.familyapp.data.ReminderHistory;
import com.shajitha.familyapp.reminders.Reminders;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Chores: household jobs for everyone, with gentle encouragement (stars this week), never penalties.
 * Ticking a chore keeps it in the history; repeating chores move to their next date.
 */
public class ChoresActivity extends BaseActivity {

    /** Filter value for "everyone's chores". */
    static final long ALL = Long.MIN_VALUE;
    /** Filter value for chores not assigned to anyone. */
    static final long UNASSIGNED = -1;

    private FamilyDao dao;
    private List<Reminder> chores = new ArrayList<>();
    private List<FamilyMember> members = new ArrayList<>();
    private List<ReminderHistory> weekHistory = new ArrayList<>();
    private long filter = ALL;
    private final ChoreAdapter adapter = new ChoreAdapter();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_list_screen);
        dao = AppDatabase.get(this).dao();
        if (savedInstanceState != null) {
            filter = savedInstanceState.getLong("filter", ALL);
        }
        RecyclerView list = findViewById(R.id.screenList);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);

        dao.remindersOfType(Reminder.TYPE_CHORE).observe(this, all -> {
            chores = all;
            adapter.rebuild();
        });
        dao.allMembers().observe(this, all -> {
            members = all;
            adapter.rebuild();
        });
        dao.historyOfTypeSince(Reminder.TYPE_CHORE, weekStart()).observe(this, h -> {
            weekHistory = h;
            adapter.rebuild();
        });
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putLong("filter", filter);
    }

    /** Monday of this week (yyyy-MM-dd). */
    static String weekStart() {
        java.util.Calendar c = java.util.Calendar.getInstance();
        int dow = c.get(java.util.Calendar.DAY_OF_WEEK); // Sunday = 1
        int back = dow == java.util.Calendar.SUNDAY ? 6 : dow - java.util.Calendar.MONDAY;
        Recurrence.Day today = Recurrence.Day.parse(DataSetup.today());
        return today.plusDays(-back).toString();
    }

    private boolean matchesFilter(Long memberId) {
        if (filter == ALL) {
            return true;
        }
        if (filter == UNASSIGNED) {
            return memberId == null;
        }
        return memberId != null && memberId == filter;
    }

    /** "⭐ This week: 👧 Mia 4 · 👦 Sam 2" */
    private String starsText() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (ReminderHistory h : weekHistory) {
            FamilyMember m = Members.find(members, h.memberId);
            String who = m == null ? getString(R.string.members_everyone) : Members.label(m);
            Integer n = counts.get(who);
            counts.put(who, n == null ? 1 : n + 1);
        }
        if (counts.isEmpty()) {
            return getString(R.string.chores_stars_none);
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            sb.append(sb.length() == 0 ? "" : " · ").append(e.getKey()).append(" ").append(e.getValue());
        }
        return getString(R.string.chores_stars, sb.toString());
    }

    private void complete(Reminder r) {
        Context app = getApplicationContext();
        AppDatabase.write(() -> Reminders.complete(app, r.id));
    }

    private void restore(Reminder r) {
        Context app = getApplicationContext();
        AppDatabase.write(() -> Reminders.restore(app, r.id));
    }

    private void doTomorrow(Reminder r) {
        Context app = getApplicationContext();
        String tomorrow = Recurrence.Day.parse(DataSetup.today()).plusDays(1).toString();
        AppDatabase.write(() -> Reminders.postponeOccurrence(app, r.id, tomorrow));
    }

    private void delete(Reminder r) {
        Context app = getApplicationContext();
        Dialogs.confirmDelete(this, getString(R.string.chores_delete_title), getString(R.string.chores_delete_message, r.title),
                () -> AppDatabase.write(() -> Reminders.delete(app, r.id)));
    }

    private final class ChoreAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private static final int HEADER = 0, SECTION = 1, CHORE = 2;
        private final List<Object> rows = new ArrayList<>();
        private boolean empty;

        void rebuild() {
            String today = DataSetup.today();
            List<Reminder> overdue = new ArrayList<>(), due = new ArrayList<>(), later = new ArrayList<>(), done = new ArrayList<>();
            for (Reminder r : chores) {
                if (!matchesFilter(r.memberId)) {
                    continue;
                }
                if (Reminder.STATUS_COMPLETED.equals(r.status)) {
                    // Finished one-off chores stay listed for a week (they are never deleted automatically).
                    if (r.completedAt != null && r.completedAt >= System.currentTimeMillis() - 7L * 24 * 3600_000) {
                        done.add(r);
                    }
                } else if (r.dueDate.compareTo(today) < 0) {
                    overdue.add(r);
                } else if (r.dueDate.equals(today)) {
                    due.add(r);
                } else {
                    later.add(r);
                }
            }
            rows.clear();
            rows.add(HEADER);
            addSection(R.string.chores_overdue, overdue);
            addSection(R.string.chores_today, due);
            addSection(R.string.chores_later, later);
            addSection(R.string.chores_done, done);
            empty = chores.isEmpty();
            notifyDataSetChanged();
        }

        private void addSection(int title, List<Reminder> items) {
            if (!items.isEmpty()) {
                rows.add(getString(title));
                rows.addAll(items);
            }
        }

        @Override
        public int getItemViewType(int position) {
            Object row = rows.get(position);
            return row instanceof Reminder ? CHORE : row instanceof String ? SECTION : HEADER;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            int layout = viewType == CHORE ? R.layout.item_chore
                    : viewType == SECTION ? R.layout.item_section_title : R.layout.item_routine_header;
            return new RecyclerView.ViewHolder(LayoutInflater.from(parent.getContext()).inflate(layout, parent, false)) {
            };
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            View v = holder.itemView;
            Object row = rows.get(position);
            if (row instanceof String) {
                ((TextView) v).setText((String) row);
            } else if (row instanceof Reminder) {
                bindChore(v, (Reminder) row);
            } else {
                bindHeader(v);
            }
        }

        private void bindHeader(View v) {
            ((TextView) v.findViewById(R.id.routineIntro)).setText(R.string.chores_intro);
            ((TextView) v.findViewById(R.id.routineStars)).setText(starsText());
            ((TextView) v.findViewById(R.id.routineAdd)).setText(R.string.chores_add);
            v.findViewById(R.id.routineAdd).setOnClickListener(x ->
                    RoutineEditor.show(ChoresActivity.this, Reminder.TYPE_CHORE, null, null, members));
            TextView emptyView = v.findViewById(R.id.routineEmpty);
            emptyView.setText(R.string.chores_empty);
            emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);

            ChipGroup filters = v.findViewById(R.id.routineFilter);
            filters.removeAllViews();
            addFilter(filters, ALL, getString(R.string.chores_filter_all));
            for (FamilyMember m : members) {
                if (!m.archived) {
                    addFilter(filters, m.id, Members.label(m));
                }
            }
            addFilter(filters, UNASSIGNED, getString(R.string.members_everyone));

            ChipGroup ideas = v.findViewById(R.id.routineSuggestions);
            ideas.removeAllViews();
            for (String idea : getResources().getStringArray(R.array.chore_ideas)) {
                Chip chip = new Chip(ChoresActivity.this);
                chip.setText(idea);
                chip.setOnClickListener(x -> RoutineEditor.show(ChoresActivity.this, Reminder.TYPE_CHORE, null, idea, members));
                ideas.addView(chip);
            }
        }

        private void addFilter(ChipGroup group, long value, String label) {
            Chip chip = new Chip(ChoresActivity.this, null, com.google.android.material.R.attr.chipStyle);
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
            });
        }

        private void bindChore(View v, Reminder r) {
            boolean finished = Reminder.STATUS_COMPLETED.equals(r.status);
            ((TextView) v.findViewById(R.id.choreTitle)).setText(r.title);
            StringBuilder details = new StringBuilder();
            FamilyMember m = Members.find(members, r.memberId);
            details.append(m == null ? getString(R.string.members_everyone) : Members.label(m));
            details.append(" · ").append(RoutineEditor.repeatLabel(ChoresActivity.this, r));
            if (!finished) {
                details.append(" · ").append(RoutineEditor.formatDay(ChoresActivity.this, r.dueDate));
            }
            if (!r.alertDays.isEmpty() && r.alertTime != null) {
                details.append(" · 🔔 ").append(RoutineEditor.formatTime(ChoresActivity.this, r.alertTime));
            }
            ((TextView) v.findViewById(R.id.choreDetails)).setText(details);
            CheckBox done = v.findViewById(R.id.choreDone);
            done.setOnCheckedChangeListener(null);
            done.setChecked(finished);
            done.setContentDescription(getString(R.string.chores_mark_done, r.title));
            done.setOnCheckedChangeListener((b, checked) -> {
                if (checked && !finished) {
                    complete(r);
                } else if (!checked && finished) {
                    restore(r);
                }
            });
            v.findViewById(R.id.choreLater).setVisibility(finished ? View.GONE : View.VISIBLE);
            v.findViewById(R.id.choreLater).setOnClickListener(x -> doTomorrow(r));
            v.findViewById(R.id.choreEdit).setOnClickListener(x ->
                    RoutineEditor.show(ChoresActivity.this, Reminder.TYPE_CHORE, r, null, members));
            v.findViewById(R.id.choreDelete).setOnClickListener(x -> delete(r));
            v.setAlpha(finished ? 0.7f : 1f);
        }

        @Override
        public int getItemCount() {
            return rows.size();
        }
    }
}
