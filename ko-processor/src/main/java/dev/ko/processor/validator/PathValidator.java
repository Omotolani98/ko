package dev.ko.processor.validator;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PathValidator {

    private static final Pattern PARAM_PATTERN = Pattern.compile(":([a-zA-Z][a-zA-Z0-9]*)");

    private PathValidator() {}

    public static boolean isValid(String path) {
        if (path == null || path.isEmpty()) {
            return false;
        }
        if (!path.startsWith("/")) {
            return false;
        }
        if (path.contains("//")) {
            return false;
        }
        // Validate each segment
        String[] segments = path.substring(1).split("/");
        for (String segment : segments) {
            if (segment.isEmpty() && segments.length > 1) {
                return false;
            }
            // Segment is either a param (:name) or a literal (alphanumeric + hyphens)
            if (segment.startsWith(":")) {
                if (!segment.matches(":[a-zA-Z][a-zA-Z0-9]*")) {
                    return false;
                }
            } else if (!segment.matches("[a-zA-Z0-9._-]*")) {
                return false;
            }
        }
        return true;
    }

    public static List<String> extractParams(String path) {
        List<String> params = new ArrayList<>();
        if (path == null) {
            return params;
        }
        Matcher matcher = PARAM_PATTERN.matcher(path);
        while (matcher.find()) {
            params.add(matcher.group(1));
        }
        return params;
    }
}
