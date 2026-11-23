package com.shajitha.familyapp.data;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

/** All database reads and writes. Lists are LiveData, so screens update automatically. */
@Dao
public interface FamilyDao {

    // ---------- Schedule ----------
    @Query("SELECT * FROM schedule_events WHERE date = :date ORDER BY time IS NULL, time, createdAt")
    LiveData<List<ScheduleEvent>> eventsOn(String date);

    /** Number of events in a month; monthPrefix is yyyy-MM-. */
    @Query("SELECT COUNT(*) FROM schedule_events WHERE date LIKE :monthPrefix || '%'")
    LiveData<Integer> eventCountInMonth(String monthPrefix);

    @Query("SELECT DISTINCT date FROM schedule_events WHERE date LIKE :monthPrefix || '%'")
    LiveData<List<String>> daysWithEvents(String monthPrefix);

    @Query("SELECT * FROM schedule_events ORDER BY date, time")
    List<ScheduleEvent> allEventsNow();

    @Insert
    long insert(ScheduleEvent event);

    @Update
    void update(ScheduleEvent event);

    @Delete
    void delete(ScheduleEvent event);

    // ---------- To-do ----------
    @Query("SELECT * FROM todo_tasks ORDER BY done, createdAt")
    LiveData<List<TodoTask>> tasks();

    @Query("SELECT * FROM todo_tasks ORDER BY done, createdAt")
    List<TodoTask> tasksNow();

    @Insert
    long insert(TodoTask task);

    @Update
    void update(TodoTask task);

    @Delete
    void delete(TodoTask task);

    @Query("DELETE FROM todo_tasks WHERE done = 1")
    int deleteCompletedTasks();

    // ---------- Checklist ----------
    @Query("SELECT * FROM checklist_items ORDER BY section, position, id")
    LiveData<List<ChecklistItem>> checklistItems();

    @Query("SELECT * FROM checklist_items ORDER BY section, position, id")
    List<ChecklistItem> checklistItemsNow();

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM checklist_items WHERE section = :section")
    int nextChecklistPosition(String section);

    @Insert
    long insert(ChecklistItem item);

    @Update
    void update(ChecklistItem item);

    @Delete
    void delete(ChecklistItem item);

    @Query("UPDATE checklist_items SET checked = 0 WHERE section = :section")
    void uncheckSection(String section);

    // ---------- Meal planner ----------
    @Query("SELECT * FROM foods ORDER BY position, id")
    LiveData<List<Food>> foods();

    @Query("SELECT * FROM foods ORDER BY position, id")
    List<Food> foodsNow();

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM foods")
    int nextFoodPosition();

    @Insert
    long insert(Food food);

    @Update
    void update(Food food);

    @Delete
    void delete(Food food);

    @Query("SELECT * FROM meal_selections WHERE date = :date")
    LiveData<MealSelection> mealOn(String date);

    @Query("SELECT * FROM meal_selections WHERE date = :date")
    MealSelection mealOnNow(String date);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void save(MealSelection selection);

    // ---------- Reading log ----------
    @Query("SELECT * FROM books ORDER BY updatedAt DESC, id DESC")
    LiveData<List<Book>> books();

    @Query("SELECT * FROM books ORDER BY updatedAt DESC, id DESC")
    List<Book> booksNow();

    @Insert
    long insert(Book book);

    @Update
    void update(Book book);

    @Delete
    void delete(Book book);
}
