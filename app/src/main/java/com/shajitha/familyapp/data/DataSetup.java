package com.shajitha.familyapp.data;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.VisibleForTesting;

import com.shajitha.familyapp.FamilyStore;
import com.shajitha.familyapp.R;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Runs once per install, on the database thread, before anything else touches the database:
 * <ol>
 *   <li>adds the default checklists and food menu for first-time users;</li>
 *   <li>copies data saved by the previous version of the app (SharedPreferences) into the database.</li>
 * </ol>
 * Each step is recorded, so it never runs twice and never overwrites what the user has changed.
 * The older saved data is left untouched as a backup.
 */
public final class DataSetup {

    static final String META = "family_meta";
    static final String SEEDED_CHECKLIST = "seeded_checklist";
    static final String SEEDED_FOODS = "seeded_foods";
    static final String MIGRATED_V1 = "migrated_prefs_v1";

    /** Default checklist sections and their items (the lists from the original app). */
    public static final String SECTION_WINTER = "Winter Checklist";
    public static final String SECTION_SUMMER = "Summer Checklist";
    public static final String SECTION_FIRST_AID = "FirstAid kit Checklist";

    private DataSetup() {
    }

    // commit() (not apply()) on purpose: the record must be on disk before anything else uses the
    // database, so defaults or old data can never be added twice.
    @android.annotation.SuppressLint("ApplySharedPref")
    @VisibleForTesting
    public static void run(Context context, AppDatabase db) {
        SharedPreferences meta = context.getSharedPreferences(META, Context.MODE_PRIVATE);
        boolean seedChecklist = !meta.getBoolean(SEEDED_CHECKLIST, false);
        boolean seedFoods = !meta.getBoolean(SEEDED_FOODS, false);
        boolean migrate = !meta.getBoolean(MIGRATED_V1, false);
        if (!seedChecklist && !seedFoods && !migrate) {
            return;
        }
        FamilyDao dao = db.dao();
        // All in one transaction: either everything is added, or nothing is.
        db.runInTransaction(() -> {
            if (seedChecklist) {
                seedChecklist(dao);
            }
            if (seedFoods) {
                for (String name : context.getResources().getStringArray(R.array.foodmenu)) {
                    Food food = new Food();
                    food.name = name;
                    food.position = dao.nextFoodPosition();
                    dao.insert(food);
                }
            }
            if (migrate) {
                migrateFromPreferences(context, dao);
            }
        });
        // Recorded only after the transaction succeeded, so a crash halfway simply runs it again.
        meta.edit()
                .putBoolean(SEEDED_CHECKLIST, true)
                .putBoolean(SEEDED_FOODS, true)
                .putBoolean(MIGRATED_V1, true)
                .commit();
    }

    private static void seedChecklist(FamilyDao dao) {
        addList(dao, "Winter", Checklist.KIND_SEASON, "winter", SECTION_WINTER, "Winter boots", "Thermal wear",
                "Cardigan/Winter coat", "Sleepwear", "Scarfs", "Gloves", "Ear muffs");
        addList(dao, "Summer", Checklist.KIND_SEASON, "summer", SECTION_SUMMER, "Sunscreen/sunblock", "Packing cubes",
                "Exercise/Active time", "Swimsuits", "Boots/Sports shoe");
        addList(dao, "First-aid kit", Checklist.KIND_LIST, "first_aid", SECTION_FIRST_AID, "Medication", "Lip balm",
                "Instant cold ice packs", "Antiseptic wipes", "Adhesive plasters large/small");
    }

    private static void addList(FamilyDao dao, String name, String kind, String template, String section, String... titles) {
        long now = System.currentTimeMillis();
        Checklist list = new Checklist();
        list.name = name;
        list.kind = kind;
        list.template = template;
        list.position = dao.nextListPosition();
        list.createdAt = list.updatedAt = now;
        long id = dao.insert(list);
        for (String title : titles) {
            ChecklistItem item = new ChecklistItem();
            item.section = section; // the original section name, as older versions saved it
            item.checklistId = id;
            item.title = title;
            item.position = dao.nextItemPosition(id);
            dao.insert(item);
        }
    }

    /**
     * Safety net, run at every start: any checklist item that isn't linked to a checklist (which
     * should never happen) gets a checklist named after its section, so no item is ever hidden.
     */
    public static void linkChecklists(AppDatabase db) {
        FamilyDao dao = db.dao();
        List<String> sections = dao.unlinkedSections();
        if (sections.isEmpty()) {
            return;
        }
        db.runInTransaction(() -> {
            long now = System.currentTimeMillis();
            for (String section : sections) {
                Checklist list = new Checklist();
                list.name = section.isEmpty() ? "Checklist" : section;
                list.position = dao.nextListPosition();
                list.createdAt = list.updatedAt = now;
                dao.linkSection(section, dao.insert(list));
            }
        });
    }

    /** Copies everything the previous version stored with FamilyStore. */
    private static void migrateFromPreferences(Context context, FamilyDao dao) {
        long now = System.currentTimeMillis();

        // To-do tasks (the old app only kept tasks that were not done yet).
        int order = 0;
        for (String title : FamilyStore.getStringList(context, "todo_items")) {
            TodoTask task = new TodoTask();
            task.title = title;
            task.createdAt = now + order++; // keeps the original order
            dao.insert(task);
        }

        // Checklist ticks were saved as "Section|Item".
        Set<String> ticked = FamilyStore.getStringSet(context, "checklist_ticked");
        if (!ticked.isEmpty()) {
            for (ChecklistItem item : dao.checklistItemsNow()) {
                if (ticked.contains(item.section + "|" + item.title)) {
                    item.checked = true;
                    dao.update(item);
                }
            }
        }

        // Today's lunch was saved as "yyyy-MM-dd|Meal".
        String meal = FamilyStore.getString(context, "meal_today");
        if (meal != null && meal.contains("|")) {
            MealSelection selection = new MealSelection();
            selection.date = meal.substring(0, meal.indexOf('|'));
            selection.food = meal.substring(meal.indexOf('|') + 1);
            dao.save(selection);
        }

        // Schedule plans were saved per day under "schedule_yyyy-MM-dd".
        for (String key : FamilyStore.keysWithPrefix(context, "schedule_")) {
            String date = key.substring("schedule_".length());
            int i = 0;
            for (String title : FamilyStore.getStringList(context, key)) {
                ScheduleEvent event = new ScheduleEvent();
                event.date = date;
                event.title = title;
                event.createdAt = now + i++;
                dao.insert(event);
            }
        }

        // Reading log entries: one book each, pages kept as pages read.
        List<JSONObject> entries = FamilyStore.getObjectList(context, "reading_log");
        for (int i = entries.size() - 1; i >= 0; i--) { // oldest first, so the newest stays on top
            JSONObject entry = entries.get(i);
            Book book = new Book();
            book.title = entry.optString("title");
            book.pagesRead = Math.max(0, entry.optInt("pages"));
            book.createdAt = entry.optLong("date", now);
            book.updatedAt = book.createdAt;
            if (!book.title.isEmpty()) {
                dao.insert(book);
            }
        }
    }

    /** Today's date as stored in the database (yyyy-MM-dd). */
    public static String today() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }
}
