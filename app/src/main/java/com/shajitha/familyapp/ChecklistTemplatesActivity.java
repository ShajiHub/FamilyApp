package com.shajitha.familyapp;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.shajitha.familyapp.data.AppDatabase;
import com.shajitha.familyapp.data.Checklist;
import com.shajitha.familyapp.data.FamilyMember;

import java.util.ArrayList;
import java.util.List;

/** Checklist Templates: seasons, back to school, grocery, travel, ... Choosing one makes your own copy. */
public class ChecklistTemplatesActivity extends BaseActivity {

    private List<FamilyMember> members = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_check_list);
        RecyclerView recyclerView = findViewById(R.id.rView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(new TemplatesAdapter());
        AppDatabase.get(this).dao().allMembers().observe(this, all -> members = all);
    }

    private final class TemplatesAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        @Override
        public int getItemCount() {
            return ChecklistTemplates.ALL.size() + 1;
        }

        @Override
        public int getItemViewType(int position) {
            return position == 0 ? 0 : 1;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            int layout = viewType == 0 ? R.layout.item_lists_header : R.layout.item_checklist_card;
            return new RecyclerView.ViewHolder(LayoutInflater.from(parent.getContext()).inflate(layout, parent, false)) {
            };
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            View v = holder.itemView;
            if (position == 0) {
                ((TextView) v.findViewById(R.id.listsIntro)).setText(R.string.lists_templates_intro);
                v.findViewById(R.id.listsNew).setVisibility(View.GONE);
                return;
            }
            ChecklistTemplates.Template t = ChecklistTemplates.ALL.get(position - 1);
            int count = t.items(ChecklistTemplatesActivity.this).length;
            ((TextView) v.findViewById(R.id.listName)).setText(t.name);
            ((TextView) v.findViewById(R.id.listDetails)).setText(count == 0
                    ? getString(R.string.lists_no_items) : getResources().getQuantityString(R.plurals.lists_items_count, count, count));
            v.findViewById(R.id.listProgress).setVisibility(View.GONE);
            Checklist preview = new Checklist();
            preview.template = t.key;
            CheckList.bindPicture(v.findViewById(R.id.listImage), preview);
            v.setOnClickListener(x -> CheckList.nameAndCreate(ChecklistTemplatesActivity.this, t, members));
        }
    }
}
