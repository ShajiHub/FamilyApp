package com.shajitha.familyapp;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.AttrRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.color.MaterialColors;

import java.util.Arrays;
import java.util.Calendar;
import java.util.List;

/** Home screen: the header picture and a card for every feature. */
public class MainActivity extends AppCompatActivity {

    /** One feature card on the Home screen. */
    static final class Tile {
        final int id;
        @StringRes final int title;
        @StringRes final int subtitle;
        @DrawableRes final int icon;
        @AttrRes final int containerColor;
        @AttrRes final int iconColor;
        final Class<?> screen;

        Tile(int id, int title, int subtitle, int icon, int containerColor, int iconColor, Class<?> screen) {
            this.id = id;
            this.title = title;
            this.subtitle = subtitle;
            this.icon = icon;
            this.containerColor = containerColor;
            this.iconColor = iconColor;
            this.screen = screen;
        }
    }

    static final List<Tile> TILES = Arrays.asList(
            new Tile(R.id.tile_schedule, R.string.title_schedule, R.string.home_schedule_sub, R.drawable.ic_calendar_month,
                    com.google.android.material.R.attr.colorPrimaryContainer, com.google.android.material.R.attr.colorOnPrimaryContainer, viewCalendar.class),
            new Tile(R.id.tile_events, R.string.title_events, R.string.home_events_sub, R.drawable.ic_event,
                    com.google.android.material.R.attr.colorPrimaryContainer, com.google.android.material.R.attr.colorOnPrimaryContainer, AddEvents.class),
            new Tile(R.id.tile_meals, R.string.title_meals, R.string.home_meals_sub, R.drawable.ic_restaurant,
                    com.google.android.material.R.attr.colorTertiaryContainer, com.google.android.material.R.attr.colorOnTertiaryContainer, MealPlanner.class),
            new Tile(R.id.tile_timer, R.string.title_timer, R.string.home_timer_sub, R.drawable.ic_timer,
                    com.google.android.material.R.attr.colorTertiaryContainer, com.google.android.material.R.attr.colorOnTertiaryContainer, FamAppTimer.class),
            new Tile(R.id.tile_todo, R.string.title_todo, R.string.home_todo_sub, R.drawable.ic_done_all,
                    com.google.android.material.R.attr.colorSecondaryContainer, com.google.android.material.R.attr.colorOnSecondaryContainer, ToDoList.class),
            new Tile(R.id.tile_checklist, R.string.title_checklist, R.string.home_checklist_sub, R.drawable.ic_fact_check,
                    com.google.android.material.R.attr.colorSecondaryContainer, com.google.android.material.R.attr.colorOnSecondaryContainer, CheckList.class),
            new Tile(R.id.tile_photos, R.string.title_photos, R.string.home_photos_sub, R.drawable.ic_photo_camera,
                    R.attr.colorAccent4Container, R.attr.colorOnAccent4Container, PhotoGallery.class),
            new Tile(R.id.tile_activities, R.string.title_activities, R.string.home_activities_sub, R.drawable.ic_sports_handball,
                    R.attr.colorAccent4Container, R.attr.colorOnAccent4Container, ActivitiesFun.class),
            new Tile(R.id.tile_reading, R.string.title_reading, R.string.home_reading_sub, R.drawable.ic_auto_stories,
                    com.google.android.material.R.attr.colorPrimaryContainer, com.google.android.material.R.attr.colorOnPrimaryContainer, Reading.class),
            new Tile(R.id.tile_contact, R.string.title_contact, R.string.home_contact_sub, R.drawable.ic_email,
                    com.google.android.material.R.attr.colorSurfaceVariant, com.google.android.material.R.attr.colorOnSurfaceVariant, ContactUs.class)
    );

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_TILE = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        RecyclerView grid = findViewById(R.id.homeGrid);
        int columns = spanCount();
        GridLayoutManager layoutManager = new GridLayoutManager(this, columns);
        // The header spans the whole width; tiles fill the columns.
        layoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                return position == 0 ? columns : 1;
            }
        });
        grid.setLayoutManager(layoutManager);
        grid.setAdapter(new HomeAdapter());
    }

    /** 2 columns on phones, more on wide screens and in landscape. */
    private int spanCount() {
        int widthDp = getResources().getConfiguration().screenWidthDp;
        return Math.max(2, Math.min(5, widthDp / 180));
    }

    @StringRes
    static int greeting() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (hour < 12) {
            return R.string.home_greeting_morning;
        } else if (hour < 18) {
            return R.string.home_greeting_afternoon;
        }
        return R.string.home_greeting_evening;
    }

    private final class HomeAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        @Override
        public int getItemViewType(int position) {
            return position == 0 ? TYPE_HEADER : TYPE_TILE;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(parent.getContext());
            int layout = viewType == TYPE_HEADER ? R.layout.item_home_header : R.layout.item_home_tile;
            return new RecyclerView.ViewHolder(inflater.inflate(layout, parent, false)) {
            };
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            View view = holder.itemView;
            if (position == 0) {
                ((TextView) view.findViewById(R.id.homeGreeting)).setText(greeting());
                return;
            }
            Tile tile = TILES.get(position - 1);
            view.setId(tile.id);
            ((TextView) view.findViewById(R.id.tileTitle)).setText(tile.title);
            ((TextView) view.findViewById(R.id.tileSubtitle)).setText(tile.subtitle);
            ImageView icon = view.findViewById(R.id.tileIcon);
            icon.setImageResource(tile.icon);
            icon.setBackgroundTintList(ColorStateList.valueOf(MaterialColors.getColor(view, tile.containerColor)));
            icon.setImageTintList(ColorStateList.valueOf(MaterialColors.getColor(view, tile.iconColor)));
            view.setContentDescription(getString(tile.title) + ". " + getString(tile.subtitle));
            view.setOnClickListener(v -> startActivity(new Intent(MainActivity.this, tile.screen)));
        }

        @Override
        public int getItemCount() {
            return TILES.size() + 1;
        }
    }
}
