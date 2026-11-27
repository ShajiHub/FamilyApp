# Testing FamilyApp on a device or emulator

The automated tests (`./gradlew testDebugUnitTest`) run every screen on a simulated phone. They cannot use the real email app, calendar app or notification sound, and they cannot show how things feel to use. Please check these on an Android Studio emulator (Pixel 8, API 35, Google Play image) or a real phone.

Mark each item ✅ or ❌. For any ❌, note what you did, take a screenshot, and copy any red error from **Logcat**.

## 0. Upgrading from the previous version (do this first, once)

If the previous version (1.1) is installed with some data in it:

- [ ] Install this version over it, without uninstalling.
- [ ] Your to-do tasks, checklist ticks, schedule plans, today's lunch and reading entries are all still there.

## 1. Look and feel

- [ ] Home: header picture, greeting, Today summary and six area cards; text is clear, nothing is cut off.
- [ ] Every screen uses the same font, colours and button style.
- [ ] The Back arrow in the title bar returns to the previous screen on every screen.
- [ ] Rotate each screen to landscape: nothing overlaps or is cut off.
- [ ] Dark mode (Settings → Display → Dark theme): all text readable on every screen.
- [ ] Larger text (Settings → Display → Font size → largest): screens still usable.

## 2. Monthly Schedule

- [ ] Add an event with a title, a time (time picker opens) and notes.
- [ ] Add an all-day event (no time). Events are listed in time order.
- [ ] Edit an event; clear its time with the ✕ in the time field.
- [ ] Delete an event: Cancel keeps it, Delete removes it.
- [ ] The line under the date shows how many events are in the month and on which days.

## 3. Also add to phone calendar

- [ ] On an event in Monthly Schedule, the calendar button ("Also add to phone calendar") opens the phone's calendar app with the event's title, date, time and notes filled in. (A new emulator may ask you to set up an account first.)

## 4. Meal Planner

- [ ] Spin the wheel: it slows down smoothly and the pointer stops on the food named in "Today's lunch".
- [ ] Choose a food from the dropdown: the wheel turns to it.
- [ ] Add a food, rename a food, delete a food (asks first). The wheel and dropdown update straight away.
- [ ] Add up to 16 foods: labels stay readable (long names end with "…").
- [ ] Delete down to 1 food: Spin is disabled with "Add at least 2 foods to spin".

## 5. Timer

- [ ] Pressing Start or Reset before choosing a routine does not crash.
- [ ] Start, Pause, Resume and Reset work; the ring empties as time passes.
- [ ] "Edit times" changes a routine's minutes; the new times are kept after closing the app.
- [ ] Start a timer, close the app completely (swipe it away from Recents), reopen after a minute: still counting from the right time.
- [ ] Let a short timer (set Get Ready to 1 minute) finish while the screen is open: "Time Up!!!" and a sound.
- [ ] Known limitation: no notification is shown if the timer finishes while the app is closed. Reopening shows "Time Up!!!".

## 6. To-do List

- [ ] Add tasks (Add button and keyboard Done). Empty text shows an error.
- [ ] Tick a task: it is crossed out and moves to the bottom; it is **not** deleted.
- [ ] Edit and delete a task (delete asks first).
- [ ] "Clear completed" asks first, then removes only ticked tasks.

## 7. Checklists and Reminders

See `docs/PHASE4_DEVICE_TESTING.md`.

## 8. Creative Whiteboard

See `docs/PHASE3_DEVICE_TESTING.md`.

## 9. Kids Activities and Reading Log

- [ ] Indoor and Outdoor ideas change each tap and show their category.
- [ ] Reading Log: add a book with total pages; "Update" pages read; the progress bar and percentage update; at 100% it shows "Finished!".
- [ ] Pages read larger than the total is refused.
- [ ] Edit and delete a book (delete asks first). Totals at the top update.

## 10. Contact Us

- [ ] Send with empty fields: each field shows what is missing.
- [ ] A wrong email address (e.g. "abc") is refused.
- [ ] With everything filled in, Send opens the email app with the subject and message filled in. FamilyApp says the email app is open; it does not claim the message was sent.
- [ ] Leave the screen with an unsent message and come back: the message is still there. "Clear" asks first.
- [ ] The two slideshows change every 1.5 seconds.

## 11. Data is never lost

- [ ] After entering data on every screen, close the app completely and reopen it: everything is still there.
- [ ] Restart the phone/emulator and reopen: everything is still there.
- [ ] Turn on Airplane mode: every feature still works (except sharing/sending, which need other apps).
