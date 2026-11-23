package com.shajitha.familyapp;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Looper;
import android.view.View;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.io.File;
import java.io.FileOutputStream;
import java.time.Duration;
import java.util.function.Consumer;

import static org.robolectric.Shadows.shadowOf;

/**
 * Draws every screen to a PNG in app/build/screenshots, in portrait, landscape and dark mode,
 * so layouts can be reviewed without an emulator. Run: ./gradlew testDebugUnitTest --tests "*ScreenshotTest*"
 */
@RunWith(AndroidJUnit4.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ScreenshotTest {

    static final Class<?>[] SCREENS = {MainActivity.class, viewCalendar.class, AddEvents.class, MealPlanner.class,
            FamAppTimer.class, ToDoList.class, CheckList.class, PhotoGallery.class,
            ActivitiesFun.class, Reading.class, ContactUs.class};

    static void capture(String prefix) {
        for (Class<?> screen : SCREENS) {
            capture(prefix, screen, null);
        }
    }

    @SuppressWarnings("unchecked")
    static <T extends Activity> void capture(String prefix, Class<?> screen, Consumer<T> setUp) {
        try (ActivityScenario<Activity> scenario = ActivityScenario.launch((Class<Activity>) screen)) {
            scenario.onActivity(activity -> {
                if (setUp != null) {
                    setUp.accept((T) activity);
                }
                shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500));
                save(activity, prefix + "_" + screen.getSimpleName());
            });
        }
    }

    static void save(Activity activity, String name) {
        View root = activity.getWindow().getDecorView();
        Bitmap bitmap = Bitmap.createBitmap(root.getWidth(), root.getHeight(), Bitmap.Config.ARGB_8888);
        root.draw(new Canvas(bitmap));
        File dir = new File("build/screenshots");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        try (FileOutputStream out = new FileOutputStream(new File(dir, name + ".png"))) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    public void portrait() {
        capture("1_portrait");
    }

    @Test
    @Config(qualifiers = "w891dp-h411dp-land-xxhdpi")
    public void landscape() {
        capture("2_landscape");
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-night-xxhdpi")
    public void dark() {
        capture("3_dark");
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp-xhdpi")
    public void smallPhone() {
        capture("4_small");
    }
}
