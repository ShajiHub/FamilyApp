package com.shajitha.familyapp.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Protects saved family data when the database is upgraded to a new version.
 *
 * <p>Before an upgrade: all pending changes are written into the main file, every table's rows
 * are counted, the file is copied to a backup, and the backup is checked (SQLite integrity check,
 * same version, same row counts). Only then may the upgrade run. After it, the row counts are
 * checked again. If anything fails, the upgrade does not happen and the app shows a recovery
 * screen; the original data is left exactly as it was.
 *
 * <p>Limits of this backup: it is stored next to the database inside the app, so it does not help
 * if the app is uninstalled, its storage is cleared, or the phone is lost or reset. It is not
 * included in Android's cloud backup. It protects against a failed or interrupted upgrade.
 */
public final class DatabaseGuard {

    static final String PREFS = "db_guard";
    private static final String KEY_PENDING_FROM = "pending_from";
    private static final String KEY_COUNTS = "counts";
    private static final String KEY_LAST_UPGRADE = "last_upgrade";
    private static final String KEY_UPGRADE_CHECK_FAILED = "upgrade_check_failed";

    /** Why the database can't be opened, shown on the recovery screen. */
    public enum Problem {
        NO_SPACE_FOR_BACKUP,
        BACKUP_CHECK_FAILED,
        UPGRADE_FAILED,
        NEWER_VERSION
    }

    /** Thrown when FamilyApp must not open the database (see {@link Problem}). */
    public static final class UnavailableException extends RuntimeException {
        public final Problem problem;

        UnavailableException(Problem problem, @Nullable Throwable cause) {
            super(problem.name(), cause);
            this.problem = problem;
        }
    }

    private DatabaseGuard() {
    }

    /** Backup file for an upgrade from {@code version}, for example family-v1-backup.db. */
    public static File backupFile(Context context, String dbName, int version) {
        return context.getDatabasePath(dbName.replace(".db", "") + "-v" + version + "-backup.db");
    }

    /** The newest safety copy older than {@code belowVersion}, or -1 if there is none. */
    public static int newestBackupVersion(Context context, String dbName, int belowVersion) {
        for (int v = belowVersion - 1; v >= 1; v--) {
            if (backupFile(context, dbName, v).exists()) {
                return v;
            }
        }
        return -1;
    }

    /**
     * Call before opening the database. Returns normally if it is safe to open (no upgrade needed,
     * or a verified backup was made); throws {@link UnavailableException} otherwise.
     */
    public static void prepare(@NonNull Context context, @NonNull String dbName, int targetVersion) {
        File db = context.getDatabasePath(dbName);
        if (!db.exists()) {
            return; // new install: nothing to protect
        }
        int version;
        Map<String, Long> counts;
        SQLiteDatabase sql;
        try {
            sql = SQLiteDatabase.openDatabase(db.getPath(), null, SQLiteDatabase.OPEN_READWRITE);
        } catch (RuntimeException e) {
            throw new UnavailableException(Problem.BACKUP_CHECK_FAILED, e);
        }
        try {
            version = sql.getVersion();
            if (version == targetVersion || version == 0) {
                return;
            }
            if (version > targetVersion) {
                throw new UnavailableException(Problem.NEWER_VERSION, null);
            }
            // Write any changes still in the write-ahead log into the main file, so the copy is complete.
            try (Cursor c = sql.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null)) {
                c.moveToFirst();
            } catch (RuntimeException ignored) {
                // Not in WAL mode: nothing to write.
            }
            counts = countRows(sql);
        } finally {
            sql.close();
        }

        File backup = backupFile(context, dbName, version);
        File partial = new File(backup.getPath() + ".partial");
        try {
            copy(db, partial);
            if (!verify(partial, version, counts)) {
                //noinspection ResultOfMethodCallIgnored
                partial.delete();
                throw new UnavailableException(Problem.BACKUP_CHECK_FAILED, null);
            }
            if (backup.exists() && !backup.delete()) {
                throw new IOException("Cannot replace old backup");
            }
            if (!partial.renameTo(backup)) {
                throw new IOException("Cannot finish backup");
            }
        } catch (IOException e) {
            //noinspection ResultOfMethodCallIgnored
            partial.delete();
            throw new UnavailableException(Problem.NO_SPACE_FOR_BACKUP, e);
        }
        prefs(context).edit()
                .putInt(KEY_PENDING_FROM, version)
                .putString(KEY_COUNTS, new JSONObject(new LinkedHashMap<String, Object>(counts)).toString())
                .commit();
    }

    /** Call right after the database opened (and was upgraded): checks no rows went missing. */
    public static void afterOpen(@NonNull Context context, @NonNull androidx.sqlite.db.SupportSQLiteDatabase db) {
        SharedPreferences prefs = prefs(context);
        int from = prefs.getInt(KEY_PENDING_FROM, -1);
        if (from < 0) {
            return;
        }
        boolean ok = true;
        try {
            JSONObject before = new JSONObject(prefs.getString(KEY_COUNTS, "{}"));
            Iterator<String> tables = before.keys();
            while (tables.hasNext()) {
                String table = tables.next();
                try (Cursor c = db.query("SELECT COUNT(*) FROM `" + table.replace("`", "") + "`")) {
                    c.moveToFirst();
                    if (c.getLong(0) != before.getLong(table)) {
                        ok = false;
                    }
                }
            }
        } catch (JSONException | RuntimeException e) {
            ok = false;
        }
        SharedPreferences.Editor editor = prefs.edit().remove(KEY_PENDING_FROM).remove(KEY_COUNTS);
        if (ok) {
            editor.putString(KEY_LAST_UPGRADE, from + "->" + db.getVersion() + " at " + System.currentTimeMillis())
                    .remove(KEY_UPGRADE_CHECK_FAILED);
        } else {
            // Should never happen (upgrades only add); the backup is kept for recovery.
            editor.putBoolean(KEY_UPGRADE_CHECK_FAILED, true);
        }
        editor.commit();
    }

    /** True if the last upgrade's after-check found a difference (the backup is kept). */
    public static boolean upgradeCheckFailed(Context context) {
        return prefs(context).getBoolean(KEY_UPGRADE_CHECK_FAILED, false);
    }

    /**
     * Recovery: puts the verified backup back in place of the database (the user confirms first).
     * Returns false if there is no valid backup. The current file is kept as "...-replaced.db".
     */
    public static boolean restoreBackup(@NonNull Context context, @NonNull String dbName, int fromVersion) {
        File backup = backupFile(context, dbName, fromVersion);
        if (!backup.exists() || !verify(backup, fromVersion, null)) {
            return false;
        }
        File db = context.getDatabasePath(dbName);
        try {
            if (db.exists()) {
                copy(db, context.getDatabasePath(dbName.replace(".db", "") + "-replaced.db"));
            }
            for (String suffix : new String[]{"-wal", "-shm"}) {
                File f = new File(db.getPath() + suffix);
                if (f.exists() && !f.delete()) {
                    return false;
                }
            }
            copy(backup, db);
        } catch (IOException e) {
            return false;
        }
        prefs(context).edit().remove(KEY_PENDING_FROM).remove(KEY_COUNTS).commit();
        return true;
    }

    /** Opens a copy read-only and checks it: integrity, version and (if given) row counts. */
    @VisibleForTesting
    static boolean verify(File file, int expectedVersion, @Nullable Map<String, Long> expectedCounts) {
        SQLiteDatabase sql = null;
        try {
            sql = SQLiteDatabase.openDatabase(file.getPath(), null, SQLiteDatabase.OPEN_READONLY);
            try (Cursor c = sql.rawQuery("PRAGMA integrity_check", null)) {
                if (!c.moveToFirst() || !"ok".equalsIgnoreCase(c.getString(0))) {
                    return false;
                }
            }
            if (sql.getVersion() != expectedVersion) {
                return false;
            }
            return expectedCounts == null || countRows(sql).equals(expectedCounts);
        } catch (RuntimeException e) {
            return false;
        } finally {
            if (sql != null) {
                sql.close();
            }
        }
    }

    /** Rows in every app table (skips SQLite's and Room's own bookkeeping tables). */
    static Map<String, Long> countRows(SQLiteDatabase sql) {
        Map<String, Long> counts = new LinkedHashMap<>();
        try (Cursor tables = sql.rawQuery("SELECT name FROM sqlite_master WHERE type = 'table' "
                + "AND name NOT LIKE 'sqlite_%' AND name NOT IN ('android_metadata', 'room_master_table') ORDER BY name", null)) {
            while (tables.moveToNext()) {
                String table = tables.getString(0);
                try (Cursor c = sql.rawQuery("SELECT COUNT(*) FROM `" + table.replace("`", "") + "`", null)) {
                    c.moveToFirst();
                    counts.put(table, c.getLong(0));
                }
            }
        }
        return counts;
    }

    private static void copy(File from, File to) throws IOException {
        try (InputStream in = new FileInputStream(from); OutputStream out = new FileOutputStream(to)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            ((FileOutputStream) out).getFD().sync();
        }
    }

    static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
