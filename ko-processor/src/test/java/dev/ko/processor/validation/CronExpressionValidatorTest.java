package dev.ko.processor.validation;

import dev.ko.processor.validator.CronExpressionValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class CronExpressionValidatorTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "0 * * * *",
            "30 2 * * 1-5",
            "0 0 1 1 *",
            "*/15 * * * *",
            "0 0 * * 0",
            "0 0 * * 7",
            "5,10,15 * * * *",
            "0 3 * * *",
            "0 0 1-15 * *",
            "0 0 * * MON,WED,FRI"
    })
    void isValid_validExpressions_returnsTrue(String expression) {
        assertThat(CronExpressionValidator.isValid(expression)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "invalid",
            "* * * *",
            "60 * * * *",
            "",
            "   ",
            "not-a-cron",
            "* * * * * *",
            "-1 * * * *",
            "0 24 * * *",
            "0 * 0 * *",
            "0 * * 13 *"
    })
    void isValid_invalidExpressions_returnsFalse(String expression) {
        assertThat(CronExpressionValidator.isValid(expression)).isFalse();
    }

    @Test
    void isValid_null_returnsFalse() {
        assertThat(CronExpressionValidator.isValid(null)).isFalse();
    }

    @Test
    void isValid_stepExpression_valid() {
        assertThat(CronExpressionValidator.isValid("*/5 * * * *")).isTrue();
        assertThat(CronExpressionValidator.isValid("1-30/2 * * * *")).isTrue();
    }

    @Test
    void isValid_rangeExpression_valid() {
        assertThat(CronExpressionValidator.isValid("0 9-17 * * *")).isTrue();
    }

    @Test
    void isValid_dayNames_valid() {
        assertThat(CronExpressionValidator.isValid("0 0 * * MON")).isTrue();
        assertThat(CronExpressionValidator.isValid("0 0 * * SUN")).isTrue();
    }

    @Test
    void isValid_monthNames_valid() {
        assertThat(CronExpressionValidator.isValid("0 0 1 JAN *")).isTrue();
        assertThat(CronExpressionValidator.isValid("0 0 1 DEC *")).isTrue();
    }
}
