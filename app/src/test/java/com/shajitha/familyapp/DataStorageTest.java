package com.shajitha.familyapp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.shajitha.familyapp.data.AppDatabase;
import com.shajitha.familyapp.data.Book;
import com.shajitha.familyapp.data.ChecklistItem;
import com.shajitha.familyapp.data.DataSetup;
import com.shajitha.familyapp.data.FamilyDao;
import com.shajitha.familyapp.data.Food;
import com.shajitha.familyapp.data.ScheduleEvent;
import com.shajitha.familyapp.data.TodoTask;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

/** The database: default lists, copying data from the previous version, and keeping data across restarts. */
@RunWith(AndroidJUnit4.class)
public class DataStorageTest {

    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        AppDatabase.useDirectExecutorsForTests();
        AppDatabase.resetForTests();
    }

    @After
    public void tearDown() {
        AppDatabase.resetForTests();
    }

    private FamilyDao dao() {
        return AppDatabase.get(context).dao();
    }

    @Test
    public void firstTimeUsersGetDefaultMenuAndChecklists() {
        List<Food> foods = dao().foodsNow();
        assertEquals(8, foods.size());
        assertEquals("Chicken wrap", foods.get(0).name);
        assertEquals(17, dao().checklistItemsNow().size());
        assertTrue(dao().tasksNow().isEmpty());
        assertTrue(dao().booksNow().isEmpty());
    }

    @Test
    public void dataSavedByThePreviousVersionIsCopiedOnce() throws Exception {
        // What version 1.1 of the app saved.
        FamilyStore.putStringList(context, "todo_items", Arrays.asList("Buy milk", "Call grandma"));
        FamilyStore.putStringSet(context, "checklist_ticked",
                new HashSet<>(Collections.singletonList("Winter Checklist|Gloves")));
        FamilyStore.putString(context, "meal_today", "2026-11-23|Sushi");
        FamilyStore.putStringList(context, "schedule_2026-11-25", Arrays.asList("Swimming 5pm", "Dentist"));
        JSONObject book = new JSONObject().put("title", "Matilda").put("pages", 240).put("date", 1000L);
        FamilyStore.putObjectList(context, "reading_log", Collections.singletonList(book));

        FamilyDao dao = dao();
        List<TodoTask> tasks = dao.tasksNow();
        assertEquals(2, tasks.size());
        assertEquals("Buy milk", tasks.get(0).title);
        assertFalse(tasks.get(0).done);

        long ticked = dao.checklistItemsNow().stream().filter(i -> i.checked).count();
        assertEquals(1, ticked);
        assertTrue(dao.checklistItemsNow().stream().anyMatch(i -> i.checked && i.title.equals("Gloves")));

        assertEquals("Sushi", dao.mealOnNow("2026-11-23").food);

        List<ScheduleEvent> events = dao.allEventsNow();
        assertEquals(2, events.size());
        assertEquals("2026-11-25", events.get(0).date);

        List<Book> books = dao.booksNow();
        assertEquals(1, books.size());
        assertEquals("Matilda", books.get(0).title);
        assertEquals(240, books.get(0).pagesRead);

        // The old saved data is kept as a backup.
        assertEquals(2, FamilyStore.getStringList(context, "todo_items").size());

        // Opening the app again must not copy anything a second time.
        AppDatabase.resetForTests();
        DataSetup.run(context, AppDatabase.get(context));
        assertEquals(2, dao().tasksNow().size());
        assertEquals(2, dao().allEventsNow().size());
        assertEquals(1, dao().booksNow().size());
        assertEquals(8, dao().foodsNow().size());
        assertEquals(17, dao().checklistItemsNow().size());
    }

    @Test
    public void userChangesAreNeverOverwrittenByDefaults() {
        FamilyDao dao = dao();
        for (Food food : dao.foodsNow()) {
            dao.delete(food);
        }
        ChecklistItem first = dao.checklistItemsNow().get(0);
        dao.delete(first);

        AppDatabase.resetForTests(); // close and reopen the app
        DataSetup.run(context, AppDatabase.get(context));
        assertTrue("deleted foods stay deleted", dao().foodsNow().isEmpty());
        assertEquals(16, dao().checklistItemsNow().size());
    }

    @Test
    public void dataSurvivesRestart() {
        TodoTask task = new TodoTask();
        task.title = "Pack lunch";
        task.createdAt = 1;
        dao().insert(task);
        Book book = new Book();
        book.title = "The Hobbit";
        book.totalPages = 300;
        book.pagesRead = 120;
        dao().insert(book);

        AppDatabase.resetForTests(); // the app is closed

        assertEquals("Pack lunch", dao().tasksNow().get(0).title);
        assertEquals(120, dao().booksNow().get(0).pagesRead);
    }
}
