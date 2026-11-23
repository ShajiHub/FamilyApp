package com.shajitha.familyapp.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** The lunch chosen for a day. Kept as a history, one row per day. */
@Entity(tableName = "meal_selections")
public class MealSelection {
    /** Day, as yyyy-MM-dd. */
    @PrimaryKey
    @NonNull
    public String date = "";

    /** Name of the food, kept as text so history stays correct if the menu changes. */
    @NonNull
    public String food = "";
}
