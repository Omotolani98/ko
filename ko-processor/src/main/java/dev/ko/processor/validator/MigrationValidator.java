package dev.ko.processor.validator;

import java.util.regex.Pattern;

public final class MigrationValidator {

    private static final Pattern MIGRATION_PATH = Pattern.compile("^[a-zA-Z][a-zA-Z0-9._/-]*$");

    private MigrationValidator() {}

    public static boolean isValidPath(String path) {
        if (path == null || path.isEmpty()) {
            return false;
        }
        return MIGRATION_PATH.matcher(path).matches();
    }
}
