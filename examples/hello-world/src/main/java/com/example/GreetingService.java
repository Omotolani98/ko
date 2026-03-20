package com.example;

import dev.ko.annotations.KoAPI;
import dev.ko.annotations.KoCron;
import dev.ko.annotations.KoDatabase;
import dev.ko.annotations.KoPubSub;
import dev.ko.annotations.KoService;
import dev.ko.annotations.KoSubscribe;
import dev.ko.annotations.PathParam;
import dev.ko.runtime.database.KoSQLDatabase;
import dev.ko.runtime.pubsub.KoTopic;

import java.util.List;
import java.util.Map;

@KoService("greeting-service")
public class GreetingService {

    @KoDatabase(name = "greetings")
    private KoSQLDatabase db;

    @dev.ko.annotations.KoCache(name = "greeting-cache", ttl = 600)
    private dev.ko.runtime.cache.KoCache<String, GreetingResponse> cache;

    @KoPubSub(topic = "greeting-events")
    private KoTopic<GreetingEvent> events;

    /** Say hello to someone. */
    @KoAPI(method = "GET", path = "/hello/:name")
    public GreetingResponse hello(@PathParam("name") String name) {
        return new GreetingResponse("Hello, " + name + "!");
    }

    /** Create a greeting, persist it, and publish an event. */
    @KoAPI(method = "POST", path = "/greetings", auth = true)
    public GreetingResponse createGreeting(CreateGreetingRequest request) {
        db.exec("INSERT INTO greetings (name, message) VALUES (?, ?)",
                request.name(), request.message());

        events.publish(new GreetingEvent(request.name(), request.message()));

        return new GreetingResponse("Hello, " + request.name() + "! " + request.message());
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
