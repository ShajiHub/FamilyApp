package com.shajitha.familyapp.data;

import androidx.annotation.NonNull;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

/**
 * Database upgrades. Each one only adds tables or empty optional columns: existing rows are never
 * changed, moved or deleted. Tested in DatabaseMigrationTest against the saved schema history.
 */
public final class Migrations {

    private Migrations() {
    }

    /** Version 1 to 2: family members, reminders, reminder history, and "for whom" on tasks and events. */
    public static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `family_members` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
                    + "`name` TEXT NOT NULL, `avatar` TEXT NOT NULL, `colorIndex` INTEGER NOT NULL, `role` TEXT NOT NULL, "
                    + "`position` INTEGER NOT NULL, `archived` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL)");
            db.execSQL("CREATE TABLE IF NOT EXISTS `reminders` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
                    + "`title` TEXT NOT NULL, `type` TEXT NOT NULL, `memberId` INTEGER, `dueDate` TEXT NOT NULL, "
                    + "`dueTime` TEXT, `notes` TEXT, `repeatUnit` TEXT NOT NULL, `repeatInterval` INTEGER NOT NULL, "
                    + "`anchorDay` INTEGER NOT NULL, `alertDays` TEXT NOT NULL, `alertTime` TEXT, `snoozedUntil` INTEGER, "
                    + "`status` TEXT NOT NULL, `completedAt` INTEGER, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_reminders_status` ON `reminders` (`status`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_reminders_memberId` ON `reminders` (`memberId`)");
            db.execSQL("CREATE TABLE IF NOT EXISTS `reminder_history` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
                    + "`reminderId` INTEGER NOT NULL, `title` TEXT NOT NULL, `memberId` INTEGER, "
                    + "`dueDate` TEXT NOT NULL, `completedAt` INTEGER NOT NULL)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_reminder_history_reminderId` ON `reminder_history` (`reminderId`)");
            // New optional columns start empty (NULL = "Everyone") for every existing task and event.
            db.execSQL("ALTER TABLE `todo_tasks` ADD COLUMN `memberId` INTEGER");
            db.execSQL("ALTER TABLE `schedule_events` ADD COLUMN `memberId` INTEGER");
        }
    };

    /** Version 2 to 3: Creative Whiteboard drawings. */
    public static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `drawings` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
                    + "`name` TEXT NOT NULL, `template` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)");
        }
    };

    /**
     * Version 3 to 4: proper checklists, item notes, and a category for reminders.
     *
     * <p>Every existing checklist item (and its tick) is kept exactly as it is. Each existing section
     * becomes a checklist record and its items are linked to it; the original section name stays on
     * every item. The three original lists get friendlier names (Winter, Summer, First-aid kit).
     * Existing reminders get a category from their type; nothing else about them changes.
     */
    public static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `checklists` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
                    + "`name` TEXT NOT NULL, `kind` TEXT NOT NULL, `template` TEXT, `memberId` INTEGER, "
                    + "`archived` INTEGER NOT NULL, `position` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, "
                    + "`updatedAt` INTEGER NOT NULL)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_checklists_archived` ON `checklists` (`archived`)");
            db.execSQL("ALTER TABLE `checklist_items` ADD COLUMN `checklistId` INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE `checklist_items` ADD COLUMN `note` TEXT");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_checklist_items_checklistId` ON `checklist_items` (`checklistId`)");

            // One checklist per existing section: the original three first, then the family's own lists.
            long now = System.currentTimeMillis();
            db.execSQL("INSERT INTO `checklists` (`name`, `kind`, `template`, `memberId`, `archived`, `position`, `createdAt`, `updatedAt`) "
                    + "SELECT `section`, 'CUSTOM', NULL, NULL, 0, 0, ?, ? FROM `checklist_items` GROUP BY `section` "
                    + "ORDER BY CASE `section` WHEN '" + DataSetup.SECTION_WINTER + "' THEN 0 WHEN '" + DataSetup.SECTION_SUMMER
                    + "' THEN 1 WHEN '" + DataSetup.SECTION_FIRST_AID + "' THEN 2 ELSE 3 END, MIN(`id`)",
                    new Object[]{now, now});
            db.execSQL("UPDATE `checklists` SET `position` = `id`");
            db.execSQL("UPDATE `checklist_items` SET `checklistId` = "
                    + "(SELECT c.`id` FROM `checklists` c WHERE c.`name` = `checklist_items`.`section`)");
            renameOriginal(db, DataSetup.SECTION_WINTER, "Winter", Checklist.KIND_SEASON, "winter");
            renameOriginal(db, DataSetup.SECTION_SUMMER, "Summer", Checklist.KIND_SEASON, "summer");
            renameOriginal(db, DataSetup.SECTION_FIRST_AID, "First-aid kit", Checklist.KIND_LIST, "first_aid");

            db.execSQL("ALTER TABLE `reminders` ADD COLUMN `category` TEXT");
            db.execSQL("UPDATE `reminders` SET `category` = CASE `type` WHEN 'RENEWAL' THEN 'DOCUMENTS' "
                    + "WHEN 'LIBRARY' THEN 'LIBRARY' WHEN 'APPOINTMENT' THEN 'HEALTH' WHEN 'VACCINATION' THEN 'HEALTH' "
                    + "WHEN 'OTHER' THEN 'OTHER' ELSE NULL END");
        }

        private void renameOriginal(SupportSQLiteDatabase db, String section, String name, String kind, String template) {
            db.execSQL("UPDATE `checklists` SET `name` = ?, `kind` = ?, `template` = ? WHERE `name` = ?",
                    new Object[]{name, kind, template, section});
        }
    };

    public static final Migration[] ALL = {MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4};
}
