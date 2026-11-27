package com.shajitha.familyapp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Property;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.res.ResourcesCompat;

import com.google.android.material.color.MaterialColors;

import java.util.ArrayList;
import java.util.List;

/**
 * The Meal Planner wheel, drawn in code so it works with any number of foods.
 * Slice i covers the angles [i * slice, (i + 1) * slice), measured clockwise from the top of the wheel.
 * The pointer is fixed at the top; the wheel turns underneath it.
 */
public class FoodWheelView extends View {

    /** Lets an ObjectAnimator turn the wheel. */
    public static final Property<FoodWheelView, Float> WHEEL_ROTATION =
            new Property<FoodWheelView, Float>(Float.class, "wheelRotation") {
                @Override
                public void set(FoodWheelView view, Float value) {
                    view.setWheelRotation(value);
                }

                @Override
                public Float get(FoodWheelView view) {
                    return view.getWheelRotation();
                }
            };

    private final List<String> foods = new ArrayList<>();
    private float wheelRotation = 0f;

    private final Paint slicePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hubPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pointerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval = new RectF();
    private final Path pointer = new Path();
    private final int[] fills = new int[4];
    private final int[] inks = new int[4];
    private final float maxSize;

    public FoodWheelView(Context context) {
        this(context, null);
    }

    public FoodWheelView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        float density = getResources().getDisplayMetrics().density;
        maxSize = 340 * density;
        fills[0] = MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimaryContainer);
        inks[0] = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnPrimaryContainer);
        fills[1] = MaterialColors.getColor(this, com.google.android.material.R.attr.colorTertiaryContainer);
        inks[1] = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnTertiaryContainer);
        fills[2] = MaterialColors.getColor(this, com.google.android.material.R.attr.colorSecondaryContainer);
        inks[2] = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSecondaryContainer);
        fills[3] = MaterialColors.getColor(this, R.attr.colorAccent4Container);
        inks[3] = MaterialColors.getColor(this, R.attr.colorOnAccent4Container);

        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(2 * density);
        linePaint.setColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurface));
        hubPaint.setColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurface));
        pointerPaint.setColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimary));
        Typeface font = isInEditMode() ? Typeface.DEFAULT_BOLD : ResourcesCompat.getFont(context, R.font.poppins_medium);
        textPaint.setTypeface(font);
        setFocusable(true);
    }

    public void setFoods(@NonNull List<String> names) {
        foods.clear();
        foods.addAll(names);
        updateDescription();
        invalidate();
    }

    public int getFoodCount() {
        return foods.size();
    }

    public float getWheelRotation() {
        return wheelRotation;
    }

    public void setWheelRotation(float degrees) {
        wheelRotation = degrees;
        invalidate();
    }

    private float sliceAngle() {
        return 360f / foods.size();
    }

    /** The food slice currently under the pointer, or -1 when there are no foods. */
    public int indexUnderPointer() {
        if (foods.isEmpty()) {
            return -1;
        }
        float angle = ((-wheelRotation) % 360 + 360) % 360;
        return Math.min(foods.size() - 1, (int) (angle / sliceAngle()));
    }

    /**
     * Rotation that puts food {@code index} under the pointer, {@code offset} (-0.5..0.5) of a slice
     * away from its middle.
     */
    public float rotationFor(int index, float offset) {
        float slice = sliceAngle();
        return -(index * slice + slice / 2f + offset * slice);
    }

    /** Shows the given food under the pointer straight away. */
    public void showFood(int index) {
        if (index >= 0 && index < foods.size()) {
            setWheelRotation(rotationFor(index, 0));
        }
    }

    private void updateDescription() {
        setContentDescription(getContext().getString(R.string.meal_wheel_description, foods.size()));
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED
                ? width : MeasureSpec.getSize(heightMeasureSpec);
        // Never taller than about 60% of the screen (matters in landscape).
        float screenLimit = getResources().getDisplayMetrics().heightPixels * 0.6f;
        int size = (int) Math.min(Math.min(maxSize, screenLimit), Math.min(width, height));
        setMeasuredDimension(resolveSize(size, widthMeasureSpec), resolveSize(size, heightMeasureSpec));
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        float density = getResources().getDisplayMetrics().density;
        float cx = getWidth() / 2f;
        float pointerHeight = 22 * density;
        float radius = Math.min(getWidth(), getHeight()) / 2f - pointerHeight / 2f - 4 * density;
        float cy = getHeight() / 2f + pointerHeight / 4f;
        oval.set(cx - radius, cy - radius, cx + radius, cy + radius);

        if (foods.size() < 2) {
            slicePaint.setColor(fills[0]);
            canvas.drawOval(oval, slicePaint);
        } else {
            float slice = sliceAngle();
            canvas.save();
            canvas.rotate(wheelRotation, cx, cy);
            float textSize = Math.max(11 * density, Math.min(16 * density, radius * 0.12f));
            textPaint.setTextSize(textSize);
            for (int i = 0; i < foods.size(); i++) {
                int color = colorIndex(i);
                slicePaint.setColor(fills[color]);
                // drawArc measures from 3 o'clock; our slices are measured from 12 o'clock.
                canvas.drawArc(oval, -90 + i * slice, slice, true, slicePaint);
                canvas.drawArc(oval, -90 + i * slice, slice, true, linePaint);

                // Label along the middle of the slice. Labels on the left half are flipped so
                // they never appear upside down.
                float middle = i * slice + slice / 2f;
                float onScreen = ((middle + wheelRotation) % 360 + 360) % 360;
                boolean flip = onScreen > 180;
                canvas.save();
                canvas.rotate(middle + (flip ? 90 : -90), cx, cy);
                textPaint.setColor(inks[color]);
                float start = radius * 0.24f;
                float available = radius * 0.92f - start;
                CharSequence label = TextUtils.ellipsize(foods.get(i), textPaint, available, TextUtils.TruncateAt.END);
                textPaint.setTextAlign(flip ? Paint.Align.RIGHT : Paint.Align.LEFT);
                float x = flip ? cx - start : cx + start;
                canvas.drawText(label, 0, label.length(), x, cy + textSize / 3f, textPaint);
                canvas.restore();
            }
            canvas.restore();
        }

        // Hub in the middle, then the pointer at the top (it doesn't turn).
        canvas.drawCircle(cx, cy, radius * 0.14f, hubPaint);
        canvas.drawCircle(cx, cy, radius * 0.06f, pointerPaint);
        float top = cy - radius - pointerHeight / 2f;
        pointer.reset();
        pointer.moveTo(cx - pointerHeight * 0.55f, top);
        pointer.lineTo(cx + pointerHeight * 0.55f, top);
        pointer.lineTo(cx, top + pointerHeight * 1.1f);
        pointer.close();
        canvas.drawPath(pointer, pointerPaint);
    }

    /** Neighbouring slices never share a colour, including the last and the first. */
    private int colorIndex(int i) {
        int c = i % fills.length;
        if (i == foods.size() - 1 && i > 0 && c == 0) {
            c = 1;
        }
        return c;
    }
}
