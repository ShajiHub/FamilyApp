package com.shajitha.familyapp.whiteboard;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.shajitha.familyapp.BaseActivity;
import com.shajitha.familyapp.Dialogs;
import com.shajitha.familyapp.R;
import com.shajitha.familyapp.data.AppDatabase;
import com.shajitha.familyapp.data.DrawingEntry;
import com.shajitha.familyapp.data.FamilyDao;

import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Creative Whiteboard: the family's saved drawings, and starting a new one. */
public class WhiteboardActivity extends BaseActivity {

    private FamilyDao dao;
    private final List<DrawingEntry> drawings = new ArrayList<>();
    private final DrawingAdapter adapter = new DrawingAdapter();
    private final ExecutorService loader = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(com.shajitha.familyapp.R.layout.activity_list_screen);
        dao = AppDatabase.get(this).dao();
        RecyclerView list = findViewById(R.id.screenList);
        int columns = Math.max(2, Math.min(5, getResources().getConfiguration().screenWidthDp / 170));
        GridLayoutManager grid = new GridLayoutManager(this, columns);
        grid.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                return position == 0 ? columns : 1;
            }
        });
        list.setLayoutManager(grid);
        list.setAdapter(adapter);
        dao.drawings().observe(this, all -> {
            drawings.clear();
            drawings.addAll(all);
            adapter.notifyDataSetChanged();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        adapter.notifyDataSetChanged(); // previews may have changed in the editor
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        loader.shutdownNow();
    }

    /** Asks for a starter template, then creates and opens a new drawing. */
    void newDrawing() {
        String[] names = new String[Templates.ALL.size()];
        for (int i = 0; i < names.length; i++) {
            names[i] = getString(Templates.nameRes(Templates.ALL.get(i)));
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.whiteboard_choose_template)
                .setItems(names, (d, which) -> create(Templates.ALL.get(which), names[which]))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void create(String template, String templateName) {
        Context app = getApplicationContext();
        DrawingEntry entry = new DrawingEntry();
        entry.template = template;
        entry.name = getString(R.string.whiteboard_default_name, templateName, drawings.size() + 1);
        entry.createdAt = entry.updatedAt = System.currentTimeMillis();
        AppDatabase.write(() -> {
            entry.id = dao.insert(entry);
            try {
                DrawingStore.save(app, entry.id, new Drawing(template));
            } catch (IOException ignored) {
                // The editor starts with an empty drawing anyway.
            }
            main.post(() -> open(entry.id));
        });
    }

    void open(long id) {
        startActivity(new Intent(this, DrawingActivity.class).putExtra(DrawingActivity.EXTRA_ID, id));
    }

    private void rename(DrawingEntry entry) {
        Dialogs.editText(this, R.string.whiteboard_rename, R.string.whiteboard_name_hint, entry.name,
                text -> text.length() > DrawingEntry.MAX_NAME_LENGTH ? getString(R.string.whiteboard_name_too_long) : null,
                text -> {
                    entry.name = text;
                    AppDatabase.write(() -> dao.update(entry));
                });
    }

    private void duplicate(DrawingEntry entry) {
        Context app = getApplicationContext();
        DrawingEntry copy = new DrawingEntry();
        String name = getString(R.string.whiteboard_copy_name, entry.name);
        copy.name = name.length() > DrawingEntry.MAX_NAME_LENGTH ? name.substring(0, DrawingEntry.MAX_NAME_LENGTH) : name;
        copy.template = entry.template;
        copy.createdAt = copy.updatedAt = System.currentTimeMillis();
        AppDatabase.write(() -> {
            copy.id = dao.insert(copy);
            try {
                DrawingStore.copy(app, entry.id, copy.id);
            } catch (IOException e) {
                main.post(() -> Toast.makeText(app, R.string.whiteboard_copy_failed, Toast.LENGTH_LONG).show());
            }
        });
    }

    private void delete(DrawingEntry entry) {
        Context app = getApplicationContext();
        Dialogs.confirmDelete(this, getString(R.string.whiteboard_delete_title), getString(R.string.whiteboard_delete_message, entry.name),
                () -> AppDatabase.write(() -> {
                    dao.delete(entry);
                    DrawingStore.delete(app, entry.id);
                }));
    }

    private final class DrawingAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        @Override
        public int getItemViewType(int position) {
            return position == 0 ? 0 : 1;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            int layout = viewType == 0 ? R.layout.item_whiteboard_header : R.layout.item_drawing;
            return new RecyclerView.ViewHolder(LayoutInflater.from(parent.getContext()).inflate(layout, parent, false)) {
            };
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            View v = holder.itemView;
            if (position == 0) {
                v.findViewById(R.id.newDrawing).setOnClickListener(x -> newDrawing());
                v.findViewById(R.id.whiteboardEmpty).setVisibility(drawings.isEmpty() ? View.VISIBLE : View.GONE);
                return;
            }
            DrawingEntry entry = drawings.get(position - 1);
            ((TextView) v.findViewById(R.id.drawingName)).setText(entry.name);
            ((TextView) v.findViewById(R.id.drawingDate)).setText(
                    DateFormat.getDateInstance(DateFormat.MEDIUM).format(new Date(entry.updatedAt)));
            ImageView thumb = v.findViewById(R.id.drawingThumb);
            thumb.setImageDrawable(null);
            File file = DrawingStore.thumbnail(WhiteboardActivity.this, entry.id);
            thumb.setTag(file);
            loader.execute(() -> {
                Bitmap bitmap = file.exists() ? BitmapFactory.decodeFile(file.getPath()) : null;
                main.post(() -> {
                    if (file.equals(thumb.getTag()) && bitmap != null) {
                        thumb.setImageBitmap(bitmap);
                    }
                });
            });
            v.setContentDescription(entry.name);
            v.setOnClickListener(x -> open(entry.id));
            v.findViewById(R.id.drawingRename).setOnClickListener(x -> rename(entry));
            v.findViewById(R.id.drawingDuplicate).setOnClickListener(x -> duplicate(entry));
            v.findViewById(R.id.drawingDelete).setOnClickListener(x -> delete(entry));
        }

        @Override
        public int getItemCount() {
            return drawings.size() + 1;
        }
    }
}
