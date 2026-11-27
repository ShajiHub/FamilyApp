package com.shajitha.familyapp.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * A person in the family. Deliberately minimal: a name or nickname, an avatar and a role.
 * No birthday, photo, contact details or ID numbers are stored.
 * Other records (tasks, events, reminders, and later chores, habits and learning progress)
 * point to a member with an optional memberId; removing a member never removes those records.
 */
@Entity(tableName = "family_members")
public class FamilyMember {
    public static final String ROLE_PARENT = "PARENT";
    public static final String ROLE_CHILD = "CHILD";
    public static final String ROLE_OTHER = "OTHER";
    public static final int MAX_NAME_LENGTH = 40;

    @PrimaryKey(autoGenerate = true)
    public long id;

    /** Name or nickname. */
    @NonNull
    public String name = "";

    /** An emoji, for example "👧". */
    @NonNull
    public String avatar = "🙂";

    /** Index into the app's avatar colours. */
    public int colorIndex;

    /** PARENT, CHILD or OTHER. Only used to adapt wording (for example kid-friendly chores). */
    @NonNull
    public String role = ROLE_OTHER;

    /** Order in lists. */
    public int position;

    /** Archived members are hidden from pickers but keep their history. */
    public boolean archived;

    public long createdAt;
}
