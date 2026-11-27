package com.shajitha.familyapp.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/** One item in a checklist (Winter, Summer, First-Aid Kit...). */
@Entity(tableName = "checklist_items", indices = {@Index("section"), @Index("checklistId")})
public class ChecklistItem {
    @PrimaryKey(autoGenerate = true)
    public long id;

    /** Name of the section, for example "Winter Checklist". */
    @NonNull
    public String section = "";

    @NonNull
    public String title = "";

    public boolean checked;

    /** Order within the section. */
    public int position;

    /** The {@link Checklist} this item belongs to. */
    @ColumnInfo(defaultValue = "0")
    public long checklistId;

    /** Optional short note, for example "2 pairs" or "size 8". */
    @Nullable
    public String note;

    public static final int MAX_TITLE_LENGTH = 120;
    public static final int MAX_NOTE_LENGTH = 120;
}
