package com.shajitha.familyapp;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.shajitha.familyapp.data.AppDatabase;
import com.shajitha.familyapp.data.DatabaseGuard;

/**
 * Shown instead of Home when the saved information can't be opened safely (for example the backup
 * before an upgrade couldn't be made). Nothing here deletes data. This screen doesn't use the database.
 */
public class DatabaseProblemActivity extends AppCompatActivity {

    static final String EXTRA_PROBLEM = "problem";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_database_problem);
        setTitle(R.string.problem_title);

        DatabaseGuard.Problem problem = DatabaseGuard.Problem.UPGRADE_FAILED;
        try {
            problem = DatabaseGuard.Problem.valueOf(String.valueOf(getIntent().getStringExtra(EXTRA_PROBLEM)));
        } catch (IllegalArgumentException ignored) {
            // keep the general message
        }
        int reason;
        switch (problem) {
            case NO_SPACE_FOR_BACKUP:
                reason = R.string.problem_no_space;
                break;
            case BACKUP_CHECK_FAILED:
                reason = R.string.problem_backup_check;
                break;
            case NEWER_VERSION:
                reason = R.string.problem_newer;
                break;
            default:
                reason = R.string.problem_upgrade;
        }
        ((TextView) findViewById(R.id.problemReason)).setText(reason);

        findViewById(R.id.problemRetry).setOnClickListener(v -> retry());
        findViewById(R.id.problemContact).setOnClickListener(v -> startActivity(new Intent(this, ContactUs.class)));
        View restore = findViewById(R.id.problemRestore);
        // The newest safety copy (from the version the data was upgraded from).
        int backupVersion = DatabaseGuard.newestBackupVersion(this, AppDatabase.NAME, AppDatabase.VERSION);
        boolean hasBackup = backupVersion > 0;
        restore.setVisibility(hasBackup && problem != DatabaseGuard.Problem.NEWER_VERSION ? View.VISIBLE : View.GONE);
        restore.setOnClickListener(v -> new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.problem_restore_title)
                .setMessage(R.string.problem_restore_message)
                .setPositiveButton(R.string.problem_restore, (d, w) -> {
                    boolean ok = DatabaseGuard.restoreBackup(this, AppDatabase.NAME, backupVersion);
                    Toast.makeText(this, ok ? R.string.problem_restored : R.string.problem_restore_failed, Toast.LENGTH_LONG).show();
                    if (ok) {
                        retry();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show());
    }

    private void retry() {
        AppDatabase.clearProblem();
        startActivity(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));
        finish();
    }
}
