package com.shajitha.familyapp.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** A book in the Reading Log, with reading progress. */
@Entity(tableName = "books")
public class Book {
    @PrimaryKey(autoGenerate = true)
    public long id;

    @NonNull
    public String title = "";

    /** Number of pages in the book, or 0 if not known. */
    public int totalPages;

    public int pagesRead;

    public long createdAt;

    public long updatedAt;

    public boolean isFinished() {
        return totalPages > 0 && pagesRead >= totalPages;
    }
}
