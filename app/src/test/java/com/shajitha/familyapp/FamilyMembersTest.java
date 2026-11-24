package com.shajitha.familyapp;

import static com.shajitha.familyapp.ScreensTest.idle;
import static com.shajitha.familyapp.ScreensTest.waitFor;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.shajitha.familyapp.data.AppDatabase;
import com.shajitha.familyapp.data.FamilyDao;
import com.shajitha.familyapp.data.FamilyMember;
import com.shajitha.familyapp.data.Reminder;
import com.shajitha.familyapp.data.ScheduleEvent;
import com.shajitha.familyapp.data.TodoTask;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.shadows.ShadowDialog;

import java.util.List;

/** Family Members: add, edit, archive (default), and delete without losing anyone's tasks or events. */
@RunWith(AndroidJUnit4.class)
public class FamilyMembersTest {

    private FamilyDao dao;

    @Before
    public void setUp() {
        AppDatabase.useDirectExecutorsForTests();
        AppDatabase.resetForTests();
        dao = AppDatabase.get(ApplicationProvider.getApplicationContext()).dao();
    }

    @After
    public void tearDown() {
        AppDatabase.resetForTests();
    }

    private static AlertDialog dialog() {
        idle();
        return (AlertDialog) ShadowDialog.getLatestDialog();
    }

    private static View row(RecyclerView rv, int position) {
        rv.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(20000, View.MeasureSpec.EXACTLY));
        rv.layout(0, 0, 1080, 20000);
        idle();
        RecyclerView.ViewHolder h = rv.findViewHolderForAdapterPosition(position);
        assertNotNull("row " + position, h);
        return h.itemView;
    }

    @Test
    public void addEditArchiveRestoreAndDelete() {
        try (ActivityScenario<FamilyMembersActivity> scenario = ActivityScenario.launch(FamilyMembersActivity.class)) {
            scenario.onActivity(a -> {
                RecyclerView rv = a.findViewById(R.id.membersList);
                assertEquals(View.VISIBLE, row(rv, 0).findViewById(R.id.membersEmpty).getVisibility());

                // Add: the name is required.
                row(rv, 0).findViewById(R.id.addMember).performClick();
                AlertDialog d = dialog();
                d.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
                assertTrue("name required", d.isShowing());
                ((EditText) d.findViewById(R.id.memberNameInput)).setText("Mia");
                ChipGroup avatars = d.findViewById(R.id.memberAvatars);
                avatars.check(avatars.getChildAt(2).getId()); // 👧
                ((ChipGroup) d.findViewById(R.id.memberRoles)).check(R.id.roleChild);
                d.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
                idle();
                List<FamilyMember> members = dao.allMembersNow();
                assertEquals(1, members.size());
                assertEquals("Mia", members.get(0).name);
                assertEquals("👧", members.get(0).avatar);
                assertEquals(FamilyMember.ROLE_CHILD, members.get(0).role);
                assertEquals("Mia", ((TextView) row(rv, 1).findViewById(R.id.memberName)).getText());

                // Edit.
                row(rv, 1).findViewById(R.id.memberEdit).performClick();
                d = dialog();
                ((EditText) d.findViewById(R.id.memberNameInput)).setText("Mia B");
                d.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
                idle();
                assertEquals("Mia B", dao.allMembersNow().get(0).name);

                // Archive (the default way to remove): nothing deleted, restorable.
                row(rv, 1).findViewById(R.id.memberArchive).performClick();
                idle();
                assertTrue(dao.allMembersNow().get(0).archived);
                assertEquals(a.getString(R.string.members_archived), ((TextView) row(rv, 1)).getText());
                View archived = row(rv, 2);
                assertEquals(View.VISIBLE, archived.findViewById(R.id.memberDelete).getVisibility());
                archived.findViewById(R.id.memberArchive).performClick(); // Restore
                idle();
                assertTrue(!dao.allMembersNow().get(0).archived);
            });
        }
    }

    @Test
    public void deletingAMemberKeepsTheirTasksEventsAndReminders() {
        FamilyMember mia = new FamilyMember();
        mia.name = "Mia";
        mia.archived = true;
        long miaId = dao.insert(mia);
        TodoTask task = new TodoTask();
        task.title = "Tidy room";
        task.memberId = miaId;
        dao.insert(task);
        ScheduleEvent event = new ScheduleEvent();
        event.date = "2026-12-01";
        event.title = "Ballet";
        event.memberId = miaId;
        dao.insert(event);
        Reminder r = new Reminder();
        r.title = "Library books";
        r.dueDate = "2026-12-05";
        r.memberId = miaId;
        dao.insert(r);

        try (ActivityScenario<FamilyMembersActivity> scenario = ActivityScenario.launch(FamilyMembersActivity.class)) {
            scenario.onActivity(a -> {
                RecyclerView rv = a.findViewById(R.id.membersList);
                View archived = row(rv, 2); // header, "Archived", Mia
                archived.findViewById(R.id.memberDelete).performClick();
                dialog().getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
                idle();
                assertEquals("Cancel keeps her", 1, dao.allMembersNow().size());
                row(rv, 2).findViewById(R.id.memberDelete).performClick();
                dialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick();
                idle();
            });
        }
        assertTrue(dao.allMembersNow().isEmpty());
        assertEquals(1, dao.tasksNow().size());
        assertNull("task kept, now for Everyone", dao.tasksNow().get(0).memberId);
        assertEquals(1, dao.allEventsNow().size());
        assertNull(dao.allEventsNow().get(0).memberId);
        assertEquals(1, dao.allRemindersNow().size());
        assertNull(dao.allRemindersNow().get(0).memberId);
    }

    @Test
    public void tasksCanBeAssignedToAMember() {
        FamilyMember sam = new FamilyMember();
        sam.name = "Sam";
        sam.avatar = "👦";
        long samId = dao.insert(sam);
        TodoTask task = new TodoTask();
        task.title = "Feed the cat";
        dao.insert(task);
        try (ActivityScenario<ToDoList> scenario = ActivityScenario.launch(ToDoList.class)) {
            scenario.onActivity(a -> {
                RecyclerView rv = a.findViewById(R.id.Listitems);
                waitFor(() -> rv.getAdapter().getItemCount() == 1);
                row(rv, 0).findViewById(R.id.rowEdit).performClick();
                AlertDialog d = dialog();
                MaterialAutoCompleteTextView who = d.findViewById(R.id.taskMember);
                assertEquals("Everyone", who.getText().toString());
                who.getOnItemClickListener().onItemClick(null, null, 1, 1); // Sam
                d.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
                idle();
                assertEquals(Long.valueOf(samId), dao.tasksNow().get(0).memberId);
                waitFor(() -> "👦 Sam".contentEquals(((TextView) row(rv, 0).findViewById(R.id.rowSubtitle)).getText()));
            });
        }
    }

    @Test
    public void eventsCanBeAssignedToAMember() {
        FamilyMember sam = new FamilyMember();
        sam.name = "Sam";
        sam.avatar = "👦";
        long samId = dao.insert(sam);
        try (ActivityScenario<viewCalendar> scenario = ActivityScenario.launch(viewCalendar.class)) {
            scenario.onActivity(a -> {
                idle();
                a.findViewById(R.id.scheduleAdd).performClick();
                AlertDialog d = dialog();
                ((EditText) d.findViewById(R.id.eventTitleInput)).setText("Football");
                MaterialAutoCompleteTextView who = d.findViewById(R.id.eventMember);
                who.getOnItemClickListener().onItemClick(null, null, 1, 1);
                d.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
                idle();
                assertEquals(Long.valueOf(samId), dao.allEventsNow().get(0).memberId);
                View card = ((android.widget.LinearLayout) a.findViewById(R.id.scheduleList)).getChildAt(0);
                assertEquals("👦 Sam", ((TextView) card.findViewById(R.id.eventMember)).getText().toString());
            });
        }
    }
}
