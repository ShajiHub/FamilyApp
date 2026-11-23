package com.shajitha.familyapp;

import android.content.ActivityNotFoundException;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;
import androidx.exifinterface.media.ExifInterface;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Capture Moments: take photos with the camera app and keep them in a family gallery. */
public class PhotoGallery extends BaseActivity {

    private static final String STATE_PENDING = "pendingPhoto";
    private static final String STATE_SHOWN = "shownPhoto";

    private final List<File> photos = new ArrayList<>();
    private final ExecutorService loader = Executors.newFixedThreadPool(2);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private ImageView preview;
    private TextView msg, count;
    private PhotoAdapter adapter;
    private File pendingPhoto;
    private File shownPhoto;

    private final ActivityResultLauncher<Uri> takePicture =
            registerForActivityResult(new ActivityResultContracts.TakePicture(), this::onPhotoTaken);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_photo_gallery);

        preview = findViewById(R.id.imageView1);
        msg = findViewById(R.id.msg);
        count = findViewById(R.id.photosCount);
        Button takePhoto = findViewById(R.id.button1);
        RecyclerView grid = findViewById(R.id.photoGrid);

        int columns = getResources().getConfiguration().screenWidthDp >= 600 ? 5 : 3;
        grid.setLayoutManager(new GridLayoutManager(this, columns));
        adapter = new PhotoAdapter();
        grid.setAdapter(adapter);

        takePhoto.setOnClickListener(v -> launchCamera());

        if (savedInstanceState != null) {
            String pending = savedInstanceState.getString(STATE_PENDING);
            pendingPhoto = pending == null ? null : new File(pending);
            String shown = savedInstanceState.getString(STATE_SHOWN);
            shownPhoto = shown == null ? null : new File(shown);
        }
        loadPhotos();
        if (shownPhoto != null && shownPhoto.exists()) {
            showLarge(shownPhoto);
        } else if (!photos.isEmpty()) {
            showLarge(photos.get(0));
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (pendingPhoto != null) {
            outState.putString(STATE_PENDING, pendingPhoto.getAbsolutePath());
        }
        if (shownPhoto != null) {
            outState.putString(STATE_SHOWN, shownPhoto.getAbsolutePath());
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        loader.shutdownNow();
    }

    private File photoFolder() {
        File folder = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        if (folder == null) {
            // Shared storage not available: fall back to internal storage.
            folder = new File(getFilesDir(), "Pictures");
        }
        //noinspection ResultOfMethodCallIgnored
        folder.mkdirs();
        return folder;
    }

    private void loadPhotos() {
        photos.clear();
        File[] files = photoFolder().listFiles((dir, name) -> name.endsWith(".jpg"));
        if (files != null) {
            // Newest first.
            Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
            for (File f : files) {
                if (f.length() > 0) {
                    photos.add(f);
                }
            }
        }
        adapter.notifyDataSetChanged();
        updateCount();
    }

    private void updateCount() {
        if (photos.isEmpty()) {
            count.setText(R.string.photos_empty);
        } else {
            count.setText(getResources().getQuantityString(R.plurals.photos_count, photos.size(), photos.size()));
        }
    }

    private void launchCamera() {
        String name = "FamilyApp_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".jpg";
        pendingPhoto = new File(photoFolder(), name);
        Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", pendingPhoto);
        try {
            takePicture.launch(uri);
        } catch (ActivityNotFoundException e) {
            pendingPhoto = null;
            Toast.makeText(this, R.string.no_camera_app, Toast.LENGTH_SHORT).show();
        }
    }

    private void onPhotoTaken(Boolean success) {
        File photo = pendingPhoto;
        pendingPhoto = null;
        if (photo == null) {
            return;
        }
        if (Boolean.TRUE.equals(success) && photo.length() > 0) {
            loadPhotos();
            showLarge(photo);
            msg.setVisibility(View.VISIBLE);
        } else {
            // Cancelled: remove the empty file the camera didn't fill.
            //noinspection ResultOfMethodCallIgnored
            photo.delete();
        }
    }

    private void showLarge(File photo) {
        shownPhoto = photo;
        int size = Math.max(getResources().getDisplayMetrics().widthPixels, 800);
        loadInto(preview, photo, size);
    }

    private void confirmDelete(File photo) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.photo_delete_title)
                .setPositiveButton(R.string.delete, (dialog, which) -> {
                    //noinspection ResultOfMethodCallIgnored
                    photo.delete();
                    loadPhotos();
                    if (photo.equals(shownPhoto)) {
                        shownPhoto = null;
                        msg.setVisibility(View.GONE);
                        if (photos.isEmpty()) {
                            preview.setTag(null);
                            preview.setImageResource(R.drawable.placeholder);
                        } else {
                            showLarge(photos.get(0));
                        }
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /** Loads a scaled-down, correctly rotated copy of the photo in the background. */
    private void loadInto(ImageView view, File file, int targetSize) {
        view.setTag(file);
        loader.execute(() -> {
            Bitmap bitmap = decode(file, targetSize);
            mainHandler.post(() -> {
                if (file.equals(view.getTag()) && bitmap != null) {
                    view.setImageBitmap(bitmap);
                }
            });
        });
    }

    private static Bitmap decode(File file, int targetSize) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
        int sample = 1;
        while (Math.max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= targetSize) {
            sample *= 2;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath(), options);
        if (bitmap == null) {
            return null;
        }
        int degrees = 0;
        try {
            ExifInterface exif = new ExifInterface(file.getAbsolutePath());
            degrees = exif.getRotationDegrees();
        } catch (IOException ignored) {
            // No rotation information: show as is.
        }
        if (degrees == 0) {
            return bitmap;
        }
        Matrix matrix = new Matrix();
        matrix.postRotate(degrees);
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
    }

    private class PhotoAdapter extends RecyclerView.Adapter<PhotoAdapter.Holder> {

        class Holder extends RecyclerView.ViewHolder {
            final ImageView thumb;

            Holder(View itemView) {
                super(itemView);
                thumb = itemView.findViewById(R.id.photoThumb);
            }
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.photo_grid_item, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            File photo = photos.get(position);
            holder.thumb.setImageDrawable(null);
            loadInto(holder.thumb, photo, 300);
            holder.itemView.setOnClickListener(v -> {
                msg.setVisibility(View.GONE);
                showLarge(photo);
            });
            holder.itemView.setOnLongClickListener(v -> {
                confirmDelete(photo);
                return true;
            });
        }

        @Override
        public int getItemCount() {
            return photos.size();
        }
    }
}
