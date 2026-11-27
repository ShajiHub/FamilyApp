package com.shajitha.familyapp.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.GregorianCalendar;
import java.util.TimeZone;

/** Repeating dates and alert times: month ends, leap years and daylight-saving changes. */
public class RecurrenceTest {

    private static Recurrence.Day d(String s) {
        return Recurrence.Day.parse(s);
    }

    private static String nextN(String start, String unit, int interval, int n) {
        Recurrence.Day day = d(start);
        int anchor = day.day;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            day = Recurrence.next(day, unit, interval, anchor);
            sb.append(i == 0 ? "" : " ").append(day);
        }
        return sb.toString();
    }

    @Test
    public void monthlyOnThe31stKeepsComingBackToTheLastDay() {
        assertEquals("2027-02-28 2027-03-31 2027-04-30 2027-05-31 2027-06-30",
                nextN("2027-01-31", Recurrence.MONTH, 1, 5));
        // In a leap year February has 29 days.
        assertEquals("2028-02-29 2028-03-31", nextN("2028-01-31", Recurrence.MONTH, 1, 2));
        // Every 3 months from Nov 30 crosses the year end.
        assertEquals("2028-02-29 2028-05-30 2028-08-30", nextN("2027-11-30", Recurrence.MONTH, 3, 3));
    }

    @Test
    public void yearlyOnFebruary29() {
        assertEquals("2029-02-28 2030-02-28 2031-02-28 2032-02-29",
                nextN("2028-02-29", Recurrence.YEAR, 1, 4));
        assertTrue(Recurrence.isLeapYear(2000));
        assertTrue(!Recurrence.isLeapYear(2100));
    }

    @Test
    public void dailyAndWeeklyAcrossMonthAndYearEnds() {
        assertEquals("2027-12-31 2028-01-01 2028-01-02", nextN("2027-12-30", Recurrence.DAY, 1, 3));
        assertEquals("2028-01-12 2028-01-26 2028-02-09", nextN("2027-12-29", Recurrence.WEEK, 2, 3));
        // Daily across the daylight-saving change still gives every calendar day.
        assertEquals("2027-03-14 2027-03-15", nextN("2027-03-13", Recurrence.DAY, 1, 2));
        assertNull(Recurrence.next(d("2027-01-01"), Recurrence.NONE, 1, 1));
    }

    @Test
    public void invalidDatesAreRejected() {
        assertNull(d("2027-02-30"));
        assertNull(d("2027-13-01"));
        assertNull(d("27-1-1"));
        assertNull(d(null));
        assertNull(d("2027-02-29")); // not a leap year
    }

    @Test
    public void alertDaysAreCleanedUp() {
        assertEquals(Arrays.asList(30, 7, 0), Recurrence.parseAlertDays("7, 30,x,0,-5,7,9999"));
        assertEquals("30,7,1", Recurrence.formatAlertDays(Arrays.asList(1, 30, 7, 7)));
        assertEquals(Collections.emptyList(), Recurrence.parseAlertDays(null));
    }

    private static int localHour(long millis, TimeZone zone) {
        Calendar c = new GregorianCalendar(zone);
        c.setTimeInMillis(millis);
        return c.get(Calendar.HOUR_OF_DAY);
    }

    @Test
    public void alertTimeStaysTheSameLocalTimeAcrossDaylightSaving() {
        TimeZone toronto = TimeZone.getTimeZone("America/Toronto");
        // Clocks go forward on 2027-03-14 and back on 2027-11-07.
        long before = Recurrence.alertMoment(d("2027-03-13"), 0, "09:00", toronto);
        long after = Recurrence.alertMoment(d("2027-03-14"), 0, "09:00", toronto);
        assertEquals(9, localHour(before, toronto));
        assertEquals(9, localHour(after, toronto));
        assertEquals("only 23 real hours between them", 23L * 3600_000, after - before);
        long fallBefore = Recurrence.alertMoment(d("2027-11-06"), 0, "09:00", toronto);
        long fallAfter = Recurrence.alertMoment(d("2027-11-07"), 0, "09:00", toronto);
        assertEquals(25L * 3600_000, fallAfter - fallBefore);
        // "7 days before" counts calendar days, not 7 x 24 hours.
        long weekBefore = Recurrence.alertMoment(d("2027-03-17"), 7, "09:00", toronto);
        assertEquals(9, localHour(weekBefore, toronto));
    }

    @Test
    public void aTimeSkippedByTheClockChangeMovesToTheNextValidTime() {
        TimeZone toronto = TimeZone.getTimeZone("America/Toronto");
        long t = Recurrence.alertMoment(d("2027-03-14"), 0, "02:30", toronto);
        assertEquals(3, localHour(t, toronto)); // 2:30 doesn't exist that night
    }

    @Test
    public void nextAlertPicksTheNextOneAndRespectsSnooze() {
        TimeZone utc = TimeZone.getTimeZone("UTC");
        Recurrence.Day due = d("2027-04-30");
        long now = Recurrence.alertMoment(d("2027-04-10"), 0, "12:00", utc);
        long sevenBefore = Recurrence.alertMoment(due, 7, "09:00", utc);
        assertEquals(sevenBefore, Recurrence.nextAlert(due, Arrays.asList(30, 7, 0), "09:00", null, now, utc));
        // Snoozed until the 25th: the 7-days-before alert (23rd) is skipped.
        long snooze = Recurrence.alertMoment(d("2027-04-25"), 0, "09:00", utc);
        assertEquals(snooze, Recurrence.nextAlert(due, Arrays.asList(30, 7, 0), "09:00", snooze, now, utc));
        // All alerts passed: none left.
        long late = Recurrence.alertMoment(d("2027-05-01"), 0, "12:00", utc);
        assertEquals(-1, Recurrence.nextAlert(due, Arrays.asList(30, 7, 0), "09:00", null, late, utc));
    }
}
