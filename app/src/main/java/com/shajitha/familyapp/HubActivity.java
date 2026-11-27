package com.shajitha.familyapp;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

/** A category screen (Daily Life, Family Planner, ...) listing its features, built from {@link Features}. */
public class HubActivity extends BaseActivity {

    static final String EXTRA_CATEGORY = "category";

    public static Intent intent(Context context, Features.Category category) {
        return new Intent(context, HubActivity.class).putExtra(EXTRA_CATEGORY, category.name());
    }

    private Features.Category category;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_hub);
        String name = getIntent().getStringExtra(EXTRA_CATEGORY);
        try {
            category = Features.Category.valueOf(name == null ? "" : name);
        } catch (IllegalArgumentException e) {
            finish(); // unknown or missing category: nothing to show
            return;
        }
        setTitle(category.title);

        List<Features.Feature> features = Features.in(category);
        RecyclerView grid = findViewById(R.id.hubGrid);
        int columns = MainActivity.spanCount(this);
        GridLayoutManager layout = new GridLayoutManager(this, columns);
        layout.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                return position == 0 ? columns : 1; // the intro text spans the width
            }
        });
        grid.setLayoutManager(layout);
        grid.setAdapter(new FeatureAdapter(features));
    }

    private final class FeatureAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private final List<Features.Feature> features;

        FeatureAdapter(List<Features.Feature> features) {
            this.features = features;
        }

        @Override
        public int getItemViewType(int position) {
            return position == 0 ? 0 : 1;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            int layout = viewType == 0 ? R.layout.item_hub_intro : R.layout.item_home_tile;
            return new RecyclerView.ViewHolder(LayoutInflater.from(parent.getContext()).inflate(layout, parent, false)) {
            };
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            View view = holder.itemView;
            if (position == 0) {
                ((TextView) view).setText(category.intro);
                return;
            }
            Features.Feature feature = features.get(position - 1);
            view.setId(feature.id);
            MainActivity.bindTile(view, feature.title, feature.subtitle, feature.icon,
                    category.containerColor, category.iconColor);
            boolean ready = feature.isAvailable();
            view.findViewById(R.id.tileBadge).setVisibility(ready ? View.GONE : View.VISIBLE);
            view.setAlpha(ready ? 1f : 0.7f);
            view.setEnabled(ready);
            view.setClickable(ready);
            view.setContentDescription(getString(feature.title) + ". "
                    + (ready ? getString(feature.subtitle) : getString(R.string.coming_soon)));
            view.setOnClickListener(ready ? v -> {
                Intent open = new Intent(HubActivity.this, feature.screen);
                if (feature.area != null) {
                    open.putExtra(RemindersActivity.EXTRA_AREA, feature.area);
                }
                startActivity(open);
            } : null);
        }

        @Override
        public int getItemCount() {
            return features.size() + 1;
        }
    }
}
