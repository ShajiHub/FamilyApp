package com.shajitha.familyapp;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.webkit.MimeTypeMap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Where Capture Moments keeps photos: the app's own Pictures folder. Photos are only ever removed
 * when the user deletes them. Also copies photos in (import) and out (save to the phone's gallery).
 */
public final class PhotoStore {

    static final List<String> EXTENSIONS = Arrays.asList("jpg", "jpeg", "png", "webp", "heic", "heif");
    static final String GALLERY_FOLDER = "FamilyApp";

    private PhotoStore() {
    }

    public static File folder(Context context) {
        File folder = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        if (folder == null) {
            // Shared storage not available: fall back to internal storage.
            folder = new File(context.getFilesDir(), "Pictures");
        }
        //noinspection ResultOfMethodCallIgnored
        folder.mkdirs();
        return folder;
    }

    /** Saved photos, newest first. Empty files (a cancelled camera) are skipped. */
    public static List<File> list(Context context) {
        List<File> photos = new ArrayList<>();
        File[] files = folder(context).listFiles();
        if (files != null) {
            Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
            for (File f : files) {
                if (f.length() > 0 && EXTENSIONS.contains(extension(f.getName()))) {
                    photos.add(f);
                }
            }
        }
        return photos;
    }

    /** A new, unused file name such as FamilyApp_20261123_174502.jpg. */
    public static File newFile(Context context, String extension) {
        String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        File file = new File(folder(context), "FamilyApp_" + stamp + "." + extension);
        int n = 2;
        while (file.exists()) {
            file = new File(folder(context), "FamilyApp_" + stamp + "_" + n++ + "." + extension);
        }
        return file;
    }

    static String extension(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.US);
    }

    static String mimeType(File file) {
        String ext = extension(file.getName());
        String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
        if (mime == null) {
            mime = "png".equals(ext) ? "image/png" : "webp".equals(ext) ? "image/webp" : "image/jpeg";
        }
        return mime;
    }

    /** Copies a photo picked from the phone into the app. Returns the new file, or null if it couldn't be read. */
    @Nullable
    public static File importPhoto(Context context, Uri uri) {
        ContentResolver resolver = context.getContentResolver();
        String mime;
        try {
            mime = resolver.getType(uri);
        } catch (RuntimeException e) {
            mime = null;
        }
        String ext = mime == null ? null : MimeTypeMap.getSingleton().getExtensionFromMimeType(mime);
        if (ext == null || !EXTENSIONS.contains(ext.toLowerCase(Locale.US))) {
            ext = "jpg";
        }
        File target = newFile(context, ext.toLowerCase(Locale.US));
        try (InputStream in = resolver.openInputStream(uri);
             OutputStream out = new FileOutputStream(target)) {
            if (in == null) {
                throw new IOException("Cannot open " + uri);
            }
            copy(in, out);
            return target.length() > 0 ? target : deleteAndNull(target);
        } catch (IOException | RuntimeException e) {
            // Unreadable photo (for example a cloud photo that is offline): skip it, keep the others.
            return deleteAndNull(target);
        }
    }

    private static File deleteAndNull(File file) {
        //noinspection ResultOfMethodCallIgnored
        file.delete();
        return null;
    }

    /**
     * Saves a copy in the phone's shared gallery (Pictures/FamilyApp).
     * Returns true only when the copy was written completely.
     */
    public static boolean saveToGallery(@NonNull Context context, @NonNull File photo) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentResolver resolver = context.getContentResolver();
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME, photo.getName());
            values.put(MediaStore.Images.Media.MIME_TYPE, mimeType(photo));
            values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/" + GALLERY_FOLDER);
            values.put(MediaStore.Images.Media.IS_PENDING, 1);
            Uri item = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (item == null) {
                return false;
            }
            try (InputStream in = new FileInputStream(photo);
                 OutputStream out = resolver.openOutputStream(item)) {
                if (out == null) {
                    throw new IOException("No output stream");
                }
                copy(in, out);
            } catch (IOException | SecurityException e) {
                resolver.delete(item, null, null); // remove the half-written gallery entry, not the photo
                return false;
            }
            values.clear();
            values.put(MediaStore.Images.Media.IS_PENDING, 0);
            resolver.update(item, values, null, null);
            return true;
        }
        // Android 9 and older (needs the storage permission, asked for by the screen).
        @SuppressWarnings("deprecation")
        File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), GALLERY_FOLDER);
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        File target = new File(dir, photo.getName());
        try (InputStream in = new FileInputStream(photo); OutputStream out = new FileOutputStream(target)) {
            copy(in, out);
        } catch (IOException | SecurityException e) {
            return false;
        }
        MediaScannerConnection.scanFile(context, new String[]{target.getAbsolutePath()}, new String[]{mimeType(photo)}, null);
        return true;
    }

    private static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[64 * 1024];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
    }
}
