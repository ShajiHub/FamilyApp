package com.shajitha.familyapp.whiteboard;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.color.MaterialColors;
import com.shajitha.familyapp.R;

/**
 * The drawing surface. The template is drawn underneath; strokes and shapes go on a transparent
 * layer above it, so the eraser removes drawing but never the template.
 */
public class DrawingView extends View {

    /** Told after every change, so the screen can update Undo/Redo and remember to save. */
    public interface OnChangeListener {
        void onDrawingChanged();
    }

    private Drawing drawing = new Drawing(Templates.BLANK);
    private Drawing.Kind tool = Drawing.Kind.PENCIL;
    private int color = Color.BLACK;
    /** Line width as a fraction of the canvas width. */
    private float size = 0.012f;
    private boolean filled = false;
    @Nullable
    private OnChangeListener listener;

    private final RectF area = new RectF();
    @Nullable
    private Bitmap background, layer;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    private final Paint framePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    // The stroke or shape being drawn right now
    @Nullable
    private float[] current;
    private int currentCount;
    private boolean full;

    public DrawingView(Context context) {
        this(context, null);
    }

    public DrawingView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        framePaint.setColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOutlineVariant));
        framePaint.setStyle(Paint.Style.STROKE);
        framePaint.setStrokeWidth(getResources().getDisplayMetrics().density);
        setContentDescription(context.getString(R.string.whiteboard_canvas_description));
    }

    public void setDrawing(@NonNull Drawing d) {
        drawing = d;
        background = null;
        rebuildLayer();
    }

    public Drawing getDrawing() {
        return drawing;
    }

    public void setTool(Drawing.Kind tool) {
        this.tool = tool;
    }

    public Drawing.Kind getTool() {
        return tool;
    }

    public void setColor(int color) {
        this.color = color;
    }

    public void setSize(float fractionOfWidth) {
        this.size = fractionOfWidth;
    }

    public void setFilled(boolean filled) {
        this.filled = filled;
    }

    public void setOnChangeListener(@Nullable OnChangeListener listener) {
        this.listener = listener;
    }

    /** True if the last stroke couldn't be added because the drawing is full. */
    public boolean wasFull() {
        return full;
    }

    public void undo() {
        drawing.undo();
        rebuildLayer();
        changed();
    }

    public void redo() {
        drawing.redo();
        rebuildLayer();
        changed();
    }

    public void clearDrawing() {
        drawing.clear();
        rebuildLayer();
        changed();
    }

    private void changed() {
        if (listener != null) {
            listener.onDrawingChanged();
        }
    }

    // ---------- Layout ----------

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        // Largest 3:4 area that fits, centred.
        float aw = w, ah = w / Drawing.ASPECT;
        if (ah > h) {
            ah = h;
            aw = h * Drawing.ASPECT;
        }
        float left = (w - aw) / 2f, top = (h - ah) / 2f;
        area.set(left, top, left + aw, top + ah);
        background = null;
        rebuildLayer();
    }

    private void rebuildLayer() {
        int w = Math.round(area.width()), h = Math.round(area.height());
        if (w <= 0 || h <= 0) {
            return;
        }
        if (layer == null || layer.getWidth() != w || layer.getHeight() != h) {
            layer = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        }
        layer.eraseColor(Color.TRANSPARENT);
        Canvas c = new Canvas(layer);
        for (Drawing.Element e : drawing.elements()) {
            drawElement(c, e, w, h, paint);
        }
        invalidate();
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        int w = Math.round(area.width()), h = Math.round(area.height());
        if (w <= 0 || h <= 0) {
            return;
        }
        if (background == null) {
            background = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Templates.draw(new Canvas(background), drawing.template, w, h);
        }
        canvas.drawBitmap(background, area.left, area.top, null);
        if (layer != null) {
            canvas.drawBitmap(layer, area.left, area.top, null);
            if (current != null && tool != Drawing.Kind.ERASER) {
                canvas.save();
                canvas.translate(area.left, area.top);
                canvas.clipRect(0, 0, w, h);
                drawElement(canvas, currentElement(), w, h, paint);
                canvas.restore();
            }
        }
        canvas.drawRect(area, framePaint);
    }

    // ---------- Touch ----------

    private Drawing.Element currentElement() {
        float[] pts = new float[currentCount];
        System.arraycopy(current, 0, pts, 0, currentCount);
        float width = tool == Drawing.Kind.BRUSH ? size * 2.2f : tool == Drawing.Kind.ERASER ? size * 3f : size;
        return new Drawing.Element(tool, color, width, filled, pts);
    }

    private void addPoint(float x, float y) {
        if (current == null) {
            return;
        }
        float nx = (x - area.left) / area.width(), ny = (y - area.top) / area.height();
        Drawing.Kind k = tool;
        boolean shape = k == Drawing.Kind.LINE || k == Drawing.Kind.RECT || k == Drawing.Kind.OVAL || k == Drawing.Kind.STAR;
        if (shape) {
            // Shapes keep the start point and the current end point.
            if (currentCount < 4) {
                current[currentCount++] = nx;
                current[currentCount++] = ny;
                if (currentCount == 2) {
                    current[currentCount++] = nx;
                    current[currentCount++] = ny;
                }
            } else {
                current[2] = nx;
                current[3] = ny;
            }
            return;
        }
        if (currentCount + 2 > Drawing.MAX_POINTS_PER_STROKE * 2) {
            return; // very long stroke: stop adding points
        }
        if (currentCount + 2 > current.length) {
            float[] bigger = new float[current.length * 2];
            System.arraycopy(current, 0, bigger, 0, currentCount);
            current = bigger;
        }
        current[currentCount++] = nx;
        current[currentCount++] = ny;
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (area.isEmpty()) {
            return false;
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                getParent().requestDisallowInterceptTouchEvent(true);
                current = new float[256];
                currentCount = 0;
                addPoint(event.getX(), event.getY());
                invalidate();
                return true;
            case MotionEvent.ACTION_MOVE:
                for (int i = 0; i < event.getHistorySize(); i++) {
                    addPoint(event.getHistoricalX(i), event.getHistoricalY(i));
                }
                addPoint(event.getX(), event.getY());
                if (tool == Drawing.Kind.ERASER && layer != null && currentCount >= 4) {
                    // Erase as the finger moves (the finished stroke is saved on lift).
                    drawElement(new Canvas(layer), currentElement(), layer.getWidth(), layer.getHeight(), paint);
                }
                invalidate();
                return true;
            case MotionEvent.ACTION_UP:
                addPoint(event.getX(), event.getY());
                commit();
                performClick();
                return true;
            case MotionEvent.ACTION_CANCEL:
                current = null;
                rebuildLayer(); // undo any live erasing
                return true;
            default:
                return super.onTouchEvent(event);
        }
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    private void commit() {
        if (current == null || currentCount < 2) {
            current = null;
            return;
        }
        if (currentCount == 2) {
            // A single tap: make it a dot.
            addPoint(area.left + current[0] * area.width() + 0.5f, area.top + current[1] * area.height());
        }
        Drawing.Element e = currentElement();
        current = null;
        full = !drawing.add(e);
        if (full) {
            rebuildLayer(); // nothing was added (also undoes any live erasing)
        } else if (layer != null) {
            drawElement(new Canvas(layer), e, layer.getWidth(), layer.getHeight(), paint);
        }
        invalidate();
        changed();
    }

    // ---------- Rendering (also used for thumbnails and gallery export) ----------

    /** Renders the whole drawing (template + elements) into a new bitmap. */
    public static Bitmap render(@NonNull Drawing d, int w, int h) {
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        Templates.draw(c, d.template, w, h);
        Bitmap layer = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas lc = new Canvas(layer);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        for (Drawing.Element e : d.elements()) {
            drawElement(lc, e, w, h, p);
        }
        c.drawBitmap(layer, 0, 0, null);
        layer.recycle();
        return out;
    }

    static void drawElement(Canvas c, Drawing.Element e, float w, float h, Paint p) {
        p.reset();
        p.setAntiAlias(true);
        p.setDither(true);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        p.setColor(e.color);
        p.setStrokeWidth(Math.max(1f, e.width * w));
        p.setStyle(Paint.Style.STROKE);
        float[] pts = e.points;
        switch (e.kind) {
            case ERASER:
                p.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
                // fall through: drawn like a stroke
            case PENCIL:
            case BRUSH: {
                if (e.kind == Drawing.Kind.BRUSH) {
                    p.setAlpha(Math.min(p.getAlpha(), 220));
                }
                Path path = new Path();
                path.moveTo(pts[0] * w, pts[1] * h);
                for (int i = 2; i + 1 < pts.length; i += 2) {
                    // Smooth curve through the midpoints.
                    float px = pts[i - 2] * w, py = pts[i - 1] * h;
                    float x = pts[i] * w, y = pts[i + 1] * h;
                    path.quadTo(px, py, (px + x) / 2, (py + y) / 2);
                }
                path.lineTo(pts[pts.length - 2] * w, pts[pts.length - 1] * h);
                c.drawPath(path, p);
                break;
            }
            case LINE:
                if (pts.length >= 4) {
                    c.drawLine(pts[0] * w, pts[1] * h, pts[2] * w, pts[3] * h, p);
                }
                break;
            case RECT:
            case OVAL:
            case STAR:
                if (pts.length >= 4) {
                    RectF r = new RectF(Math.min(pts[0], pts[2]) * w, Math.min(pts[1], pts[3]) * h,
                            Math.max(pts[0], pts[2]) * w, Math.max(pts[1], pts[3]) * h);
                    if (e.filled) {
                        p.setStyle(Paint.Style.FILL_AND_STROKE);
                    }
                    if (e.kind == Drawing.Kind.RECT) {
                        c.drawRect(r, p);
                    } else if (e.kind == Drawing.Kind.OVAL) {
                        c.drawOval(r, p);
                    } else {
                        c.drawPath(star(r), p);
                    }
                }
                break;
        }
        p.setXfermode(null);
    }

    private static Path star(RectF r) {
        Path path = new Path();
        float cx = r.centerX(), cy = r.centerY();
        float outer = Math.min(r.width(), r.height()) / 2f, inner = outer * 0.45f;
        for (int i = 0; i < 10; i++) {
            double a = -Math.PI / 2 + i * Math.PI / 5;
            float rad = i % 2 == 0 ? outer : inner;
            float x = cx + (float) Math.cos(a) * rad, y = cy + (float) Math.sin(a) * rad;
            if (i == 0) {
                path.moveTo(x, y);
            } else {
                path.lineTo(x, y);
            }
        }
        path.close();
        return path;
    }
}
