package com.shajitha.familyapp;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.shajitha.familyapp.data.AppDatabase;
import com.shajitha.familyapp.data.Checklist;
import com.shajitha.familyapp.data.ChecklistCount;
import com.shajitha.familyapp.data.FamilyDao;
import com.shajitha.familyapp.data.FamilyMember;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Smart Checklists: every checklist with its progress. Tap one to open it; "New checklist" starts
 * one from a template or a blank page. Archived lists are kept at the bottom and can be restored.
 */
public class CheckList extends BaseActivity {

    private FamilyDao dao;
    private List<Checklist> lists = new ArrayList<>();
    private final Map<Long, int[]> counts = new HashMap<>();
    private List<FamilyMember> members = new ArrayList<>();
    private final ListsAdapter adapter = new ListsAdapter();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_check_list);
        dao = AppDatabase.get(this).dao();

        RecyclerView recyclerView = findViewById(R.id.rView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        dao.checklists().observe(this, all -> {
            lists = all;
            adapter.rebuild();
        });
        dao.checklistCounts().observe(this, all -> {
            counts.clear();
            for (ChecklistCount c : all) {
                counts.put(c.checklistId, new int[]{c.done, c.total});
            }
            adapter.rebuild();
        });
        dao.allMembers().observe(this, all -> {
            members = all;
            adapter.rebuild();
        });
    }

    // ---------- Shared by the Checklists, Templates and checklist screens ----------

    /** "Start a checklist": choose a template (or blank), then name it. */
    static void chooseTemplate(@NonNull Activity activity, @Nullable List<FamilyMember> members) {
        String[] names = new String[ChecklistTemplates.ALL.size()];
        for (int i = 0; i < names.length; i++) {
            names[i] = activity.getString(ChecklistTemplates.ALL.get(i).name);
        }
        new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.lists_choose_template)
                .setItems(names, (d, which) -> nameAndCreate(activity, ChecklistTemplates.ALL.get(which), members))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /** Asks for the name and (optionally) who it's for, creates the checklist and opens it. */
    static AlertDialog nameAndCreate(@NonNull Activity activity, @NonNull ChecklistTemplates.Template template,
                                     @Nullable List<FamilyMember> members) {
        String suggested = ChecklistTemplates.BLANK.equals(template.key) ? "" : activity.getString(template.name);
        return nameDialog(activity, R.string.lists_new_title, R.string.lists_create, suggested, null, members,
                (name, memberId) -> {
                    Context app = activity.getApplicationContext();
                    FamilyDao dao = AppDatabase.get(app).dao();
                    AppDatabase.write(() -> {
                        long id = ChecklistTemplates.create(app, dao, template, name, memberId);
                        activity.runOnUiThread(() -> {
                            if (!activity.isFinishing()) {
                                activity.startActivity(ChecklistActivity.intent(activity, id));
                            }
                        });
                    });
                });
    }

    interface OnNamed {
        void onNamed(@NonNull String name, @Nullable Long memberId);
    }

    /** The name + "For (optional)" dialog used to create, duplicate and rename checklists. */
    static AlertDialog nameDialog(@NonNull Context context, int title, int positive, @Nullable String name,
                                  @Nullable Long memberId, @Nullable List<FamilyMember> members, @NonNull OnNamed onNamed) {
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_checklist, null);
        TextInputLayout layout = view.findViewById(R.id.checklistNameLayout);
        TextInputEditText input = view.findViewById(R.id.checklistNameInput);
        MaterialAutoCompleteTextView who = view.findViewById(R.id.checklistMember);
        if (name != null) {
            input.setText(name);
            input.setSelection(input.length());
        }
        Members.attachPicker(who, members, memberId);
        AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                .setTitle(title)
                .setView(view)
                .setPositiveButton(positive, null)
                .setNegativeButton(R.string.cancel, null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String text = Dialogs.text(input);
            if (text.isEmpty()) {
                layout.setError(context.getString(R.string.error_required));
                return;
            }
            if (text.length() > Checklist.MAX_NAME_LENGTH) {
                layout.setError(context.getString(R.string.lists_name_too_long));
                return;
            }
            onNamed.onNamed(text, Members.selected(who));
            dialog.dismiss();
        }));
        Dialogs.showWithKeyboard(dialog);
        return dialog;
    }

    /** "5 of 12 completed" or "No items yet". */
    static String progressText(Context context, int done, int total) {
        return total == 0 ? context.getString(R.string.lists_no_items)
                : context.getString(R.string.lists_progress, done, total);
    }

    /** Shows a checklist's photo, or its icon on a soft background. */
    static void bindPicture(@NonNull ImageView image, @NonNull Checklist list) {
        int picture = ChecklistTemplates.pictureFor(list);
        if (picture != 0) {
            image.setImageResource(picture);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            image.setPadding(0, 0, 0, 0);
        } else {
            image.setImageResource(ChecklistTemplates.iconFor(list));
            image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            int pad = (int) (14 * image.getResources().getDisplayMetrics().density);
            image.setPadding(pad, pad, pad, pad);
        }
    }

    private final class ListsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private static final int HEADER = 0, LIST = 1, SECTION = 2;
        private final List<Object> rows = new ArrayList<>();

        void rebuild() {
            rows.clear();
            rows.add(HEADER);
            List<Checklist> archived = new ArrayList<>();
            for (Checklist list : lists) {
                if (list.archived) {
                    archived.add(list);
                } else {
                    rows.add(list);
                }
            }
            if (!archived.isEmpty()) {
                rows.add(getString(R.string.lists_archived));
                rows.addAll(archived);
            }
            notifyDataSetChanged();
        }

        @Override
        public int getItemViewType(int position) {
            Object row = rows.get(position);
            return row instanceof Checklist ? LIST : row instanceof String ? SECTION : HEADER;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            int layout = viewType == LIST ? R.layout.item_checklist_card
                    : viewType == SECTION ? R.layout.item_section_title : R.layout.item_lists_header;
            return new RecyclerView.ViewHolder(LayoutInflater.from(parent.getContext()).inflate(layout, parent, false)) {
            };
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            View v = holder.itemView;
            Object row = rows.get(position);
            if (row instanceof String) {
                ((TextView) v).setText((String) row);
            } else if (row instanceof Checklist) {
                bindList(v, (Checklist) row);
            } else {
                ((TextView) v.findViewById(R.id.listsIntro)).setText(R.string.lists_intro);
                v.findViewById(R.id.listsNew).setOnClickListener(x -> chooseTemplate(CheckList.this, members));
                v.findViewById(R.id.listsEmpty).setVisibility(lists.isEmpty() ? View.VISIBLE : View.GONE);
            }
        }

        private void bindList(View v, Checklist list) {
            int[] c = counts.get(list.id);
            int done = c == null ? 0 : c[0];
            int total = c == null ? 0 : c[1];
            ((TextView) v.findViewById(R.id.listName)).setText(list.name);
            String details = progressText(CheckList.this, done, total);
            FamilyMember m = Members.find(members, list.memberId);
            if (m != null) {
                details = getString(R.string.lists_for, Members.label(m)) + " · " + details;
            }
            ((TextView) v.findViewById(R.id.listDetails)).setText(details);
            LinearProgressIndicator bar = v.findViewById(R.id.listProgress);
            bar.setMax(Math.max(1, total));
            bar.setProgress(done);
            bindPicture(v.findViewById(R.id.listImage), list);
            v.setAlpha(list.archived ? 0.7f : 1f);
            v.setContentDescription(getString(R.string.lists_open, list.name) + ". " + details);
            v.setOnClickListener(x -> startActivity(ChecklistActivity.intent(CheckList.this, list.id)));
        }

        @Override
        public int getItemCount() {
            return rows.size();
        }
    }

    /** Opens the Checklists screen. */
    static Intent intent(Context context) {
        return new Intent(context, CheckList.class);
    }
}
