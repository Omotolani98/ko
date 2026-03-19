package dev.ko.processor.validator;

import java.util.regex.Pattern;

public final class NamingConventionValidator {

    private static final Pattern KEBAB_CASE = Pattern.compile("^[a-z][a-z0-9]*(-[a-z0-9]+)*$");

    private NamingConventionValidator() {}

    public static boolean isValidKebabCase(String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }
        return KEBAB_CASE.matcher(name).matches();
    }
}
