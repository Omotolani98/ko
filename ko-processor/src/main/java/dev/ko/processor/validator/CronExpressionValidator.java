package dev.ko.processor.validator;

import java.util.Set;
import java.util.regex.Pattern;

public final class CronExpressionValidator {

    private static final Set<String> MONTH_NAMES = Set.of(
            "JAN", "FEB", "MAR", "APR", "MAY", "JUN",
            "JUL", "AUG", "SEP", "OCT", "NOV", "DEC"
    );

    private static final Set<String> DAY_NAMES = Set.of(
            "MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN"
    );

    private static final Pattern STEP_PATTERN = Pattern.compile("^(\\*|\\d+(-\\d+)?)/\\d+$");

    private CronExpressionValidator() {}

    public static boolean isValid(String expression) {
        if (expression == null || expression.isBlank()) {
            return false;
        }

        String[] parts = expression.trim().split("\\s+");
        if (parts.length != 5) {
            return false;
        }

        return isValidField(parts[0], 0, 59, Set.of())       // minute
                && isValidField(parts[1], 0, 23, Set.of())    // hour
                && isValidField(parts[2], 1, 31, Set.of())    // day of month
                && isValidField(parts[3], 1, 12, MONTH_NAMES) // month
                && isValidField(parts[4], 0, 7, DAY_NAMES);   // day of week (0 and 7 = Sunday)
    }

    private static boolean isValidField(String field, int min, int max, Set<String> names) {
        if (field.equals("*")) {
            return true;
        }

        // Handle lists (comma-separated)
        if (field.contains(",")) {
            for (String part : field.split(",")) {
                if (!isValidField(part, min, max, names)) {
                    return false;
                }
            }
            return true;
        }

        // Handle steps (*/2, 1-5/2)
        if (field.contains("/")) {
            if (!STEP_PATTERN.matcher(field).matches()) {
                return false;
            }
            String[] stepParts = field.split("/");
            int step = Integer.parseInt(stepParts[1]);
            if (step < 1 || step > max) {
                return false;
            }
            if (stepParts[0].equals("*")) {
                return true;
            }
            return isValidRange(stepParts[0], min, max);
        }

        // Handle ranges (1-5)
        if (field.contains("-")) {
            return isValidRange(field, min, max);
        }

        // Handle names
        if (names.contains(field.toUpperCase())) {
            return true;
        }

        // Handle single value
        return isValidValue(field, min, max);
    }

    private static boolean isValidRange(String range, int min, int max) {
        String[] parts = range.split("-");
        if (parts.length != 2) {
            return false;
        }
        if (!isValidValue(parts[0], min, max) || !isValidValue(parts[1], min, max)) {
            return false;
        }
        int start = Integer.parseInt(parts[0]);
        int end = Integer.parseInt(parts[1]);
        return start <= end;
    }

    private static boolean isValidValue(String value, int min, int max) {
        try {
            int num = Integer.parseInt(value);
            return num >= min && num <= max;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
