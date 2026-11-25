package com.shajitha.familyapp.whiteboard;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * An editable drawing: a starter template plus a list of strokes and shapes. Coordinates are
 * stored as fractions of the canvas (0..1), so a drawing looks the same on any screen and can be
 * reopened and changed later. Undo and redo work on whole steps (a stroke, a shape, or "clear").
 */
public final class Drawing {

    /** Width and height of the canvas: portrait 3:4. */
    public static final float ASPECT = 3f / 4f;

    // Limits that keep a damaged or oversized file from slowing down or crashing the app.
    static final int MAX_ELEMENTS = 3000;
    static final int MAX_POINTS_PER_STROKE = 20000;
    static final int MAX_TOTAL_POINTS = 300_000;
    static final int FORMAT_VERSION = 1;

    public enum Kind { PENCIL, BRUSH, ERASER, LINE, RECT, OVAL, STAR }

    /** One stroke or shape. */
    public static final class Element {
        public final Kind kind;
        public final int color;
        /** Line width as a fraction of the canvas width. */
        public final float width;
        public final boolean filled;
        /** x,y pairs (0..1). Strokes: every point. Shapes: start and end corner. */
        public final float[] points;

        public Element(Kind kind, int color, float width, boolean filled, float[] points) {
            this.kind = kind;
            this.color = color;
            this.width = width;
            this.filled = filled;
            this.points = points;
        }

        public boolean isShape() {
            return kind == Kind.LINE || kind == Kind.RECT || kind == Kind.OVAL || kind == Kind.STAR;
        }
    }

    /** An undoable step: either one element added, or the canvas cleared. */
    private static final class Step {
        @Nullable final Element added;
        @Nullable final List<Element> clearedElements;

        Step(@Nullable Element added, @Nullable List<Element> clearedElements) {
            this.added = added;
            this.clearedElements = clearedElements;
        }
    }

    @NonNull
    public String template;
    private final List<Element> elements = new ArrayList<>();
    private final List<Step> undo = new ArrayList<>();
    private final List<Step> redo = new ArrayList<>();
    private int totalPoints;

    public Drawing(@NonNull String template) {
        this.template = Templates.isKnown(template) ? template : Templates.BLANK;
    }

    public List<Element> elements() {
        return Collections.unmodifiableList(elements);
    }

    /** Adds a stroke or shape. Returns false (and adds nothing) if the drawing is full. */
    public boolean add(@NonNull Element e) {
        if (elements.size() >= MAX_ELEMENTS || totalPoints + e.points.length / 2 > MAX_TOTAL_POINTS) {
            return false;
        }
        elements.add(e);
        totalPoints += e.points.length / 2;
        undo.add(new Step(e, null));
        redo.clear();
        return true;
    }

    /** Clears the canvas (can be undone). */
    public void clear() {
        if (elements.isEmpty()) {
            return;
        }
        undo.add(new Step(null, new ArrayList<>(elements)));
        redo.clear();
        elements.clear();
        totalPoints = 0;
    }

    public boolean canUndo() {
        return !undo.isEmpty();
    }

    public boolean canRedo() {
        return !redo.isEmpty();
    }

    public void undo() {
        if (undo.isEmpty()) {
            return;
        }
        Step step = undo.remove(undo.size() - 1);
        if (step.added != null) {
            elements.remove(elements.size() - 1);
        } else if (step.clearedElements != null) {
            elements.addAll(step.clearedElements);
        }
        recount();
        redo.add(step);
    }

    public void redo() {
        if (redo.isEmpty()) {
            return;
        }
        Step step = redo.remove(redo.size() - 1);
        if (step.added != null) {
            elements.add(step.added);
        } else {
            elements.clear();
        }
        recount();
        undo.add(step);
    }

    private void recount() {
        totalPoints = 0;
        for (Element e : elements) {
            totalPoints += e.points.length / 2;
        }
    }

    public boolean isEmpty() {
        return elements.isEmpty();
    }

    // ---------- Saving and loading ----------

    @NonNull
    public String toJson() {
        try {
            JSONObject root = new JSONObject();
            root.put("format", "familyapp-drawing");
            root.put("version", FORMAT_VERSION);
            root.put("template", template);
            JSONArray list = new JSONArray();
            for (Element e : elements) {
                JSONObject o = new JSONObject();
                o.put("k", e.kind.name());
                o.put("c", e.color);
                o.put("w", (double) e.width);
                if (e.filled) {
                    o.put("f", true);
                }
                JSONArray pts = new JSONArray();
                for (float p : e.points) {
                    pts.put(Math.round(p * 10000) / 10000.0); // 4 decimals is plenty
                }
                o.put("p", pts);
                list.put(o);
            }
            root.put("elements", list);
            return root.toString();
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Thrown when a saved drawing can't be read safely. The file is left as it is. */
    public static final class InvalidDrawingException extends Exception {
        InvalidDrawingException(String message) {
            super(message);
        }
    }

    /**
     * Reads a saved drawing, checking everything: unknown kinds, out-of-range values and oversized
     * content are rejected or clamped, so a damaged file can't crash the app.
     */
    @NonNull
    public static Drawing fromJson(@NonNull String json) throws InvalidDrawingException {
        if (json.length() > 20_000_000) {
            throw new InvalidDrawingException("too large");
        }
        try {
            JSONObject root = new JSONObject(json);
            if (!"familyapp-drawing".equals(root.optString("format")) || root.optInt("version") > FORMAT_VERSION) {
                throw new InvalidDrawingException("not a FamilyApp drawing");
            }
            Drawing d = new Drawing(root.optString("template", Templates.BLANK));
            JSONArray list = root.optJSONArray("elements");
            if (list == null) {
                return d;
            }
            if (list.length() > MAX_ELEMENTS) {
                throw new InvalidDrawingException("too many elements");
            }
            for (int i = 0; i < list.length(); i++) {
                JSONObject o = list.getJSONObject(i);
                Kind kind;
                try {
                    kind = Kind.valueOf(o.getString("k"));
                } catch (IllegalArgumentException e) {
                    throw new InvalidDrawingException("unknown kind");
                }
                JSONArray pts = o.getJSONArray("p");
                int n = pts.length();
                if (n < 2 || n % 2 != 0 || n / 2 > MAX_POINTS_PER_STROKE) {
                    throw new InvalidDrawingException("bad points");
                }
                float[] points = new float[n];
                for (int j = 0; j < n; j++) {
                    double v = pts.getDouble(j);
                    if (Double.isNaN(v) || Double.isInfinite(v)) {
                        throw new InvalidDrawingException("bad number");
                    }
                    points[j] = (float) Math.max(-0.5, Math.min(1.5, v));
                }
                float width = (float) Math.max(0.001, Math.min(0.2, o.optDouble("w", 0.01)));
                Element e = new Element(kind, o.optInt("c", 0xFF000000), width, o.optBoolean("f", false), points);
                if (!d.addLoaded(e)) {
                    throw new InvalidDrawingException("too large");
                }
            }
            return d;
        } catch (JSONException e) {
            throw new InvalidDrawingException("damaged");
        }
    }

    /** Adds without creating an undo step (loading a saved drawing). */
    private boolean addLoaded(Element e) {
        if (elements.size() >= MAX_ELEMENTS || totalPoints + e.points.length / 2 > MAX_TOTAL_POINTS) {
            return false;
        }
        elements.add(e);
        totalPoints += e.points.length / 2;
        return true;
    }
}
