package com.shajitha.familyapp;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.splashscreen.SplashScreen;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.color.MaterialColors;
import com.shajitha.familyapp.data.AppDatabase;
import com.shajitha.familyapp.data.DataSetup;
import com.shajitha.familyapp.data.DatabaseGuard;
import com.shajitha.familyapp.reminders.Notifications;
import com.shajitha.familyapp.reminders.Reminders;
import com.shajitha.familyapp.data.FamilyDao;
import com.shajitha.familyapp.data.MealSelection;
import com.shajitha.familyapp.data.ScheduleEvent;
import com.shajitha.familyapp.data.TodoTask;

import java.text.DateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

/** Home: brand, a "Today" summary, and the six areas of the app. */
public class MainActivity extends AppCompatActivity {

    private final Handler handler = new Handler(Looper.getMainLooper());
    private LinearLayout todayRows;
    private List<ScheduleEvent> todaysEvents;
    private List<TodoTask> tasks;
    private MealSelection todaysMeal;
    private List<com.shajitha.familyapp.data.Reminder> chores;
    private List<com.shajitha.familyapp.data.Reminder> habits;
    private List<com.shajitha.familyapp.data.ReminderHistory> habitsToday;

    /** Refreshes the timer line every second while a timer runs. */
    private final Runnable timerRefresh = new Runnable() {
        @Override
        public void run() {
            showToday();
            if (FamAppTimer.isRunning(timerPrefs())) {
                handler.postDelayed(this, 1000);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SplashScreen.installSplashScreen(this);
        super.onCreate(savedInstanceState);
        // Open the saved information first. If it can't be opened safely (for example the backup
        // before an upgrade couldn't be made), show the recovery screen instead. Nothing is deleted.
        FamilyDao dao;
        try {
            dao = AppDatabase.get(this).dao();
        } catch (DatabaseGuard.UnavailableException e) {
            startActivity(new Intent(this, DatabaseProblemActivity.class)
                    .putExtra(DatabaseProblemActivity.EXTRA_PROBLEM, e.problem.name()));
            finish();
            return;
        }
        setContentView(R.layout.activity_main);
        startUpOnce();

        ((TextView) findViewById(R.id.homeGreeting)).setText(greeting());
        ((TextView) findViewById(R.id.todayTitle)).setText(getString(R.string.today_title,
                DateFormat.getDateInstance(DateFormat.MEDIUM).format(new Date())));
        todayRows = findViewById(R.id.todayRows);
        findViewById(R.id.homeMenu).setOnClickListener(this::showMenu);

        RecyclerView grid = findViewById(R.id.homeGrid);
        grid.setLayoutManager(new GridLayoutManager(this, spanCount(this)));
        grid.setAdapter(new CategoryAdapter());

        String today = DataSetup.today();
        dao.eventsOn(today).observe(this, events -> {
            todaysEvents = events;
            showToday();
        });
        dao.tasks().observe(this, list -> {
            tasks = list;
            showToday();
        });
        dao.mealOn(today).observe(this, meal -> {
            todaysMeal = meal;
            showToday();
        });
        dao.remindersOfType(com.shajitha.familyapp.data.Reminder.TYPE_CHORE).observe(this, list -> {
            chores = list;
            showToday();
        });
        dao.remindersOfType(com.shajitha.familyapp.data.Reminder.TYPE_HABIT).observe(this, list -> {
            habits = list;
            showToday();
        });
        dao.historyOfTypeSince(com.shajitha.familyapp.data.Reminder.TYPE_HABIT, today).observe(this, list -> {
            habitsToday = list;
            showToday();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.removeCallbacks(timerRefresh);
        handler.post(timerRefresh);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(timerRefresh);
    }

    private static boolean startedUp;

    /** Once per app start: notification categories, and reminder alerts scheduled again. */
    private void startUpOnce() {
        if (startedUp) {
            return;
        }
        startedUp = true;
        Notifications.ensureChannels(this);
        Context app = getApplicationContext();
        AppDatabase.write(() -> Reminders.rescheduleAll(app));
    }

    private SharedPreferences timerPrefs() {
        return getSharedPreferences(FamAppTimer.PREFS, Context.MODE_PRIVATE);
    }

    /** 2 columns on phones, more on wide screens and in landscape. */
    static int spanCount(Context context) {
        int widthDp = context.getResources().getConfiguration().screenWidthDp;
        return Math.max(2, Math.min(4, widthDp / 200));
    }

    @StringRes
    static int greeting() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (hour < 12) {
            return R.string.home_greeting_morning;
        } else if (hour < 18) {
            return R.string.home_greeting_afternoon;
        }
        return R.string.home_greeting_evening;
    }

    private void showMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add(0, R.id.menu_settings, 0, R.string.title_settings);
        menu.getMenu().add(0, R.id.menu_contact, 1, R.string.title_contact);
        menu.getMenu().add(0, R.id.menu_about, 2, R.string.title_about);
        menu.setOnMenuItemClickListener(item -> {
            Class<?> screen = item.getItemId() == R.id.menu_about ? AboutActivity.class
                    : item.getItemId() == R.id.menu_settings ? SettingsActivity.class : ContactUs.class;
            startActivity(new Intent(this, screen));
            return true;
        });
        menu.show();
    }

    // ---------- Today ----------

    private void showToday() {
        todayRows.removeAllViews();
        // Events today
        if (todaysEvents != null) {
            String text;
            if (todaysEvents.isEmpty()) {
                text = getString(R.string.today_no_events);
            } else {
                ScheduleEvent next = todaysEvents.get(0);
                text = getResources().getQuantityString(R.plurals.today_events, todaysEvents.size(),
                        todaysEvents.size(), next.title);
            }
            addRow(R.id.today_events, R.drawable.ic_calendar_month, text, viewCalendar.class);
        }
        // Tasks still to do
        if (tasks != null) {
            int open = 0;
            for (TodoTask t : tasks) {
                if (!t.done) {
                    open++;
                }
            }
            addRow(R.id.today_tasks, R.drawable.ic_done_all, open == 0 ? getString(R.string.today_no_tasks)
                    : getResources().getQuantityString(R.plurals.today_tasks, open, open), ToDoList.class);
        }
        // Chores due today (and overdue), only when the family uses chores
        if (chores != null) {
            String todayKey = DataSetup.today();
            int dueToday = 0, overdue = 0, active = 0;
            for (com.shajitha.familyapp.data.Reminder r : chores) {
                if (!com.shajitha.familyapp.data.Reminder.STATUS_ACTIVE.equals(r.status)) {
                    continue;
                }
                active++;
                int cmp = r.dueDate.compareTo(todayKey);
                if (cmp < 0) {
                    overdue++;
                } else if (cmp == 0) {
                    dueToday++;
                }
            }
            if (active > 0) {
                int total = dueToday + overdue;
                String text = total == 0 ? getString(R.string.today_no_chores)
                        : getResources().getQuantityString(R.plurals.today_chores, total, total);
                if (overdue > 0) {
                    text = getString(R.string.today_chores_overdue, text, overdue);
                }
                addRow(R.id.today_chores, R.drawable.ic_home, text, ChoresActivity.class);
            }
        }
        // Habits done today
        if (habits != null && habitsToday != null) {
            int[] progress = HabitsActivity.todayProgress(habits, habitsToday);
            if (progress[1] > 0) {
                addRow(R.id.today_habits, R.drawable.ic_wb_sunny,
                        getString(R.string.today_habits, progress[0], progress[1]), HabitsActivity.class);
            }
        }
        // Lunch
        addRow(R.id.today_lunch, R.drawable.ic_restaurant, todaysMeal == null ? getString(R.string.today_no_lunch)
                : getString(R.string.today_lunch, todaysMeal.food), MealPlanner.class);
        // Timer, only while one is running or paused
        SharedPreferences prefs = timerPrefs();
        long left = FamAppTimer.remainingMillis(prefs, FamAppTimer.clock.now());
        if (left > 0) {
            String time = FamAppTimer.format(left);
            addRow(R.id.today_timer, R.drawable.ic_timer, FamAppTimer.isRunning(prefs)
                    ? getString(R.string.today_timer_running, time) : getString(R.string.today_timer_paused, time), FamAppTimer.class);
        }
    }

    private void addRow(@IdRes int id, @DrawableRes int icon, String text, Class<?> screen) {
        View row = LayoutInflater.from(this).inflate(R.layout.item_today_row, todayRows, false);
        row.setId(id);
        ((ImageView) row.findViewById(R.id.todayIcon)).setImageResource(icon);
        ((TextView) row.findViewById(R.id.todayText)).setText(text);
        row.setOnClickListener(v -> startActivity(new Intent(this, screen)));
        todayRows.addView(row);
    }

    // ---------- Categories ----------

    /** Binds a tile (item_home_tile.xml) with an icon in a coloured circle. */
    static void bindTile(View view, int title, int subtitle, int icon, int containerAttr, int iconAttr) {
        ((TextView) view.findViewById(R.id.tileTitle)).setText(title);
        ((TextView) view.findViewById(R.id.tileSubtitle)).setText(subtitle);
        ImageView iconView = view.findViewById(R.id.tileIcon);
        iconView.setImageResource(icon);
        iconView.setBackgroundTintList(ColorStateList.valueOf(MaterialColors.getColor(view, containerAttr)));
        iconView.setImageTintList(ColorStateList.valueOf(MaterialColors.getColor(view, iconAttr)));
    }

    private final class CategoryAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private final Features.Category[] categories = Features.Category.values();

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new RecyclerView.ViewHolder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_home_tile, parent, false)) {
            };
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            Features.Category category = categories[position];
            View view = holder.itemView;
            view.setId(category.id);
            bindTile(view, category.title, category.subtitle, category.icon, category.containerColor, category.iconColor);
            view.setContentDescription(getString(category.title) + ". " + getString(category.subtitle));
            view.setOnClickListener(v -> startActivity(HubActivity.intent(MainActivity.this, category)));
        }

        @Override
        public int getItemCount() {
            return categories.length;
        }
    }
}
