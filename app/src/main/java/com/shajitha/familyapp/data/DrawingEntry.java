package com.shajitha.familyapp.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * A saved Creative Whiteboard drawing. The drawing itself (every stroke and shape, so it stays
 * editable) is kept in a file named after the id in the app's private storage; see DrawingStore.
 */
@Entity(tableName = "drawings")
public class DrawingEntry {
    public static final int MAX_NAME_LENGTH = 60;

    @PrimaryKey(autoGenerate = true)
    public long id;

    @NonNull
    public String name = "";

    /** Starter background, for example "garden" or "space" (see Templates). */
    @NonNull
    public String template = "blank";

    public long createdAt;

    public long updatedAt;
}
