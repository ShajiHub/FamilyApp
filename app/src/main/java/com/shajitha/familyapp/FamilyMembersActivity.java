package com.shajitha.familyapp;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.shajitha.familyapp.data.AppDatabase;
import com.shajitha.familyapp.data.FamilyDao;
import com.shajitha.familyapp.data.FamilyMember;

import java.util.ArrayList;
import java.util.List;

/**
 * Family Members: names or nicknames, an avatar and a role, stored only on this phone.
 * Archiving hides a member and keeps everything; deleting (from Archived, after confirming)
 * keeps their tasks, events and reminders and shows them as "Everyone".
 */
public class FamilyMembersActivity extends BaseActivity {

    private FamilyDao dao;
    private final MembersAdapter adapter = new MembersAdapter();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_family_members);
        dao = AppDatabase.get(this).dao();
        RecyclerView list = findViewById(R.id.membersList);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);
        dao.allMembers().observe(this, adapter::setMembers);
    }

    /** Adds a member (member == null) or edits one. */
    void showMemberDialog(FamilyMember member) {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_member, null);
        TextInputLayout nameLayout = view.findViewById(R.id.memberNameLayout);
        TextInputEditText name = view.findViewById(R.id.memberNameInput);
        ChipGroup avatars = view.findViewById(R.id.memberAvatars);
        ChipGroup colours = view.findViewById(R.id.memberColours);
        ChipGroup roles = view.findViewById(R.id.memberRoles);

        String avatar = member == null ? Members.AVATARS[0] : member.avatar;
        for (int i = 0; i < Members.AVATARS.length; i++) {
            Chip chip = new Chip(this, null, com.google.android.material.R.attr.chipStyle);
            chip.setCheckable(true);
            chip.setText(Members.AVATARS[i]);
            chip.setTextSize(20);
            chip.setId(View.generateViewId());
            chip.setTag(Members.AVATARS[i]);
            avatars.addView(chip);
            if (Members.AVATARS[i].equals(avatar)) {
                avatars.check(chip.getId());
            }
        }
        if (avatars.getCheckedChipId() == View.NO_ID) {
            avatars.check(avatars.getChildAt(0).getId());
        }
        int colourIndex = member == null ? 0 : member.colorIndex;
        for (int i = 0; i < Members.COLOURS.length; i++) {
            Chip chip = new Chip(this, null, com.google.android.material.R.attr.chipStyle);
            chip.setCheckable(true);
            chip.setId(View.generateViewId());
            chip.setTag(i);
            chip.setChipBackgroundColor(ColorStateList.valueOf(ContextCompat.getColor(this, Members.COLOURS[i])));
            chip.setContentDescription(getString(R.string.members_colour_item, i + 1));
            chip.setText("   ");
            colours.addView(chip);
            if (i == colourIndex) {
                colours.check(chip.getId());
            }
        }
        String role = member == null ? FamilyMember.ROLE_CHILD : member.role;
        roles.check(FamilyMember.ROLE_PARENT.equals(role) ? R.id.roleParent
                : FamilyMember.ROLE_CHILD.equals(role) ? R.id.roleChild : R.id.roleOther);
        if (member != null) {
            name.setText(member.name);
        }

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(member == null ? R.string.members_add : R.string.members_edit)
                .setView(view)
                .setPositiveButton(R.string.save, null)
                .setNegativeButton(R.string.cancel, null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String text = Dialogs.text(name);
            if (text.isEmpty()) {
                nameLayout.setError(getString(R.string.error_required));
                return;
            }
            FamilyMember m = member == null ? new FamilyMember() : copy(member);
            m.name = text.length() > FamilyMember.MAX_NAME_LENGTH ? text.substring(0, FamilyMember.MAX_NAME_LENGTH) : text;
            m.avatar = (String) view.findViewById(avatars.getCheckedChipId()).getTag();
            m.colorIndex = (Integer) view.findViewById(colours.getCheckedChipId()).getTag();
            int checkedRole = roles.getCheckedChipId();
            m.role = checkedRole == R.id.roleParent ? FamilyMember.ROLE_PARENT
                    : checkedRole == R.id.roleChild ? FamilyMember.ROLE_CHILD : FamilyMember.ROLE_OTHER;
            AppDatabase.write(() -> {
                if (member == null) {
                    m.createdAt = System.currentTimeMillis();
                    m.position = dao.nextMemberPosition();
                    dao.insert(m);
                } else {
                    dao.update(m);
                }
            });
            dialog.dismiss();
        }));
        Dialogs.showWithKeyboard(dialog);
    }

    private void setArchived(FamilyMember member, boolean archived) {
        FamilyMember m = copy(member);
        m.archived = archived;
        AppDatabase.write(() -> dao.update(m));
    }

    /** Permanent delete: asks first and keeps everything assigned to them. */
    private void confirmDelete(FamilyMember member) {
        Dialogs.confirmDelete(this, getString(R.string.members_delete_title, member.name),
                getString(R.string.members_delete_message, member.name),
                () -> AppDatabase.write(() -> dao.deleteMemberKeepingItems(member.id)));
    }

    private static FamilyMember copy(FamilyMember s) {
        FamilyMember m = new FamilyMember();
        m.id = s.id;
        m.name = s.name;
        m.avatar = s.avatar;
        m.colorIndex = s.colorIndex;
        m.role = s.role;
        m.position = s.position;
        m.archived = s.archived;
        m.createdAt = s.createdAt;
        return m;
    }

    private final class MembersAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private static final int HEADER = 0, MEMBER = 1, SECTION = 2;
        private final List<Object> rows = new ArrayList<>();
        private boolean empty = true;

        void setMembers(List<FamilyMember> members) {
            rows.clear();
            rows.add(HEADER);
            List<FamilyMember> archived = new ArrayList<>();
            for (FamilyMember m : members) {
                if (m.archived) {
                    archived.add(m);
                } else {
                    rows.add(m);
                }
            }
            empty = rows.size() == 1;
            if (!archived.isEmpty()) {
                rows.add(getString(R.string.members_archived));
                rows.addAll(archived);
            }
            notifyDataSetChanged();
        }

        @Override
        public int getItemViewType(int position) {
            Object row = rows.get(position);
            return row instanceof FamilyMember ? MEMBER : row instanceof String ? SECTION : HEADER;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            int layout = viewType == MEMBER ? R.layout.item_member
                    : viewType == SECTION ? R.layout.item_section_title : R.layout.item_members_header;
            return new RecyclerView.ViewHolder(LayoutInflater.from(parent.getContext()).inflate(layout, parent, false)) {
            };
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            View v = holder.itemView;
            Object row = rows.get(position);
            if (row instanceof String) {
                ((TextView) v).setText((String) row);
                return;
            }
            if (!(row instanceof FamilyMember)) {
                v.findViewById(R.id.addMember).setOnClickListener(x -> showMemberDialog(null));
                v.findViewById(R.id.membersEmpty).setVisibility(empty ? View.VISIBLE : View.GONE);
                return;
            }
            FamilyMember m = (FamilyMember) row;
            Members.bindAvatar(v.findViewById(R.id.memberAvatar), m);
            ((TextView) v.findViewById(R.id.memberName)).setText(m.name);
            ((TextView) v.findViewById(R.id.memberRole)).setText(Members.roleLabel(FamilyMembersActivity.this, m.role));
            v.setAlpha(m.archived ? 0.7f : 1f);
            TextView archive = v.findViewById(R.id.memberArchive);
            archive.setText(m.archived ? R.string.members_restore : R.string.members_archive);
            archive.setOnClickListener(x -> setArchived(m, !m.archived));
            v.findViewById(R.id.memberEdit).setOnClickListener(x -> showMemberDialog(m));
            View delete = v.findViewById(R.id.memberDelete);
            delete.setVisibility(m.archived ? View.VISIBLE : View.GONE);
            delete.setOnClickListener(x -> confirmDelete(m));
        }

        @Override
        public int getItemCount() {
            return rows.size();
        }
    }
}
