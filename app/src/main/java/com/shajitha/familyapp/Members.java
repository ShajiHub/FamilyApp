package com.shajitha.familyapp;

import android.content.Context;
import android.content.res.ColorStateList;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.shajitha.familyapp.data.FamilyMember;

import java.util.ArrayList;
import java.util.List;

/** Shared helpers for showing and choosing family members. */
public final class Members {

    /** Avatar choices. */
    static final String[] AVATARS = {"👩", "👨", "👧", "👦", "👶", "👵", "👴", "🧑", "🐶", "🐱", "⭐", "🌻"};

    /** Soft background colours for avatars (readable with emoji in light and dark mode). */
    static final int[] COLOURS = {R.color.avatar_0, R.color.avatar_1, R.color.avatar_2, R.color.avatar_3,
            R.color.avatar_4, R.color.avatar_5};

    private Members() {
    }

    static void bindAvatar(@NonNull TextView view, @NonNull FamilyMember m) {
        view.setText(m.avatar);
        int colour = COLOURS[Math.abs(m.colorIndex) % COLOURS.length];
        view.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(view.getContext(), colour)));
    }

    static String roleLabel(Context context, String role) {
        if (FamilyMember.ROLE_PARENT.equals(role)) {
            return context.getString(R.string.role_parent);
        } else if (FamilyMember.ROLE_CHILD.equals(role)) {
            return context.getString(R.string.role_child);
        }
        return context.getString(R.string.role_other);
    }

    /** "👧 Mia" */
    static String label(@NonNull FamilyMember m) {
        return m.avatar + " " + m.name;
    }

    @Nullable
    static FamilyMember find(@Nullable List<FamilyMember> members, @Nullable Long id) {
        if (members == null || id == null) {
            return null;
        }
        for (FamilyMember m : members) {
            if (m.id == id) {
                return m;
            }
        }
        return null;
    }

    /**
     * A "For" dropdown: Everyone plus active members (and the current one if it was archived).
     * Returns the list behind the dropdown: index 0 is Everyone (null).
     */
    static List<FamilyMember> attachPicker(@NonNull MaterialAutoCompleteTextView picker,
                                           @Nullable List<FamilyMember> all, @Nullable Long selected) {
        Context context = picker.getContext();
        List<FamilyMember> choices = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        choices.add(null);
        labels.add(context.getString(R.string.members_everyone));
        if (all != null) {
            for (FamilyMember m : all) {
                if (!m.archived || (selected != null && m.id == selected)) {
                    choices.add(m);
                    labels.add(label(m));
                }
            }
        }
        picker.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_list_item_1, labels));
        int index = 0;
        for (int i = 1; i < choices.size(); i++) {
            if (selected != null && choices.get(i).id == selected) {
                index = i;
            }
        }
        picker.setText(labels.get(index), false);
        picker.setTag(choices.get(index) == null ? null : choices.get(index).id);
        picker.setOnItemClickListener((p, v, position, id) ->
                picker.setTag(choices.get(position) == null ? null : choices.get(position).id));
        return choices;
    }

    /** The member chosen in a picker made by {@link #attachPicker}, or null for Everyone. */
    @Nullable
    static Long selected(@NonNull MaterialAutoCompleteTextView picker) {
        Object tag = picker.getTag();
        return tag instanceof Long ? (Long) tag : null;
    }
}
