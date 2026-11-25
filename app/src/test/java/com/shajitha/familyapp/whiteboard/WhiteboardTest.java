package com.shajitha.familyapp.whiteboard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.content.Intent;
import android.os.Looper;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;

import androidx.appcompat.app.AlertDialog;
import androidx.lifecycle.Lifecycle;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.shajitha.familyapp.R;
import com.shajitha.familyapp.data.AppDatabase;
import com.shajitha.familyapp.data.DrawingEntry;
import com.shajitha.familyapp.data.FamilyDao;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.fakes.RoboMenuItem;
import org.robolectric.shadows.ShadowDialog;
import org.robolectric.shadows.ShadowToast;

import java.io.File;
import java.nio.file.Files;
import java.util.function.BooleanSupplier;

/** Creative Whiteboard: the editable drawing format, saving, and the screens. */
@RunWith(AndroidJUnit4.class)
public class WhiteboardTest {

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

    private static Drawing.Element stroke(float... pts) {
        return new Drawing.Element(Drawing.Kind.PENCIL, 0xFFE53935, 0.01f, false, pts);
    }

    private static void idle() {
        shadowOf(Looper.getMainLooper()).idle();
    }

    /** Waits for background saves and loads (they run on their own thread). */
    private static void waitFor(BooleanSupplier condition) {
        long end = System.currentTimeMillis() + 5000;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > end) {
                throw new AssertionError("timed out");
            }
            idle();
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
        idle();
    }

    // ---------- The drawing model ----------

    @Test
    public void undoRedoAndClear() {
        Drawing d = new Drawing(Templates.SPACE);
        assertFalse(d.canUndo());
        d.add(stroke(0.1f, 0.1f, 0.2f, 0.2f));
        d.add(new Drawing.Element(Drawing.Kind.STAR, 0xFFFDD835, 0.01f, true, new float[]{0.4f, 0.4f, 0.6f, 0.6f}));
        assertEquals(2, d.elements().size());
        d.undo();
        assertEquals(1, d.elements().size());
        assertTrue(d.canRedo());
        d.redo();
        assertEquals(2, d.elements().size());
        d.clear();
        assertTrue(d.isEmpty());
        d.undo(); // undo the clear
        assertEquals(2, d.elements().size());
        d.undo();
        d.add(stroke(0.5f, 0.5f, 0.6f, 0.6f)); // a new step clears redo
        assertFalse(d.canRedo());
    }

    @Test
    public void savedDrawingsStayEditable() throws Exception {
        Drawing d = new Drawing(Templates.ROADS);
        d.add(stroke(0.1f, 0.2f, 0.3f, 0.4f, 0.5f, 0.6f));
        d.add(new Drawing.Element(Drawing.Kind.RECT, 0xFF1E88E5, 0.02f, true, new float[]{0.1f, 0.1f, 0.4f, 0.3f}));
        d.add(new Drawing.Element(Drawing.Kind.ERASER, 0xFF000000, 0.05f, false, new float[]{0.2f, 0.2f, 0.25f, 0.25f}));
        Drawing back = Drawing.fromJson(d.toJson());
        assertEquals(Templates.ROADS, back.template);
        assertEquals(3, back.elements().size());
        Drawing.Element rect = back.elements().get(1);
        assertEquals(Drawing.Kind.RECT, rect.kind);
        assertEquals(0xFF1E88E5, rect.color);
        assertTrue(rect.filled);
        assertEquals(0.4f, rect.points[2], 0.0001f);
        // Still editable after loading: undo continues from here.
        back.add(stroke(0.9f, 0.9f, 0.95f, 0.95f));
        back.undo();
        assertEquals(3, back.elements().size());
    }

    @Test
    public void damagedOrHostileFilesAreRejected() {
        String[] bad = {
                "not json",
                "{\"format\":\"something-else\",\"version\":1}",
                "{\"format\":\"familyapp-drawing\",\"version\":99}",
                "{\"format\":\"familyapp-drawing\",\"version\":1,\"elements\":[{\"k\":\"ROCKET\",\"c\":0,\"w\":0.1,\"p\":[0,0]}]}",
                "{\"format\":\"familyapp-drawing\",\"version\":1,\"elements\":[{\"k\":\"PENCIL\",\"c\":0,\"w\":0.1,\"p\":[0,0,1]}]}",
                "{\"format\":\"familyapp-drawing\",\"version\":1,\"elements\":[{\"k\":\"PENCIL\",\"c\":0,\"w\":0.1,\"p\":[\"x\",0]}]}",
        };
        for (String json : bad) {
            try {
                Drawing.fromJson(json);
                fail("should reject " + json);
            } catch (Drawing.InvalidDrawingException expected) {
                // ok
            }
        }
        // Out-of-range values are clamped, unknown templates fall back to blank.
        try {
            Drawing d = Drawing.fromJson("{\"format\":\"familyapp-drawing\",\"version\":1,\"template\":\"../../etc\","
                    + "\"elements\":[{\"k\":\"PENCIL\",\"c\":0,\"w\":99,\"p\":[-50,50]}]}");
            assertEquals(Templates.BLANK, d.template);
            assertEquals(0.2f, d.elements().get(0).width, 0.0001f);
            assertEquals(-0.5f, d.elements().get(0).points[0], 0.0001f);
        } catch (Drawing.InvalidDrawingException e) {
            fail(e.getMessage());
        }
    }

    @Test
    public void aFullDrawingRefusesMore() {
        Drawing d = new Drawing(Templates.BLANK);
        for (int i = 0; i < Drawing.MAX_ELEMENTS; i++) {
            assertTrue(d.add(stroke(0.1f, 0.1f, 0.2f, 0.2f)));
        }
        assertFalse(d.add(stroke(0.1f, 0.1f, 0.2f, 0.2f)));
        assertEquals(Drawing.MAX_ELEMENTS, d.elements().size());
    }

    // ---------- Files ----------

    @Test
    public void storeSavesLoadsCopiesAndDeletes() throws Exception {
        Drawing d = new Drawing(Templates.GARDEN);
        d.add(stroke(0.1f, 0.1f, 0.5f, 0.5f));
        DrawingStore.save(context, 7, d);
        assertTrue(DrawingStore.file(context, 7).exists());
        assertTrue("preview made", DrawingStore.thumbnail(context, 7).exists());
        assertEquals(1, DrawingStore.load(context, 7, Templates.BLANK).elements().size());
        assertTrue("missing file: empty drawing", DrawingStore.load(context, 99, Templates.SPACE).isEmpty());

        DrawingStore.copy(context, 7, 8);
        assertEquals(1, DrawingStore.load(context, 8, Templates.BLANK).elements().size());

        // A damaged file is reported and left exactly as it is.
        File f = DrawingStore.file(context, 8);
        Files.write(f.toPath(), "garbage".getBytes());
        try {
            DrawingStore.load(context, 8, Templates.BLANK);
            fail("damaged");
        } catch (Drawing.InvalidDrawingException expected) {
            assertEquals("garbage", new String(Files.readAllBytes(f.toPath())));
        }
        DrawingStore.delete(context, 7);
        assertFalse(DrawingStore.file(context, 7).exists());
        assertFalse(DrawingStore.thumbnail(context, 7).exists());
        // File names never come from what people type.
        assertEquals("drawing-8.json", DrawingStore.file(context, 8).getName());
        assertEquals("FamilyApp etcpasswd", DrawingStore.safeFileName("../../etc/passwd"));
        assertEquals("FamilyApp Drawing", DrawingStore.safeFileName("///"));
    }

    // ---------- Screens ----------

    private static void touch(View v, int action, float x, float y) {
        long t = SystemClock.uptimeMillis();
        MotionEvent e = MotionEvent.obtain(t, t, action, x, y, 0);
        v.dispatchTouchEvent(e);
        e.recycle();
    }

    private static void drag(View v, float x1, float y1, float x2, float y2) {
        touch(v, MotionEvent.ACTION_DOWN, x1, y1);
        touch(v, MotionEvent.ACTION_MOVE, (x1 + x2) / 2, (y1 + y2) / 2);
        touch(v, MotionEvent.ACTION_MOVE, x2, y2);
        touch(v, MotionEvent.ACTION_UP, x2, y2);
        idle();
    }

    private long newDrawingFromGallery() {
        long[] id = {-1};
        try (ActivityScenario<WhiteboardActivity> s = ActivityScenario.launch(WhiteboardActivity.class)) {
            s.onActivity(a -> {
                RecyclerView rv = a.findViewById(R.id.screenList);
                rv.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(4000, View.MeasureSpec.EXACTLY));
                rv.layout(0, 0, 1080, 4000);
                idle();
                rv.findViewHolderForAdapterPosition(0).itemView.findViewById(R.id.newDrawing).performClick();
                idle();
                AlertDialog choose = (AlertDialog) ShadowDialog.getLatestDialog();
                assertEquals(Templates.ALL.size(), choose.getListView().getAdapter().getCount());
                shadowOf(choose.getListView()).performItemClick(4); // Roads and cars
                idle();
                waitFor(() -> !dao.drawingsNow().isEmpty());
                Intent open = shadowOf(a).getNextStartedActivity();
                assertNotNull(open);
                id[0] = open.getLongExtra(DrawingActivity.EXTRA_ID, -1);
            });
        }
        return id[0];
    }

    @Test
    public void drawSaveReopenRenameAndExport() {
        long id = newDrawingFromGallery();
        assertEquals("Roads and cars 1", dao.drawingNow(id).name);
        assertEquals(Templates.ROADS, dao.drawingNow(id).template);

        Intent intent = new Intent(context, DrawingActivity.class).putExtra(DrawingActivity.EXTRA_ID, id);
        try (ActivityScenario<DrawingActivity> s = ActivityScenario.launch(intent)) {
            s.onActivity(a -> waitFor(() -> a.drawingView().getDrawing().template.equals(Templates.ROADS)));
            s.onActivity(a -> {
                DrawingView v = a.drawingView();
                assertTrue(v.getWidth() > 0);
                drag(v, v.getWidth() * 0.3f, v.getHeight() * 0.3f, v.getWidth() * 0.6f, v.getHeight() * 0.5f);
                assertEquals(1, v.getDrawing().elements().size());
                a.findViewById(R.id.toolStar).performClick();
                drag(v, v.getWidth() * 0.2f, v.getHeight() * 0.2f, v.getWidth() * 0.4f, v.getHeight() * 0.4f);
                assertEquals(Drawing.Kind.STAR, v.getDrawing().elements().get(1).kind);
                a.onOptionsItemSelected(new RoboMenuItem(R.id.action_undo));
                assertEquals(1, v.getDrawing().elements().size());
                a.onOptionsItemSelected(new RoboMenuItem(R.id.action_redo));
                assertEquals(2, v.getDrawing().elements().size());
            });
            s.recreate(); // rotate: the drawing and its undo history are kept
            s.onActivity(a -> {
                assertEquals(2, a.drawingView().getDrawing().elements().size());
                assertTrue(a.drawingView().getDrawing().canUndo());
                a.onOptionsItemSelected(new RoboMenuItem(R.id.action_export));
                waitFor(() -> ShadowToast.getTextOfLatestToast() != null);
                String msg = ShadowToast.getTextOfLatestToast().toString();
                assertTrue(msg, msg.equals(a.getString(R.string.whiteboard_exported))
                        || msg.equals(a.getString(R.string.whiteboard_export_failed)));
            });
            s.moveToState(Lifecycle.State.CREATED); // leaving the screen saves automatically
        }
        waitFor(() -> {
            try {
                return DrawingStore.load(context, id, Templates.BLANK).elements().size() == 2;
            } catch (Exception e) {
                return false;
            }
        });
        // Reopen and continue editing.
        try (ActivityScenario<DrawingActivity> s = ActivityScenario.launch(intent)) {
            s.onActivity(a -> waitFor(() -> a.drawingView().getDrawing().elements().size() == 2));
            s.onActivity(a -> {
                a.onOptionsItemSelected(new RoboMenuItem(R.id.action_rename));
                idle(); // let the dialog finish showing (its Save button is wired up then)
                AlertDialog d = (AlertDialog) ShadowDialog.getLatestDialog();
                ((android.widget.EditText) d.findViewById(R.id.dialogInput)).setText("x".repeat(61));
                d.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
                assertTrue("too long", d.isShowing());
                ((android.widget.EditText) d.findViewById(R.id.dialogInput)).setText("Busy crossing");
                d.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
                idle();
            });
        }
        waitFor(() -> "Busy crossing".equals(dao.drawingNow(id).name));
    }

    @Test
    public void duplicateAndDeleteFromTheGallery() throws Exception {
        DrawingEntry e = new DrawingEntry();
        e.name = "Space trip";
        e.template = Templates.SPACE;
        e.id = dao.insert(e);
        Drawing d = new Drawing(Templates.SPACE);
        d.add(stroke(0.1f, 0.1f, 0.2f, 0.2f));
        DrawingStore.save(context, e.id, d);
        try (ActivityScenario<WhiteboardActivity> s = ActivityScenario.launch(WhiteboardActivity.class)) {
            s.onActivity(a -> {
                RecyclerView rv = a.findViewById(R.id.screenList);
                rv.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(4000, View.MeasureSpec.EXACTLY));
                rv.layout(0, 0, 1080, 4000);
                idle();
                View card = rv.findViewHolderForAdapterPosition(1).itemView;
                card.findViewById(R.id.drawingDuplicate).performClick();
                idle();
                assertEquals(2, dao.drawingsNow().size());
                long copyId = dao.drawingsNow().get(0).id == e.id ? dao.drawingsNow().get(1).id : dao.drawingsNow().get(0).id;
                assertTrue(DrawingStore.file(a, copyId).exists());

                rv.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(4000, View.MeasureSpec.EXACTLY));
                rv.layout(0, 0, 1080, 4000);
                idle();
                rv.findViewHolderForAdapterPosition(1).itemView.findViewById(R.id.drawingDelete).performClick();
                idle();
                ((AlertDialog) ShadowDialog.getLatestDialog()).getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
                idle();
                assertEquals("Cancel keeps it", 2, dao.drawingsNow().size());
                rv.findViewHolderForAdapterPosition(1).itemView.findViewById(R.id.drawingDelete).performClick();
                idle();
                ((AlertDialog) ShadowDialog.getLatestDialog()).getButton(AlertDialog.BUTTON_POSITIVE).performClick();
                idle();
                assertEquals(1, dao.drawingsNow().size());
            });
        }
    }

    @Test
    public void everyTemplateDrawsWithoutErrors() {
        for (String t : Templates.ALL) {
            Drawing d = new Drawing(t);
            d.add(stroke(0.1f, 0.1f, 0.9f, 0.9f));
            assertNotNull(DrawingView.render(d, 300, 400));
        }
    }
}
