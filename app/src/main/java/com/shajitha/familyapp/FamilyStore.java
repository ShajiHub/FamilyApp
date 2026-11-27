package com.shajitha.familyapp;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Small helper that saves the app's lists on the phone (in SharedPreferences),
 * so to-dos, checklist ticks, schedules and the reading log survive closing the app.
 */
public final class FamilyStore {

    private static final String PREFS = "family_store";

    private FamilyStore() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static List<String> getStringList(Context context, String key) {
        List<String> result = new ArrayList<>();
        String json = prefs(context).getString(key, null);
        if (json == null) {
            return result;
        }
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                result.add(array.getString(i));
            }
        } catch (JSONException ignored) {
            // Corrupt data: start again with an empty list.
        }
        return result;
    }

    public static void putStringList(Context context, String key, List<String> values) {
        prefs(context).edit().putString(key, new JSONArray(values).toString()).apply();
    }

    public static List<JSONObject> getObjectList(Context context, String key) {
        List<JSONObject> result = new ArrayList<>();
        String json = prefs(context).getString(key, null);
        if (json == null) {
            return result;
        }
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                result.add(array.getJSONObject(i));
            }
        } catch (JSONException ignored) {
            // Corrupt data: start again with an empty list.
        }
        return result;
    }

    public static void putObjectList(Context context, String key, List<JSONObject> values) {
        prefs(context).edit().putString(key, new JSONArray(values).toString()).apply();
    }

    public static Set<String> getStringSet(Context context, String key) {
        // Copy: the set returned by SharedPreferences must not be modified.
        return new HashSet<>(prefs(context).getStringSet(key, new HashSet<>()));
    }

    public static void putStringSet(Context context, String key, Set<String> values) {
        prefs(context).edit().putStringSet(key, new HashSet<>(values)).apply();
    }

    public static String getString(Context context, String key) {
        return prefs(context).getString(key, null);
    }

    public static void putString(Context context, String key, String value) {
        prefs(context).edit().putString(key, value).apply();
    }

    /** Keys of every saved value that starts with the given prefix. */
    public static Set<String> keysWithPrefix(Context context, String prefix) {
        Set<String> keys = new HashSet<>();
        for (String key : prefs(context).getAll().keySet()) {
            if (key.startsWith(prefix)) {
                keys.add(key);
            }
        }
        return keys;
    }

    public static void remove(Context context, String key) {
        prefs(context).edit().remove(key).apply();
    }
}
