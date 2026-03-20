package dev.ko.runtime.cron;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;

/**
 * Parses a 5-field cron expression (minute hour day-of-month month day-of-week)
 * and computes the next execution time.
 */
public class CronExpression {

    private final Set<Integer> minutes;
    private final Set<Integer> hours;
    private final Set<Integer> daysOfMonth;
    private final Set<Integer> months;
    private final Set<Integer> daysOfWeek;

    public CronExpression(String expression) {
        String[] parts = expression.trim().split("\\s+");
        if (parts.length != 5) {
            throw new IllegalArgumentException(
                    "Cron expression must have 5 fields (minute hour day-of-month month day-of-week): " + expression);
        }

        this.minutes = parseField(parts[0], 0, 59);
        this.hours = parseField(parts[1], 0, 23);
        this.daysOfMonth = parseField(parts[2], 1, 31);
        this.months = parseField(parts[3], 1, 12);
        this.daysOfWeek = parseField(parts[4], 0, 6); // 0=Sunday
    }

    /**
     * Compute the next execution time after the given time.
     */
    public LocalDateTime nextAfter(LocalDateTime after) {
        LocalDateTime candidate = after.plusMinutes(1).withSecond(0).withNano(0);

        // Search up to 4 years ahead to handle edge cases
        LocalDateTime limit = after.plusYears(4);

        while (candidate.isBefore(limit)) {
            if (!months.contains(candidate.getMonthValue())) {
                candidate = candidate.plusMonths(1).withDayOfMonth(1).withHour(0).withMinute(0);
                continue;
            }
            if (!daysOfMonth.contains(candidate.getDayOfMonth())) {
                candidate = candidate.plusDays(1).withHour(0).withMinute(0);
                continue;
            }
            // Java: 1=Monday..7=Sunday → convert to cron: 0=Sunday..6=Saturday
            int cronDow = candidate.getDayOfWeek().getValue() % 7;
            if (!daysOfWeek.contains(cronDow)) {
                candidate = candidate.plusDays(1).withHour(0).withMinute(0);
                continue;
            }
            if (!hours.contains(candidate.getHour())) {
                candidate = candidate.plusHours(1).withMinute(0);
                continue;
            }
            if (!minutes.contains(candidate.getMinute())) {
                candidate = candidate.plusMinutes(1);
                continue;
            }
            return candidate;
        }

        throw new IllegalStateException("Could not find next execution time for cron expression within 4 years");
    }

    private static Set<Integer> parseField(String field, int min, int max) {
        Set<Integer> values = new TreeSet<>();

        for (String part : field.split(",")) {
            if (part.equals("*")) {
                for (int i = min; i <= max; i++) values.add(i);
            } else if (part.contains("/")) {
                String[] split = part.split("/");
                int start = split[0].equals("*") ? min : Integer.parseInt(split[0]);
                int step = Integer.parseInt(split[1]);
                for (int i = start; i <= max; i += step) values.add(i);
            } else if (part.contains("-")) {
                String[] split = part.split("-");
                int start = Integer.parseInt(split[0]);
                int end = Integer.parseInt(split[1]);
                for (int i = start; i <= end; i++) values.add(i);
            } else {
                values.add(Integer.parseInt(part));
            }
        }

        return values;
    }
}
