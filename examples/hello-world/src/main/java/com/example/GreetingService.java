package com.example;

import dev.ko.annotations.KoAPI;
import dev.ko.annotations.KoBucket;
import dev.ko.annotations.KoCache;
import dev.ko.annotations.KoCron;
import dev.ko.annotations.KoDatabase;
import dev.ko.annotations.KoPubSub;
import dev.ko.annotations.KoSecret;
import dev.ko.annotations.KoService;
import dev.ko.annotations.KoSubscribe;
import dev.ko.annotations.PathParam;
import dev.ko.runtime.cache.KoCacheCluster;
import dev.ko.runtime.database.KoSQLDatabase;
import dev.ko.runtime.pubsub.KoTopic;
import dev.ko.runtime.secrets.KoSecretValue;
import dev.ko.runtime.storage.KoBucketStore;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@KoService("greeting-service")
public class GreetingService {

    @KoDatabase(name = "greetings")
    private KoSQLDatabase db;

    @KoCache(name = "greeting-cache", ttl = 600)
    private KoCacheCluster<String, GreetingResponse> cache;

    @KoPubSub(topic = "greeting-events")
    private KoTopic<GreetingEvent> events;

    @KoBucket(name = "greeting-files")
    private KoBucketStore files;

    @KoSecret("greeting-api-key")
    private KoSecretValue apiKey;

    /** Say hello to someone. */
    @KoAPI(method = "GET", path = "/hello/:name")
    public GreetingResponse hello(@PathParam("name") String name) {
        return new GreetingResponse("Hello, " + name + "!");
    }

    /** Create a greeting, persist it, publish an event, and store a file. */
    @KoAPI(method = "POST", path = "/greetings", auth = true)
    public GreetingResponse createGreeting(CreateGreetingRequest request) {
        db.exec("INSERT INTO greetings (name, message) VALUES (?, ?)",
                request.name(), request.message());

        events.publish(new GreetingEvent(request.name(), request.message()));

        // Store greeting as a text file in the bucket
        String content = "Hello, " + request.name() + "! " + request.message();
        files.upload(request.name() + ".txt", content.getBytes(StandardCharsets.UTF_8), "text/plain");

        return new GreetingResponse(content);
    }

    /** List all saved greetings. */
    @KoAPI(method = "GET", path = "/greetings")
    public List<Map<String, Object>> listGreetings() {
        return db.query("SELECT id, name, message, created_at FROM greetings ORDER BY created_at DESC");
    }

    /** Get a specific greeting by ID. */
    @KoAPI(method = "GET", path = "/greetings/:id")
    public Map<String, Object> getGreeting(@PathParam("id") String id) {
        return db.queryRow("SELECT id, name, message, created_at FROM greetings WHERE id = ?",
                Long.parseLong(id));
    }

    /** List all files in the greeting-files bucket. */
    @KoAPI(method = "GET", path = "/files")
    public List<String> listFiles() {
        return files.list();
    }

    /** Check if the API key secret is configured. */
    @KoAPI(method = "GET", path = "/secret-check")
    public Map<String, Object> checkSecret() {
        String value = apiKey.value();
        return Map.of(
                "name", apiKey.name(),
                "configured", value != null
        );
    }

    /** Handle greeting events — log them. */
    @KoSubscribe(topic = "greeting-events", name = "log-greeting")
    public void onGreetingCreated(GreetingEvent event) {
        System.out.println("Ko PubSub: Received greeting event for " + event.name() + ": " + event.message());
    }

    @KoCron(schedule = "0 3 * * *", name = "cleanup-old-greetings")
    public void cleanupOldGreetings() {
        db.exec("DELETE FROM greetings WHERE created_at < DATEADD('DAY', -30, CURRENT_TIMESTAMP)");
    }
}
