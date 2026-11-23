package com.shajitha.familyapp.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/** A plan or event on one day of the Monthly Schedule. */
@Entity(tableName = "schedule_events", indices = {@Index("date")})
public class ScheduleEvent {
    @PrimaryKey(autoGenerate = true)
    public long id;

    /** Day of the event, as yyyy-MM-dd. */
    @NonNull
    public String date = "";

    /** Optional start time, as HH:mm (24-hour). */
    @Nullable
    public String time;

    @NonNull
    public String title = "";

    @Nullable
    public String notes;

    public long createdAt;
}
