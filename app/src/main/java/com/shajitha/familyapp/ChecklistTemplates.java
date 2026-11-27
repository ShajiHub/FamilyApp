package com.shajitha.familyapp;

import android.content.Context;

import androidx.annotation.ArrayRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.shajitha.familyapp.data.Checklist;
import com.shajitha.familyapp.data.ChecklistItem;
import com.shajitha.familyapp.data.FamilyDao;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Reusable checklist templates. Choosing one makes the family's own copy; the template never changes
 * and nothing is added until someone picks it.
 */
public final class ChecklistTemplates {

    /** One template. {@code items} is 0 for the blank checklist. */
    public static final class Template {
        @NonNull public final String key;
        @StringRes public final int name;
        @NonNull public final String kind;
        @DrawableRes public final int picture;
        @DrawableRes public final int icon;
        @ArrayRes public final int items;

        Template(@NonNull String key, int name, @NonNull String kind, int picture, int icon, int items) {
            this.key = key;
            this.name = name;
            this.kind = kind;
            this.picture = picture;
            this.icon = icon;
            this.items = items;
        }

        @NonNull
        public String[] items(@NonNull Context context) {
            return items == 0 ? new String[0] : context.getResources().getStringArray(items);
        }
    }

    public static final String BLANK = "blank";

    public static final List<Template> ALL = Collections.unmodifiableList(Arrays.asList(
            new Template("spring", R.string.tpl_spring, Checklist.KIND_SEASON, 0, R.drawable.ic_eco, R.array.tpl_items_spring),
            new Template("summer", R.string.tpl_summer, Checklist.KIND_SEASON, R.drawable.done, R.drawable.ic_wb_sunny, R.array.tpl_items_summer),
            new Template("fall", R.string.tpl_fall, Checklist.KIND_SEASON, 0, R.drawable.ic_eco, R.array.tpl_items_fall),
            new Template("winter", R.string.tpl_winter, Checklist.KIND_SEASON, R.drawable.winter, R.drawable.ic_home, R.array.tpl_items_winter),
            new Template("school", R.string.tpl_school, Checklist.KIND_SCHOOL, 0, R.drawable.ic_school, R.array.tpl_items_school),
            new Template("grocery", R.string.tpl_grocery, Checklist.KIND_LIST, 0, R.drawable.ic_shopping_cart, R.array.tpl_items_grocery),
            new Template("travel", R.string.tpl_travel, Checklist.KIND_LIST, 0, R.drawable.ic_directions_car, R.array.tpl_items_travel),
            new Template("birthday", R.string.tpl_birthday, Checklist.KIND_LIST, 0, R.drawable.ic_cake, R.array.tpl_items_birthday),
            new Template("first_aid", R.string.tpl_first_aid, Checklist.KIND_LIST, R.drawable.firstaidkit, R.drawable.ic_favorite, R.array.tpl_items_first_aid),
            new Template("emergency", R.string.tpl_emergency, Checklist.KIND_LIST, 0, R.drawable.ic_notifications, R.array.tpl_items_emergency),
            new Template("household", R.string.tpl_household, Checklist.KIND_LIST, 0, R.drawable.ic_home, R.array.tpl_items_household),
            new Template("celebration", R.string.tpl_celebration, Checklist.KIND_LIST, 0, R.drawable.ic_emoji_events, R.array.tpl_items_celebration),
            new Template(BLANK, R.string.lists_blank, Checklist.KIND_CUSTOM, 0, R.drawable.ic_fact_check, 0)
    ));

    private ChecklistTemplates() {
    }

    @Nullable
    public static Template find(@Nullable String key) {
        for (Template t : ALL) {
            if (t.key.equals(key)) {
                return t;
            }
        }
        return null;
    }

    /** The picture for a checklist (Winter, Summer and First-aid have photos), or 0. */
    @DrawableRes
    public static int pictureFor(@NonNull Checklist list) {
        Template t = find(list.template);
        return t == null ? 0 : t.picture;
    }

    /** The icon for a checklist without a picture. */
    @DrawableRes
    public static int iconFor(@NonNull Checklist list) {
        Template t = find(list.template);
        return t == null ? R.drawable.ic_fact_check : t.icon;
    }

    /**
     * Creates the family's own checklist from a template (database thread). Returns its id.
     * The name is limited to {@link Checklist#MAX_NAME_LENGTH} characters.
     */
    public static long create(@NonNull Context context, @NonNull FamilyDao dao, @NonNull Template template,
                              @NonNull String name, @Nullable Long memberId) {
        long now = System.currentTimeMillis();
        Checklist list = new Checklist();
        list.name = trim(name, Checklist.MAX_NAME_LENGTH);
        list.kind = template.kind;
        list.template = BLANK.equals(template.key) ? null : template.key;
        list.memberId = memberId;
        list.position = dao.nextListPosition();
        list.createdAt = list.updatedAt = now;
        long id = dao.insert(list);
        int position = 0;
        for (String title : template.items(context)) {
            ChecklistItem item = new ChecklistItem();
            item.checklistId = id;
            item.section = list.name;
            item.title = title;
            item.position = position++;
            dao.insert(item);
        }
        return id;
    }

    /** Copies a checklist and its items (unticked, so it's ready to use again). Returns the new id. */
    public static long duplicate(@NonNull FamilyDao dao, @NonNull Checklist source, @NonNull String name,
                                 @Nullable Long memberId) {
        long now = System.currentTimeMillis();
        Checklist copy = new Checklist();
        copy.name = trim(name, Checklist.MAX_NAME_LENGTH);
        copy.kind = source.kind;
        copy.template = source.template;
        copy.memberId = memberId;
        copy.position = dao.nextListPosition();
        copy.createdAt = copy.updatedAt = now;
        long id = dao.insert(copy);
        for (ChecklistItem i : dao.itemsOfNow(source.id)) {
            ChecklistItem item = new ChecklistItem();
            item.checklistId = id;
            item.section = copy.name;
            item.title = i.title;
            item.note = i.note;
            item.position = i.position;
            dao.insert(item);
        }
        return id;
    }

    static String trim(String text, int max) {
        String t = text.trim();
        return t.length() > max ? t.substring(0, max) : t;
    }
}
