package com.shajitha.familyapp.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * A reminder: an appointment, renewal, library book, vaccination or anything else with a date.
 * Only a title, dates and optional notes are stored; the app asks people not to enter
 * document numbers or medical details.
 */
@Entity(tableName = "reminders", indices = {@Index("status"), @Index("memberId")})
public class Reminder {
    // Types
    public static final String TYPE_APPOINTMENT = "APPOINTMENT";
    public static final String TYPE_RENEWAL = "RENEWAL";
    public static final String TYPE_LIBRARY = "LIBRARY";
    public static final String TYPE_VACCINATION = "VACCINATION";
    public static final String TYPE_CHORE = "CHORE";
    public static final String TYPE_HABIT = "HABIT";
    public static final String TYPE_OTHER = "OTHER";
    // Status
    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_ARCHIVED = "ARCHIVED";

    public static final int MAX_TITLE_LENGTH = 80;
    public static final int MAX_NOTES_LENGTH = 500;

    @PrimaryKey(autoGenerate = true)
    public long id;

    @NonNull
    public String title = "";

    @NonNull
    public String type = TYPE_OTHER;

    /** Who it is for, or null for everyone. */
    @Nullable
    public Long memberId;

    /** Next due date, as yyyy-MM-dd (a calendar day, not a moment in time). */
    @NonNull
    public String dueDate = "";

    /** Optional time on the due date, as HH:mm. */
    @Nullable
    public String dueTime;

    @Nullable
    public String notes;

    /** How it repeats: see {@link Recurrence}. */
    @NonNull
    public String repeatUnit = Recurrence.NONE;

    public int repeatInterval = 1;

    /**
     * The day of the month the reminder was set for. Kept separately so a reminder on the 31st
     * goes back to the 31st after a short month, and Feb 29 comes back in leap years.
     */
    public int anchorDay;

    /** Days before the due date to alert, for example "30,7,0". */
    @NonNull
    public String alertDays = "0";

    /** Time of day for alerts (HH:mm), or null to use the app-wide setting. */
    @Nullable
    public String alertTime;

    /** When set, "remind me later": the next alert is not before this moment (epoch millis). */
    @Nullable
    public Long snoozedUntil;

    @NonNull
    public String status = STATUS_ACTIVE;

    @Nullable
    public Long completedAt;

    public long createdAt;

    public long updatedAt;

    /**
     * Which area it belongs to (DOCUMENTS, LIBRARY, HEALTH, HOME, ...; see ReminderTemplates), so a
     * whole area's alerts can be turned off. Null for chores, habits and older reminders without one.
     */
    @Nullable
    public String category;
}
