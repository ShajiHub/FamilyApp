package com.shajitha.familyapp.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * A checklist (Winter, Back to school, Grocery shopping, ...). Its items are {@link ChecklistItem}s
 * with this list's id. Only a name and an optional family member (nickname) are stored.
 */
@Entity(tableName = "checklists", indices = {@Index("archived")})
public class Checklist {

    public static final String KIND_SEASON = "SEASON";
    public static final String KIND_SCHOOL = "SCHOOL";
    public static final String KIND_LIST = "LIST";
    public static final String KIND_CUSTOM = "CUSTOM";

    public static final int MAX_NAME_LENGTH = 60;

    @PrimaryKey(autoGenerate = true)
    public long id;

    @NonNull
    public String name = "";

    /** SEASON, SCHOOL, LIST or CUSTOM (chooses the picture/icon). */
    @NonNull
    public String kind = KIND_CUSTOM;

    /** The template it was made from (for example "winter"), or null. */
    @Nullable
    public String template;

    /** Who it's for (a family member), or null for everyone. */
    @Nullable
    public Long memberId;

    /** Hidden from the main list, but kept and can be restored. */
    public boolean archived;

    /** Order on the Checklists screen. */
    public int position;

    public long createdAt;
    public long updatedAt;
}
