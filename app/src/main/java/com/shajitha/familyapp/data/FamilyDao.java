package com.shajitha.familyapp.data;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;
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

    // ---------- Family members ----------
    @Query("SELECT * FROM family_members WHERE archived = 0 ORDER BY position, id")
    LiveData<List<FamilyMember>> activeMembers();

    @Query("SELECT * FROM family_members ORDER BY archived, position, id")
    LiveData<List<FamilyMember>> allMembers();

    @Query("SELECT * FROM family_members ORDER BY archived, position, id")
    List<FamilyMember> allMembersNow();

    @Query("SELECT * FROM family_members WHERE id = :id")
    FamilyMember memberNow(long id);

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM family_members")
    int nextMemberPosition();

    @Insert
    long insert(FamilyMember member);

    @Update
    void update(FamilyMember member);

    @Query("UPDATE todo_tasks SET memberId = NULL WHERE memberId = :memberId")
    void unassignTasks(long memberId);

    @Query("UPDATE schedule_events SET memberId = NULL WHERE memberId = :memberId")
    void unassignEvents(long memberId);

    @Query("UPDATE reminders SET memberId = NULL WHERE memberId = :memberId")
    void unassignReminders(long memberId);

    @Query("UPDATE reminder_history SET memberId = NULL WHERE memberId = :memberId")
    void unassignHistory(long memberId);

    @Query("DELETE FROM family_members WHERE id = :memberId")
    void deleteMemberRow(long memberId);

    /**
     * Permanently removes a member. Their tasks, events, reminders and history are kept and become
     * "Everyone". All in one transaction: either everything happens or nothing does.
     */
    @Transaction
    default void deleteMemberKeepingItems(long memberId) {
        unassignTasks(memberId);
        unassignEvents(memberId);
        unassignReminders(memberId);
        unassignHistory(memberId);
        deleteMemberRow(memberId);
    }

    @Query("SELECT COUNT(*) FROM todo_tasks WHERE memberId = :memberId")
    int taskCountFor(long memberId);

    @Query("SELECT COUNT(*) FROM schedule_events WHERE memberId = :memberId")
    int eventCountFor(long memberId);

    // ---------- Reminders ----------
    @Query("SELECT * FROM reminders WHERE status != 'ARCHIVED' ORDER BY status, dueDate, dueTime")
    LiveData<List<Reminder>> reminders();

    @Query("SELECT * FROM reminders WHERE status = 'ACTIVE'")
    List<Reminder> activeRemindersNow();

    @Query("SELECT * FROM reminders ORDER BY id")
    List<Reminder> allRemindersNow();

    @Query("SELECT * FROM reminders WHERE id = :id")
    Reminder reminderNow(long id);

    @Insert
    long insert(Reminder reminder);

    @Update
    void update(Reminder reminder);

    @Delete
    void delete(Reminder reminder);

    @Insert
    long insert(ReminderHistory history);

    @Query("SELECT * FROM reminder_history WHERE reminderId = :reminderId ORDER BY completedAt DESC, id DESC")
    List<ReminderHistory> historyNow(long reminderId);

    @Query("SELECT * FROM reminder_history ORDER BY completedAt DESC, id DESC")
    List<ReminderHistory> allHistoryNow();
}
