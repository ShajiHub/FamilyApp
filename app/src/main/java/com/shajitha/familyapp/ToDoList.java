package com.shajitha.familyapp;


import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;

import java.util.ArrayList;

public class ToDoList extends BaseActivity {

    private static final String STORE_KEY = "todo_items";
    private static final long REMOVE_DELAY_MS = 1000;

    private ArrayList<String> itemList;
    private ArrayAdapter<String> adapter;
    private EditText itemInput;
    private ListView list;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_to_do_list);

        itemInput = findViewById(R.id.items);
        Button addItem = findViewById(R.id.addButton);
        list = findViewById(R.id.Listitems);

        // Saved items are loaded every time, so they survive closing the app and rotating the phone.
        itemList = new ArrayList<>(FamilyStore.getStringList(this, STORE_KEY));
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_multiple_choice, itemList);
        list.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
        list.setAdapter(adapter);

        addItem.setOnClickListener(v -> addItem());
        itemInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                addItem();
                return true;
            }
            return false;
        });

        // Tapping an item ticks it, then removes it a second later.
        list.setOnItemClickListener((parent, view, position, id) -> {
            String value = itemList.get(position);
            list.setItemChecked(position, true);
            list.setEnabled(false);

            // Save straight away, so the item stays done even if the screen closes during the delay.
            ArrayList<String> remaining = new ArrayList<>(itemList);
            remaining.remove(position);
            FamilyStore.putStringList(this, STORE_KEY, remaining);

            handler.postDelayed(() -> {
                list.setItemChecked(position, false);
                adapter.remove(value);
                list.setEnabled(true);
            }, REMOVE_DELAY_MS);
        });
    }

    private void addItem() {
        String text = itemInput.getText().toString().trim();
        if (text.isEmpty()) {
            itemInput.setHint(R.string.todo_hint_empty);
            return;
        }
        adapter.add(text);
        FamilyStore.putStringList(this, STORE_KEY, itemList);
        itemInput.setText("");
        itemInput.setHint(R.string.todo_hint);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
    }
}
