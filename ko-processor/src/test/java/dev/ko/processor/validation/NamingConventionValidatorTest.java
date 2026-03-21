package dev.ko.processor.validation;

import dev.ko.processor.validator.NamingConventionValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class NamingConventionValidatorTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "my-service",
            "greeting-service",
            "a",
            "service1",
            "user-auth-service",
            "api"
    })
    void isValidKebabCase_validNames_returnsTrue(String name) {
        assertThat(NamingConventionValidator.isValidKebabCase(name)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "MyService",
            "my_service",
            "my service",
            "-leading-dash",
            "trailing-dash-",
            "UPPERCASE",
            "camelCase",
            "has--double-dash",
            "1starts-with-number"
    })
    void isValidKebabCase_invalidNames_returnsFalse(String name) {
        assertThat(NamingConventionValidator.isValidKebabCase(name)).isFalse();
    }

    @Test
    void isValidKebabCase_null_returnsFalse() {
        assertThat(NamingConventionValidator.isValidKebabCase(null)).isFalse();
    }

    @Test
    void isValidKebabCase_empty_returnsFalse() {
        assertThat(NamingConventionValidator.isValidKebabCase("")).isFalse();
    }
}
