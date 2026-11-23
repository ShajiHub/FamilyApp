package com.shajitha.familyapp;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;
import java.util.Set;

import static com.shajitha.familyapp.ChecklistItems.imageLayout;
import static com.shajitha.familyapp.ChecklistItems.listLayout;

public class ChecklistAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    /** Called whenever an item is ticked or unticked. */
    public interface OnCheckedChange {
        void onChanged(ChecklistItems item, boolean checked);
    }

    private final List<ChecklistItems> itemClassList;
    private final Set<String> checkedKeys;
    private final OnCheckedChange listener;

    public ChecklistAdapter(List<ChecklistItems> itemClassList, Set<String> checkedKeys, OnCheckedChange listener) {
        this.itemClassList = itemClassList;
        this.checkedKeys = checkedKeys;
        this.listener = listener;
    }

    @Override
    public int getItemViewType(int position) {
        return itemClassList.get(position).getchooseLayout();
    }

    class listLayoutViewHolder extends RecyclerView.ViewHolder {
        private final TextView textview;
        private final CheckBox checkBox;

        listLayoutViewHolder(@NonNull View itemView) {
            super(itemView);
            textview = itemView.findViewById(R.id.text);
            checkBox = itemView.findViewById(R.id.cbSelect);
        }

        void bind(ChecklistItems item) {
            textview.setText(item.getText());
            // Clear the old listener first: rows are reused while scrolling.
            checkBox.setOnCheckedChangeListener(null);
            checkBox.setChecked(checkedKeys.contains(item.getKey()));
            checkBox.setOnCheckedChangeListener((button, isChecked) -> {
                if (isChecked) {
                    checkedKeys.add(item.getKey());
                } else {
                    checkedKeys.remove(item.getKey());
                }
                listener.onChanged(item, isChecked);
            });
            // Tapping anywhere on the row ticks the box too.
            itemView.setOnClickListener(v -> checkBox.toggle());
        }
    }

    class imageLayoutTwoViewHolder extends RecyclerView.ViewHolder {
        private final ImageView img;
        private final TextView text1;

        imageLayoutTwoViewHolder(@NonNull View itemView) {
            super(itemView);
            img = itemView.findViewById(R.id.image);
            text1 = itemView.findViewById(R.id.text1);
        }

        void bind(ChecklistItems item) {
            img.setImageResource(item.getimg());
            text1.setText(item.getdispText());
        }
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == imageLayout) {
            return new imageLayoutTwoViewHolder(inflater.inflate(R.layout.image_and_text_layout, parent, false));
        }
        return new listLayoutViewHolder(inflater.inflate(R.layout.text_layout, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ChecklistItems item = itemClassList.get(position);
        if (item.getchooseLayout() == listLayout) {
            ((listLayoutViewHolder) holder).bind(item);
        } else {
            ((imageLayoutTwoViewHolder) holder).bind(item);
        }
    }

    @Override
    public int getItemCount() {
        return itemClassList.size();
    }
}
