package com.shajitha.familyapp;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.exifinterface.media.ExifInterface;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Capture Moments: take photos with the camera app, import photos from the phone, and view, share,
 * save to the phone's gallery or delete them. Photos stay in the app until the user deletes them.
 */
public class PhotoGallery extends BaseActivity {

    private static final String STATE_PENDING = "pendingPhoto";
    private static final String STATE_SHOWN = "shownPhoto";
    static final int MAX_IMPORT = 30;

    private final List<File> photos = new ArrayList<>();
    private final ExecutorService worker = Executors.newFixedThreadPool(2);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private ImageView preview;
    private TextView msg, count;
    private View actions;
    private PhotoAdapter adapter;
    private File pendingPhoto;
    private File shownPhoto;
    private File pendingExport;

    private final ActivityResultLauncher<Uri> takePicture =
            registerForActivityResult(new ActivityResultContracts.TakePicture(), this::onPhotoTaken);

    private final ActivityResultLauncher<PickVisualMediaRequest> pickPhotos =
            registerForActivityResult(new ActivityResultContracts.PickMultipleVisualMedia(MAX_IMPORT), this::onPhotosPicked);

    /** Only used on Android 9 and older, to save into the shared gallery. */
    private final ActivityResultLauncher<String> storagePermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                File photo = pendingExport;
                pendingExport = null;
                if (Boolean.TRUE.equals(granted) && photo != null) {
                    exportToGallery(photo);
                } else if (photo != null) {
                    toast(R.string.photos_export_needs_permission);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_photo_gallery);

        preview = findViewById(R.id.imageView1);
        msg = findViewById(R.id.msg);
        count = findViewById(R.id.photosCount);
        actions = findViewById(R.id.photoActions);
        RecyclerView grid = findViewById(R.id.photoGrid);

        int widthDp = getResources().getConfiguration().screenWidthDp;
        boolean land = getResources().getConfiguration().orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE;
        int columns = Math.max(3, Math.min(6, (land ? widthDp / 2 : widthDp) / 110));
        grid.setLayoutManager(new GridLayoutManager(this, columns));
        adapter = new PhotoAdapter();
        grid.setAdapter(adapter);

        findViewById(R.id.button1).setOnClickListener(v -> launchCamera());
        findViewById(R.id.importPhotos).setOnClickListener(v -> pickPhotos.launch(new PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                .build()));
        findViewById(R.id.photoShare).setOnClickListener(v -> {
            if (shownPhoto != null) {
                share(shownPhoto);
            }
        });
        findViewById(R.id.photoExport).setOnClickListener(v -> {
            if (shownPhoto != null) {
                exportToGallery(shownPhoto);
            }
        });
        findViewById(R.id.photoDelete).setOnClickListener(v -> {
            if (shownPhoto != null) {
                confirmDelete(shownPhoto);
            }
        });

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
        } else {
            showNothing();
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
        worker.shutdownNow();
    }

    private void loadPhotos() {
        photos.clear();
        photos.addAll(PhotoStore.list(this));
        adapter.notifyDataSetChanged();
        if (photos.isEmpty()) {
            count.setText(R.string.photos_empty);
        } else {
            count.setText(getResources().getQuantityString(R.plurals.photos_count, photos.size(), photos.size()));
        }
    }

    // ---------- Camera ----------

    private void launchCamera() {
        pendingPhoto = PhotoStore.newFile(this, "jpg");
        Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", pendingPhoto);
        try {
            takePicture.launch(uri);
        } catch (ActivityNotFoundException e) {
            pendingPhoto = null;
            toast(R.string.no_camera_app);
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
            // Cancelled: remove only the empty file the camera didn't fill. Saved photos are never touched.
            if (photo.length() == 0) {
                //noinspection ResultOfMethodCallIgnored
                photo.delete();
            }
        }
    }

    // ---------- Import ----------

    private void onPhotosPicked(List<Uri> uris) {
        if (uris == null || uris.isEmpty()) {
            return; // nothing chosen
        }
        worker.execute(() -> {
            List<File> imported = new ArrayList<>();
            for (Uri uri : uris) {
                File file = PhotoStore.importPhoto(this, uri);
                if (file != null) {
                    imported.add(file);
                }
            }
            int failed = uris.size() - imported.size();
            mainHandler.post(() -> {
                if (isDestroyed()) {
                    return;
                }
                loadPhotos();
                if (!imported.isEmpty()) {
                    showLarge(imported.get(0));
                }
                if (failed == 0) {
                    toast(getResources().getQuantityString(R.plurals.photos_imported, imported.size(), imported.size()));
                } else {
                    toast(getString(R.string.photos_import_some_failed, imported.size(), failed));
                }
            });
        });
    }

    // ---------- Share, save to gallery, delete ----------

    private void share(File photo) {
        Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", photo);
        Intent send = new Intent(Intent.ACTION_SEND)
                .setType(PhotoStore.mimeType(photo))
                .putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivity(Intent.createChooser(send, getString(R.string.photos_share)));
        } catch (ActivityNotFoundException e) {
            toast(R.string.photos_no_share_app);
        }
    }

    private void exportToGallery(File photo) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q && ContextCompat.checkSelfPermission(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            pendingExport = photo;
            storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            return;
        }
        worker.execute(() -> {
            boolean saved = PhotoStore.saveToGallery(this, photo);
            // Only say it was saved when the copy was actually written.
            mainHandler.post(() -> toast(saved ? R.string.photos_exported : R.string.photos_export_failed));
        });
    }

    private void confirmDelete(File photo) {
        Dialogs.confirmDelete(this, getString(R.string.photo_delete_title), getString(R.string.photo_delete_message), () -> {
            //noinspection ResultOfMethodCallIgnored
            photo.delete();
            loadPhotos();
            if (photo.equals(shownPhoto)) {
                if (photos.isEmpty()) {
                    showNothing();
                } else {
                    showLarge(photos.get(0));
                }
            }
        });
    }

    // ---------- Showing photos ----------

    private void showNothing() {
        shownPhoto = null;
        preview.setTag(null);
        preview.setImageResource(R.drawable.placeholder);
        msg.setVisibility(View.GONE);
        actions.setVisibility(View.GONE);
    }

    private void showLarge(File photo) {
        shownPhoto = photo;
        actions.setVisibility(View.VISIBLE);
        int size = Math.max(getResources().getDisplayMetrics().widthPixels, 800);
        loadInto(preview, photo, size);
    }

    private void toast(int text) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }

    private void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }

    /** Loads a scaled-down, correctly rotated copy of the photo in the background. */
    private void loadInto(ImageView view, File file, int targetSize) {
        view.setTag(file);
        worker.execute(() -> {
            Bitmap bitmap = decode(file, targetSize);
            mainHandler.post(() -> {
                if (file.equals(view.getTag()) && bitmap != null) {
                    view.setImageBitmap(bitmap);
                }
            });
        });
    }

    @Nullable
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
            degrees = new ExifInterface(file.getAbsolutePath()).getRotationDegrees();
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
            return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.photo_grid_item, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            File photo = photos.get(position);
            holder.thumb.setImageDrawable(null);
            loadInto(holder.thumb, photo, 300);
            holder.itemView.setContentDescription(getString(R.string.photos_item, position + 1, photos.size()));
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
