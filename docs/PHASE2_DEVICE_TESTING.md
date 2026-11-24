# Phase 2: testing on an emulator or phone

The automated tests cover the database upgrade, the backup, repeat dates, scheduling, notification
content and security on a simulated phone. These items need a real Android emulator or phone,
because they depend on Android itself (permission prompts, the lock screen, alarms while the phone
sleeps, restarts, and notification settings).

Use a **debug build** from Android Studio: Settings has a **"Send a test reminder in 1 minute"**
button there (it isn't in release builds).

Mark each ✅ or ❌. For any ❌, note the Android version, take a screenshot, and copy any red
error from **Logcat**.

## 1. Upgrade from Phase 1 (do this first)

1. Install the Phase 1 build (`feature/phase1-branding-navigation`) and add some data: tasks
   (one ticked), events with notes, checklist ticks, a custom food, today's lunch, a book.
2. Without uninstalling, run this Phase 2 build.
- [ ] Everything from step 1 is still there and unchanged.
- [ ] In Android Studio's **Device Explorer**, `data/data/com.shajitha.familyapp/databases/`
      contains `family-v1-backup.db` (the checked safety copy).
- [ ] Opening the app a second time is normal (no new copy, no delay).

## 2. Family members

- [ ] Family Hub → Family Members: add 2–3 people (name or nickname, avatar, colour, role).
- [ ] Edit one; archive one; it moves to "Archived" and can be restored.
- [ ] Delete permanently (only from Archived) asks first; Cancel keeps them.
- [ ] Assign a task (To-do → ✎) and an event (Monthly Schedule → event) to someone: their avatar
      and name show on the row. Delete that person: the task and event stay, shown for "Everyone".

## 3. Notification permission (Android 13+)

- [ ] Opening FamilyApp and Settings does **not** ask for notification permission.
- [ ] Settings → turn on **Reminder alerts** → Android asks once. Choose **Don't allow**: the switch
      turns back off and a note explains notifications are off.
- [ ] Turn it on again and **Allow** (on Android 13+ you may have to allow it in Android settings via
      "Open Android settings" after a refusal).

## 4. Reminder notifications

With Reminder alerts on:
- [ ] Settings → **Send a test reminder in 1 minute**. Lock the phone. After about a minute a
      notification arrives (WorkManager may delay it by a few minutes when the phone is idle).
- [ ] On the **lock screen** it only says "FamilyApp – You have a reminder" (details hidden).
- [ ] After unlocking, it shows "Test reminder · Due today" with **Done** and **Tomorrow** buttons.
- [ ] Tap **Tomorrow**: the notification goes away (it would come back tomorrow at the alert time).
- [ ] Send another test reminder, tap **Done**: the notification goes away.
- [ ] Settings → turn on **Show details on the lock screen**, send a test reminder, lock the phone:
      the title now shows on the lock screen. Turn it off again.
- [ ] Settings → **Alert time**: pick a time; it's shown and kept after closing the app.
- [ ] Android settings → Apps → FamilyApp → Notifications shows two categories: **Reminders** and
      **Timer**, each can be switched off.
- [ ] Send a test reminder, then **restart the emulator/phone** before it fires: it still arrives
      after the restart (may be a few minutes late).

## 5. Timer alerts

- [ ] Settings → turn on **Alert when the timer finishes**. On Android 14 the status says exact
      timing isn't allowed; **Allow exact timing** opens Android's "Alarms & reminders" page.
- [ ] Timer → Edit times → set Get Ready to **1** minute → start it → close FamilyApp (Home button)
      → after about 1 minute a **"Time Up!!!"** notification with "Get Ready" appears.
  - With "Alarms & reminders" allowed it should be on time; without it, up to ~10 minutes late.
- [ ] With the Timer screen open, no notification appears (the Timer rings itself).
- [ ] Start a 2-minute timer, **restart** the emulator/phone straight away: the alert still comes
      when the timer ends (or right after the restart if it ended while the phone was off).
- [ ] Pause or Reset a timer: no alert comes.

## 6. Settings and privacy

- [ ] Home ⋮ → Settings opens; everything is off by default on a fresh install.
- [ ] Reminder notes never appear in any notification.

## Known limitations (by design for Phase 2)

- There are no reminder screens yet: Phase 3 (chores, habits) and Phase 4 (renewals, appointments,
  library, vaccinations) add them. Until then, the debug test button is the only way to create a
  reminder; test reminders will show up in those screens later and can be deleted there.
- Reminder alerts use WorkManager, so Android may deliver them a few minutes late to save battery.
- The safety copy (`family-v1-backup.db`) lives inside the app: it doesn't help if the app is
  uninstalled, its storage is cleared, or the phone is lost or reset.
