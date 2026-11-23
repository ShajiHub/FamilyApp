package com.shajitha.familyapp.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/** One item in a checklist section (Winter, Summer, First-Aid Kit...). */
@Entity(tableName = "checklist_items", indices = {@Index("section")})
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
}
