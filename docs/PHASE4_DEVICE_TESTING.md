# Phase 4: testing on an emulator or phone

The automated tests cover the database upgrade, checklists, reminder templates, actions, privacy
warnings, wording, Home and Schedule on a simulated phone. These checks need a real emulator
(Pixel 8, API 35) or phone, because they depend on Android itself: notifications, the lock screen,
date pickers, alarms over time and how things feel to use.

Mark each ✅ or ❌. For any ❌, note what you did, take a screenshot and copy any red error from
**Logcat**.

## 0. Upgrade from Phase 3 (do this first)

1. Keep the Phase 3 build installed with some data: tick a few Winter and Summer items, add an item
   of your own, make a list of your own (Phase 3 "New checklist"), add chores, habits and a drawing.
2. Without uninstalling, run this Phase 4 build.
- [ ] Checklists shows Winter, Summer, First-aid kit and your own list, each with "x of y completed".
- [ ] Every tick and every item you added is still there.
- [ ] Chores, habits, drawings, events, tasks, books and family members are unchanged.
- [ ] **Device Explorer** → `data/data/com.shajitha.familyapp/databases/` has `family-v3-backup.db`.

## 1. Smart Checklists

- [ ] Open Winter: clean tick boxes, no Edit/Delete buttons beside each item.
- [ ] ⋮ on an item (or press and hold) → Edit: change the text and add a note such as "2 pairs";
      the note shows under the item.
- [ ] ⋮ → Delete asks first; Cancel keeps it.
- [ ] "Add to Winter" adds items (keyboard Done works too).
- [ ] Title-bar ⋮ → Start again: asks first, unticks all, keeps every item.
- [ ] Duplicate → name it "Winter – Kid 1", choose a family member → a new list, unticked.
- [ ] Archive → it moves under "Archived"; open it → Restore.
- [ ] Delete checklist asks first, and explains Archive as the gentler option.
- [ ] Checklist Templates (Smart Checklists area): 12 templates plus a blank one. Back to school
      for a child creates that child's own copy; the template doesn't change.
- [ ] Rotate while a list is open: nothing is lost.

## 2. Reminders & Renewals

With **Settings → Reminder alerts** on:
- [ ] Add a reminder → Documents & cards → Passport renewal: the form shows only title, date,
      time (optional), repeat, for, and "Send me a reminder". **More options** shows the alert days
      (about 6 months, about 3 months and 30 days before), alert time, kind and a note.
- [ ] Type a long number such as "1234 5678" in the title: FamilyApp asks whether it's an ID or
      account number. "Change it" keeps the form open.
- [ ] Library → Return a library book, due in 2 days → **Returned** moves it to "Show done and
      archived". Add another, then ⋮ → **Renewed** asks for the new return date.
- [ ] Health card renewal → **Renewed** asks for the new expiry date; ⋮ → History shows the old date.
- [ ] Dental visit tomorrow at a time: Done, Remind me tomorrow, Choose another date, Edit,
      Archive, Restore and Delete (asks first) all work.
- [ ] A reminder whose date has passed is listed under **"Whenever you're ready"**, not "overdue".
- [ ] Filter chips show one area at a time with its short note (for example Home & safety's
      safety note, Bills' "never asks for bank or card details").
- [ ] Family Planner → Library, Family Hub → Family Time and Hobbies → Hobby Reminders open the
      screen filtered to that area.

## 3. Notifications

- [ ] Make a reminder for today with "On the day" at a time 2 minutes from now (More options →
      alert time). Lock the phone: the lock screen says only "A little reminder from FamilyApp".
- [ ] Unlocked, the notification shows the title and "Today", with Done and Tomorrow.
- [ ] A past date says "Whenever you're ready"; nothing says "overdue", "urgent" or "missed".
- [ ] Settings → **Reminder areas** → turn off Bills & budget → a Bills reminder sends no
      notification but is still listed. Turn it back on.
- [ ] A reminder with "Send me a reminder" off is listed with "No alert" and never notifies.
- [ ] Restart the emulator before a reminder fires: it still arrives (may be a few minutes late).

## 4. Home and Monthly Schedule

- [ ] Home's Today card shows one line, "N reminders coming up · first: …", only when something is
      due within a week (or has passed). Tapping it opens Reminders & Renewals.
- [ ] Monthly Schedule: a day with a reminder lists it under "Reminders on this day".

## 5. Look and feel

- [ ] Checklists, a checklist, Templates and Reminders in landscape, dark mode and largest font.
- [ ] About → Your privacy explains what's stored and that a lost phone can't be restored without
      Android's own backup.

## Known limitations (Phase 4)

- **Backup and restore** inside FamilyApp is designed (`docs/BACKUP_RESTORE_DESIGN.md`) but not
  built yet. Until then, only Android's own phone backup can bring data back after a lost phone.
- Alerts go out once per chosen time and are never repeated for a date that has passed.
- "About 6 months" and "about 3 months" before are 180 and 90 days.
- Appointment alerts are by day (the day before, or the morning of), not "2 hours before".
- Reminders use WorkManager, so Android may deliver them a few minutes late to save battery.
