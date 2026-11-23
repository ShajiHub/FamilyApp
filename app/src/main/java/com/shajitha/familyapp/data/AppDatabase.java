package com.shajitha.familyapp.data;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * The app's local database (Room / SQLite), stored privately on the phone and available offline.
 *
 * <p>Important: there is no "destructive migration" fallback. When the database changes in a future
 * version, a Migration must be added so saved family data is never wiped.
 */
@Database(entities = {ScheduleEvent.class, TodoTask.class, ChecklistItem.class, Food.class,
        MealSelection.class, Book.class}, version = 1, exportSchema = true)
public abstract class AppDatabase extends RoomDatabase {

    public abstract FamilyDao dao();

    private static final String NAME = "family.db";
    private static volatile AppDatabase instance;
    private static Executor io = Executors.newSingleThreadExecutor();
    private static boolean directForTests = false;

    public static AppDatabase get(Context context) {
        if (instance == null) {
            synchronized (AppDatabase.class) {
                if (instance == null) {
                    Context app = context.getApplicationContext();
                    Builder<AppDatabase> builder = Room.databaseBuilder(app, AppDatabase.class, NAME);
                    if (directForTests) {
                        Executor direct = Runnable::run;
                        builder.allowMainThreadQueries().setQueryExecutor(direct).setTransactionExecutor(direct);
                    }
                    instance = builder.build();
                    // First thing on the database thread: default lists and copying older saved data.
                    AppDatabase db = instance;
                    io.execute(() -> DataSetup.run(app, db));
                }
            }
        }
        return instance;
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
    }
}
