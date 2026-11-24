package com.shajitha.familyapp.data;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * The app's local database (Room / SQLite), stored privately on the phone and available offline.
 *
 * <p>Important: there is no "destructive migration" fallback. Every version change has a Migration
 * in {@link Migrations} that only adds, and {@link DatabaseGuard} makes and checks a backup first.
 */
@Database(entities = {ScheduleEvent.class, TodoTask.class, ChecklistItem.class, Food.class,
        MealSelection.class, Book.class, FamilyMember.class, Reminder.class, ReminderHistory.class},
        version = 2, exportSchema = true)
public abstract class AppDatabase extends RoomDatabase {

    public abstract FamilyDao dao();

    public static final String NAME = "family.db";
    public static final int VERSION = 2;
    private static volatile AppDatabase instance;
    /** Set when the database must not be opened; the Home screen then shows the recovery screen. */
    @Nullable
    private static volatile DatabaseGuard.Problem problem;
    @VisibleForTesting
    static Migration[] migrations = Migrations.ALL;
    private static Executor io = Executors.newSingleThreadExecutor();
    private static boolean directForTests = false;

    public static AppDatabase get(Context context) {
        if (instance == null) {
            synchronized (AppDatabase.class) {
                if (instance == null) {
                    Context app = context.getApplicationContext();
                    if (problem != null) {
                        throw new DatabaseGuard.UnavailableException(problem, null);
                    }
                    // Verified backup first; refuses to continue if the backup can't be made.
                    try {
                        DatabaseGuard.prepare(app, NAME, VERSION);
                    } catch (DatabaseGuard.UnavailableException e) {
                        problem = e.problem;
                        throw e;
                    }
                    Builder<AppDatabase> builder = Room.databaseBuilder(app, AppDatabase.class, NAME)
                            .addMigrations(migrations);
                    if (directForTests) {
                        Executor direct = Runnable::run;
                        builder.allowMainThreadQueries().setQueryExecutor(direct).setTransactionExecutor(direct);
                    }
                    AppDatabase db = builder.build();
                    // Open (and upgrade) now, so a failed upgrade is caught here and not in some screen.
                    // A failed upgrade is rolled back by SQLite, leaving the old data as it was.
                    try {
                        DatabaseGuard.afterOpen(app, db.getOpenHelper().getWritableDatabase());
                    } catch (RuntimeException e) {
                        db.close();
                        problem = DatabaseGuard.Problem.UPGRADE_FAILED;
                        throw new DatabaseGuard.UnavailableException(problem, e);
                    }
                    instance = db;
                    // First thing on the database thread: default lists and copying older saved data.
                    io.execute(() -> DataSetup.run(app, db));
                }
            }
        }
        return instance;
    }

    /** The reason the database can't be used right now, or null when everything is fine. */
    @Nullable
    public static DatabaseGuard.Problem problem() {
        return problem;
    }

    /** Clears a problem so the next {@link #get} tries again (from the recovery screen). */
    public static synchronized void clearProblem() {
        problem = null;
    }

    /** Runs a database write in the background, in the order it was requested. */
    public static void write(@NonNull Runnable work) {
        io.execute(work);
    }

    /** Tests: run everything immediately on the calling thread. */
    @VisibleForTesting
    public static synchronized void useDirectExecutorsForTests() {
        directForTests = true;
        io = Runnable::run;
    }

    /** Tests: forget the database so each test starts fresh. */
    @VisibleForTesting
    public static synchronized void resetForTests() {
        if (instance != null) {
            instance.close();
            instance = null;
        }
        problem = null;
        migrations = Migrations.ALL;
    }
}
