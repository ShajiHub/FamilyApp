package com.shajitha.familyapp.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * One completed occurrence of a reminder. The title is copied, so the history stays readable
 * even if the reminder is later renamed or deleted.
 */
@Entity(tableName = "reminder_history", indices = {@Index("reminderId")})
public class ReminderHistory {
    @PrimaryKey(autoGenerate = true)
    public long id;

    public long reminderId;

    @NonNull
    public String title = "";

    @Nullable
    public Long memberId;

    /** The due date that was completed (yyyy-MM-dd). */
    @NonNull
    public String dueDate = "";

    public long completedAt;
}
