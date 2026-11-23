package com.shajitha.familyapp;

import android.graphics.Paint;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.divider.MaterialDividerItemDecoration;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.shajitha.familyapp.data.AppDatabase;
import com.shajitha.familyapp.data.FamilyDao;
import com.shajitha.familyapp.data.TodoTask;

import java.util.List;
import java.util.Objects;

/** To-do List: add, edit, tick off and delete family tasks. Saved in the database. */
public class ToDoList extends BaseActivity {

    private FamilyDao dao;
    private TextInputEditText itemInput;
    private TextInputLayout itemLayout;
    private TextView summary, empty;
    private View clearDone;
    private final TaskAdapter adapter = new TaskAdapter();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_to_do_list);
        dao = AppDatabase.get(this).dao();

        itemInput = findViewById(R.id.items);
        itemLayout = findViewById(R.id.itemsLayout);
        summary = findViewById(R.id.todoSummary);
        empty = findViewById(R.id.todoEmpty);
        clearDone = findViewById(R.id.clearDone);
        RecyclerView list = findViewById(R.id.Listitems);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.addItemDecoration(new MaterialDividerItemDecoration(this, MaterialDividerItemDecoration.VERTICAL));
        list.setAdapter(adapter);

        findViewById(R.id.addButton).setOnClickListener(v -> addTask());
        itemInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                addTask();
                return true;
            }
            return false;
        });
        clearDone.setOnClickListener(v -> Dialogs.confirmDelete(this, getString(R.string.todo_clear_done_title),
                getString(R.string.todo_clear_done_message),
                () -> AppDatabase.write(dao::deleteCompletedTasks)));

        dao.tasks().observe(this, this::show);
    }

    private void show(List<TodoTask> tasks) {
        adapter.submitList(tasks);
        int done = 0;
        for (TodoTask t : tasks) {
            if (t.done) {
                done++;
            }
        }
        summary.setText(tasks.isEmpty() ? "" : getString(R.string.todo_summary, done, tasks.size()));
        clearDone.setVisibility(done > 0 ? View.VISIBLE : View.GONE);
        empty.setVisibility(tasks.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void addTask() {
        String text = Dialogs.text(itemInput);
        if (text.isEmpty()) {
            itemLayout.setError(getString(R.string.todo_hint_empty));
            return;
        }
        itemLayout.setError(null);
        TodoTask task = new TodoTask();
        task.title = text;
        task.createdAt = System.currentTimeMillis();
        AppDatabase.write(() -> dao.insert(task));
        itemInput.setText("");
    }

    private void setDone(TodoTask task, boolean done) {
        if (task.done == done) {
            return;
        }
        TodoTask copy = copy(task);
        copy.done = done;
        AppDatabase.write(() -> dao.update(copy));
    }

    private void edit(TodoTask task) {
        Dialogs.editText(this, R.string.todo_edit_title, R.string.todo_hint, task.title, null, text -> {
            TodoTask copy = copy(task);
            copy.title = text;
            AppDatabase.write(() -> dao.update(copy));
        });
    }

    private void delete(TodoTask task) {
        Dialogs.confirmDelete(this, getString(R.string.todo_delete_title), task.title,
                () -> AppDatabase.write(() -> dao.delete(task)));
    }

    private static TodoTask copy(TodoTask t) {
        TodoTask c = new TodoTask();
        c.id = t.id;
        c.title = t.title;
        c.done = t.done;
        c.createdAt = t.createdAt;
        return c;
    }

    private final class TaskAdapter extends ListAdapter<TodoTask, RowHolder> {
        TaskAdapter() {
            super(new DiffUtil.ItemCallback<TodoTask>() {
                @Override
                public boolean areItemsTheSame(@NonNull TodoTask a, @NonNull TodoTask b) {
                    return a.id == b.id;
                }

                @Override
                public boolean areContentsTheSame(@NonNull TodoTask a, @NonNull TodoTask b) {
                    return a.done == b.done && Objects.equals(a.title, b.title);
                }
            });
        }

        @NonNull
        @Override
        public RowHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new RowHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_list_row, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull RowHolder holder, int position) {
            TodoTask task = getItem(position);
            holder.title.setText(task.title);
            holder.title.setPaintFlags(task.done
                    ? holder.title.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG
                    : holder.title.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
            holder.title.setAlpha(task.done ? 0.6f : 1f);
            holder.check.setOnCheckedChangeListener(null);
            holder.check.setChecked(task.done);
            holder.check.setContentDescription(task.title);
            holder.check.setOnCheckedChangeListener((b, checked) -> setDone(task, checked));
            holder.itemView.setOnClickListener(v -> holder.check.toggle());
            holder.edit.setOnClickListener(v -> edit(task));
            holder.delete.setOnClickListener(v -> delete(task));
        }
    }

    /** Views of one list row (item_list_row.xml). */
    static final class RowHolder extends RecyclerView.ViewHolder {
        final CheckBox check;
        final TextView title, subtitle;
        final View edit, delete;

        RowHolder(View itemView) {
            super(itemView);
            check = itemView.findViewById(R.id.rowCheck);
            title = itemView.findViewById(R.id.rowTitle);
            subtitle = itemView.findViewById(R.id.rowSubtitle);
            edit = itemView.findViewById(R.id.rowEdit);
            delete = itemView.findViewById(R.id.rowDelete);
        }
    }
}
