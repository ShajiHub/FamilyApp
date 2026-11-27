# Backup and restore: design (not built yet)

**Status:** design only, for review. Nothing in this document is in the app yet. It should be built
and tested before FamilyApp is released publicly.

## Why

Everything in FamilyApp lives only on the phone. Today, if the phone is lost, broken or reset, the
family's information comes back only if Android's own phone backup was turned on (FamilyApp allows
it, but many people don't use it, and it needs a Google account). FamilyApp must never claim it can
recover data when no backup exists.

## Goals

- A family can save a backup **file** to a place they choose, and restore it on the same or a new
  Android phone.
- No account, no cloud service, no subscription, no internet permission.
- The file is **encrypted** with a password the family chooses, so a lost USB stick or a shared
  drive doesn't expose their information.
- Restoring never silently replaces anything: the backup is checked first, and the family confirms.

## What goes in the file

| Included | Not included |
|---|---|
| The database (`family.db`): events, tasks, checklists, foods, lunches, books, members, reminders, history, drawing names | Old safety copies (`family-vN-backup.db`) |
| Whiteboard drawings (`files/drawings/*.json`); previews are rebuilt | Gallery pictures (already in the phone's Gallery) |
| Settings (alerts, times, sounds, quiet areas), timer lengths | The notification permission (Android asks again) |

## File format

`FamilyApp-backup-2027-03-14.fabackup`: a ZIP made inside the app, then encrypted.

```
manifest.json   format version, app version, database version, created time, item counts, SHA-256 of each entry
family.db       a consistent copy (WAL checkpoint first, as DatabaseGuard already does)
drawings/…      drawing JSON files
settings.json   app settings (no secrets)
```

Encryption, using only Android's built-in crypto (no extra library):

- **Key:** PBKDF2-HMAC-SHA256 from the password, with a random 16-byte salt and a high iteration
  count (tuned so it takes about 0.5 s on a mid-range phone).
- **Cipher:** AES-256-GCM with a random 12-byte IV. GCM also detects tampering and wrong passwords.
- **Header (not secret):** a magic string, the format version, the salt, the IV and the iteration count.

## Saving a backup

1. Settings → **Back up my FamilyApp** → choose a password (shown twice, with a strength hint) →
   Android's own "Save to…" screen (Storage Access Framework, `ACTION_CREATE_DOCUMENT`). The family
   picks Downloads, a USB drive, Google Drive or any other provider they already use. FamilyApp
   needs **no storage permission**.
2. The app writes and then **reads the file back** to check it, and only then says "Backup saved".
3. Settings shows "Last backup: 14 Mar 2027" and, gently, a reminder if it's been a long time (off
   by default, using the existing reminder engine).

## Restoring

1. Settings → **Restore from a backup** → Android's "Open" screen (`ACTION_OPEN_DOCUMENT`) → password.
2. FamilyApp decrypts the file into its **private cache only**, then checks everything before
   touching the family's data:
   - the GCM tag (wrong password or a damaged file stops here, with a clear message);
   - the manifest checksums and item counts;
   - `PRAGMA integrity_check` on the database;
   - its database version: older versions are upgraded with the normal, tested migrations; a newer
     version is refused ("This backup is from a newer FamilyApp; please update the app first").
   - size limits and safe file names for every ZIP entry (no `../` paths, no huge files).
3. A summary is shown: "This backup from 14 Mar 2027 has 4 checklists, 12 reminders, 3 drawings…".
4. Confirmation: **Replace what's on this phone** (with a second confirmation if this phone has
   data). Before replacing, the current data is kept as a safety copy (`family-replaced.db`, already
   excluded from Android backup), so a mistaken restore can be undone.
5. After restoring, every reminder is rescheduled (`Reminders.rescheduleAll`).

## Protecting the password

- FamilyApp never stores or sends the backup password. If it's forgotten, the backup can't be
  opened. The app says this clearly when the backup is made.
- Suggest writing it down and keeping it somewhere safe, away from the backup file.

## Tests to write

- Round trip: back up, wipe, restore: every table, drawing and setting is identical.
- Wrong password, damaged file, truncated file, a ZIP with `../` entries, an oversized entry: all
  refused, and nothing on the phone changes.
- Backups from database versions 1, 2, 3 and 4 restore correctly into the current version.
- A backup from a newer version is refused.
- Cancel at every step leaves the phone's data unchanged.

## Limits to explain to families

- A backup only contains what existed when it was made.
- The file is only as safe as the place it's kept and the password protecting it.
- There is no automatic or cloud backup; the family chooses when and where to save.
