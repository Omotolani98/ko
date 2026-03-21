package dev.ko.processor.validation;

import dev.ko.processor.validator.PathValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class PathValidatorTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "/hello",
            "/hello/:name",
            "/greetings/:id",
            "/api/v1/users",
            "/users/:userId/posts/:postId",
            "/"
    })
    void isValid_validPaths_returnsTrue(String path) {
        assertThat(PathValidator.isValid(path)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "no-leading-slash",
            "/hello//world",
            "/invalid/:123bad"
    })
    void isValid_invalidPaths_returnsFalse(String path) {
        assertThat(PathValidator.isValid(path)).isFalse();
    }

    @Test
    void isValid_null_returnsFalse() {
        assertThat(PathValidator.isValid(null)).isFalse();
    }

    @Test
    void extractParams_pathWithParams_returnsParamNames() {
        assertThat(PathValidator.extractParams("/users/:userId/posts/:postId"))
                .containsExactly("userId", "postId");
    }

    @Test
    void extractParams_pathWithNoParams_returnsEmptyList() {
        assertThat(PathValidator.extractParams("/hello")).isEmpty();
    }

    @Test
    void extractParams_null_returnsEmptyList() {
        assertThat(PathValidator.extractParams(null)).isEmpty();
    }
}
