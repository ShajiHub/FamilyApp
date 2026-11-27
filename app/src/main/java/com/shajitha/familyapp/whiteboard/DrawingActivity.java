package com.shajitha.familyapp.whiteboard;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.slider.Slider;
import com.shajitha.familyapp.BaseActivity;
import com.shajitha.familyapp.Dialogs;
import com.shajitha.familyapp.R;
import com.shajitha.familyapp.data.AppDatabase;
import com.shajitha.familyapp.data.DrawingEntry;
import com.shajitha.familyapp.data.FamilyDao;

import java.io.IOException;
import java.util.concurrent.ExecutorService;

/** The drawing editor. Saves automatically when you leave, and with the Save button. */
public class DrawingActivity extends BaseActivity {

    public static final String EXTRA_ID = "drawingId";

    /** Bright, kid-friendly colours. */
    static final int[] COLOURS = {0xFF212121, 0xFFFFFFFF, 0xFFE53935, 0xFFFB8C00, 0xFFFDD835, 0xFF7CB342,
            0xFF2E7D32, 0xFF29B6F6, 0xFF1E88E5, 0xFF8E24AA, 0xFFEC407A, 0xFF8D6E63, 0xFF9E9E9E};

    private FamilyDao dao;
    private DrawingView view;
    private DrawingEntry entry;
    private boolean dirty;
    private boolean loaded;
    private Menu menu;
    private final ExecutorService io = DrawingStore.IO;
    /** Keeps the drawing (and its undo history) when the phone is rotated. */
    public static class Model extends androidx.lifecycle.ViewModel {
        Drawing drawing;
        DrawingEntry entry;
        boolean dirty;
    }

    private Model model;
    private final Handler main = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_drawing);
        dao = AppDatabase.get(this).dao();
        view = findViewById(R.id.drawingView);
        view.setOnChangeListener(() -> {
            dirty = true;
            if (model != null) {
                model.dirty = true;
            }
            updateMenu();
            if (view.wasFull()) {
                Toast.makeText(this, R.string.whiteboard_full, Toast.LENGTH_SHORT).show();
            }
        });
        setUpTools();
        model = new androidx.lifecycle.ViewModelProvider(this).get(Model.class);
        if (model.drawing != null) {
            dirty = model.dirty;
            show(model.entry, model.drawing); // rotated: carry on where we were
            return;
        }

        long id = getIntent().getLongExtra(EXTRA_ID, -1);
        Context app = getApplicationContext();
        io.execute(() -> {
            DrawingEntry e = id < 0 ? null : dao.drawingNow(id);
            if (e == null) {
                main.post(this::finish);
                return;
            }
            try {
                Drawing d = DrawingStore.load(app, e.id, e.template);
                main.post(() -> show(e, d));
            } catch (IOException | Drawing.InvalidDrawingException ex) {
                // The file is kept as it is; nothing is overwritten.
                main.post(() -> new MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.whiteboard_damaged_title)
                        .setMessage(R.string.whiteboard_damaged_message)
                        .setPositiveButton(android.R.string.ok, (dlg, w) -> finish())
                        .setCancelable(false)
                        .show());
            }
        });
    }

    private void show(DrawingEntry e, Drawing d) {
        model.entry = e;
        model.drawing = d;
        entry = e;
        loaded = true;
        setTitle(e.name);
        view.setDrawing(d);
        ((TextView) findViewById(R.id.drawingIdea)).setText(getString(Templates.ideaRes(d.template)));
        updateMenu();
    }

    private void setUpTools() {
        MaterialButtonToggleGroup tools = findViewById(R.id.toolGroup);
        tools.check(R.id.toolPencil);
        tools.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) {
                return;
            }
            Drawing.Kind kind = checkedId == R.id.toolBrush ? Drawing.Kind.BRUSH
                    : checkedId == R.id.toolEraser ? Drawing.Kind.ERASER
                    : checkedId == R.id.toolLine ? Drawing.Kind.LINE
                    : checkedId == R.id.toolRect ? Drawing.Kind.RECT
                    : checkedId == R.id.toolOval ? Drawing.Kind.OVAL
                    : checkedId == R.id.toolStar ? Drawing.Kind.STAR : Drawing.Kind.PENCIL;
            view.setTool(kind);
        });

        LinearLayout row = findViewById(R.id.colourRow);
        float density = getResources().getDisplayMetrics().density;
        String[] names = getResources().getStringArray(R.array.colour_names);
        View[] swatches = new View[COLOURS.length];
        for (int i = 0; i < COLOURS.length; i++) {
            int colour = COLOURS[i];
            View swatch = new View(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams((int) (40 * density), (int) (40 * density));
            lp.setMargins((int) (4 * density), (int) (4 * density), (int) (4 * density), (int) (4 * density));
            swatch.setLayoutParams(lp);
            android.graphics.drawable.GradientDrawable circle = new android.graphics.drawable.GradientDrawable();
            circle.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            circle.setColor(colour);
            circle.setStroke((int) Math.max(1, density), 0x55000000); // outline keeps white visible
            swatch.setBackground(circle);
            swatch.setContentDescription(getString(R.string.whiteboard_colour, names[i]));
            swatch.setClickable(true);
            swatch.setFocusable(true);
            swatches[i] = swatch;
            swatch.setOnClickListener(v -> {
                view.setColor(colour);
                for (View s : swatches) {
                    s.setSelected(s == v);
                    s.setScaleX(s == v ? 1.15f : 1f);
                    s.setScaleY(s == v ? 1.15f : 1f);
                }
            });
            row.addView(swatch);
        }
        swatches[0].performClick();

        Slider size = findViewById(R.id.sizeSlider);
        view.setSize(size.getValue() * 0.004f);
        size.addOnChangeListener((s, value, fromUser) -> view.setSize(value * 0.004f));
        MaterialSwitch fill = findViewById(R.id.fillSwitch);
        fill.setOnCheckedChangeListener((b, on) -> view.setFilled(on));
    }

    // ---------- Menu ----------

    @Override
    public boolean onCreateOptionsMenu(Menu m) {
        getMenuInflater().inflate(R.menu.menu_drawing, m);
        menu = m;
        updateMenu();
        return true;
    }

    private void updateMenu() {
        if (menu == null) {
            return;
        }
        Drawing d = view.getDrawing();
        menu.findItem(R.id.action_undo).setEnabled(loaded && d.canUndo());
        menu.findItem(R.id.action_redo).setEnabled(loaded && d.canRedo());
        menu.findItem(R.id.action_export).setVisible(DrawingStore.canExport());
        for (int id : new int[]{R.id.action_save, R.id.action_export, R.id.action_rename, R.id.action_clear}) {
            menu.findItem(id).setEnabled(loaded);
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_undo) {
            view.undo();
        } else if (id == R.id.action_redo) {
            view.redo();
        } else if (id == R.id.action_save) {
            save(true);
        } else if (id == R.id.action_export) {
            export();
        } else if (id == R.id.action_rename) {
            rename();
        } else if (id == R.id.action_clear) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.whiteboard_clear_title)
                    .setMessage(R.string.whiteboard_clear_message)
                    .setPositiveButton(R.string.whiteboard_clear, (d, w) -> view.clearDrawing())
                    .setNegativeButton(R.string.cancel, null)
                    .show();
        } else {
            return super.onOptionsItemSelected(item);
        }
        return true;
    }

    // ---------- Saving ----------

    @Override
    protected void onPause() {
        super.onPause();
        if (dirty) {
            save(false); // never lose a drawing when leaving the screen
        }
    }


    /** Saves the drawing (file + preview) and its "last changed" time. */
    void save(boolean showMessage) {
        if (!loaded || entry == null) {
            return;
        }
        dirty = false;
        model.dirty = false;
        String json = view.getDrawing().toJson();
        Context app = getApplicationContext();
        DrawingEntry e = entry;
        io.execute(() -> {
            try {
                Drawing copy = Drawing.fromJson(json);
                DrawingStore.save(app, e.id, copy);
                e.updatedAt = System.currentTimeMillis();
                dao.update(e);
                if (showMessage) {
                    main.post(() -> Toast.makeText(app, R.string.whiteboard_saved, Toast.LENGTH_SHORT).show());
                }
            } catch (IOException | Drawing.InvalidDrawingException ex) {
                dirty = true;
                main.post(() -> Toast.makeText(app, R.string.whiteboard_save_failed, Toast.LENGTH_LONG).show());
            }
        });
    }

    private void rename() {
        Dialogs.editText(this, R.string.whiteboard_rename, R.string.whiteboard_name_hint, entry.name,
                text -> text.length() > DrawingEntry.MAX_NAME_LENGTH ? getString(R.string.whiteboard_name_too_long) : null,
                text -> {
                    entry.name = text;
                    setTitle(text);
                    DrawingEntry e = entry;
                    io.execute(() -> dao.update(e));
                });
    }

    private void export() {
        String json = view.getDrawing().toJson();
        String name = entry.name;
        Context app = getApplicationContext();
        io.execute(() -> {
            boolean ok;
            try {
                ok = DrawingStore.exportToGallery(app, Drawing.fromJson(json), name) != null;
            } catch (Drawing.InvalidDrawingException e) {
                ok = false;
            }
            boolean saved = ok;
            // Only says "saved" when the picture was fully written.
            main.post(() -> Toast.makeText(app, saved ? R.string.whiteboard_exported : R.string.whiteboard_export_failed,
                    Toast.LENGTH_LONG).show());
        });
    }

    /** Tests: the drawing view. */
    DrawingView drawingView() {
        return view;
    }
}
