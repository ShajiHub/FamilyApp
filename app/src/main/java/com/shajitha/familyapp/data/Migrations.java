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

    public static final Migration[] ALL = {MIGRATION_1_2, MIGRATION_2_3};
}
