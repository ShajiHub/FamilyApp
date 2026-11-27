package com.shajitha.familyapp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.shajitha.familyapp.data.AppDatabase;
import com.shajitha.familyapp.data.FamilyDao;
import com.shajitha.familyapp.data.FamilyMember;
import com.shajitha.familyapp.data.TodoTask;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Security and privacy checks: what other apps can reach, logging, backups, and hostile input. */
@RunWith(AndroidJUnit4.class)
public class SecurityTest {

    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        AppDatabase.useDirectExecutorsForTests();
        AppDatabase.resetForTests();
    }

    @After
    public void tearDown() {
        AppDatabase.resetForTests();
    }

    @Test
    public void onlyTheHomeScreenIsOpenToOtherApps() throws Exception {
        PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(),
                PackageManager.GET_ACTIVITIES | PackageManager.GET_RECEIVERS | PackageManager.GET_PROVIDERS);
        for (ActivityInfo a : info.activities) {
            assertEquals(a.name, a.name.endsWith(".MainActivity"), a.exported);
        }
        for (ActivityInfo r : info.receivers) {
            if (r.name.startsWith("com.shajitha")) {
                assertFalse(r.name + " must not be exported", r.exported);
            }
        }
        if (info.providers != null) {
            for (ProviderInfo p : info.providers) {
                assertFalse("no file sharing provider is needed any more", p.name.contains("FileProvider"));
            }
        }
    }

    @Test
    public void permissionsAreOnlyTheExpectedOnes() throws Exception {
        PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), PackageManager.GET_PERMISSIONS);
        List<String> expected = Arrays.asList(
                "android.permission.POST_NOTIFICATIONS", "android.permission.SCHEDULE_EXACT_ALARM",
                "android.permission.RECEIVE_BOOT_COMPLETED",
                // added by WorkManager (free Google library) to run reminders reliably
                "android.permission.WAKE_LOCK", "android.permission.ACCESS_NETWORK_STATE",
                "android.permission.FOREGROUND_SERVICE",
                context.getPackageName() + ".DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION");
        for (String p : info.requestedPermissions) {
            assertTrue("unexpected permission " + p, expected.contains(p));
        }
        List<String> requested = Arrays.asList(info.requestedPermissions);
        assertFalse(requested.contains("android.permission.INTERNET"));
        assertFalse(requested.contains("android.permission.CAMERA"));
        assertFalse(requested.contains("android.permission.WRITE_EXTERNAL_STORAGE"));
        assertFalse(requested.contains("android.permission.READ_MEDIA_IMAGES"));
        assertFalse(requested.contains("android.permission.READ_CONTACTS"));
        assertFalse(requested.contains("android.permission.ACCESS_FINE_LOCATION"));
    }

    private static List<Path> mainSources() throws IOException {
        Path root = Paths.get("src/main/java");
        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(p -> p.toString().endsWith(".java")).collect(Collectors.toList());
        }
    }

    @Test
    public void nothingPersonalIsWrittenToLogs() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path p : mainSources()) {
            String code = new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
            if (code.matches("(?s).*\\bLog\\.[vdiwe]\\(.*") || code.contains("System.out.") || code.contains("printStackTrace")) {
                offenders.add(p.toString());
            }
        }
        assertTrue("logging found in " + offenders, offenders.isEmpty());
    }

    @Test
    public void noCameraOrPhotoCodeRemains() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path p : mainSources()) {
            String code = new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
            for (String banned : new String[]{"IMAGE_CAPTURE", "TakePicture", "PickVisualMedia", "PickMultipleVisualMedia",
                    "ExifInterface", "FileProvider", "PhotoGallery", "PhotoStore", "CameraManager", "android.hardware.camera"}) {
                if (code.contains(banned)) {
                    offenders.add(p.getFileName() + ": " + banned);
                }
            }
        }
        String manifest = new String(Files.readAllBytes(Paths.get("src/main/AndroidManifest.xml")), StandardCharsets.UTF_8);
        String gradle = new String(Files.readAllBytes(Paths.get("build.gradle")), StandardCharsets.UTF_8);
        assertFalse(manifest.contains("camera"));
        assertFalse(manifest.contains("FileProvider"));
        assertFalse(gradle.contains("exifinterface"));
        assertTrue(offenders.toString(), offenders.isEmpty());
    }

    @Test
    public void rawSqlOnlyInTheUpgradeCode() throws IOException {
        // Everything else uses Room's parameterised queries, so typed text can never become SQL.
        List<String> offenders = new ArrayList<>();
        for (Path p : mainSources()) {
            String code = new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
            String name = p.getFileName().toString();
            if ((code.contains("execSQL(") || code.contains("rawQuery(") || code.contains("RawQuery"))
                    && !name.equals("Migrations.java") && !name.equals("DatabaseGuard.java")) {
                offenders.add(name);
            }
        }
        assertTrue(offenders.toString(), offenders.isEmpty());
    }

    @Test
    public void noAdsTrackingOrNetworkLibraries() throws IOException {
        String gradle = new String(Files.readAllBytes(Paths.get("build.gradle")), StandardCharsets.UTF_8);
        for (String banned : new String[]{"admob", "play-services-ads", "firebase", "analytics", "crashlytics", "okhttp", "retrofit"}) {
            assertFalse("found " + banned, gradle.toLowerCase().contains(banned));
        }
    }

    @Test
    public void backupLeavesOutRecoveryFiles() throws IOException {
        for (String file : new String[]{"src/main/res/xml/backup_rules.xml", "src/main/res/xml/data_extraction_rules.xml"}) {
            String xml = new String(Files.readAllBytes(Paths.get(file)), StandardCharsets.UTF_8);
            assertTrue(file, xml.contains("family-v1-backup.db"));
            assertTrue(file, xml.contains("family-v2-backup.db"));
            assertTrue(file, xml.contains("androidx.work.workdb"));
            assertTrue(file, xml.contains("db_guard.xml"));
        }
    }

    @Test
    public void hostileTextIsStoredAsPlainText() {
        FamilyDao dao = AppDatabase.get(context).dao();
        String[] hostile = {"'); DROP TABLE todo_tasks;--", "Robert\"); DELETE FROM family_members; --",
                "<script>alert(1)</script>", "../../databases/family.db", "‮evil", "%s%s%s%n", "😀".repeat(50)};
        for (String text : hostile) {
            TodoTask t = new TodoTask();
            t.title = text;
            dao.insert(t);
            FamilyMember m = new FamilyMember();
            m.name = text;
            dao.insert(m);
        }
        List<String> titles = new ArrayList<>();
        for (TodoTask t : dao.tasksNow()) {
            titles.add(t.title);
        }
        assertTrue(titles.containsAll(Arrays.asList(hostile)));
        assertEquals(hostile.length, dao.allMembersNow().size());
    }

    @Test
    public void screensSurviveUnexpectedInput() {
        // Unknown hub category: closes quietly.
        Intent hub = new Intent(context, HubActivity.class).putExtra(HubActivity.EXTRA_CATEGORY, "'; DROP");
        try (ActivityScenario<HubActivity> s = ActivityScenario.launch(hub)) {
            assertNotNull(s);
        }
        Intent noExtra = new Intent(context, HubActivity.class);
        try (ActivityScenario<HubActivity> s = ActivityScenario.launch(noExtra)) {
            assertNotNull(s);
        }
        // Recovery screen with a made-up problem: shows the general message.
        Intent problem = new Intent(context, DatabaseProblemActivity.class).putExtra(DatabaseProblemActivity.EXTRA_PROBLEM, "../../x");
        try (ActivityScenario<DatabaseProblemActivity> s = ActivityScenario.launch(problem)) {
            s.onActivity(a -> assertEquals(a.getString(R.string.problem_upgrade),
                    ((android.widget.TextView) a.findViewById(R.id.problemReason)).getText().toString()));
        }
    }

    @Test
    public void memberNamesAreLimited() {
        assertEquals(40, FamilyMember.MAX_NAME_LENGTH);
        File db = context.getDatabasePath(AppDatabase.NAME);
        assertNotNull(db);
    }
}
