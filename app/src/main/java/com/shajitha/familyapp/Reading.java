package com.shajitha.familyapp;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/** Reading Log: keep track of the books the family has read and how many pages. */
public class Reading extends AppCompatActivity {

    private static final String STORE_KEY = "reading_log";

    private List<JSONObject> entries;
    private final List<String> rows = new ArrayList<>();
    private ArrayAdapter<String> adapter;
    private EditText bookInput, pagesInput;
    private TextView summary;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reading);
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        bookInput = findViewById(R.id.readingBook);
        pagesInput = findViewById(R.id.readingPages);
        summary = findViewById(R.id.readingSummary);
        ListView list = findViewById(R.id.readingList);

        entries = FamilyStore.getObjectList(this, STORE_KEY);
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, rows);
        list.setAdapter(adapter);

        findViewById(R.id.readingAdd).setOnClickListener(v -> addEntry());
        pagesInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                addEntry();
                return true;
            }
            return false;
        });

        // Long-press an entry to delete it.
        list.setOnItemLongClickListener((parent, view, position, id) -> {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.delete_item_title)
                    .setMessage(rows.get(position))
                    .setPositiveButton(R.string.delete, (dialog, which) -> {
                        entries.remove(position);
                        save();
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
            return true;
        });

        refresh();
    }

    private void addEntry() {
        String title = bookInput.getText().toString().trim();
        if (title.isEmpty()) {
            Toast.makeText(this, R.string.reading_need_title, Toast.LENGTH_SHORT).show();
            return;
        }
        int pages = 0;
        try {
            pages = Integer.parseInt(pagesInput.getText().toString().trim());
        } catch (NumberFormatException ignored) {
            // Pages are optional.
        }
        try {
            JSONObject entry = new JSONObject();
            entry.put("title", title);
            entry.put("pages", pages);
            entry.put("date", System.currentTimeMillis());
            entries.add(0, entry); // newest first
        } catch (JSONException e) {
            return;
        }
        bookInput.setText("");
        pagesInput.setText("");
        bookInput.requestFocus();
        save();
    }

    private void save() {
        FamilyStore.putObjectList(this, STORE_KEY, entries);
        refresh();
    }

    private void refresh() {
        DateFormat dateFormat = DateFormat.getDateInstance(DateFormat.MEDIUM);
        rows.clear();
        int totalPages = 0;
        for (JSONObject entry : entries) {
            int pages = entry.optInt("pages");
            totalPages += pages;
            String date = dateFormat.format(new Date(entry.optLong("date")));
            rows.add(getString(R.string.reading_entry, entry.optString("title"), pages, date));
        }
        adapter.notifyDataSetChanged();
        summary.setText(getString(R.string.reading_summary, entries.size(), totalPages));
    }
}
