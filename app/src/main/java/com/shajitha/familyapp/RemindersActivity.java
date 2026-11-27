package com.shajitha.familyapp;

import android.app.DatePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.widget.PopupMenu;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.shajitha.familyapp.data.AppDatabase;
import com.shajitha.familyapp.data.DataSetup;
import com.shajitha.familyapp.data.FamilyDao;
import com.shajitha.familyapp.data.FamilyMember;
import com.shajitha.familyapp.data.Recurrence;
import com.shajitha.familyapp.data.Reminder;
import com.shajitha.familyapp.data.ReminderHistory;
import com.shajitha.familyapp.reminders.Notifications;
import com.shajitha.familyapp.reminders.ReminderTemplates;
import com.shajitha.familyapp.reminders.Reminders;

import java.util.ArrayList;
import java.util.List;

/**
 * Reminders &amp; Renewals: one simple screen for every kind of family reminder (documents, library,
 * appointments, home, school, bills, ...). Areas are only filters; all reminders use the same engine.
 * Wording stays gentle: a date that has passed is "Whenever you're ready", never "failed" or "missed".
 */
public class RemindersActivity extends BaseActivity {

    /** Optional: open with one area selected (for example LIBRARY). */
    public static final String EXTRA_AREA = "area";
    static final String ALL = "ALL";
    /** How far ahead "Coming up" looks. */
    static final int COMING_UP_DAYS = 30;

    private FamilyDao dao;
    private List<Reminder> reminders = new ArrayList<>();
    private List<FamilyMember> members = new ArrayList<>();
    private String filter = ALL;
    private boolean showAll;
    private final RemindersAdapter adapter = new RemindersAdapter();

    public static Intent intent(Context context, @Nullable String area) {
        Intent i = new Intent(context, RemindersActivity.class);
        if (area != null) {
            i.putExtra(EXTRA_AREA, area);
        }
        return i;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_list_screen);
        dao = AppDatabase.get(this).dao();
        String area = getIntent().getStringExtra(EXTRA_AREA);
        if (ReminderTemplates.isArea(area)) {
            filter = area;
            setTitle(ReminderTemplates.area(area).name);
        }
        if (savedInstanceState != null) {
            filter = savedInstanceState.getString("filter", filter);
            showAll = savedInstanceState.getBoolean("showAll");
        }
        RecyclerView list = findViewById(R.id.screenList);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);

        dao.familyReminders().observe(this, all -> {
            reminders = all;
            adapter.rebuild();
        });
        dao.allMembers().observe(this, all -> {
            members = all;
            adapter.rebuild();
        });
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString("filter", filter);
        outState.putBoolean("showAll", showAll);
    }

    // ---------- Adding ----------

    /** Add: choose an area (unless one is selected), then a template or a blank reminder. */
    void add() {
        if (ALL.equals(filter)) {
            String[] names = new String[ReminderTemplates.AREAS.size()];
            for (int i = 0; i < names.length; i++) {
                names[i] = getString(ReminderTemplates.AREAS.get(i).name);
            }
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.reminders_choose_area)
                    .setItems(names, (d, which) -> chooseTemplate(ReminderTemplates.AREAS.get(which)))
                    .setNegativeButton(R.string.cancel, null)
                    .show();
        } else {
            chooseTemplate(ReminderTemplates.area(filter));
        }
    }

    void chooseTemplate(ReminderTemplates.Area area) {
        List<ReminderTemplates.Template> templates = area.templates(this);
        if (templates.isEmpty()) {
            edit(blank(area.key));
            return;
        }
        String[] names = new String[templates.size() + 1];
        names[0] = getString(R.string.reminders_blank);
        for (int i = 0; i < templates.size(); i++) {
            names[i + 1] = templates.get(i).title;
        }
        MaterialAlertDialogBuilder b = new MaterialAlertDialogBuilder(this)
                .setTitle(area.name)
                .setItems(names, (d, which) -> edit(which == 0 ? blank(area.key)
                        : templates.get(which - 1).newReminder(DataSetup.today())))
                .setNegativeButton(R.string.cancel, null);
        b.show();
    }

    private static Reminder blank(String area) {
        Reminder r = Reminders.create("", Reminder.TYPE_OTHER, DataSetup.today(), null);
        r.category = area;
        r.alertDays = "1";
        return r;
    }

    void edit(Reminder r) {
        ReminderEditor.show(this, r, members);
    }

    // ---------- Actions ----------

    private void run(Reminder r, String action) {
        Context app = getApplicationContext();
        long id = r.id;
        switch (action) {
            case "done":
                AppDatabase.write(() -> Reminders.complete(app, id));
                break;
            case "later":
                AppDatabase.write(() -> Reminders.postponeToTomorrow(app, id));
                android.widget.Toast.makeText(this, R.string.reminders_later_done, android.widget.Toast.LENGTH_SHORT).show();
                break;
            case "archive":
                AppDatabase.write(() -> Reminders.archive(app, id));
                break;
            case "restore":
                AppDatabase.write(() -> Reminders.restore(app, id));
                break;
            default:
                break;
        }
    }

    /** Asks for a date (starting from the current one), then runs {@code onDate}. */
    private void pickDate(Reminder r, int title, OnDate onDate) {
        Recurrence.Day d = Recurrence.Day.parse(r.dueDate);
        if (d == null) {
            d = Recurrence.Day.parse(DataSetup.today());
        }
        DatePickerDialog picker = new DatePickerDialog(this, (p, y, m, dd) ->
                onDate.onDate(new Recurrence.Day(y, m + 1, dd).toString()), d.year, d.month - 1, d.day);
        picker.setTitle(title);
        picker.show();
    }

    interface OnDate {
        void onDate(String yyyyMmDd);
    }

    /** "Renewed": asks for the confirmed new date; the old one stays in the history. */
    void renewed(Reminder r) {
        Context app = getApplicationContext();
        long id = r.id;
        pickDate(r, Reminder.TYPE_LIBRARY.equals(r.type) ? R.string.reminders_new_return_date : R.string.reminders_new_expiry_date,
                day -> AppDatabase.write(() -> Reminders.renew(app, id, day)));
    }

    /** Moves just this date (the repeat pattern stays). */
    void moveDate(Reminder r) {
        Context app = getApplicationContext();
        long id = r.id;
        pickDate(r, R.string.reminders_move, day -> AppDatabase.write(() -> {
            if (Recurrence.NONE.equals(r.repeatUnit) || !Reminder.STATUS_ACTIVE.equals(r.status)) {
                Reminders.reschedule(app, id, day);
            } else {
                Reminders.postponeOccurrence(app, id, day);
            }
        }));
    }

    void delete(Reminder r) {
        Context app = getApplicationContext();
        Dialogs.confirmDelete(this, getString(R.string.reminders_delete_title),
                getString(R.string.reminders_delete_message, r.title),
                () -> AppDatabase.write(() -> Reminders.delete(app, r.id)));
    }

    void showHistory(Reminder r) {
        long id = r.id;
        AppDatabase.write(() -> {
            List<ReminderHistory> history = dao.historyNow(id);
            runOnUiThread(() -> {
                if (isFinishing()) {
                    return;
                }
                StringBuilder sb = new StringBuilder();
                for (ReminderHistory h : history) {
                    sb.append("• ").append(RoutineEditor.formatDay(this, h.dueDate)).append('\n');
                }
                new MaterialAlertDialogBuilder(this)
                        .setTitle(getString(R.string.reminders_history_title, r.title))
                        .setMessage(history.isEmpty() ? getString(R.string.reminders_history_empty) : sb.toString().trim())
                        .setPositiveButton(android.R.string.ok, null)
                        .show();
            });
        });
    }

    /** The main button's label: Returned for library books, Renewed for renewals, otherwise Done. */
    static int primaryLabel(Reminder r) {
        if (!Reminder.STATUS_ACTIVE.equals(r.status)) {
            return R.string.reminders_restore;
        }
        if (Reminder.TYPE_LIBRARY.equals(r.type)) {
            return R.string.reminders_returned;
        }
        if (Reminder.TYPE_RENEWAL.equals(r.type)) {
            return R.string.reminders_renewed;
        }
        return R.string.reminders_done;
    }

    private void primary(Reminder r) {
        if (!Reminder.STATUS_ACTIVE.equals(r.status)) {
            run(r, "restore");
        } else if (Reminder.TYPE_RENEWAL.equals(r.type)) {
            renewed(r);
        } else {
            run(r, "done");
        }
    }

    private void showMenu(View anchor, Reminder r) {
        PopupMenu popup = new PopupMenu(this, anchor);
        android.view.Menu m = popup.getMenu();
        boolean active = Reminder.STATUS_ACTIVE.equals(r.status);
        if (active) {
            m.add(0, 1, 0, R.string.reminders_later);
            m.add(0, 2, 1, R.string.reminders_move);
            if (Reminder.TYPE_LIBRARY.equals(r.type)) {
                m.add(0, 3, 2, R.string.reminders_renewed);
            }
        }
        m.add(0, 4, 3, R.string.edit);
        m.add(0, 5, 4, R.string.reminders_history);
        if (Reminder.STATUS_ARCHIVED.equals(r.status)) {
            m.add(0, 6, 5, R.string.reminders_restore);
        } else {
            m.add(0, 7, 5, R.string.list_archive);
        }
        m.add(0, 8, 6, R.string.delete);
        popup.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1:
                    run(r, "later");
                    break;
                case 2:
                    moveDate(r);
                    break;
                case 3:
                    renewed(r);
                    break;
                case 4:
                    edit(copy(r));
                    break;
                case 5:
                    showHistory(r);
                    break;
                case 6:
                    run(r, "restore");
                    break;
                case 7:
                    run(r, "archive");
                    break;
                default:
                    delete(r);
                    break;
            }
            return true;
        });
        popup.show();
    }

    private static Reminder copy(Reminder r) {
        Reminder c = new Reminder();
        c.id = r.id;
        c.title = r.title;
        c.type = r.type;
        c.memberId = r.memberId;
        c.dueDate = r.dueDate;
        c.dueTime = r.dueTime;
        c.notes = r.notes;
        c.repeatUnit = r.repeatUnit;
        c.repeatInterval = r.repeatInterval;
        c.anchorDay = r.anchorDay;
        c.alertDays = r.alertDays;
        c.alertTime = r.alertTime;
        c.snoozedUntil = r.snoozedUntil;
        c.status = r.status;
        c.completedAt = r.completedAt;
        c.createdAt = r.createdAt;
        c.updatedAt = r.updatedAt;
        c.category = r.category;
        return c;
    }

    // ---------- List ----------

    private boolean matches(Reminder r) {
        return ALL.equals(filter) || ReminderTemplates.areaOf(r).equals(filter);
    }

    private final class RemindersAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private static final int HEADER = 0, SECTION = 1, REMINDER = 2, MORE = 3;
        private final List<Object> rows = new ArrayList<>();
        private int hiddenCount;
        private boolean empty;

        void rebuild() {
            String today = DataSetup.today();
            String soon = Recurrence.Day.parse(today).plusDays(COMING_UP_DAYS).toString();
            List<Reminder> ready = new ArrayList<>(), comingUp = new ArrayList<>(), later = new ArrayList<>(),
                    done = new ArrayList<>(), archived = new ArrayList<>();
            for (Reminder r : reminders) {
                if (!matches(r)) {
                    continue;
                }
                if (Reminder.STATUS_ARCHIVED.equals(r.status)) {
                    archived.add(r);
                } else if (Reminder.STATUS_COMPLETED.equals(r.status)) {
                    done.add(r);
                } else if (r.dueDate.compareTo(today) < 0) {
                    ready.add(r);
                } else if (r.dueDate.compareTo(soon) <= 0) {
                    comingUp.add(r);
                } else {
                    later.add(r);
                }
            }
            rows.clear();
            rows.add(HEADER);
            add(R.string.reminders_section_ready, ready);
            add(R.string.reminders_section_coming_up, comingUp);
            add(R.string.reminders_section_later, later);
            hiddenCount = done.size() + archived.size();
            if (hiddenCount > 0) {
                rows.add(MORE);
                if (showAll) {
                    add(R.string.reminders_section_done, done);
                    add(R.string.lists_archived, archived);
                }
            }
            empty = ready.isEmpty() && comingUp.isEmpty() && later.isEmpty() && hiddenCount == 0;
            notifyDataSetChanged();
        }

        private void add(int title, List<Reminder> items) {
            if (!items.isEmpty()) {
                rows.add(getString(title));
                rows.addAll(items);
            }
        }

        @Override
        public int getItemViewType(int position) {
            Object row = rows.get(position);
            if (row instanceof Reminder) {
                return REMINDER;
            }
            if (row instanceof String) {
                return SECTION;
            }
            return (Integer) row == MORE ? MORE : HEADER;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            int layout = viewType == REMINDER ? R.layout.item_reminder
                    : viewType == SECTION ? R.layout.item_section_title
                    : viewType == MORE ? R.layout.item_reminders_more : R.layout.item_reminders_header;
            return new RecyclerView.ViewHolder(LayoutInflater.from(parent.getContext()).inflate(layout, parent, false)) {
            };
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            View v = holder.itemView;
            Object row = rows.get(position);
            int type = getItemViewType(position);
            if (type == SECTION) {
                ((TextView) v).setText((String) row);
            } else if (type == REMINDER) {
                bindReminder(v, (Reminder) row);
            } else if (type == MORE) {
                ((Button) v).setText(showAll ? getString(R.string.reminders_hide_done)
                        : getString(R.string.reminders_show_done, hiddenCount));
                v.setOnClickListener(x -> {
                    showAll = !showAll;
                    rebuild();
                });
            } else {
                bindHeader(v);
            }
        }

        private void bindHeader(View v) {
            v.findViewById(R.id.remindersAdd).setOnClickListener(x -> RemindersActivity.this.add());
            ChipGroup chips = v.findViewById(R.id.remindersFilter);
            chips.removeAllViews();
            addChip(chips, ALL, getString(R.string.reminders_filter_all));
            for (ReminderTemplates.Area a : ReminderTemplates.AREAS) {
                addChip(chips, a.key, getString(a.name));
            }
            TextView note = v.findViewById(R.id.remindersNote);
            ReminderTemplates.Area area = ALL.equals(filter) ? null : ReminderTemplates.area(filter);
            note.setVisibility(area == null || area.note == 0 ? View.GONE : View.VISIBLE);
            if (area != null && area.note != 0) {
                note.setText(area.note);
            }
            v.findViewById(R.id.remindersEmpty).setVisibility(empty ? View.VISIBLE : View.GONE);
        }

        private void addChip(ChipGroup group, String key, String label) {
            Chip chip = new Chip(RemindersActivity.this, null, com.google.android.material.R.attr.chipStyle);
            chip.setCheckable(true);
            chip.setText(label);
            chip.setId(View.generateViewId());
            chip.setTag(key);
            group.addView(chip);
            if (key.equals(filter)) {
                group.check(chip.getId());
            }
            chip.setOnClickListener(x -> {
                filter = key;
                rebuild();
            });
        }

        private void bindReminder(View v, Reminder r) {
            ((TextView) v.findViewById(R.id.reminderTitle)).setText(r.title);
            ((ImageView) v.findViewById(R.id.reminderIcon)).setImageResource(ReminderTemplates.area(ReminderTemplates.areaOf(r)).icon);
            ((TextView) v.findViewById(R.id.reminderDetails)).setText(details(r));
            Button action = v.findViewById(R.id.reminderAction);
            action.setText(primaryLabel(r));
            action.setContentDescription(getString(primaryLabel(r)) + ": " + r.title);
            action.setOnClickListener(x -> primary(r));
            View more = v.findViewById(R.id.reminderMore);
            more.setContentDescription(getString(R.string.list_item_more, r.title));
            more.setOnClickListener(x -> showMenu(more, r));
            v.setOnClickListener(x -> edit(copy(r)));
            v.setAlpha(Reminder.STATUS_ACTIVE.equals(r.status) ? 1f : 0.7f);
        }

        @Override
        public int getItemCount() {
            return rows.size();
        }
    }

    /** "👧 Mia · Sat, Mar 14, 2027 · Coming up in 3 days · Repeats yearly · 🔕" */
    String details(Reminder r) {
        StringBuilder sb = new StringBuilder();
        FamilyMember m = Members.find(members, r.memberId);
        if (m != null) {
            sb.append(Members.label(m)).append(" · ");
        }
        sb.append(RoutineEditor.formatDay(this, r.dueDate));
        if (r.dueTime != null) {
            sb.append(", ").append(RoutineEditor.formatTime(this, r.dueTime));
        }
        if (Reminder.STATUS_ACTIVE.equals(r.status)) {
            sb.append(" · ").append(Notifications.dueText(this, r.dueDate, System.currentTimeMillis()));
        } else if (Reminder.STATUS_COMPLETED.equals(r.status)) {
            sb.append(" · ").append(getString(R.string.reminders_done_label));
        }
        String repeat = ReminderEditor.repeatText(this, r);
        if (!repeat.isEmpty()) {
            sb.append(" · ").append(repeat);
        }
        if (r.alertDays.isEmpty()) {
            sb.append(" · ").append(getString(R.string.reminders_no_alert_short));
        }
        return sb.toString();
    }
}
