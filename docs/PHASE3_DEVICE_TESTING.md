# Phase 3: testing on an emulator or phone

Automated tests cover the logic and screens on a simulated phone. Please check these on your
Pixel 8 emulator (or a phone), because they depend on real touch, sound and Android itself.

Mark each ✅ or ❌; for any ❌, send a screenshot and any red error from **Logcat**.

## 1. Upgrade from Phase 2 (first)
- [ ] Install over the Phase 2 build without uninstalling: family members, tasks, events,
      checklists, menu, books and settings are all still there.
- [ ] Device Explorer: `databases/family-v2-backup.db` exists (checked safety copy).
- [ ] "Capture Moments" is gone from Home and Family Hub; nothing else is missing.

## 2. Calendar
- [ ] "Calendar Events" is no longer on Family Planner; Monthly Schedule is.
- [ ] On an event, the calendar icon (**Also add to phone calendar**) opens the phone's calendar
      app with the title, date, time and notes filled in. Cancel there: FamilyApp still has it.

## 3. Chores
- [ ] Daily Life → Chores: tap an idea (e.g. "Make the bed"), choose a person, Every week,
      turn on "Remind me" and pick a time. It appears under Today.
- [ ] Tick it: the star count for that person goes up; a weekly chore moves to Coming up.
- [ ] The clock button ("Do it tomorrow") moves a chore to tomorrow.
- [ ] Filter chips show one person's chores. Edit and delete (asks first) work.
- [ ] With Reminder alerts on (Settings), a chore reminder arrives at its time.

## 4. Healthy Habits
- [ ] Add a habit from an idea. Tick today: the dot for today fills and "1 of the last 7 days" shows.
- [ ] Untick it (a mistake): the dot empties.
- [ ] Home's Today card shows "x of y habits done today" and the chores line.

## 5. Checklists
- [ ] Each list has an "Add to …" field at the bottom: type and press Enter (or +); the item is
      added and the field clears. Empty text is ignored.

## 6. Sounds
- [ ] Settings → Sounds → **Play the chime**: a short, soft two-note chime.
- [ ] Timer chime off → a finished timer is silent; on → the chime plays.
- [ ] Reminder chime off → test reminders arrive quietly.
- [ ] Android settings → FamilyApp → Notifications: Reminders, Reminders (quiet), Timer and
      Timer (quiet) categories.

## 7. Creative Whiteboard
- [ ] Hobbies & Business → Creative Whiteboard → New drawing → pick each template in turn
      (Blank, Garden, Nature, Space, Roads); each background looks right.
- [ ] Draw with the pencil and brush; try several colours (including white on a coloured
      template) and sizes; it feels smooth with a finger or mouse.
- [ ] Eraser removes drawing but not the background scene.
- [ ] Line, rectangle, circle and star; with **Fill** on, shapes are filled.
- [ ] Undo and Redo (top bar) step back and forward; Clear (menu) asks first and can be undone.
- [ ] Rotate the emulator while drawing: the drawing and Undo history are kept.
- [ ] Press Back: the drawing appears in the gallery with a preview. Reopen it and keep drawing.
- [ ] Rename, duplicate and delete (asks first) from the gallery.
- [ ] Menu → **Save to Gallery**: "Saved to your Gallery"; the picture appears in the Photos app
      under Pictures/FamilyApp. Deleting the drawing in FamilyApp leaves that picture.

## Known limitations
- Moving and resizing objects after drawing, and editable sticker-style objects (cars, trees,
  flowers), are planned for a later phase; the starter scenes are backgrounds.
- Save to Gallery needs Android 10 or newer (the menu item is hidden on older phones).
