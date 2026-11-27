package com.shajitha.familyapp.whiteboard;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Where drawings live: app-private files named only after the drawing's number (never after
 * anything typed), one editable .json file and one small preview .png per drawing.
 * Files are written to a temporary file first, so an interrupted save never damages a drawing.
 */
public final class DrawingStore {

    static final int THUMB_W = 300;
    static final int THUMB_H = 400;
    static final int EXPORT_W = 1500;
    static final int EXPORT_H = 2000;
    static final String GALLERY_FOLDER = "FamilyApp";

    /** One queue for every drawing save and load, so they always happen in order. */
    static final java.util.concurrent.ExecutorService IO = java.util.concurrent.Executors.newSingleThreadExecutor();

    private DrawingStore() {
    }

    static File folder(Context context) {
        File dir = new File(context.getFilesDir(), "drawings");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        return dir;
    }

    public static File file(Context context, long id) {
        return new File(folder(context), "drawing-" + id + ".json");
    }

    public static File thumbnail(Context context, long id) {
        return new File(folder(context), "drawing-" + id + ".png");
    }

    /** Saves the drawing and its preview. */
    @WorkerThread
    public static void save(@NonNull Context context, long id, @NonNull Drawing drawing) throws IOException {
        writeAtomically(file(context, id), drawing.toJson().getBytes(StandardCharsets.UTF_8));
        Bitmap thumb = DrawingView.render(drawing, THUMB_W, THUMB_H);
        File thumbFile = thumbnail(context, id);
        File tmp = new File(thumbFile.getPath() + ".tmp");
        try (OutputStream out = new FileOutputStream(tmp)) {
            thumb.compress(Bitmap.CompressFormat.PNG, 100, out);
        } finally {
            thumb.recycle();
        }
        if (!tmp.renameTo(thumbFile)) {
            throw new IOException("Cannot save preview");
        }
    }

    private static void writeAtomically(File target, byte[] data) throws IOException {
        File tmp = new File(target.getPath() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            out.write(data);
            out.getFD().sync();
        }
        if (!tmp.renameTo(target)) {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
            throw new IOException("Cannot save drawing");
        }
    }

    /**
     * Loads a drawing. A missing file gives an empty drawing on the given template; a damaged file
     * throws (and is left untouched, never deleted).
     */
    @WorkerThread
    @NonNull
    public static Drawing load(@NonNull Context context, long id, @NonNull String template)
            throws IOException, Drawing.InvalidDrawingException {
        File f = file(context, id);
        if (!f.exists()) {
            return new Drawing(template);
        }
        if (f.length() > 20_000_000) {
            throw new Drawing.InvalidDrawingException("too large");
        }
        byte[] bytes = new byte[(int) f.length()];
        try (InputStream in = new FileInputStream(f)) {
            int read = 0;
            while (read < bytes.length) {
                int n = in.read(bytes, read, bytes.length - read);
                if (n < 0) {
                    break;
                }
                read += n;
            }
        }
        return Drawing.fromJson(new String(bytes, StandardCharsets.UTF_8));
    }

    /** Copies a drawing's files to a new id. */
    @WorkerThread
    public static void copy(@NonNull Context context, long fromId, long toId) throws IOException {
        copyFile(file(context, fromId), file(context, toId));
        if (thumbnail(context, fromId).exists()) {
            copyFile(thumbnail(context, fromId), thumbnail(context, toId));
        }
    }

    private static void copyFile(File from, File to) throws IOException {
        if (!from.exists()) {
            return;
        }
        try (InputStream in = new FileInputStream(from); OutputStream out = new FileOutputStream(to)) {
            byte[] buffer = new byte[64 * 1024];
            int n;
            while ((n = in.read(buffer)) != -1) {
                out.write(buffer, 0, n);
            }
        }
    }

    /** Removes a drawing's files (only after the person confirmed deleting it). */
    public static void delete(@NonNull Context context, long id) {
        //noinspection ResultOfMethodCallIgnored
        file(context, id).delete();
        //noinspection ResultOfMethodCallIgnored
        thumbnail(context, id).delete();
    }

    /** Saving to the phone's Gallery is supported on Android 10 and newer (no permission needed). */
    public static boolean canExport() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q;
    }

    /**
     * Saves a full-size picture of the drawing to Gallery → Pictures/FamilyApp.
     * Returns the picture's address, or null if it couldn't be saved completely.
     */
    @WorkerThread
    @Nullable
    public static Uri exportToGallery(@NonNull Context context, @NonNull Drawing drawing, @NonNull String name) {
        if (!canExport()) {
            return null;
        }
        ContentResolver resolver = context.getContentResolver();
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, safeFileName(name) + ".png");
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
        values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/" + GALLERY_FOLDER);
        values.put(MediaStore.Images.Media.IS_PENDING, 1);
        Uri item;
        try {
            item = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        } catch (RuntimeException e) {
            return null;
        }
        if (item == null) {
            return null;
        }
        Bitmap picture = DrawingView.render(drawing, EXPORT_W, EXPORT_H);
        try (OutputStream out = resolver.openOutputStream(item)) {
            if (out == null || !picture.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                throw new IOException("write failed");
            }
        } catch (IOException | RuntimeException e) {
            resolver.delete(item, null, null); // remove the half-saved gallery entry
            return null;
        } finally {
            picture.recycle();
        }
        values.clear();
        values.put(MediaStore.Images.Media.IS_PENDING, 0);
        resolver.update(item, values, null, null);
        return item;
    }

    /** A file name made only of letters, digits, spaces, dashes and underscores. */
    static String safeFileName(String name) {
        String clean = name.replaceAll("[^\\p{L}\\p{N} _-]", "").trim();
        if (clean.isEmpty()) {
            clean = "Drawing";
        }
        return "FamilyApp " + (clean.length() > 40 ? clean.substring(0, 40) : clean);
    }
}
