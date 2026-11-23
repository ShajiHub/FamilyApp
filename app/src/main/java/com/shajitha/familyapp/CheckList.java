package com.shajitha.familyapp;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.shajitha.familyapp.data.AppDatabase;
import com.shajitha.familyapp.data.ChecklistItem;
import com.shajitha.familyapp.data.DataSetup;
import com.shajitha.familyapp.data.FamilyDao;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Checklists: packing and first-aid lists. Items and ticks are saved; items can be added, edited and deleted. */
public class CheckList extends BaseActivity {

    /** The original lists, shown first and with their pictures. */
    private static final List<String> BUILT_IN = Arrays.asList(
            DataSetup.SECTION_WINTER, DataSetup.SECTION_SUMMER, DataSetup.SECTION_FIRST_AID);

    private FamilyDao dao;
    private final ChecklistAdapter adapter = new ChecklistAdapter();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_check_list);
        dao = AppDatabase.get(this).dao();

        RecyclerView recyclerView = findViewById(R.id.rView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        dao.checklistItems().observe(this, adapter::setItems);
    }

    /** Friendly name for a section. */
    String displayName(String section) {
        if (DataSetup.SECTION_WINTER.equals(section)) {
            return getString(R.string.checklist_winter);
        } else if (DataSetup.SECTION_SUMMER.equals(section)) {
            return getString(R.string.checklist_summer);
        } else if (DataSetup.SECTION_FIRST_AID.equals(section)) {
            return getString(R.string.checklist_first_aid);
        }
        return section;
    }

    private static int imageFor(String section) {
        if (DataSetup.SECTION_WINTER.equals(section)) {
            return R.drawable.winter;
        } else if (DataSetup.SECTION_SUMMER.equals(section)) {
            return R.drawable.done;
        } else if (DataSetup.SECTION_FIRST_AID.equals(section)) {
            return R.drawable.firstaidkit;
        }
        return 0;
    }

    private void addItem(String section) {
        Dialogs.editText(this, R.string.checklist_add_item, R.string.checklist_item_hint, null, null, text -> {
            ChecklistItem item = new ChecklistItem();
            item.section = section;
            item.title = text;
            AppDatabase.write(() -> {
                item.position = dao.nextChecklistPosition(section);
                dao.insert(item);
            });
        });
    }

    private void editItem(ChecklistItem item) {
        Dialogs.editText(this, R.string.checklist_edit_item, R.string.checklist_item_hint, item.title, null, text -> {
            ChecklistItem copy = copy(item);
            copy.title = text;
            AppDatabase.write(() -> dao.update(copy));
        });
    }

    private void deleteItem(ChecklistItem item) {
        Dialogs.confirmDelete(this, getString(R.string.checklist_delete_item), item.title,
                () -> AppDatabase.write(() -> dao.delete(item)));
    }

    private void setChecked(ChecklistItem item, boolean checked) {
        if (item.checked == checked) {
            return;
        }
        ChecklistItem copy = copy(item);
        copy.checked = checked;
        AppDatabase.write(() -> dao.update(copy));
    }

    private void untickAll(String section) {
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.checklist_untick_title, displayName(section)))
                .setMessage(R.string.checklist_untick_message)
                .setPositiveButton(R.string.checklist_untick_all, (d, w) -> AppDatabase.write(() -> dao.uncheckSection(section)))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /** Creates a new checklist: asks for its name, then its first item. */
    private void newChecklist() {
        Dialogs.editText(this, R.string.checklist_new, R.string.checklist_name_hint, null,
                name -> adapter.hasSection(name) ? getString(R.string.error_duplicate) : null,
                name -> Dialogs.editText(this, R.string.checklist_add_item, R.string.checklist_item_hint, null, null,
                        first -> {
                            ChecklistItem item = new ChecklistItem();
                            item.section = name;
                            item.title = first;
                            AppDatabase.write(() -> dao.insert(item));
                        }));
    }

    private static ChecklistItem copy(ChecklistItem i) {
        ChecklistItem c = new ChecklistItem();
        c.id = i.id;
        c.section = i.section;
        c.title = i.title;
        c.checked = i.checked;
        c.position = i.position;
        return c;
    }

    /** Rows: a heading per section, its items, and a "New checklist" button at the end. */
    private final class ChecklistAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private static final int HEADER = 0, ITEM = 1, FOOTER = 2;
        private final List<Object> rows = new ArrayList<>();
        private final Map<String, int[]> progress = new LinkedHashMap<>();

        void setItems(List<ChecklistItem> items) {
            // Built-in lists first, in their usual order, then the family's own lists A–Z.
            Map<String, List<ChecklistItem>> bySection = new LinkedHashMap<>();
            for (String section : BUILT_IN) {
                bySection.put(section, new ArrayList<>());
            }
            Map<String, List<ChecklistItem>> custom = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            for (ChecklistItem item : items) {
                Map<String, List<ChecklistItem>> target = bySection.containsKey(item.section) ? bySection : custom;
                List<ChecklistItem> sectionItems = target.get(item.section);
                if (sectionItems == null) {
                    sectionItems = new ArrayList<>();
                    target.put(item.section, sectionItems);
                }
                sectionItems.add(item);
            }
            bySection.putAll(custom);

            rows.clear();
            progress.clear();
            for (Map.Entry<String, List<ChecklistItem>> e : bySection.entrySet()) {
                List<ChecklistItem> sectionItems = e.getValue();
                if (sectionItems.isEmpty() && !BUILT_IN.contains(e.getKey())) {
                    continue;
                }
                int done = 0;
                for (ChecklistItem item : sectionItems) {
                    if (item.checked) {
                        done++;
                    }
                }
                progress.put(e.getKey(), new int[]{done, sectionItems.size()});
                rows.add(e.getKey());
                rows.addAll(sectionItems);
            }
            rows.add(FOOTER);
            notifyDataSetChanged();
        }

        boolean hasSection(String name) {
            for (String s : progress.keySet()) {
                if (s.equalsIgnoreCase(name) || displayName(s).equalsIgnoreCase(name)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public int getItemViewType(int position) {
            Object row = rows.get(position);
            if (row instanceof String) {
                return HEADER;
            }
            return row instanceof ChecklistItem ? ITEM : FOOTER;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(parent.getContext());
            if (viewType == ITEM) {
                return new ToDoList.RowHolder(inflater.inflate(R.layout.item_list_row, parent, false));
            }
            int layout = viewType == HEADER ? R.layout.item_checklist_section : R.layout.item_checklist_footer;
            return new RecyclerView.ViewHolder(inflater.inflate(layout, parent, false)) {
            };
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            Object row = rows.get(position);
            View view = holder.itemView;
            if (row instanceof String) {
                String section = (String) row;
                int[] p = progress.get(section);
                ((TextView) view.findViewById(R.id.sectionTitle)).setText(displayName(section));
                ((TextView) view.findViewById(R.id.sectionProgress)).setText(p[1] == 0
                        ? getString(R.string.checklist_empty_section)
                        : getString(R.string.checklist_progress, p[0], p[1]));
                ImageView image = view.findViewById(R.id.sectionImage);
                int res = imageFor(section);
                if (res != 0) {
                    image.setImageResource(res);
                    image.setPadding(0, 0, 0, 0);
                } else {
                    image.setImageResource(R.drawable.ic_fact_check);
                    int pad = (int) (14 * getResources().getDisplayMetrics().density);
                    image.setPadding(pad, pad, pad, pad);
                }
                View reset = view.findViewById(R.id.sectionReset);
                reset.setVisibility(p[0] > 0 ? View.VISIBLE : View.INVISIBLE);
                reset.setOnClickListener(v -> untickAll(section));
                view.findViewById(R.id.sectionAdd).setOnClickListener(v -> addItem(section));
            } else if (row instanceof ChecklistItem) {
                ChecklistItem item = (ChecklistItem) row;
                ToDoList.RowHolder h = (ToDoList.RowHolder) holder;
                h.title.setText(item.title);
                h.check.setOnCheckedChangeListener(null);
                h.check.setChecked(item.checked);
                h.check.setContentDescription(item.title);
                h.check.setOnCheckedChangeListener((b, checked) -> setChecked(item, checked));
                h.itemView.setOnClickListener(v -> h.check.toggle());
                h.edit.setOnClickListener(v -> editItem(item));
                h.delete.setOnClickListener(v -> deleteItem(item));
            } else {
                view.findViewById(R.id.newChecklist).setOnClickListener(v -> newChecklist());
            }
        }

        @Override
        public int getItemCount() {
            return rows.size();
        }
    }
}
