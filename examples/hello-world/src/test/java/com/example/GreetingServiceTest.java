package com.example;

import dev.ko.test.KoTestApp;
import dev.ko.test.MockServiceClient;
import dev.ko.test.TestDatabase;
import dev.ko.test.TestPubSub;
import dev.ko.test.TestSecretProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@KoTestApp
class GreetingServiceTest {

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    TestPubSub testPubSub;

    @Autowired
    TestDatabase testDatabase;

    @Autowired
    TestSecretProvider testSecrets;

    @Autowired
    MockServiceClient mockServiceClient;

    @BeforeEach
    void setUp() {
        testDatabase.truncate("greetings", "greetings");
        testSecrets.set("greeting-api-key", "test-key-123");
    }

    @Test
    void hello_returnsGreeting() {
        var response = restTemplate.getForEntity("/hello/world", GreetingResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("Hello, world!");
    }

    @Test
    void createGreeting_persistsAndPublishes() {
        var request = new CreateGreetingRequest("Alice", "Welcome!");

        var response = restTemplate.postForEntity("/greetings", request, GreetingResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().message()).contains("Alice");

        // Verify database
        assertThat(testDatabase.count("greetings", "greetings")).isEqualTo(1);
        var row = testDatabase.queryRow("greetings",
                "SELECT name, message FROM greetings WHERE name = ?", "Alice");
        assertThat(row.get("name")).isEqualTo("Alice");
        assertThat(row.get("message")).isEqualTo("Welcome!");

        // Verify pub/sub event
        List<GreetingEvent> events = testPubSub.published("greeting-events");
        assertThat(events).hasSize(1);
        assertThat(events.get(0).name()).isEqualTo("Alice");
    }

    @Test
    void listGreetings_returnsAll() {
        testDatabase.exec("greetings",
                "INSERT INTO greetings (name, message) VALUES (?, ?)", "Bob", "Hi!");
        testDatabase.exec("greetings",
                "INSERT INTO greetings (name, message) VALUES (?, ?)", "Carol", "Hey!");

        var response = restTemplate.getForEntity("/greetings", List.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(2);
    }

    @Test
    void secretCheck_showsConfigured() {
        var response = restTemplate.getForEntity("/secret-check", Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("configured")).isEqualTo(true);
    }

    @Test
    void notFoundEndpoint_returnsStructuredError() {
        var response = restTemplate.getForEntity("/nonexistent", Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().get("code")).isEqualTo("not_found");
    }
}
