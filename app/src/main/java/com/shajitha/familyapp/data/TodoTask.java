package com.shajitha.familyapp.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** One task on the To-do list. */
@Entity(tableName = "todo_tasks")
public class TodoTask {
    @PrimaryKey(autoGenerate = true)
    public long id;

    @NonNull
    public String title = "";

    public boolean done;

    public long createdAt;
}
