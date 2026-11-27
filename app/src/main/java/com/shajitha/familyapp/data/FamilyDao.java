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

    // ---------- Checklists (lists of items) ----------
    @Query("SELECT * FROM checklists ORDER BY archived, position, id")
    LiveData<List<Checklist>> checklists();

    @Query("SELECT * FROM checklists ORDER BY archived, position, id")
    List<Checklist> checklistsNow();

    @Query("SELECT * FROM checklists WHERE id = :id")
    LiveData<Checklist> checklist(long id);

    @Query("SELECT * FROM checklists WHERE id = :id")
    Checklist checklistNow(long id);

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM checklists")
    int nextListPosition();

    @Insert
    long insert(Checklist checklist);

    @Update
    void update(Checklist checklist);

    @Delete
    void delete(Checklist checklist);

    @Query("SELECT * FROM checklist_items WHERE checklistId = :checklistId ORDER BY position, id")
    LiveData<List<ChecklistItem>> itemsOf(long checklistId);

    @Query("SELECT * FROM checklist_items WHERE checklistId = :checklistId ORDER BY position, id")
    List<ChecklistItem> itemsOfNow(long checklistId);

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM checklist_items WHERE checklistId = :checklistId")
    int nextItemPosition(long checklistId);

    @Query("UPDATE checklist_items SET checked = 0 WHERE checklistId = :checklistId")
    void uncheckChecklist(long checklistId);

    @Query("DELETE FROM checklist_items WHERE checklistId = :checklistId")
    void deleteItemsOf(long checklistId);

    @Query("SELECT checklistId, SUM(checked) AS done, COUNT(*) AS total FROM checklist_items GROUP BY checklistId")
    LiveData<List<ChecklistCount>> checklistCounts();

    /** Sections whose items aren't linked to an existing checklist (safety net; normally none). */
    @Query("SELECT DISTINCT section FROM checklist_items WHERE checklistId NOT IN (SELECT id FROM checklists)")
    List<String> unlinkedSections();

    @Query("UPDATE checklist_items SET checklistId = :checklistId WHERE section = :section "
            + "AND checklistId NOT IN (SELECT id FROM checklists)")
    void linkSection(String section, long checklistId);

    /** Removes a checklist and its items in one step (screens ask first). */
    @Transaction
    default void deleteChecklist(Checklist checklist) {
        deleteItemsOf(checklist.id);
        delete(checklist);
    }

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

    // ---------- Reminders & Renewals (everything except chores and habits) ----------
    @Query("SELECT * FROM reminders WHERE type NOT IN ('CHORE', 'HABIT') ORDER BY dueDate, dueTime, title")
    LiveData<List<Reminder>> familyReminders();

    @Query("SELECT * FROM reminders WHERE type NOT IN ('CHORE', 'HABIT') ORDER BY dueDate, dueTime, title")
    List<Reminder> familyRemindersNow();

    @Query("SELECT * FROM reminders WHERE type NOT IN ('CHORE', 'HABIT') AND status = 'ACTIVE' "
            + "AND dueDate = :day ORDER BY dueTime, title")
    LiveData<List<Reminder>> familyRemindersOn(String day);

    @Query("SELECT * FROM reminders WHERE type NOT IN ('CHORE', 'HABIT') AND status = 'ACTIVE' "
            + "AND dueDate <= :lastDay ORDER BY dueDate, dueTime, title")
    LiveData<List<Reminder>> familyRemindersUntil(String lastDay);

    // ---------- Chores and habits (reminders of type CHORE / HABIT) ----------
    @Query("SELECT * FROM reminders WHERE type = :type AND status != 'ARCHIVED' ORDER BY status, dueDate, title")
    LiveData<List<Reminder>> remindersOfType(String type);

    @Query("SELECT * FROM reminders WHERE type = :type AND status != 'ARCHIVED' ORDER BY status, dueDate, title")
    List<Reminder> remindersOfTypeNow(String type);

    /** Completions of every reminder of a type on or after a day (yyyy-MM-dd). */
    @Query("SELECT h.* FROM reminder_history h JOIN reminders r ON h.reminderId = r.id "
            + "WHERE r.type = :type AND h.dueDate >= :fromDate ORDER BY h.dueDate, h.id")
    LiveData<List<ReminderHistory>> historyOfTypeSince(String type, String fromDate);

    @Query("SELECT h.* FROM reminder_history h JOIN reminders r ON h.reminderId = r.id "
            + "WHERE r.type = :type AND h.dueDate >= :fromDate ORDER BY h.dueDate, h.id")
    List<ReminderHistory> historyOfTypeSinceNow(String type, String fromDate);

    @Query("SELECT * FROM reminder_history WHERE reminderId = :reminderId AND dueDate = :date LIMIT 1")
    ReminderHistory historyOn(long reminderId, String date);

    @Delete
    void delete(ReminderHistory history);

    // ---------- Creative Whiteboard ----------
    @Query("SELECT * FROM drawings ORDER BY updatedAt DESC, id DESC")
    LiveData<List<DrawingEntry>> drawings();

    @Query("SELECT * FROM drawings ORDER BY updatedAt DESC, id DESC")
    List<DrawingEntry> drawingsNow();

    @Query("SELECT * FROM drawings WHERE id = :id")
    DrawingEntry drawingNow(long id);

    @Insert
    long insert(DrawingEntry drawing);

    @Update
    void update(DrawingEntry drawing);

    @Delete
    void delete(DrawingEntry drawing);
}
