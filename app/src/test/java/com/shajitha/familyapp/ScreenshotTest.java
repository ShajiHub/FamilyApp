package com.shajitha.familyapp;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Looper;
import android.view.View;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.io.File;
import java.io.FileOutputStream;
import java.time.Duration;
import java.util.function.Consumer;

import static org.robolectric.Shadows.shadowOf;

/**
 * Draws every screen to a PNG in app/build/screenshots, in portrait, landscape and dark mode,
 * so layouts can be reviewed without an emulator. Run: ./gradlew testDebugUnitTest --tests "*ScreenshotTest*"
 */
@RunWith(AndroidJUnit4.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ScreenshotTest {

    @org.junit.Before
    public void setUp() {
        com.shajitha.familyapp.data.AppDatabase.useDirectExecutorsForTests();
        com.shajitha.familyapp.data.AppDatabase.resetForTests();
        androidx.work.testing.WorkManagerTestInitHelper.initializeTestWorkManager(
                androidx.test.core.app.ApplicationProvider.getApplicationContext(),
                new androidx.work.Configuration.Builder().setMinimumLoggingLevel(android.util.Log.ERROR)
                        .setExecutor(new androidx.work.testing.SynchronousExecutor()).build());
        addSampleData();
    }

    /** A little family data, so screens are shown in use. */
    static void addSampleData() {
        android.content.Context c = androidx.test.core.app.ApplicationProvider.getApplicationContext();
        com.shajitha.familyapp.data.FamilyDao dao = com.shajitha.familyapp.data.AppDatabase.get(c).dao();
        String[] tasks = {"Pack school bags", "Water the plants", "Call grandma"};
        for (int i = 0; i < tasks.length; i++) {
            com.shajitha.familyapp.data.TodoTask t = new com.shajitha.familyapp.data.TodoTask();
            t.title = tasks[i];
            t.done = i == 1;
            t.createdAt = i;
            dao.insert(t);
        }
        String today = com.shajitha.familyapp.data.DataSetup.today();
        String[][] events = {{"08:30", "Swimming class", "Bring goggles and towel"}, {null, "Grandma's birthday", null},
                {"16:00", "Parent-teacher meeting", null}};
        for (String[] e : events) {
            com.shajitha.familyapp.data.ScheduleEvent ev = new com.shajitha.familyapp.data.ScheduleEvent();
            ev.date = today;
            ev.time = e[0];
            ev.title = e[1];
            ev.notes = e[2];
            dao.insert(ev);
        }
        Object[][] books = {{"Matilda", 240, 240}, {"The Hobbit", 310, 120}, {"Charlotte's Web", 0, 45}};
        for (Object[] b : books) {
            com.shajitha.familyapp.data.Book book = new com.shajitha.familyapp.data.Book();
            book.title = (String) b[0];
            book.totalPages = (Integer) b[1];
            book.pagesRead = (Integer) b[2];
            dao.insert(book);
        }
        for (com.shajitha.familyapp.data.ChecklistItem item : dao.checklistItemsNow()) {
            if (item.title.equals("Gloves") || item.title.equals("Scarfs")) {
                item.checked = true;
                dao.update(item);
            }
        }
        String[][] people = {{"Mum", "👩", "PARENT", "0"}, {"Mia", "👧", "CHILD", "1"}, {"Sam", "👦", "CHILD", "2"}, {"Grandpa", "👴", "OTHER", "3"}};
        long first = 0;
        for (String[] p : people) {
            com.shajitha.familyapp.data.FamilyMember m = new com.shajitha.familyapp.data.FamilyMember();
            m.name = p[0];
            m.avatar = p[1];
            m.role = p[2];
            m.colorIndex = Integer.parseInt(p[3]);
            m.archived = p[0].equals("Grandpa");
            long id = dao.insert(m);
            if (first == 0) {
                first = id;
            }
        }
        for (com.shajitha.familyapp.data.TodoTask t : dao.tasksNow()) {
            if (t.title.equals("Call grandma")) {
                t.memberId = first;
                dao.update(t);
            }
        }
        // Chores, habits and a drawing.
        String[][] chores = {{"Make the bed", "DAY"}, {"Feed the pet", "DAY"}, {"Vacuum", "WEEK"}, {"Take out the recycling", "WEEK"}};
        for (String[] ch : chores) {
            com.shajitha.familyapp.data.Reminder r = com.shajitha.familyapp.reminders.Reminders.create(ch[0],
                    com.shajitha.familyapp.data.Reminder.TYPE_CHORE, today, first == 0 ? null : first + (ch[0].length() % 2));
            r.repeatUnit = ch[1];
            r.alertDays = "";
            long id = com.shajitha.familyapp.reminders.Reminders.save(c, r);
            if (ch[0].equals("Feed the pet")) {
                com.shajitha.familyapp.reminders.Reminders.complete(c, id);
            }
        }
        String[] habits = {"Drink water", "Play outside", "Read for 20 minutes"};
        for (String hb : habits) {
            com.shajitha.familyapp.data.Reminder r = com.shajitha.familyapp.reminders.Reminders.create(hb,
                    com.shajitha.familyapp.data.Reminder.TYPE_HABIT, today, null);
            r.repeatUnit = "DAY";
            r.alertDays = hb.startsWith("Read") ? "0" : "";
            r.alertTime = hb.startsWith("Read") ? "19:00" : null;
            long id = com.shajitha.familyapp.reminders.Reminders.save(c, r);
            com.shajitha.familyapp.data.Recurrence.Day d = com.shajitha.familyapp.data.Recurrence.Day.parse(today);
            for (int back = 0; back < 7; back++) {
                if ((back + hb.length()) % 3 != 0) {
                    com.shajitha.familyapp.reminders.Reminders.checkIn(c, id, d.plusDays(-back).toString());
                }
            }
        }
        com.shajitha.familyapp.data.DrawingEntry drawing = new com.shajitha.familyapp.data.DrawingEntry();
        drawing.name = "Our garden";
        drawing.template = "garden";
        drawing.createdAt = drawing.updatedAt = System.currentTimeMillis();
        drawing.id = dao.insert(drawing);
        com.shajitha.familyapp.whiteboard.Drawing art = new com.shajitha.familyapp.whiteboard.Drawing("garden");
        art.add(new com.shajitha.familyapp.whiteboard.Drawing.Element(com.shajitha.familyapp.whiteboard.Drawing.Kind.STAR,
                0xFFFDD835, 0.01f, true, new float[]{0.1f, 0.05f, 0.3f, 0.2f}));
        art.add(new com.shajitha.familyapp.whiteboard.Drawing.Element(com.shajitha.familyapp.whiteboard.Drawing.Kind.BRUSH,
                0xFFE53935, 0.03f, false, new float[]{0.2f, 0.7f, 0.35f, 0.65f, 0.5f, 0.72f, 0.65f, 0.66f}));
        try {
            com.shajitha.familyapp.whiteboard.DrawingStore.save(c, drawing.id, art);
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }
        com.shajitha.familyapp.data.MealSelection meal = new com.shajitha.familyapp.data.MealSelection();
        meal.date = today;
        meal.food = "Burger";
        dao.save(meal);
    }

    @org.junit.After
    public void tearDown() {
        com.shajitha.familyapp.data.AppDatabase.resetForTests();
    }

    static final Class<?>[] SCREENS = {MainActivity.class, viewCalendar.class, MealPlanner.class,
            FamAppTimer.class, ToDoList.class, CheckList.class, ChoresActivity.class, HabitsActivity.class,
            com.shajitha.familyapp.whiteboard.WhiteboardActivity.class,
            ActivitiesFun.class, Reading.class, ContactUs.class, AboutActivity.class,
            FamilyMembersActivity.class, SettingsActivity.class};

    static void capture(String prefix) {
        for (Class<?> screen : SCREENS) {
            capture(prefix, screen, null);
        }
        for (Features.Category category : Features.Category.values()) {
            android.content.Intent intent = HubActivity.intent(
                    androidx.test.core.app.ApplicationProvider.getApplicationContext(), category);
            try (ActivityScenario<Activity> scenario = ActivityScenario.launch(intent)) {
                scenario.onActivity(activity -> {
                    shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(300));
                    save(activity, prefix + "_Hub_" + category.name());
                });
            }
        }
    }

    @SuppressWarnings("unchecked")
    static <T extends Activity> void capture(String prefix, Class<?> screen, Consumer<T> setUp) {
        try (ActivityScenario<Activity> scenario = ActivityScenario.launch((Class<Activity>) screen)) {
            scenario.onActivity(activity -> {
                if (setUp != null) {
                    setUp.accept((T) activity);
                }
                shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500));
                // Lists update in the background; give them a moment.
                for (int i = 0; i < 20; i++) {
                    shadowOf(Looper.getMainLooper()).idle();
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException ignored) {
                    }
                }
                shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100));
                save(activity, prefix + "_" + screen.getSimpleName());
            });
        }
    }

    static void save(Activity activity, String name) {
        View root = activity.getWindow().getDecorView();
        Bitmap bitmap = Bitmap.createBitmap(root.getWidth(), root.getHeight(), Bitmap.Config.ARGB_8888);
        root.draw(new Canvas(bitmap));
        File dir = new File("build/screenshots");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        try (FileOutputStream out = new FileOutputStream(new File(dir, name + ".png"))) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    public void portrait() {
        capture("1_portrait");
    }

    @Test
    @Config(qualifiers = "w891dp-h411dp-land-xxhdpi")
    public void landscape() {
        capture("2_landscape");
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-night-xxhdpi")
    public void dark() {
        capture("3_dark");
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp-xhdpi")
    public void smallPhone() {
        capture("4_small");
    }
}
