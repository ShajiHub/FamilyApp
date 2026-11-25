package com.shajitha.familyapp.reminders;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.shajitha.familyapp.data.AppDatabase;
import com.shajitha.familyapp.data.FamilyDao;
import com.shajitha.familyapp.data.Recurrence;
import com.shajitha.familyapp.data.Reminder;
import com.shajitha.familyapp.data.ReminderHistory;

import java.util.Calendar;
import java.util.TimeZone;

/**
 * Everything that changes a reminder, in one place, so the database and the scheduled alerts
 * always agree. Call from a background thread (for example inside {@link AppDatabase#write}).
 * Nothing here deletes a reminder except {@link #delete}, which the screens only call after the
 * user confirms; completion history is always kept.
 */
public final class Reminders {

    private Reminders() {
    }

    private static FamilyDao dao(Context context) {
        return AppDatabase.get(context).dao();
    }

    /** Thrown for input that can't be saved (empty title, invalid date, ...). */
    public static final class InvalidReminderException extends IllegalArgumentException {
        InvalidReminderException(String message) {
            super(message);
        }
    }

    /** Checks and tidies a reminder before saving. */
    static void validate(@NonNull Reminder r) {
        r.title = r.title.trim();
        if (r.title.isEmpty() || r.title.length() > Reminder.MAX_TITLE_LENGTH) {
            throw new InvalidReminderException("title");
        }
        Recurrence.Day due = Recurrence.Day.parse(r.dueDate);
        if (due == null) {
            throw new InvalidReminderException("dueDate");
        }
        if (r.notes != null) {
            r.notes = r.notes.trim();
            if (r.notes.isEmpty()) {
                r.notes = null;
            } else if (r.notes.length() > Reminder.MAX_NOTES_LENGTH) {
                throw new InvalidReminderException("notes");
            }
        }
        if (r.dueTime != null && !r.dueTime.matches("([01]\\d|2[0-3]):[0-5]\\d")) {
            throw new InvalidReminderException("dueTime");
        }
        if (r.alertTime != null && !r.alertTime.matches("([01]\\d|2[0-3]):[0-5]\\d")) {
            throw new InvalidReminderException("alertTime");
        }
        switch (r.repeatUnit) {
            case Recurrence.NONE:
            case Recurrence.DAY:
            case Recurrence.WEEK:
            case Recurrence.MONTH:
            case Recurrence.YEAR:
                break;
            default:
                throw new InvalidReminderException("repeatUnit");
        }
        r.repeatInterval = Math.max(1, Math.min(r.repeatInterval, 365));
        // Empty means "no alerts" (for example a chore or habit without a reminder).
        r.alertDays = Recurrence.formatAlertDays(Recurrence.parseAlertDays(r.alertDays));
        if (r.anchorDay <= 0) {
            r.anchorDay = due.day;
        }
    }

    /** Adds or updates a reminder and schedules its next alert. Returns its id. */
    @WorkerThread
    public static long save(@NonNull Context context, @NonNull Reminder r) {
        validate(r);
        long now = ReminderScheduler.clock.now();
        r.updatedAt = now;
        FamilyDao dao = dao(context);
        if (r.id == 0) {
            r.createdAt = now;
            r.id = dao.insert(r);
        } else {
            dao.update(r);
        }
        ReminderScheduler.schedule(context, r);
        return r.id;
    }

    /**
     * Marks the current occurrence done: it is added to the history, and a repeating reminder
     * moves on to its next date. A one-off reminder becomes Completed (it is kept, not deleted).
     */
    @WorkerThread
    public static void complete(@NonNull Context context, long id) {
        FamilyDao dao = dao(context);
        Reminder[] updated = new Reminder[1];
        AppDatabase.get(context).runInTransaction(() -> {
            Reminder r = dao.reminderNow(id);
            if (r == null || !Reminder.STATUS_ACTIVE.equals(r.status)) {
                return;
            }
            long now = ReminderScheduler.clock.now();
            ReminderHistory h = new ReminderHistory();
            h.reminderId = r.id;
            h.title = r.title;
            h.memberId = r.memberId;
            h.dueDate = r.dueDate;
            h.completedAt = now;
            dao.insert(h);

            Recurrence.Day due = Recurrence.Day.parse(r.dueDate);
            Recurrence.Day next = due == null ? null : Recurrence.next(due, r.repeatUnit, r.repeatInterval, r.anchorDay);
            r.snoozedUntil = null;
            r.updatedAt = now;
            if (next != null) {
                r.dueDate = next.toString();
            } else {
                r.status = Reminder.STATUS_COMPLETED;
                r.completedAt = now;
            }
            dao.update(r);
            updated[0] = r;
        });
        if (updated[0] != null) {
            ReminderScheduler.schedule(context, updated[0]);
        }
    }

    /** "Remind me later": the next alert comes at {@code untilMillis}; the due date doesn't change. */
    @WorkerThread
    public static void postpone(@NonNull Context context, long id, long untilMillis) {
        Reminder r = dao(context).reminderNow(id);
        if (r == null || !Reminder.STATUS_ACTIVE.equals(r.status)) {
            return;
        }
        r.snoozedUntil = untilMillis;
        r.updatedAt = ReminderScheduler.clock.now();
        dao(context).update(r);
        ReminderScheduler.schedule(context, r);
    }

    /** "Remind me tomorrow" at the reminder's alert time. */
    @WorkerThread
    public static void postponeToTomorrow(@NonNull Context context, long id) {
        Reminder r = dao(context).reminderNow(id);
        if (r == null) {
            return;
        }
        postpone(context, id, tomorrowAt(r.alertTime != null ? r.alertTime : ReminderSettings.alertTime(context),
                ReminderScheduler.clock.now()));
    }

    /** The alert time of day, tomorrow, in the phone's time zone. */
    static long tomorrowAt(String hhmm, long now) {
        Calendar c = Calendar.getInstance(TimeZone.getDefault());
        c.setTimeInMillis(now);
        Recurrence.Day today = new Recurrence.Day(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
        return Recurrence.alertMoment(today.plusDays(1), 0, hhmm, TimeZone.getDefault());
    }

    /** Moves the reminder to a new due date (and makes it active again if it was completed). */
    @WorkerThread
    public static void reschedule(@NonNull Context context, long id, @NonNull String newDueDate) {
        Recurrence.Day day = Recurrence.Day.parse(newDueDate);
        Reminder r = dao(context).reminderNow(id);
        if (r == null || day == null) {
            throw new InvalidReminderException("dueDate");
        }
        r.dueDate = day.toString();
        r.anchorDay = day.day;
        r.snoozedUntil = null;
        if (!Reminder.STATUS_ARCHIVED.equals(r.status)) {
            r.status = Reminder.STATUS_ACTIVE;
            r.completedAt = null;
        }
        r.updatedAt = ReminderScheduler.clock.now();
        dao(context).update(r);
        ReminderScheduler.schedule(context, r);
    }

    /** Hides a reminder without deleting it; it can be restored. */
    @WorkerThread
    public static void archive(@NonNull Context context, long id) {
        setStatus(context, id, Reminder.STATUS_ARCHIVED);
    }

    /** Brings an archived or completed reminder back. */
    @WorkerThread
    public static void restore(@NonNull Context context, long id) {
        setStatus(context, id, Reminder.STATUS_ACTIVE);
    }

    private static void setStatus(Context context, long id, String status) {
        Reminder r = dao(context).reminderNow(id);
        if (r == null) {
            return;
        }
        r.status = status;
        r.updatedAt = ReminderScheduler.clock.now();
        dao(context).update(r);
        ReminderScheduler.schedule(context, r); // cancels unless active
    }

    /** Permanently deletes a reminder (screens ask first). Its completion history is kept. */
    @WorkerThread
    public static void delete(@NonNull Context context, long id) {
        Reminder r = dao(context).reminderNow(id);
        ReminderScheduler.cancel(context, id);
        Notifications.cancelReminder(context, id);
        if (r != null) {
            dao(context).delete(r);
        }
    }

    /**
     * Moves just this occurrence to another day (for example "do this chore tomorrow"); the repeat
     * pattern is unchanged and nothing is marked done.
     */
    @WorkerThread
    public static void postponeOccurrence(@NonNull Context context, long id, @NonNull String newDueDate) {
        Recurrence.Day day = Recurrence.Day.parse(newDueDate);
        Reminder r = dao(context).reminderNow(id);
        if (r == null || day == null || !Reminder.STATUS_ACTIVE.equals(r.status)) {
            return;
        }
        r.dueDate = day.toString();
        r.snoozedUntil = null;
        r.updatedAt = ReminderScheduler.clock.now();
        dao(context).update(r);
        ReminderScheduler.schedule(context, r);
    }

    /**
     * Habits: records that the habit was done on {@code date} (once per day). The habit's next
     * reminder moves to the following day, so it doesn't nag after it's done.
     */
    @WorkerThread
    public static void checkIn(@NonNull Context context, long id, @NonNull String date) {
        Recurrence.Day day = Recurrence.Day.parse(date);
        FamilyDao dao = dao(context);
        Reminder[] updated = new Reminder[1];
        AppDatabase.get(context).runInTransaction(() -> {
            Reminder r = dao.reminderNow(id);
            if (r == null || day == null || dao.historyOn(id, day.toString()) != null) {
                return;
            }
            ReminderHistory h = new ReminderHistory();
            h.reminderId = r.id;
            h.title = r.title;
            h.memberId = r.memberId;
            h.dueDate = day.toString();
            h.completedAt = ReminderScheduler.clock.now();
            dao.insert(h);
            Recurrence.Day due = Recurrence.Day.parse(r.dueDate);
            if (due == null || due.compareTo(day) <= 0) {
                r.dueDate = day.plusDays(1).toString();
                r.updatedAt = h.completedAt;
                dao.update(r);
            }
            updated[0] = r;
        });
        if (updated[0] != null) {
            ReminderScheduler.schedule(context, updated[0]);
            Notifications.cancelReminder(context, id);
        }
    }

    /** Habits: un-ticks a day that was ticked by mistake (the person's own choice). */
    @WorkerThread
    public static void undoCheckIn(@NonNull Context context, long id, @NonNull String date) {
        FamilyDao dao = dao(context);
        Reminder[] updated = new Reminder[1];
        AppDatabase.get(context).runInTransaction(() -> {
            ReminderHistory h = dao.historyOn(id, date);
            Reminder r = dao.reminderNow(id);
            if (h == null || r == null) {
                return;
            }
            dao.delete(h);
            Recurrence.Day day = Recurrence.Day.parse(date);
            Recurrence.Day due = Recurrence.Day.parse(r.dueDate);
            if (day != null && due != null && due.equals(day.plusDays(1))) {
                r.dueDate = day.toString(); // due again on that day
                r.updatedAt = ReminderScheduler.clock.now();
                dao.update(r);
            }
            updated[0] = r;
        });
        if (updated[0] != null) {
            ReminderScheduler.schedule(context, updated[0]);
        }
    }

    /** Schedules every active reminder again (app start, settings change, after a restore). */
    @WorkerThread
    public static void rescheduleAll(@NonNull Context context) {
        for (Reminder r : dao(context).activeRemindersNow()) {
            ReminderScheduler.schedule(context, r);
        }
    }

    /** A new reminder with the suggested alerts for its type. */
    @NonNull
    public static Reminder create(@NonNull String title, @NonNull String type, @NonNull String dueDate, @Nullable Long memberId) {
        Reminder r = new Reminder();
        r.title = title;
        r.type = type;
        r.dueDate = dueDate;
        r.memberId = memberId;
        r.alertDays = ReminderSettings.defaultAlertDays(type);
        return r;
    }
}
