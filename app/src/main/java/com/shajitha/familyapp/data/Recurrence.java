package com.shajitha.familyapp.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Date rules for repeating reminders, kept separate from Android so they can be tested exactly.
 *
 * <p>Due dates are calendar days ("2027-01-31"), not moments in time, so daylight-saving changes
 * never move them. Alert moments are worked out in the phone's own time zone at the moment of
 * scheduling, so "9:00" stays 9:00 local time before and after a clock change.
 */
public final class Recurrence {

    public static final String NONE = "NONE";
    public static final String DAY = "DAY";
    public static final String WEEK = "WEEK";
    public static final String MONTH = "MONTH";
    public static final String YEAR = "YEAR";

    private Recurrence() {
    }

    /** A calendar day. */
    public static final class Day {
        public final int year, month, day; // month 1-12

        public Day(int year, int month, int day) {
            this.year = year;
            this.month = month;
            this.day = day;
        }

        /** Parses yyyy-MM-dd; returns null if the text isn't a real date. */
        @Nullable
        public static Day parse(@Nullable String text) {
            if (text == null || !text.matches("\\d{4}-\\d{2}-\\d{2}")) {
                return null;
            }
            int y = Integer.parseInt(text.substring(0, 4));
            int m = Integer.parseInt(text.substring(5, 7));
            int d = Integer.parseInt(text.substring(8, 10));
            if (m < 1 || m > 12 || d < 1 || d > lengthOfMonth(y, m)) {
                return null;
            }
            return new Day(y, m, d);
        }

        @NonNull
        @Override
        public String toString() {
            return String.format(Locale.US, "%04d-%02d-%02d", year, month, day);
        }

        /** Days added using a time-zone-free calendar, so clock changes can't shift the date. */
        public Day plusDays(int days) {
            Calendar c = utc();
            c.set(year, month - 1, day);
            c.add(Calendar.DAY_OF_MONTH, days);
            return new Day(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
        }

        /** Months added, keeping the anchor day where the month allows it (Jan 31 + 1 month = Feb 28/29). */
        public Day plusMonths(int months, int anchorDay) {
            int total = (year * 12 + (month - 1)) + months;
            int y = total / 12;
            int m = total % 12 + 1;
            return new Day(y, m, Math.min(anchorDay, lengthOfMonth(y, m)));
        }

        public int compareTo(Day other) {
            return toString().compareTo(other.toString());
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Day && o.toString().equals(toString());
        }

        @Override
        public int hashCode() {
            return toString().hashCode();
        }
    }

    static Calendar utc() {
        Calendar c = new GregorianCalendar(TimeZone.getTimeZone("UTC"), Locale.US);
        c.clear();
        return c;
    }

    public static boolean isLeapYear(int year) {
        return (year % 4 == 0 && year % 100 != 0) || year % 400 == 0;
    }

    public static int lengthOfMonth(int year, int month) {
        switch (month) {
            case 2:
                return isLeapYear(year) ? 29 : 28;
            case 4:
            case 6:
            case 9:
            case 11:
                return 30;
            default:
                return 31;
        }
    }

    /**
     * The next due date after {@code due} for a repeating reminder, or null if it doesn't repeat.
     * {@code anchorDay} is the day of the month the reminder was originally set for.
     */
    @Nullable
    public static Day next(@NonNull Day due, @NonNull String unit, int interval, int anchorDay) {
        int n = Math.max(1, interval);
        int anchor = anchorDay > 0 ? anchorDay : due.day;
        switch (unit) {
            case DAY:
                return due.plusDays(n);
            case WEEK:
                return due.plusDays(7 * n);
            case MONTH:
                return due.plusMonths(n, anchor);
            case YEAR:
                return due.plusMonths(12 * n, anchor);
            default:
                return null;
        }
    }

    /** Parses "30,7,0" into [30, 7, 0]; ignores anything that isn't 0-366, removes repeats. */
    public static List<Integer> parseAlertDays(@Nullable String text) {
        List<Integer> days = new ArrayList<>();
        if (text != null) {
            for (String part : text.split(",")) {
                try {
                    int d = Integer.parseInt(part.trim());
                    if (d >= 0 && d <= 366 && !days.contains(d)) {
                        days.add(d);
                    }
                } catch (NumberFormatException ignored) {
                    // skip
                }
            }
        }
        Collections.sort(days, Collections.reverseOrder());
        return days;
    }

    /** "30,7,0" from a list of days. */
    public static String formatAlertDays(List<Integer> days) {
        List<Integer> clean = parseAlertDays(joinRaw(days));
        return joinRaw(clean);
    }

    private static String joinRaw(List<Integer> days) {
        StringBuilder sb = new StringBuilder();
        for (Integer d : days) {
            sb.append(sb.length() == 0 ? "" : ",").append(d);
        }
        return sb.toString();
    }

    /** Parses HH:mm into {hour, minute}; falls back to 9:00. */
    public static int[] parseTime(@Nullable String hhmm) {
        if (hhmm != null && hhmm.matches("\\d{2}:\\d{2}")) {
            int h = Integer.parseInt(hhmm.substring(0, 2));
            int m = Integer.parseInt(hhmm.substring(3, 5));
            if (h < 24 && m < 60) {
                return new int[]{h, m};
            }
        }
        return new int[]{9, 0};
    }

    /**
     * The moment to alert: {@code daysBefore} days before {@code due}, at {@code hhmm} local time in
     * {@code zone}. If that time doesn't exist (the hour skipped when clocks go forward), the
     * next valid moment is used.
     */
    public static long alertMoment(@NonNull Day due, int daysBefore, @Nullable String hhmm, @NonNull TimeZone zone) {
        Day day = due.plusDays(-daysBefore);
        int[] t = parseTime(hhmm);
        Calendar c = new GregorianCalendar(zone, Locale.US);
        c.clear();
        c.setLenient(true);
        c.set(day.year, day.month - 1, day.day, t[0], t[1], 0);
        return c.getTimeInMillis();
    }

    /**
     * The next alert moment after {@code now} for a reminder, or -1 if none is left.
     * A snooze ("remind me later") replaces earlier alerts until it has passed.
     */
    public static long nextAlert(@NonNull Day due, @NonNull List<Integer> alertDays, @Nullable String hhmm,
                                 @Nullable Long snoozedUntil, long now, @NonNull TimeZone zone) {
        long best = -1;
        for (int days : alertDays) {
            long at = alertMoment(due, days, hhmm, zone);
            if (at > now && (snoozedUntil == null || at >= snoozedUntil) && (best < 0 || at < best)) {
                best = at;
            }
        }
        if (snoozedUntil != null && snoozedUntil > now && (best < 0 || snoozedUntil < best)) {
            best = snoozedUntil;
        }
        return best;
    }
}
