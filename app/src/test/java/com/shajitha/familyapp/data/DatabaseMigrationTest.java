package com.shajitha.familyapp.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.annotation.NonNull;
import androidx.room.migration.Migration;
import androidx.room.testing.MigrationTestHelper;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Database version 1 to 2: every existing record is kept exactly, a verified backup is made first,
 * and failures leave the original data untouched.
 */
@RunWith(AndroidJUnit4.class)
public class DatabaseMigrationTest {

    @Rule
    public MigrationTestHelper helper = new MigrationTestHelper(InstrumentationRegistry.getInstrumentation(),
            AppDatabase.class, new ArrayList<>(), new FrameworkSQLiteOpenHelperFactory());

    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        AppDatabase.useDirectExecutorsForTests();
        AppDatabase.resetForTests();
        // A real version-1 user already has their defaults and old data copied.
        context.getSharedPreferences(DataSetup.META, Context.MODE_PRIVATE).edit()
                .putBoolean(DataSetup.SEEDED_CHECKLIST, true).putBoolean(DataSetup.SEEDED_FOODS, true)
                .putBoolean(DataSetup.MIGRATED_V1, true).commit();
    }

    @After
    public void tearDown() {
        AppDatabase.resetForTests();
    }

    /** A realistic version-1 database, created from the saved version-1 schema. */
    private void createVersion1(String name) throws Exception {
        SupportSQLiteDatabase db = helper.createDatabase(name, 1);
        db.execSQL("INSERT INTO todo_tasks (id, title, done, createdAt) VALUES (1, 'Pack school bags', 0, 100), "
                + "(2, 'Call grandma', 1, 200), (3, 'Emoji 👧 and \"quotes\" and ''apostrophes''', 0, 300)");
        db.execSQL("INSERT INTO schedule_events (id, date, time, title, notes, createdAt) VALUES "
                + "(1, '2026-11-25', '08:30', 'Swimming', 'Bring goggles', 1), (2, '2026-12-01', NULL, 'Birthday', NULL, 2)");
        db.execSQL("INSERT INTO checklist_items (id, section, title, checked, position) VALUES "
                + "(1, 'Winter Checklist', 'Gloves', 1, 0), (2, 'Camping', 'Tent', 0, 0)");
        db.execSQL("INSERT INTO foods (id, name, position) VALUES (1, 'Tacos', 0), (2, 'Pizza', 1)");
        db.execSQL("INSERT INTO meal_selections (date, food) VALUES ('2026-11-24', 'Tacos')");
        db.execSQL("INSERT INTO books (id, title, totalPages, pagesRead, createdAt, updatedAt) VALUES (1, 'Matilda', 240, 120, 5, 6)");
        db.close();
    }

    private static List<String> rows(SupportSQLiteDatabase db, String sql) {
        List<String> out = new ArrayList<>();
        try (Cursor c = db.query(sql)) {
            while (c.moveToNext()) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < c.getColumnCount(); i++) {
                    sb.append(i == 0 ? "" : "|").append(c.isNull(i) ? "NULL" : c.getString(i));
                }
                out.add(sb.toString());
            }
        }
        return out;
    }

    @Test
    public void upgradeKeepsEveryRecordExactly() throws Exception {
        createVersion1("migration-test.db");
        SupportSQLiteDatabase v1 = helper.runMigrationsAndValidate("migration-test.db", 1, false);
        List<String> tasksBefore = rows(v1, "SELECT id, title, done, createdAt FROM todo_tasks ORDER BY id");
        List<String> eventsBefore = rows(v1, "SELECT id, date, time, title, notes, createdAt FROM schedule_events ORDER BY id");
        List<String> othersBefore = new ArrayList<>();
        for (String t : new String[]{"checklist_items", "foods", "meal_selections", "books"}) {
            othersBefore.addAll(rows(v1, "SELECT * FROM " + t));
        }
        v1.close();

        // Validates the upgraded schema matches version 2 exactly.
        SupportSQLiteDatabase v2 = helper.runMigrationsAndValidate("migration-test.db", 2, true, Migrations.MIGRATION_1_2);
        assertEquals(tasksBefore, rows(v2, "SELECT id, title, done, createdAt FROM todo_tasks ORDER BY id"));
        assertEquals(eventsBefore, rows(v2, "SELECT id, date, time, title, notes, createdAt FROM schedule_events ORDER BY id"));
        List<String> othersAfter = new ArrayList<>();
        for (String t : new String[]{"checklist_items", "foods", "meal_selections", "books"}) {
            othersAfter.addAll(rows(v2, "SELECT * FROM " + t));
        }
        assertEquals(othersBefore, othersAfter);
        // New "for whom" fields start empty (Everyone).
        assertEquals(List.of("NULL", "NULL", "NULL"), rows(v2, "SELECT memberId FROM todo_tasks"));
        assertEquals(List.of("NULL", "NULL"), rows(v2, "SELECT memberId FROM schedule_events"));
        // New tables exist and are empty.
        assertEquals(List.of("0"), rows(v2, "SELECT COUNT(*) FROM family_members"));
        assertEquals(List.of("0"), rows(v2, "SELECT COUNT(*) FROM reminders"));
        assertEquals(List.of("0"), rows(v2, "SELECT COUNT(*) FROM reminder_history"));
        v2.close();
    }

    @Test
    public void appStartMakesAVerifiedBackupThenUpgrades() throws Exception {
        createVersion1(AppDatabase.NAME);
        FamilyDao dao = AppDatabase.get(context).dao(); // the real start-up path

        File backup = DatabaseGuard.backupFile(context, AppDatabase.NAME, 1);
        assertTrue("backup made", backup.exists());
        assertTrue("backup passes integrity, version and count checks", DatabaseGuard.verify(backup, 1, null));
        SQLiteDatabase b = SQLiteDatabase.openDatabase(backup.getPath(), null, SQLiteDatabase.OPEN_READONLY);
        Map<String, Long> backupCounts = DatabaseGuard.countRows(b);
        b.close();
        assertEquals(Long.valueOf(3), backupCounts.get("todo_tasks"));
        assertEquals(Long.valueOf(2), backupCounts.get("schedule_events"));

        // Everything is there after the upgrade.
        assertEquals(3, dao.tasksNow().size());
        assertEquals("Pack school bags", dao.tasksNow().get(0).title);
        assertNull(dao.tasksNow().get(0).memberId);
        assertEquals(2, dao.allEventsNow().size());
        assertEquals("Bring goggles", dao.allEventsNow().get(0).notes);
        assertEquals(2, dao.foodsNow().size());
        assertEquals("Tacos", dao.mealOnNow("2026-11-24").food);
        assertEquals(120, dao.booksNow().get(0).pagesRead);
        assertFalse("after-upgrade check passed", DatabaseGuard.upgradeCheckFailed(context));
        // Defaults weren't added again for an existing user.
        assertEquals(2, dao.checklistItemsNow().size());

        // Opening again: no new backup needed, nothing changes.
        long backupTime = backup.lastModified();
        AppDatabase.resetForTests();
        assertEquals(3, AppDatabase.get(context).dao().tasksNow().size());
        assertEquals(backupTime, backup.lastModified());
    }

    @Test
    public void noUpgradeWhenTheBackupCannotBeMade() throws Exception {
        createVersion1(AppDatabase.NAME);
        // Simulate a full disk: the backup file can't be written.
        File partial = new File(DatabaseGuard.backupFile(context, AppDatabase.NAME, 1).getPath() + ".partial");
        assertTrue(partial.mkdirs());
        try {
            AppDatabase.get(context);
            fail("should not open");
        } catch (DatabaseGuard.UnavailableException e) {
            assertEquals(DatabaseGuard.Problem.NO_SPACE_FOR_BACKUP, e.problem);
        }
        assertEquals(DatabaseGuard.Problem.NO_SPACE_FOR_BACKUP, AppDatabase.problem());
        assertOriginalUntouched();
    }

    @Test
    public void aFailedUpgradeIsRolledBackAndTheBackupCanBeRestored() throws Exception {
        createVersion1(AppDatabase.NAME);
        AppDatabase.migrations = new Migration[]{new Migration(1, 2) {
            @Override
            public void migrate(@NonNull SupportSQLiteDatabase db) {
                db.execSQL("CREATE TABLE half_done (id INTEGER)");
                throw new IllegalStateException("simulated upgrade failure");
            }
        }, Migrations.MIGRATION_2_3, Migrations.MIGRATION_3_4};
        try {
            AppDatabase.get(context);
            fail("should not open");
        } catch (DatabaseGuard.UnavailableException e) {
            assertEquals(DatabaseGuard.Problem.UPGRADE_FAILED, e.problem);
        }
        assertOriginalUntouched();
        assertTrue(DatabaseGuard.backupFile(context, AppDatabase.NAME, 1).exists());

        // Recovery: restore the safety copy, then a working version upgrades normally.
        assertTrue(DatabaseGuard.restoreBackup(context, AppDatabase.NAME, 1));
        assertTrue(context.getDatabasePath("family-replaced.db").exists());
        AppDatabase.resetForTests(); // back to the real upgrade
        assertEquals(3, AppDatabase.get(context).dao().tasksNow().size());
    }

    @Test
    public void version1UpgradesAllTheWayToTheLatest() throws Exception {
        createVersion1("chain.db");
        SupportSQLiteDatabase v4 = helper.runMigrationsAndValidate("chain.db", 4, true,
                Migrations.MIGRATION_1_2, Migrations.MIGRATION_2_3, Migrations.MIGRATION_3_4);
        assertEquals(List.of("3"), rows(v4, "SELECT COUNT(*) FROM todo_tasks"));
        assertEquals(List.of("0"), rows(v4, "SELECT COUNT(*) FROM drawings"));
        // The tick on Gloves and the family's own Camping list are kept, now as checklists.
        assertEquals(List.of("Winter|SEASON|winter", "Camping|CUSTOM|NULL"),
                rows(v4, "SELECT name, kind, template FROM checklists ORDER BY position"));
        assertEquals(List.of("1|Winter Checklist|Gloves|1|Winter", "2|Camping|Tent|0|Camping"),
                rows(v4, "SELECT i.id, i.section, i.title, i.checked, c.name FROM checklist_items i "
                        + "JOIN checklists c ON c.id = i.checklistId ORDER BY i.id"));
        v4.close();
    }

    /** A realistic version-3 database: the three original lists, a list of the family's own, reminders. */
    @Test
    public void version3UpgradesTo4KeepingEveryChecklistAndTick() throws Exception {
        SupportSQLiteDatabase v3 = helper.createDatabase("v3.db", 3);
        v3.execSQL("INSERT INTO checklist_items (id, section, title, checked, position) VALUES "
                + "(1, 'Winter Checklist', 'Winter boots', 1, 0), (2, 'Winter Checklist', 'Hand warmers', 0, 7), "
                + "(3, 'Summer Checklist', 'Swimsuits', 0, 3), (4, 'FirstAid kit Checklist', 'Lip balm', 1, 1), "
                + "(5, 'Camping', 'Tent', 1, 0), (6, 'Camping', 'Torch ''big''', 0, 1)");
        v3.execSQL("INSERT INTO reminders (id, title, type, memberId, dueDate, dueTime, notes, repeatUnit, repeatInterval, "
                + "anchorDay, alertDays, alertTime, snoozedUntil, status, completedAt, createdAt, updatedAt) VALUES "
                + "(1, 'Passport', 'RENEWAL', NULL, '2027-05-01', NULL, NULL, 'NONE', 1, 1, '30,7', NULL, NULL, 'ACTIVE', NULL, 1, 1), "
                + "(2, 'Make the bed', 'CHORE', NULL, '2026-11-27', NULL, NULL, 'DAY', 1, 27, '', NULL, NULL, 'ACTIVE', NULL, 1, 1), "
                + "(3, 'Dentist', 'APPOINTMENT', NULL, '2026-12-03', '10:00', NULL, 'NONE', 1, 3, '1', NULL, NULL, 'ACTIVE', NULL, 1, 1), "
                + "(4, 'Test reminder', 'OTHER', NULL, '2026-11-20', NULL, NULL, 'NONE', 1, 20, '0', NULL, NULL, 'COMPLETED', 5, 1, 1)");
        List<String> itemsBefore = rows(v3, "SELECT id, section, title, checked, position FROM checklist_items ORDER BY id");
        List<String> remindersBefore = rows(v3, "SELECT id, title, type, dueDate, dueTime, alertDays, status FROM reminders ORDER BY id");
        v3.close();

        SupportSQLiteDatabase v4 = helper.runMigrationsAndValidate("v3.db", 4, true, Migrations.MIGRATION_3_4);
        assertEquals("every item, tick and order kept exactly", itemsBefore,
                rows(v4, "SELECT id, section, title, checked, position FROM checklist_items ORDER BY id"));
        assertEquals(remindersBefore, rows(v4, "SELECT id, title, type, dueDate, dueTime, alertDays, status FROM reminders ORDER BY id"));
        assertEquals(List.of("Winter|SEASON|winter|0", "Summer|SEASON|summer|0", "First-aid kit|LIST|first_aid|0", "Camping|CUSTOM|NULL|0"),
                rows(v4, "SELECT name, kind, template, archived FROM checklists ORDER BY position"));
        assertEquals(List.of("1|Winter", "2|Winter", "3|Summer", "4|First-aid kit", "5|Camping", "6|Camping"),
                rows(v4, "SELECT i.id, c.name FROM checklist_items i JOIN checklists c ON c.id = i.checklistId ORDER BY i.id"));
        assertEquals(List.of("NULL", "NULL", "NULL", "NULL", "NULL", "NULL"), rows(v4, "SELECT note FROM checklist_items"));
        assertEquals(List.of("1|DOCUMENTS", "2|NULL", "3|HEALTH", "4|OTHER"),
                rows(v4, "SELECT id, category FROM reminders ORDER BY id"));
        v4.close();
    }

    @Test
    public void version3UpgradesWithItsOwnCheckedBackupAndTheAppSeesEveryList() throws Exception {
        SupportSQLiteDatabase v3 = helper.createDatabase(AppDatabase.NAME, 3);
        v3.execSQL("INSERT INTO checklist_items (id, section, title, checked, position) VALUES "
                + "(1, 'Winter Checklist', 'Gloves', 1, 0), (2, 'Camping', 'Tent', 0, 0)");
        v3.close();
        FamilyDao dao = AppDatabase.get(context).dao();
        File backup = DatabaseGuard.backupFile(context, AppDatabase.NAME, 3);
        assertTrue("version-3 safety copy made", backup.exists());
        assertTrue(DatabaseGuard.verify(backup, 3, null));
        List<Checklist> lists = dao.checklistsNow();
        assertEquals(2, lists.size());
        assertEquals("Winter", lists.get(0).name);
        assertTrue(dao.itemsOfNow(lists.get(0).id).get(0).checked);
        assertEquals("Tent", dao.itemsOfNow(lists.get(1).id).get(0).title);
        assertEquals("nothing is left unlinked", 0, dao.unlinkedSections().size());
    }

    @Test
    public void version2UpgradesWithItsOwnCheckedBackup() throws Exception {
        SupportSQLiteDatabase v2 = helper.createDatabase(AppDatabase.NAME, 2);
        v2.execSQL("INSERT INTO family_members (id, name, avatar, colorIndex, role, position, archived, createdAt) "
                + "VALUES (1, 'Mia', '👧', 1, 'CHILD', 0, 0, 1)");
        v2.execSQL("INSERT INTO todo_tasks (id, title, done, createdAt, memberId) VALUES (1, 'Tidy room', 0, 1, 1)");
        v2.execSQL("INSERT INTO reminders (id, title, type, memberId, dueDate, dueTime, notes, repeatUnit, repeatInterval, "
                + "anchorDay, alertDays, alertTime, snoozedUntil, status, completedAt, createdAt, updatedAt) VALUES "
                + "(1, 'Library books', 'LIBRARY', 1, '2026-12-05', NULL, NULL, 'NONE', 1, 5, '2', NULL, NULL, 'ACTIVE', NULL, 1, 1)");
        v2.execSQL("INSERT INTO reminder_history (id, reminderId, title, memberId, dueDate, completedAt) VALUES (1, 1, 'Library books', 1, '2026-11-01', 5)");
        v2.close();

        FamilyDao dao = AppDatabase.get(context).dao();
        File backup = DatabaseGuard.backupFile(context, AppDatabase.NAME, 2);
        assertTrue("version-2 safety copy made", backup.exists());
        assertTrue(DatabaseGuard.verify(backup, 2, null));
        assertEquals("Mia", dao.allMembersNow().get(0).name);
        assertEquals(Long.valueOf(1), dao.tasksNow().get(0).memberId);
        assertEquals("Library books", dao.reminderNow(1).title);
        assertEquals(1, dao.historyNow(1).size());
        assertTrue(dao.drawingsNow().isEmpty());
        assertEquals("the old library reminder gets the Library area", "LIBRARY", dao.reminderNow(1).category);
        assertEquals("the recovery screen offers the newest copy", 2,
                DatabaseGuard.newestBackupVersion(context, AppDatabase.NAME, AppDatabase.VERSION));
    }

    @Test
    public void dataFromANewerVersionIsNeverTouched() {
        File file = context.getDatabasePath(AppDatabase.NAME);
        //noinspection ResultOfMethodCallIgnored
        file.getParentFile().mkdirs();
        SQLiteDatabase db = SQLiteDatabase.openOrCreateDatabase(file, null);
        db.execSQL("CREATE TABLE future (x TEXT)");
        db.execSQL("INSERT INTO future VALUES ('keep me')");
        db.setVersion(AppDatabase.VERSION + 1);
        db.close();
        try {
            AppDatabase.get(context);
            fail("should not open");
        } catch (DatabaseGuard.UnavailableException e) {
            assertEquals(DatabaseGuard.Problem.NEWER_VERSION, e.problem);
        }
        SQLiteDatabase check = SQLiteDatabase.openDatabase(file.getPath(), null, SQLiteDatabase.OPEN_READONLY);
        assertEquals(AppDatabase.VERSION + 1, check.getVersion());
        try (Cursor c = check.rawQuery("SELECT x FROM future", null)) {
            assertTrue(c.moveToFirst());
            assertEquals("keep me", c.getString(0));
        }
        check.close();
    }

    @Test
    public void aDamagedBackupFailsVerification() throws Exception {
        createVersion1("other.db");
        File copy = context.getDatabasePath("other.db");
        assertTrue(DatabaseGuard.verify(copy, 1, null));
        assertFalse("wrong version", DatabaseGuard.verify(copy, 2, null));
        java.nio.file.Files.write(copy.toPath(), "not a database".getBytes());
        assertFalse(DatabaseGuard.verify(copy, 1, null));
    }

    @Test
    public void newInstallNeedsNoBackup() {
        AppDatabase.get(context).dao().tasksNow();
        assertFalse(DatabaseGuard.backupFile(context, AppDatabase.NAME, 1).exists());
        SharedPreferences prefs = DatabaseGuard.prefs(context);
        assertFalse(prefs.contains("pending_from"));
    }

    private void assertOriginalUntouched() {
        SQLiteDatabase db = SQLiteDatabase.openDatabase(context.getDatabasePath(AppDatabase.NAME).getPath(), null,
                SQLiteDatabase.OPEN_READONLY);
        try {
            assertEquals("still version 1", 1, db.getVersion());
            Map<String, Long> counts = DatabaseGuard.countRows(db);
            assertEquals(Long.valueOf(3), counts.get("todo_tasks"));
            assertEquals(Long.valueOf(2), counts.get("schedule_events"));
            assertEquals(Long.valueOf(1), counts.get("books"));
            assertNull("no half-finished tables", counts.get("half_done"));
            assertNull(counts.get("reminders"));
            try (Cursor c = db.rawQuery("SELECT title FROM todo_tasks WHERE id = 3", null)) {
                assertTrue(c.moveToFirst());
                assertEquals("Emoji 👧 and \"quotes\" and 'apostrophes'", c.getString(0));
            }
        } finally {
            db.close();
        }
        assertNotNull(context);
    }
}
