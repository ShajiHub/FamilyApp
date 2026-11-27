package com.shajitha.familyapp;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import android.widget.PopupMenu;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.shajitha.familyapp.data.AppDatabase;
import com.shajitha.familyapp.data.Checklist;
import com.shajitha.familyapp.data.ChecklistItem;
import com.shajitha.familyapp.data.FamilyDao;
import com.shajitha.familyapp.data.FamilyMember;

import java.util.ArrayList;
import java.util.List;

/**
 * One checklist: clean tick boxes, an "Add new item" field, and a ⋮ menu per item for Edit and
 * Delete. The title-bar menu renames, duplicates, unticks, archives, restores or deletes the list.
 * Nothing is ever unticked or deleted automatically; everything that removes something asks first.
 */
public class ChecklistActivity extends BaseActivity {

    static final String EXTRA_ID = "checklist_id";

    private FamilyDao dao;
    private long id;
    private Checklist list;
    private List<ChecklistItem> items = new ArrayList<>();
    private List<FamilyMember> members = new ArrayList<>();
    private final ItemsAdapter adapter = new ItemsAdapter();

    static Intent intent(Context context, long checklistId) {
        return new Intent(context, ChecklistActivity.class).putExtra(EXTRA_ID, checklistId);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_check_list);
        dao = AppDatabase.get(this).dao();
        id = getIntent().getLongExtra(EXTRA_ID, -1);

        RecyclerView recyclerView = findViewById(R.id.rView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        dao.checklist(id).observe(this, c -> {
            if (c == null) {
                finish(); // deleted (or never existed)
                return;
            }
            list = c;
            setTitle(c.name);
            invalidateOptionsMenu();
            adapter.notifyDataSetChanged();
        });
        dao.itemsOf(id).observe(this, all -> {
            items = all;
            adapter.notifyDataSetChanged();
        });
        dao.allMembers().observe(this, all -> {
            members = all;
            adapter.notifyDataSetChanged();
        });
    }

    // ---------- Title-bar menu ----------

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_checklist, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        boolean archived = list != null && list.archived;
        menu.findItem(R.id.action_list_archive).setVisible(!archived);
        menu.findItem(R.id.action_list_restore).setVisible(archived);
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (list == null) {
            return super.onOptionsItemSelected(item);
        }
        int itemId = item.getItemId();
        if (itemId == R.id.action_list_rename) {
            rename();
        } else if (itemId == R.id.action_list_duplicate) {
            duplicate();
        } else if (itemId == R.id.action_list_reset) {
            reset();
        } else if (itemId == R.id.action_list_archive) {
            setArchived(true);
        } else if (itemId == R.id.action_list_restore) {
            setArchived(false);
        } else if (itemId == R.id.action_list_delete) {
            deleteList();
        } else {
            return super.onOptionsItemSelected(item);
        }
        return true;
    }

    private void rename() {
        CheckList.nameDialog(this, R.string.list_rename, R.string.save, list.name, list.memberId, members,
                (name, memberId) -> {
                    Checklist copy = copy(list);
                    copy.name = name;
                    copy.memberId = memberId;
                    copy.updatedAt = System.currentTimeMillis();
                    AppDatabase.write(() -> dao.update(copy));
                });
    }

    private void duplicate() {
        Checklist source = list;
        CheckList.nameDialog(this, R.string.list_duplicate_title, R.string.list_duplicate,
                getString(R.string.list_copy_name, source.name), source.memberId, members,
                (name, memberId) -> AppDatabase.write(() -> {
                    long copyId = ChecklistTemplates.duplicate(dao, source, name, memberId);
                    runOnUiThread(() -> {
                        if (!isFinishing()) {
                            startActivity(intent(this, copyId));
                        }
                    });
                }));
    }

    private void reset() {
        long listId = id;
        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.list_reset_title, list.name))
                .setMessage(R.string.list_reset_message)
                .setPositiveButton(R.string.list_reset_confirm, (d, w) -> AppDatabase.write(() -> dao.uncheckChecklist(listId)))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void setArchived(boolean archived) {
        Checklist copy = copy(list);
        copy.archived = archived;
        copy.updatedAt = System.currentTimeMillis();
        AppDatabase.write(() -> dao.update(copy));
    }

    private void deleteList() {
        Checklist target = list;
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.list_delete_title)
                .setMessage(getString(R.string.list_delete_message, target.name))
                .setPositiveButton(R.string.delete, (d, w) -> AppDatabase.write(() -> dao.deleteChecklist(target)))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private static Checklist copy(Checklist c) {
        Checklist x = new Checklist();
        x.id = c.id;
        x.name = c.name;
        x.kind = c.kind;
        x.template = c.template;
        x.memberId = c.memberId;
        x.archived = c.archived;
        x.position = c.position;
        x.createdAt = c.createdAt;
        x.updatedAt = c.updatedAt;
        return x;
    }

    // ---------- Items ----------

    /** Adds an item typed into the "Add new item" field. */
    void addItem(String text) {
        String title = ChecklistTemplates.trim(text, ChecklistItem.MAX_TITLE_LENGTH);
        if (title.isEmpty() || list == null) {
            return;
        }
        ChecklistItem item = new ChecklistItem();
        item.checklistId = id;
        item.section = list.name;
        item.title = title;
        AppDatabase.write(() -> {
            item.position = dao.nextItemPosition(id);
            dao.insert(item);
        });
    }

    private void setChecked(ChecklistItem item, boolean checked) {
        if (item.checked == checked) {
            return;
        }
        ChecklistItem copy = copy(item);
        copy.checked = checked;
        AppDatabase.write(() -> dao.update(copy));
    }

    void editItem(ChecklistItem item) {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_checklist_item, null);
        TextInputLayout layout = view.findViewById(R.id.itemTitleLayout);
        TextInputEditText title = view.findViewById(R.id.itemTitleInput);
        TextInputEditText note = view.findViewById(R.id.itemNoteInput);
        title.setText(item.title);
        title.setSelection(title.length());
        note.setText(item.note);
        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.list_item_edit)
                .setView(view)
                .setPositiveButton(R.string.save, null)
                .setNegativeButton(R.string.cancel, null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String text = Dialogs.text(title);
            if (text.isEmpty()) {
                layout.setError(getString(R.string.error_required));
                return;
            }
            ChecklistItem copy = copy(item);
            copy.title = ChecklistTemplates.trim(text, ChecklistItem.MAX_TITLE_LENGTH);
            String n = ChecklistTemplates.trim(Dialogs.text(note), ChecklistItem.MAX_NOTE_LENGTH);
            copy.note = n.isEmpty() ? null : n;
            AppDatabase.write(() -> dao.update(copy));
            dialog.dismiss();
        }));
        Dialogs.showWithKeyboard(dialog);
    }

    void deleteItem(ChecklistItem item) {
        Dialogs.confirmDelete(this, getString(R.string.list_item_delete), item.title,
                () -> AppDatabase.write(() -> dao.delete(item)));
    }

    /** The ⋮ menu of an item (also on long-press and for screen readers). */
    void showItemMenu(View anchor, ChecklistItem item) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenu().add(0, 1, 0, R.string.edit);
        popup.getMenu().add(0, 2, 1, R.string.delete);
        popup.setOnMenuItemClickListener(m -> {
            if (m.getItemId() == 1) {
                editItem(item);
            } else {
                deleteItem(item);
            }
            return true;
        });
        popup.show();
    }

    private static ChecklistItem copy(ChecklistItem i) {
        ChecklistItem c = new ChecklistItem();
        c.id = i.id;
        c.section = i.section;
        c.checklistId = i.checklistId;
        c.title = i.title;
        c.note = i.note;
        c.checked = i.checked;
        c.position = i.position;
        return c;
    }

    private final class ItemsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private static final int HEADER = 0, ITEM = 1, ADD = 2;

        @Override
        public int getItemCount() {
            return list == null ? 0 : items.size() + 2;
        }

        @Override
        public int getItemViewType(int position) {
            return position == 0 ? HEADER : position == items.size() + 1 ? ADD : ITEM;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            int layout = viewType == ITEM ? R.layout.item_checklist_item
                    : viewType == ADD ? R.layout.item_checklist_add : R.layout.item_checklist_header;
            return new RecyclerView.ViewHolder(LayoutInflater.from(parent.getContext()).inflate(layout, parent, false)) {
            };
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            View v = holder.itemView;
            int type = getItemViewType(position);
            if (type == HEADER) {
                bindHeader(v);
            } else if (type == ADD) {
                bindAdd(v);
            } else {
                bindItem(v, items.get(position - 1));
            }
        }

        private void bindHeader(View v) {
            int done = 0;
            for (ChecklistItem i : items) {
                if (i.checked) {
                    done++;
                }
            }
            String progress = CheckList.progressText(ChecklistActivity.this, done, items.size());
            if (!items.isEmpty() && done == items.size()) {
                progress = progress + " · " + getString(R.string.list_all_done);
            }
            ((TextView) v.findViewById(R.id.listProgressText)).setText(progress);
            LinearProgressIndicator bar = v.findViewById(R.id.listProgressBar);
            bar.setMax(Math.max(1, items.size()));
            bar.setProgress(done);
            TextView who = v.findViewById(R.id.listFor);
            FamilyMember m = Members.find(members, list.memberId);
            who.setVisibility(m == null ? View.GONE : View.VISIBLE);
            who.setText(m == null ? "" : getString(R.string.lists_for, Members.label(m)));
            v.findViewById(R.id.listArchivedNote).setVisibility(list.archived ? View.VISIBLE : View.GONE);
        }

        private void bindItem(View v, ChecklistItem item) {
            CheckBox check = v.findViewById(R.id.itemCheck);
            ((TextView) v.findViewById(R.id.itemTitle)).setText(item.title);
            TextView note = v.findViewById(R.id.itemNote);
            note.setText(item.note);
            note.setVisibility(item.note == null || item.note.isEmpty() ? View.GONE : View.VISIBLE);
            check.setOnCheckedChangeListener(null);
            check.setChecked(item.checked);
            check.setContentDescription(item.title);
            check.setOnCheckedChangeListener((b, checked) -> setChecked(item, checked));
            v.setOnClickListener(x -> check.toggle());
            View more = v.findViewById(R.id.itemMore);
            more.setContentDescription(getString(R.string.list_item_more, item.title));
            more.setOnClickListener(x -> showItemMenu(more, item));
            v.setOnLongClickListener(x -> {
                showItemMenu(more, item);
                return true;
            });
            // Screen readers: Edit and Delete as actions on the row itself (replacing a recycled row's).
            if (v.getTag() instanceof int[]) {
                for (int actionId : (int[]) v.getTag()) {
                    ViewCompat.removeAccessibilityAction(v, actionId);
                }
            }
            int edit = ViewCompat.addAccessibilityAction(v, getString(R.string.list_item_edit), (view, args) -> {
                editItem(item);
                return true;
            });
            int delete = ViewCompat.addAccessibilityAction(v, getString(R.string.list_item_delete), (view, args) -> {
                deleteItem(item);
                return true;
            });
            v.setTag(new int[]{edit, delete});
        }

        private void bindAdd(View v) {
            EditText input = v.findViewById(R.id.addItemInput);
            ((TextInputLayout) v.findViewById(R.id.addItemLayout)).setHint(getString(R.string.checklist_add_to, list.name));
            Runnable add = () -> {
                addItem(input.getText().toString());
                input.setText("");
            };
            v.findViewById(R.id.addItemButton).setOnClickListener(x -> add.run());
            input.setOnEditorActionListener((x, actionId, event) -> {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    add.run();
                    return true;
                }
                return false;
            });
        }
    }
}
