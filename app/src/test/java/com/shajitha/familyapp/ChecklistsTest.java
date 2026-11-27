package com.shajitha.familyapp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.content.Intent;
import android.os.Looper;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.shajitha.familyapp.data.AppDatabase;
import com.shajitha.familyapp.data.Checklist;
import com.shajitha.familyapp.data.ChecklistItem;
import com.shajitha.familyapp.data.FamilyDao;
import com.shajitha.familyapp.data.FamilyMember;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.fakes.RoboMenuItem;
import org.robolectric.shadows.ShadowDialog;
import org.robolectric.shadows.ShadowPopupMenu;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Smart Checklists: the list of lists, one checklist, its menus, templates and duplicates. */
@RunWith(AndroidJUnit4.class)
public class ChecklistsTest {

    private Context context;
    private FamilyDao dao;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        AppDatabase.useDirectExecutorsForTests();
        AppDatabase.resetForTests();
        dao = AppDatabase.get(context).dao();
    }

    @After
    public void tearDown() {
        AppDatabase.resetForTests();
    }

    private static void idle() {
        shadowOf(Looper.getMainLooper()).idle();
    }

    private static AlertDialog dialog() {
        idle();
        AlertDialog d = (AlertDialog) ShadowDialog.getLatestDialog();
        assertNotNull(d);
        assertTrue(d.isShowing());
        return d;
    }

    private static void press(int button) {
        dialog().getButton(button).performClick();
        idle();
    }

    private static RecyclerView.ViewHolder row(RecyclerView rv, int position) {
        rv.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(20000, View.MeasureSpec.EXACTLY));
        rv.layout(0, 0, 1080, 20000);
        rv.scrollToPosition(position);
        idle();
        RecyclerView.ViewHolder holder = rv.findViewHolderForAdapterPosition(position);
        assertNotNull("row " + position, holder);
        return holder;
    }

    private Checklist list(String name) {
        for (Checklist c : dao.checklistsNow()) {
            if (c.name.equals(name)) {
                return c;
            }
        }
        return null;
    }

    private int checked(long listId) {
        int n = 0;
        for (ChecklistItem i : dao.itemsOfNow(listId)) {
            if (i.checked) {
                n++;
            }
        }
        return n;
    }

    @Test
    public void defaultListsAreRealChecklists() {
        List<Checklist> lists = dao.checklistsNow();
        assertEquals(3, lists.size());
        assertEquals("Winter", lists.get(0).name);
        assertEquals("Summer", lists.get(1).name);
        assertEquals("First-aid kit", lists.get(2).name);
        assertEquals(7, dao.itemsOfNow(lists.get(0).id).size());
        // Older versions' section names are still on the items (used once to copy version 1.1 ticks).
        assertEquals("Winter Checklist", dao.itemsOfNow(lists.get(0).id).get(0).section);
        assertEquals(0, dao.unlinkedSections().size());
    }

    @Test
    public void checklistsScreenShowsProgressAndOpensAList() {
        Checklist winter = list("Winter");
        ChecklistItem gloves = dao.itemsOfNow(winter.id).get(5);
        gloves.checked = true;
        dao.update(gloves);
        try (ActivityScenario<CheckList> s = ActivityScenario.launch(CheckList.class)) {
            s.onActivity(a -> {
                idle();
                RecyclerView rv = a.findViewById(R.id.rView);
                assertEquals("header + 3 lists", 4, rv.getAdapter().getItemCount());
                View card = row(rv, 1).itemView;
                assertEquals("Winter", ((TextView) card.findViewById(R.id.listName)).getText().toString());
                assertEquals("1 of 7 completed", ((TextView) card.findViewById(R.id.listDetails)).getText().toString());
                card.performClick();
                Intent next = shadowOf(a).getNextStartedActivity();
                assertEquals(ChecklistActivity.class.getName(), next.getComponent().getClassName());
                assertEquals(winter.id, next.getLongExtra(ChecklistActivity.EXTRA_ID, -1));
            });
        }
    }

    @Test
    public void tickAddEditAndDeleteItemsInOneChecklist() {
        Checklist winter = list("Winter");
        Intent open = ChecklistActivity.intent(context, winter.id);
        try (ActivityScenario<ChecklistActivity> s = ActivityScenario.launch(open)) {
            s.onActivity(a -> {
                idle();
                assertEquals("Winter", a.getTitle().toString());
                RecyclerView rv = a.findViewById(R.id.rView);
                // Row 0 is the progress header, rows 1-7 the items, row 8 "Add new item".
                assertEquals(9, rv.getAdapter().getItemCount());
                View boots = row(rv, 1).itemView;
                assertEquals(View.GONE, ((View) boots.findViewById(R.id.itemNote)).getVisibility());
                boots.performClick();
                idle();
                assertTrue(((CheckBox) row(rv, 1).itemView.findViewById(R.id.itemCheck)).isChecked());
                assertEquals("1 of 7 completed", ((TextView) row(rv, 0).itemView.findViewById(R.id.listProgressText)).getText().toString());

                // "Add new item": blank is ignored; text is added to this list.
                View add = row(rv, 8).itemView;
                ((EditText) add.findViewById(R.id.addItemInput)).setText("   ");
                add.findViewById(R.id.addItemButton).performClick();
                ((EditText) add.findViewById(R.id.addItemInput)).setText("Hand warmers");
                add.findViewById(R.id.addItemButton).performClick();
                idle();
                assertEquals(8, dao.itemsOfNow(winter.id).size());
                assertEquals("Hand warmers", dao.itemsOfNow(winter.id).get(7).title);

                // No visible Edit/Delete buttons; the ⋮ menu (and long-press) offers them.
                View thermal = row(rv, 2).itemView;
                assertNull(thermal.findViewById(R.id.rowEdit));
                assertNull(thermal.findViewById(R.id.rowDelete));
                thermal.findViewById(R.id.itemMore).performClick();
                assertNotNull(ShadowPopupMenu.getLatestPopupMenu());
                assertEquals(2, ShadowPopupMenu.getLatestPopupMenu().getMenu().size());
                assertTrue(thermal.performLongClick());

                // Edit with a note.
                ChecklistItem item = dao.itemsOfNow(winter.id).get(1);
                a.editItem(item);
                AlertDialog d = dialog();
                ((EditText) d.findViewById(R.id.itemTitleInput)).setText("Thermal underwear");
                ((EditText) d.findViewById(R.id.itemNoteInput)).setText("2 pairs each");
                press(AlertDialog.BUTTON_POSITIVE);
                ChecklistItem edited = dao.itemsOfNow(winter.id).get(1);
                assertEquals("Thermal underwear", edited.title);
                assertEquals("2 pairs each", edited.note);
                assertEquals("2 pairs each", ((TextView) row(rv, 2).itemView.findViewById(R.id.itemNote)).getText().toString());

                // Delete asks first: Cancel keeps it, Delete removes it.
                a.deleteItem(edited);
                press(AlertDialog.BUTTON_NEGATIVE);
                assertEquals(8, dao.itemsOfNow(winter.id).size());
                a.deleteItem(edited);
                press(AlertDialog.BUTTON_POSITIVE);
                assertEquals(7, dao.itemsOfNow(winter.id).size());
            });
            s.recreate(); // rotation
            s.onActivity(a -> {
                idle();
                RecyclerView rv = a.findViewById(R.id.rView);
                assertTrue("tick kept", ((CheckBox) row(rv, 1).itemView.findViewById(R.id.itemCheck)).isChecked());
            });
        }
    }

    @Test
    public void resetDuplicateArchiveRestoreAndDeleteAList() {
        Checklist winter = list("Winter");
        for (ChecklistItem i : dao.itemsOfNow(winter.id)) {
            i.checked = true;
            dao.update(i);
        }
        try (ActivityScenario<ChecklistActivity> s = ActivityScenario.launch(ChecklistActivity.intent(context, winter.id))) {
            s.onActivity(a -> {
                idle();
                // Start again: asks first, unticks everything, keeps every item.
                a.onOptionsItemSelected(new RoboMenuItem(R.id.action_list_reset));
                press(AlertDialog.BUTTON_NEGATIVE);
                assertEquals(7, checked(winter.id));
                a.onOptionsItemSelected(new RoboMenuItem(R.id.action_list_reset));
                press(AlertDialog.BUTTON_POSITIVE);
                assertEquals(0, checked(winter.id));
                assertEquals(7, dao.itemsOfNow(winter.id).size());

                // Duplicate for someone else: a new list with the same items, unticked.
                a.onOptionsItemSelected(new RoboMenuItem(R.id.action_list_duplicate));
                AlertDialog d = dialog();
                assertEquals("Winter (copy)", ((EditText) d.findViewById(R.id.checklistNameInput)).getText().toString());
                ((EditText) d.findViewById(R.id.checklistNameInput)).setText("Winter cabin");
                press(AlertDialog.BUTTON_POSITIVE);
                Checklist copy = list("Winter cabin");
                assertNotNull(copy);
                assertEquals(7, dao.itemsOfNow(copy.id).size());
                assertEquals(ChecklistActivity.class.getName(), shadowOf(a).getNextStartedActivity().getComponent().getClassName());

                // Archive keeps it; Restore brings it back.
                a.onOptionsItemSelected(new RoboMenuItem(R.id.action_list_archive));
                idle();
                assertTrue(dao.checklistNow(winter.id).archived);
                assertEquals(7, dao.itemsOfNow(winter.id).size());
                a.onOptionsItemSelected(new RoboMenuItem(R.id.action_list_restore));
                idle();
                assertFalse(dao.checklistNow(winter.id).archived);

                // Delete asks first.
                a.onOptionsItemSelected(new RoboMenuItem(R.id.action_list_delete));
                press(AlertDialog.BUTTON_NEGATIVE);
                assertNotNull(dao.checklistNow(winter.id));
                a.onOptionsItemSelected(new RoboMenuItem(R.id.action_list_delete));
                press(AlertDialog.BUTTON_POSITIVE);
                assertNull(dao.checklistNow(winter.id));
                assertTrue(dao.itemsOfNow(winter.id).isEmpty());
                assertEquals("the copy is untouched", 7, dao.itemsOfNow(copy.id).size());
            });
        }
    }

    @Test
    public void archivedListsStayAtTheBottom() {
        Checklist summer = list("Summer");
        summer.archived = true;
        dao.update(summer);
        try (ActivityScenario<CheckList> s = ActivityScenario.launch(CheckList.class)) {
            s.onActivity(a -> {
                idle();
                RecyclerView rv = a.findViewById(R.id.rView);
                assertEquals("header, 2 lists, Archived, 1 list", 5, rv.getAdapter().getItemCount());
                assertEquals(a.getString(R.string.lists_archived), ((TextView) row(rv, 3).itemView).getText().toString());
                assertEquals("Summer", ((TextView) row(rv, 4).itemView.findViewById(R.id.listName)).getText().toString());
            });
        }
    }

    @Test
    public void templatesMakeOwnCopiesForAChildWithANickname() {
        FamilyMember kid = new FamilyMember();
        kid.name = "Kid 1";
        kid.avatar = "👧";
        kid.role = "CHILD";
        long kidId = dao.insert(kid);
        try (ActivityScenario<ChecklistTemplatesActivity> s = ActivityScenario.launch(ChecklistTemplatesActivity.class)) {
            s.onActivity(a -> {
                idle();
                RecyclerView rv = a.findViewById(R.id.rView);
                assertEquals(ChecklistTemplates.ALL.size() + 1, rv.getAdapter().getItemCount());
                int school = ChecklistTemplates.ALL.indexOf(ChecklistTemplates.find("school")) + 1;
                View card = row(rv, school).itemView;
                assertEquals("Back to school", ((TextView) card.findViewById(R.id.listName)).getText().toString());
                card.performClick();
                AlertDialog d = dialog();
                assertEquals("Back to school", ((EditText) d.findViewById(R.id.checklistNameInput)).getText().toString());
                MaterialAutoCompleteTextView who = d.findViewById(R.id.checklistMember);
                who.getOnItemClickListener().onItemClick(null, null, 1, 0); // Kid 1
                press(AlertDialog.BUTTON_POSITIVE);
                Checklist made = list("Back to school");
                assertNotNull(made);
                assertEquals(Long.valueOf(kidId), made.memberId);
                assertEquals(Checklist.KIND_SCHOOL, made.kind);
                assertEquals(12, dao.itemsOfNow(made.id).size());
                assertEquals(0, checked(made.id));
            });
        }
        assertEquals("the template itself is unchanged", 12,
                ChecklistTemplates.find("school").items(context).length);
    }

    @Test
    public void newBlankChecklistFromTheChecklistsScreen() {
        try (ActivityScenario<CheckList> s = ActivityScenario.launch(CheckList.class)) {
            s.onActivity(a -> {
                idle();
                RecyclerView rv = a.findViewById(R.id.rView);
                row(rv, 0).itemView.findViewById(R.id.listsNew).performClick();
                AlertDialog choose = dialog();
                assertEquals(ChecklistTemplates.ALL.size(), choose.getListView().getAdapter().getCount());
                shadowOf(choose.getListView()).performItemClick(ChecklistTemplates.ALL.size() - 1); // Blank
                AlertDialog name = dialog();
                assertEquals("", ((EditText) name.findViewById(R.id.checklistNameInput)).getText().toString());
                press(AlertDialog.BUTTON_POSITIVE); // empty: refused
                assertTrue(name.isShowing());
                ((EditText) name.findViewById(R.id.checklistNameInput)).setText("Camping");
                press(AlertDialog.BUTTON_POSITIVE);
                Checklist camping = list("Camping");
                assertNotNull(camping);
                assertTrue(dao.itemsOfNow(camping.id).isEmpty());
                assertNull(camping.template);
            });
        }
    }

    @Test
    public void everyTemplateIsReadyToUse() {
        Set<String> keys = new HashSet<>();
        for (ChecklistTemplates.Template t : ChecklistTemplates.ALL) {
            assertTrue("unique " + t.key, keys.add(t.key));
            if (!ChecklistTemplates.BLANK.equals(t.key)) {
                assertTrue(t.key, t.items(context).length >= 5);
                for (String item : t.items(context)) {
                    assertTrue(item, item.length() <= ChecklistItem.MAX_TITLE_LENGTH);
                }
            }
        }
        for (String season : new String[]{"spring", "summer", "fall", "winter", "school", "grocery", "travel",
                "birthday", "first_aid", "emergency", "household", "celebration", ChecklistTemplates.BLANK}) {
            assertNotNull(season, ChecklistTemplates.find(season));
        }
    }
}
