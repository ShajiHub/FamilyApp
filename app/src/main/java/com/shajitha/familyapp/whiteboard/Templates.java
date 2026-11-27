package com.shajitha.familyapp.whiteboard;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;

import androidx.annotation.NonNull;

import com.shajitha.familyapp.R;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * Starter backgrounds for the Creative Whiteboard, drawn in code (sharp at any size, nothing to
 * download). The drawing stores only the template's name, so it is redrawn when reopened.
 */
public final class Templates {

    public static final String BLANK = "blank";
    public static final String GARDEN = "garden";
    public static final String NATURE = "nature";
    public static final String SPACE = "space";
    public static final String ROADS = "roads";

    public static final List<String> ALL = Arrays.asList(BLANK, GARDEN, NATURE, SPACE, ROADS);

    private Templates() {
    }

    public static boolean isKnown(String template) {
        return ALL.contains(template);
    }

    public static int nameRes(String template) {
        switch (template) {
            case GARDEN:
                return R.string.template_garden;
            case NATURE:
                return R.string.template_nature;
            case SPACE:
                return R.string.template_space;
            case ROADS:
                return R.string.template_roads;
            default:
                return R.string.template_blank;
        }
    }

    /** A drawing idea for each template, shown in the editor. */
    public static int ideaRes(String template) {
        switch (template) {
            case GARDEN:
                return R.string.idea_garden;
            case NATURE:
                return R.string.idea_nature;
            case SPACE:
                return R.string.idea_space;
            case ROADS:
                return R.string.idea_roads;
            default:
                return R.string.idea_blank;
        }
    }

    /** Draws the template's background in a w x h area starting at (0, 0). */
    public static void draw(@NonNull Canvas c, @NonNull String template, float w, float h) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        switch (template) {
            case GARDEN:
                garden(c, p, w, h);
                break;
            case NATURE:
                nature(c, p, w, h);
                break;
            case SPACE:
                space(c, p, w, h);
                break;
            case ROADS:
                roads(c, p, w, h);
                break;
            default:
                c.drawColor(Color.WHITE);
        }
    }

    private static void sky(Canvas c, Paint p, float w, float h, int top, int bottom, float until) {
        p.setShader(new LinearGradient(0, 0, 0, h * until, top, bottom, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h * until, p);
        p.setShader(null);
    }

    private static void sun(Canvas c, Paint p, float cx, float cy, float r) {
        p.setColor(0xFFFFD54F);
        c.drawCircle(cx, cy, r, p);
        p.setStrokeWidth(r * 0.12f);
        p.setStrokeCap(Paint.Cap.ROUND);
        for (int i = 0; i < 8; i++) {
            double a = Math.PI * 2 * i / 8;
            c.drawLine(cx + (float) Math.cos(a) * r * 1.3f, cy + (float) Math.sin(a) * r * 1.3f,
                    cx + (float) Math.cos(a) * r * 1.7f, cy + (float) Math.sin(a) * r * 1.7f, p);
        }
    }

    private static void garden(Canvas c, Paint p, float w, float h) {
        sky(c, p, w, h, 0xFFBFE3F7, 0xFFEAF6FD, 0.62f);
        sun(c, p, w * 0.82f, h * 0.12f, w * 0.07f);
        p.setColor(0xFF8BC34A);
        c.drawRect(0, h * 0.62f, w, h, p);
        p.setColor(0xFF7CB342);
        c.drawRect(0, h * 0.8f, w, h, p);
        // White fence
        p.setColor(0xFFFFFFFF);
        float picket = w / 14f;
        for (int i = 0; i < 14; i++) {
            float x = i * picket + picket * 0.2f;
            c.drawRect(x, h * 0.5f, x + picket * 0.6f, h * 0.64f, p);
        }
        c.drawRect(0, h * 0.54f, w, h * 0.56f, p);
        // A few flowers to start with
        float[][] flowers = {{0.15f, 0.74f, 0xFFE91E63}, {0.3f, 0.78f, 0xFFFFEB3B}, {0.72f, 0.75f, 0xFF9C27B0}};
        for (float[] f : flowers) {
            flower(c, p, w * f[0], h * f[1], w * 0.03f, (int) (long) f[2]);
        }
    }

    private static void flower(Canvas c, Paint p, float x, float y, float r, int petal) {
        p.setColor(0xFF388E3C);
        p.setStrokeWidth(r * 0.35f);
        c.drawLine(x, y, x, y + r * 4, p);
        p.setColor(petal);
        for (int i = 0; i < 5; i++) {
            double a = Math.PI * 2 * i / 5;
            c.drawCircle(x + (float) Math.cos(a) * r, y + (float) Math.sin(a) * r, r * 0.8f, p);
        }
        p.setColor(0xFFFFC107);
        c.drawCircle(x, y, r * 0.6f, p);
    }

    private static void nature(Canvas c, Paint p, float w, float h) {
        sky(c, p, w, h, 0xFFB3E5FC, 0xFFE1F5FE, 0.6f);
        sun(c, p, w * 0.18f, h * 0.12f, w * 0.06f);
        p.setColor(0xFFA5D6A7);
        c.drawOval(new RectF(-w * 0.3f, h * 0.42f, w * 0.7f, h * 0.85f), p);
        p.setColor(0xFF81C784);
        c.drawOval(new RectF(w * 0.3f, h * 0.45f, w * 1.3f, h * 0.9f), p);
        p.setColor(0xFF66BB6A);
        c.drawRect(0, h * 0.68f, w, h, p);
        // Path
        p.setColor(0xFFD7CCC8);
        android.graphics.Path path = new android.graphics.Path();
        path.moveTo(w * 0.42f, h);
        path.lineTo(w * 0.58f, h);
        path.lineTo(w * 0.52f, h * 0.68f);
        path.lineTo(w * 0.48f, h * 0.68f);
        path.close();
        c.drawPath(path, p);
        // Trees
        float[][] trees = {{0.15f, 0.7f, 1f}, {0.82f, 0.72f, 1.2f}, {0.32f, 0.66f, 0.7f}};
        for (float[] t : trees) {
            tree(c, p, w * t[0], h * t[1], w * 0.08f * t[2]);
        }
    }

    private static void tree(Canvas c, Paint p, float x, float base, float s) {
        p.setColor(0xFF795548);
        c.drawRect(x - s * 0.2f, base - s * 1.6f, x + s * 0.2f, base, p);
        p.setColor(0xFF2E7D32);
        c.drawCircle(x, base - s * 2.1f, s, p);
        c.drawCircle(x - s * 0.7f, base - s * 1.6f, s * 0.75f, p);
        c.drawCircle(x + s * 0.7f, base - s * 1.6f, s * 0.75f, p);
    }

    private static void space(Canvas c, Paint p, float w, float h) {
        p.setShader(new LinearGradient(0, 0, 0, h, 0xFF0B1026, 0xFF1B2550, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        p.setShader(null);
        Random random = new Random(42); // the same stars every time
        p.setColor(Color.WHITE);
        for (int i = 0; i < 90; i++) {
            c.drawCircle(random.nextFloat() * w, random.nextFloat() * h, w * (0.002f + random.nextFloat() * 0.004f), p);
        }
        // Moon
        p.setColor(0xFFECEFF1);
        c.drawCircle(w * 0.78f, h * 0.15f, w * 0.09f, p);
        p.setColor(0xFFCFD8DC);
        c.drawCircle(w * 0.75f, h * 0.13f, w * 0.018f, p);
        c.drawCircle(w * 0.81f, h * 0.18f, w * 0.012f, p);
        // Planet with a ring
        p.setColor(0xFFFFB74D);
        c.drawCircle(w * 0.25f, h * 0.78f, w * 0.12f, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(w * 0.012f);
        p.setColor(0xFFFFE0B2);
        c.drawOval(new RectF(w * 0.07f, h * 0.755f, w * 0.43f, h * 0.805f), p);
        p.setStyle(Paint.Style.FILL);
    }

    private static void roads(Canvas c, Paint p, float w, float h) {
        c.drawColor(0xFFA5D6A7);
        float road = w * 0.2f;
        float cx = w * 0.5f, cy = h * 0.55f;
        p.setColor(0xFF616161);
        c.drawRect(cx - road / 2, 0, cx + road / 2, h, p);
        c.drawRect(0, cy - road / 2, w, cy + road / 2, p);
        // Lane markings (dashed), not across the intersection
        p.setColor(0xFFFFFFFF);
        float dash = w * 0.05f;
        for (float x = 0; x < w; x += dash * 2) {
            if (x + dash < cx - road / 2 - dash || x > cx + road / 2 + dash) {
                c.drawRect(x, cy - w * 0.006f, x + dash, cy + w * 0.006f, p);
            }
        }
        for (float y = 0; y < h; y += dash * 2) {
            if (y + dash < cy - road / 2 - dash || y > cy + road / 2 + dash) {
                c.drawRect(cx - w * 0.006f, y, cx + w * 0.006f, y + dash, p);
            }
        }
        // Zebra crossings at the intersection
        float stripe = road / 9f;
        for (int i = 0; i < 9; i += 2) {
            float x = cx - road / 2 + i * stripe;
            c.drawRect(x, cy - road / 2 - dash * 1.2f, x + stripe, cy - road / 2 - dash * 0.2f, p);
            c.drawRect(x, cy + road / 2 + dash * 0.2f, x + stripe, cy + road / 2 + dash * 1.2f, p);
        }
        // Traffic light on the corner
        float tx = cx + road / 2 + w * 0.06f, ty = cy - road / 2 - w * 0.2f;
        p.setColor(0xFF424242);
        c.drawRect(tx - w * 0.008f, ty, tx + w * 0.008f, cy - road / 2 - w * 0.01f, p);
        c.drawRoundRect(new RectF(tx - w * 0.035f, ty - w * 0.15f, tx + w * 0.035f, ty), w * 0.015f, w * 0.015f, p);
        int[] lights = {0xFFE53935, 0xFFFFB300, 0xFF43A047};
        for (int i = 0; i < 3; i++) {
            p.setColor(lights[i]);
            c.drawCircle(tx, ty - w * 0.12f + i * w * 0.045f, w * 0.017f, p);
        }
        // A few houses
        float[] houses = {0.12f, 0.8f};
        for (float hx : houses) {
            float x = w * hx, base = cy - road / 2 - w * 0.05f;
            p.setColor(0xFFFFF3E0);
            c.drawRect(x - w * 0.07f, base - w * 0.1f, x + w * 0.07f, base, p);
            p.setColor(0xFFE57373);
            android.graphics.Path roof = new android.graphics.Path();
            roof.moveTo(x - w * 0.09f, base - w * 0.1f);
            roof.lineTo(x + w * 0.09f, base - w * 0.1f);
            roof.lineTo(x, base - w * 0.18f);
            roof.close();
            c.drawPath(roof, p);
        }
    }
}
