package com.shajitha.familyapp;


import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.os.Bundle;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class CheckList extends BaseActivity {
    private static final String STORE_KEY = "checklist_ticked";

    RecyclerView recyclerView;
    LinearLayoutManager layoutManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_check_list);

        recyclerView = findViewById(R.id.rView);

        layoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(layoutManager);


        List<ChecklistItems> items = new ArrayList<>();

        // pass the arguments
        items.add(new ChecklistItems(ChecklistItems.imageLayout,R.drawable.winter,"Winter Checklist"));

        items.add(new ChecklistItems(ChecklistItems.listLayout, "Winter boots"));
        items.add(new ChecklistItems(ChecklistItems.listLayout, "Thermal wear"));
        items.add(new ChecklistItems(ChecklistItems.listLayout, "Cardigan/Winter coat"));
        items.add(new ChecklistItems(ChecklistItems.listLayout, "Sleepwear"));
        items.add(new ChecklistItems(ChecklistItems.listLayout, "Scarfs"));
        items.add(new ChecklistItems(ChecklistItems.listLayout, "Gloves"));
        items.add(new ChecklistItems(ChecklistItems.listLayout, "Ear muffs"));


        items.add(new ChecklistItems(ChecklistItems.imageLayout,R.drawable.done, "Summer Checklist"));
        items.add(new ChecklistItems(ChecklistItems.listLayout, "Sunscreen/sunblock"));
        items.add(new ChecklistItems(ChecklistItems.listLayout, "Packing cubes"));
        items.add(new ChecklistItems(ChecklistItems.listLayout, "Exercise/Active time"));
        items.add(new ChecklistItems(ChecklistItems.listLayout, "Swimsuits"));
        items.add(new ChecklistItems(ChecklistItems.listLayout, "Boots/Sports shoe"));


        items.add(new ChecklistItems(ChecklistItems.imageLayout,R.drawable.firstaidkit, "FirstAid kit Checklist"));
        items.add(new ChecklistItems(ChecklistItems.listLayout, "Medication"));
        items.add(new ChecklistItems(ChecklistItems.listLayout, "Lip balm"));
        items.add(new ChecklistItems(ChecklistItems.listLayout, "Instant cold ice packs"));
        items.add(new ChecklistItems(ChecklistItems.listLayout, "Antiseptic wipes"));
        items.add(new ChecklistItems(ChecklistItems.listLayout, "Adhesive plasters large/small"));


        // Remember which section each item belongs to, so ticks are saved per list.
        String section = "";
        for (ChecklistItems item : items) {
            if (item.getchooseLayout() == ChecklistItems.imageLayout) {
                section = item.getdispText();
            } else {
                item.setSection(section);
            }
        }

        Set<String> ticked = FamilyStore.getStringSet(this, STORE_KEY);
        ChecklistAdapter adapter = new ChecklistAdapter(items, ticked,
                (item, checked) -> FamilyStore.putStringSet(this, STORE_KEY, ticked));

        recyclerView.setAdapter(adapter);
    }
}