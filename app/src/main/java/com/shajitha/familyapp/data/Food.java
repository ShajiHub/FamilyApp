package com.shajitha.familyapp.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** One choice on the Meal Planner menu and wheel. */
@Entity(tableName = "foods")
public class Food {
    @PrimaryKey(autoGenerate = true)
    public long id;

    @NonNull
    public String name = "";

    /** Order on the menu and the wheel. */
    public int position;
}
