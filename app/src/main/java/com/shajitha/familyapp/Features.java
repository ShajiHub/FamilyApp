package com.shajitha.familyapp;

import androidx.annotation.AttrRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.IdRes;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The app's feature catalogue: every feature is listed once here, with its category.
 * The Home screen and the category hubs are built from this list, so a new feature
 * (for example a future Learning Hub module) only needs one entry and its screen.
 */
public final class Features {

    private Features() {
    }

    /**
     * Tile colours. Kept in their own holder class: if Category read them from Features directly,
     * loading Category would start building Features.ALL before Category's own values exist.
     */
    private static final class Colors {
        static final int PRIMARY = com.google.android.material.R.attr.colorPrimaryContainer;
        static final int ON_PRIMARY = com.google.android.material.R.attr.colorOnPrimaryContainer;
        static final int SECONDARY = com.google.android.material.R.attr.colorSecondaryContainer;
        static final int ON_SECONDARY = com.google.android.material.R.attr.colorOnSecondaryContainer;
        static final int TERTIARY = com.google.android.material.R.attr.colorTertiaryContainer;
        static final int ON_TERTIARY = com.google.android.material.R.attr.colorOnTertiaryContainer;
        static final int ACCENT = R.attr.colorAccent4Container;
        static final int ON_ACCENT = R.attr.colorOnAccent4Container;
    }

    /** The six areas of the app shown on the Home screen. */
    public enum Category {
        DAILY_LIFE(R.id.cat_daily_life, R.string.cat_daily_life, R.string.cat_daily_life_sub, R.string.cat_daily_life_intro,
                R.drawable.ic_wb_sunny, Colors.TERTIARY, Colors.ON_TERTIARY),
        FAMILY_PLANNER(R.id.cat_family_planner, R.string.cat_family_planner, R.string.cat_family_planner_sub, R.string.cat_family_planner_intro,
                R.drawable.ic_calendar_month, Colors.PRIMARY, Colors.ON_PRIMARY),
        CHECKLISTS(R.id.cat_checklists, R.string.cat_checklists, R.string.cat_checklists_sub, R.string.cat_checklists_intro,
                R.drawable.ic_fact_check, Colors.SECONDARY, Colors.ON_SECONDARY),
        HOBBIES(R.id.cat_hobbies, R.string.cat_hobbies, R.string.cat_hobbies_sub, R.string.cat_hobbies_intro,
                R.drawable.ic_palette, Colors.ACCENT, Colors.ON_ACCENT),
        FAMILY_HUB(R.id.cat_family_hub, R.string.cat_family_hub, R.string.cat_family_hub_sub, R.string.cat_family_hub_intro,
                R.drawable.ic_family_restroom, Colors.PRIMARY, Colors.ON_PRIMARY),
        LEARNING(R.id.cat_learning, R.string.cat_learning, R.string.cat_learning_sub, R.string.cat_learning_intro,
                R.drawable.ic_school, Colors.TERTIARY, Colors.ON_TERTIARY);

        @IdRes public final int id;
        @StringRes public final int title;
        @StringRes public final int subtitle;
        @StringRes public final int intro;
        @DrawableRes public final int icon;
        @AttrRes public final int containerColor;
        @AttrRes public final int iconColor;

        Category(int id, int title, int subtitle, int intro, int icon, int containerColor, int iconColor) {
            this.id = id;
            this.title = title;
            this.subtitle = subtitle;
            this.intro = intro;
            this.icon = icon;
            this.containerColor = containerColor;
            this.iconColor = iconColor;
        }
    }

    /** One feature card inside a category hub. */
    public static final class Feature {
        public final Category category;
        @IdRes public final int id;
        @StringRes public final int title;
        @StringRes public final int subtitle;
        @DrawableRes public final int icon;
        /** The screen to open, or null for a feature that is planned but not built yet ("Coming soon"). */
        @Nullable public final Class<?> screen;

        Feature(Category category, int id, int title, int subtitle, int icon, @Nullable Class<?> screen) {
            this.category = category;
            this.id = id;
            this.title = title;
            this.subtitle = subtitle;
            this.icon = icon;
            this.screen = screen;
        }

        public boolean isAvailable() {
            return screen != null;
        }
    }

    public static final List<Feature> ALL = Collections.unmodifiableList(Arrays.asList(
            // Daily Life
            new Feature(Category.DAILY_LIFE, R.id.feat_todo, R.string.title_todo, R.string.home_todo_sub, R.drawable.ic_done_all, ToDoList.class),
            new Feature(Category.DAILY_LIFE, R.id.feat_meals, R.string.title_meals, R.string.home_meals_sub, R.drawable.ic_restaurant, MealPlanner.class),
            new Feature(Category.DAILY_LIFE, R.id.feat_timer, R.string.title_timer, R.string.home_timer_sub, R.drawable.ic_timer, FamAppTimer.class),
            new Feature(Category.DAILY_LIFE, R.id.feat_chores, R.string.feat_chores, R.string.feat_chores_sub, R.drawable.ic_home, null),
            new Feature(Category.DAILY_LIFE, R.id.feat_habits, R.string.feat_habits, R.string.feat_habits_sub, R.drawable.ic_wb_sunny, null),
            // Family Planner
            new Feature(Category.FAMILY_PLANNER, R.id.feat_schedule, R.string.title_schedule, R.string.home_schedule_sub, R.drawable.ic_calendar_month, viewCalendar.class),
            new Feature(Category.FAMILY_PLANNER, R.id.feat_events, R.string.title_events, R.string.home_events_sub, R.drawable.ic_event, AddEvents.class),
            new Feature(Category.FAMILY_PLANNER, R.id.feat_reminders, R.string.feat_reminders, R.string.feat_reminders_sub, R.drawable.ic_schedule, null),
            new Feature(Category.FAMILY_PLANNER, R.id.feat_library, R.string.feat_library, R.string.feat_library_sub, R.drawable.ic_auto_stories, null),
            // Smart Checklists
            new Feature(Category.CHECKLISTS, R.id.feat_checklists, R.string.title_checklist, R.string.home_checklist_sub, R.drawable.ic_fact_check, CheckList.class),
            new Feature(Category.CHECKLISTS, R.id.feat_templates, R.string.feat_templates, R.string.feat_templates_sub, R.drawable.ic_cake, null),
            // Hobbies & Business
            new Feature(Category.HOBBIES, R.id.feat_activities, R.string.title_activities, R.string.home_activities_sub, R.drawable.ic_sports_handball, ActivitiesFun.class),
            new Feature(Category.HOBBIES, R.id.feat_projects, R.string.feat_projects, R.string.feat_projects_sub, R.drawable.ic_palette, null),
            new Feature(Category.HOBBIES, R.id.feat_business, R.string.feat_business, R.string.feat_business_sub, R.drawable.ic_lightbulb, null),
            new Feature(Category.HOBBIES, R.id.feat_resources, R.string.feat_resources, R.string.feat_resources_sub, R.drawable.ic_share, null),
            // Family Hub
            new Feature(Category.FAMILY_HUB, R.id.feat_photos, R.string.title_photos, R.string.home_photos_sub, R.drawable.ic_photo_camera, PhotoGallery.class),
            new Feature(Category.FAMILY_HUB, R.id.feat_members, R.string.feat_members, R.string.feat_members_sub, R.drawable.ic_family_restroom, FamilyMembersActivity.class),
            new Feature(Category.FAMILY_HUB, R.id.feat_rules, R.string.feat_rules, R.string.feat_rules_sub, R.drawable.ic_fact_check, null),
            // Learning Hub
            new Feature(Category.LEARNING, R.id.feat_reading, R.string.title_reading, R.string.home_reading_sub, R.drawable.ic_auto_stories, Reading.class),
            new Feature(Category.LEARNING, R.id.feat_math, R.string.feat_math, R.string.feat_math_sub, R.drawable.ic_school, null),
            new Feature(Category.LEARNING, R.id.feat_coding, R.string.feat_coding, R.string.feat_coding_sub, R.drawable.ic_lightbulb, null)
    ));

    /** Features of one category: ready ones first, then "Coming soon". */
    public static List<Feature> in(Category category) {
        List<Feature> ready = new ArrayList<>();
        List<Feature> soon = new ArrayList<>();
        for (Feature f : ALL) {
            if (f.category == category) {
                (f.isAvailable() ? ready : soon).add(f);
            }
        }
        ready.addAll(soon);
        return ready;
    }
}
