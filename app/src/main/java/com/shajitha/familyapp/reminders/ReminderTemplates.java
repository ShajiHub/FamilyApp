package com.shajitha.familyapp.reminders;

import android.content.Context;

import androidx.annotation.ArrayRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.shajitha.familyapp.R;
import com.shajitha.familyapp.data.Recurrence;
import com.shajitha.familyapp.data.Reminder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Optional, ready-made reminders grouped into areas (Documents, Library, Health, Home, ...).
 * Nothing is added until the family picks a template, and every suggestion (repeat, alerts) can be
 * changed or turned off. All areas use the one reminder engine and the one Reminders screen.
 *
 * <p>Titles are in {@code res/values/strings_reminders.xml} (one array per area); the matching
 * suggestions are listed here, in the same order: "TYPE|REPEAT_UNIT|INTERVAL|ALERT_DAYS".
 */
public final class ReminderTemplates {

    public static final String DOCUMENTS = "DOCUMENTS";
    public static final String LIBRARY = "LIBRARY";
    public static final String HEALTH = "HEALTH";
    public static final String HOME = "HOME";
    public static final String SCHOOL = "SCHOOL";
    public static final String BILLS = "BILLS";
    public static final String VEHICLE = "VEHICLE";
    public static final String FAMILY = "FAMILY";
    public static final String PETS = "PETS";
    public static final String GARDEN = "GARDEN";
    public static final String HOBBIES = "HOBBIES";
    public static final String DIGITAL = "DIGITAL";
    public static final String FOOD = "FOOD";
    public static final String OTHER = "OTHER";

    /** One area of reminders. */
    public static final class Area {
        @NonNull public final String key;
        @StringRes public final int name;
        @DrawableRes public final int icon;
        /** A short, friendly note shown with the templates (privacy or safety), or 0. */
        @StringRes public final int note;
        @ArrayRes public final int titles;
        @NonNull final String[] specs;

        Area(@NonNull String key, int name, int icon, int note, int titles, @NonNull String... specs) {
            this.key = key;
            this.name = name;
            this.icon = icon;
            this.note = note;
            this.titles = titles;
            this.specs = specs;
        }

        /** How many suggestions are listed for this area (tests check it matches the titles). */
        public int suggestionCount() {
            return specs.length;
        }

        /** The templates of this area, with their titles. */
        @NonNull
        public List<Template> templates(@NonNull Context context) {
            if (titles == 0) {
                return Collections.emptyList();
            }
            String[] names = context.getResources().getStringArray(titles);
            List<Template> list = new ArrayList<>();
            for (int i = 0; i < names.length && i < specs.length; i++) {
                list.add(Template.parse(key, names[i], specs[i]));
            }
            return list;
        }
    }

    /** A suggested reminder. */
    public static final class Template {
        @NonNull public final String area;
        @NonNull public final String title;
        @NonNull public final String type;
        @NonNull public final String repeatUnit;
        public final int repeatInterval;
        @NonNull public final String alertDays;

        Template(@NonNull String area, @NonNull String title, @NonNull String type, @NonNull String repeatUnit,
                 int repeatInterval, @NonNull String alertDays) {
            this.area = area;
            this.title = title;
            this.type = type;
            this.repeatUnit = repeatUnit;
            this.repeatInterval = repeatInterval;
            this.alertDays = alertDays;
        }

        static Template parse(String area, String title, String spec) {
            String[] p = spec.split("\\|", -1);
            return new Template(area, title, p[0], p[1], Integer.parseInt(p[2]), p[3]);
        }

        /** A new (unsaved) reminder from this template, due on {@code dueDate}. */
        @NonNull
        public Reminder newReminder(@NonNull String dueDate) {
            Reminder r = new Reminder();
            r.title = title;
            r.type = type;
            r.category = area;
            r.dueDate = dueDate;
            r.repeatUnit = repeatUnit;
            r.repeatInterval = repeatInterval;
            r.alertDays = alertDays;
            Recurrence.Day d = Recurrence.Day.parse(dueDate);
            r.anchorDay = d == null ? 0 : d.day;
            return r;
        }
    }

    private static final String APPT = Reminder.TYPE_APPOINTMENT;
    private static final String RENEW = Reminder.TYPE_RENEWAL;
    private static final String OTHER_T = Reminder.TYPE_OTHER;

    private static String s(String type, String unit, int interval, String alerts) {
        return type + "|" + unit + "|" + interval + "|" + alerts;
    }

    private static final String N = Recurrence.NONE, D = Recurrence.DAY, W = Recurrence.WEEK, M = Recurrence.MONTH, Y = Recurrence.YEAR;

    public static final List<Area> AREAS = Collections.unmodifiableList(Arrays.asList(
            new Area(DOCUMENTS, R.string.area_documents, R.drawable.ic_badge, R.string.area_documents_note, R.array.tpl_documents,
                    s(RENEW, N, 1, "180,90,30"), // passport: about 6 months, 3 months and 1 month before
                    s(RENEW, N, 1, "60,30"), // health card
                    s(RENEW, N, 1, "60,30"), // driver's licence
                    s(RENEW, Y, 1, "30,7"), // vehicle registration
                    s(RENEW, N, 1, "30,7"), // professional licence
                    s(RENEW, Y, 1, "30,7"), // membership
                    s(RENEW, N, 1, "30,7")), // other document
            new Area(LIBRARY, R.string.area_library, R.drawable.ic_auto_stories, R.string.area_library_note, R.array.tpl_library,
                    s(Reminder.TYPE_LIBRARY, N, 1, "2"), // return a book
                    s(RENEW, N, 1, "30,7"), // library card
                    s(RENEW, Y, 1, "30,7")), // membership
            new Area(HEALTH, R.string.area_health, R.drawable.ic_favorite, R.string.area_health_note, R.array.tpl_health,
                    s(Reminder.TYPE_VACCINATION, N, 1, "1"),
                    s(APPT, N, 1, "1"), s(APPT, N, 1, "1"), s(APPT, N, 1, "1"), s(APPT, N, 1, "1"),
                    s(APPT, N, 1, "1"), s(APPT, N, 1, "1")),
            new Area(HOME, R.string.area_home, R.drawable.ic_home, R.string.area_home_note, R.array.tpl_home,
                    s(OTHER_T, M, 1, "0"), // smoke alarms
                    s(OTHER_T, M, 1, "0"), // carbon monoxide alarms
                    s(OTHER_T, M, 3, "0"), // furnace filter
                    s(OTHER_T, Y, 1, "14"), // heating and cooling
                    s(OTHER_T, M, 3, "0"), // water leaks
                    s(OTHER_T, M, 6, "0"), // appliances
                    s(OTHER_T, M, 6, "0"), // emergency kit
                    s(OTHER_T, M, 6, "0"), // first-aid supplies
                    s(OTHER_T, M, 3, "0"), // seasonal inspection
                    s(OTHER_T, M, 6, "0")), // storage
            new Area(SCHOOL, R.string.area_school, R.drawable.ic_school, R.string.area_school_note, R.array.tpl_school,
                    s(OTHER_T, N, 1, "7,1"), // registration deadline
                    s(OTHER_T, N, 1, "1"), // permission slip
                    s(OTHER_T, N, 1, "1"), // school trip
                    s(APPT, N, 1, "1"), // parent-teacher meeting
                    s(OTHER_T, N, 1, "2"), // homework or project
                    s(OTHER_T, W, 1, "0"), // extracurricular
                    s(OTHER_T, W, 1, "0"), // sports practice
                    s(OTHER_T, N, 1, "1"), // school event
                    s(OTHER_T, Y, 1, "14"), // school supplies
                    s(OTHER_T, W, 1, "0")), // after-school program
            new Area(BILLS, R.string.area_bills, R.drawable.ic_receipt, R.string.area_bills_note, R.array.tpl_bills,
                    s(OTHER_T, M, 1, "2"), // rent or mortgage
                    s(OTHER_T, M, 1, "3"), // electricity and water
                    s(OTHER_T, M, 1, "3"), // internet and phone
                    s(RENEW, Y, 1, "30,7"), // insurance
                    s(RENEW, N, 1, "7"), // subscription
                    s(RENEW, N, 1, "30"), // warranty
                    s(OTHER_T, M, 1, "0"), // budget review
                    s(OTHER_T, Y, 1, "7")), // yearly planning
            new Area(VEHICLE, R.string.area_vehicle, R.drawable.ic_directions_car, R.string.area_vehicle_note, R.array.tpl_vehicle,
                    s(OTHER_T, M, 6, "7"), // service
                    s(OTHER_T, M, 3, "7"), // oil change
                    s(OTHER_T, M, 1, "0"), // tire check
                    s(OTHER_T, M, 6, "14"), // seasonal tires
                    s(RENEW, Y, 1, "30,7"), // vehicle insurance
                    s(RENEW, Y, 1, "14"), // parking permit
                    s(RENEW, M, 1, "3"), // transit pass
                    s(OTHER_T, M, 6, "0"), // bicycle
                    s(OTHER_T, N, 1, "1")), // other
            new Area(FAMILY, R.string.area_family, R.drawable.ic_family_restroom, R.string.area_family_note, R.array.tpl_family,
                    s(OTHER_T, W, 1, "0"), s(OTHER_T, W, 1, "0"), s(OTHER_T, W, 1, "0"), // game night, outdoors, walk
                    s(OTHER_T, M, 1, "3"), // visit relatives
                    s(OTHER_T, Y, 1, "7,0"), s(OTHER_T, Y, 1, "7,0"), // birthday, anniversary
                    s(OTHER_T, M, 1, "0"), // family meeting
                    s(OTHER_T, W, 1, "0"), s(OTHER_T, W, 1, "0"), // reading together, screen-free time
                    s(OTHER_T, N, 1, "7"), // celebration
                    s(OTHER_T, N, 1, "1")), // community activity
            new Area(PETS, R.string.area_pets, R.drawable.ic_pets, R.string.area_pets_note, R.array.tpl_pets,
                    s(OTHER_T, D, 1, "0"), // feeding
                    s(OTHER_T, M, 1, "0"), // grooming
                    s(APPT, N, 1, "1"), s(APPT, N, 1, "1"), // vet, pet vaccination appointment
                    s(OTHER_T, M, 1, "2"), // supplies
                    s(OTHER_T, W, 1, "0")), // general care
            new Area(GARDEN, R.string.area_garden, R.drawable.ic_eco, R.string.area_garden_note, R.array.tpl_garden,
                    s(OTHER_T, W, 1, "0"), // water plants
                    s(OTHER_T, Y, 1, "7"), // plant seeds
                    s(OTHER_T, W, 1, "0"), // garden maintenance
                    s(OTHER_T, M, 3, "7"), // seasonal planting
                    s(OTHER_T, W, 1, "0"), // composting
                    s(OTHER_T, W, 1, "1"), // recycling day
                    s(OTHER_T, N, 1, "2"), // community cleanup
                    s(OTHER_T, N, 1, "1")), // outdoor project
            new Area(HOBBIES, R.string.area_hobbies, R.drawable.ic_palette, R.string.area_hobbies_note, R.array.tpl_hobbies,
                    s(OTHER_T, W, 1, "0"), s(OTHER_T, W, 1, "0"), s(OTHER_T, W, 1, "0"), s(OTHER_T, W, 1, "0"),
                    s(OTHER_T, W, 1, "0"), s(OTHER_T, W, 1, "0"), s(OTHER_T, W, 1, "0"), s(OTHER_T, W, 1, "0"),
                    s(OTHER_T, W, 1, "0"), s(OTHER_T, W, 1, "0"),
                    s(OTHER_T, M, 1, "0")), // small business goals
            new Area(DIGITAL, R.string.area_digital, R.drawable.ic_phone_android, R.string.area_digital_note, R.array.tpl_digital,
                    s(OTHER_T, M, 1, "0"), // back up files
                    s(OTHER_T, M, 1, "0"), // update apps
                    s(OTHER_T, M, 6, "0"), // account security
                    s(OTHER_T, M, 3, "0"), // organize files
                    s(OTHER_T, M, 3, "0"), // subscriptions
                    s(OTHER_T, M, 1, "0"), // storage
                    s(OTHER_T, Y, 1, "0")), // contact information
            new Area(FOOD, R.string.area_food, R.drawable.ic_shopping_cart, R.string.area_food_note, R.array.tpl_food,
                    s(OTHER_T, W, 1, "0"), // grocery shopping
                    s(OTHER_T, W, 1, "0"), // meal preparation
                    s(OTHER_T, W, 1, "0"), // food dates
                    s(OTHER_T, W, 1, "0"), // healthy meals
                    s(OTHER_T, M, 1, "0"), // household supplies
                    s(OTHER_T, M, 3, "0"), // toothbrushes
                    s(OTHER_T, M, 3, "0"), // seasonal clothes
                    s(OTHER_T, D, 1, "0")), // packed lunches
            new Area(OTHER, R.string.area_other, R.drawable.ic_notifications, 0, 0)
    ));

    private ReminderTemplates() {
    }

    /** The area with this key, or "Other" for an unknown or missing one. */
    @NonNull
    public static Area area(@Nullable String key) {
        for (Area a : AREAS) {
            if (a.key.equals(key)) {
                return a;
            }
        }
        return AREAS.get(AREAS.size() - 1);
    }

    /** True for a known area key. */
    public static boolean isArea(@Nullable String key) {
        for (Area a : AREAS) {
            if (a.key.equals(key)) {
                return true;
            }
        }
        return false;
    }

    /** The area of a reminder: its own, or one that fits its type (for reminders made before Phase 4). */
    @NonNull
    public static String areaOf(@NonNull Reminder r) {
        if (isArea(r.category)) {
            return r.category;
        }
        switch (r.type) {
            case Reminder.TYPE_RENEWAL:
                return DOCUMENTS;
            case Reminder.TYPE_LIBRARY:
                return LIBRARY;
            case Reminder.TYPE_APPOINTMENT:
            case Reminder.TYPE_VACCINATION:
                return HEALTH;
            default:
                return OTHER;
        }
    }
}
