package com.example;

import dev.ko.annotations.KoAPI;
import dev.ko.annotations.KoCron;
import dev.ko.annotations.KoDatabase;
import dev.ko.annotations.KoService;
import dev.ko.annotations.PathParam;
import dev.ko.runtime.database.KoSQLDatabase;

@KoService("greeting-service")
public class GreetingService {

    @KoDatabase(name = "greetings")
    private KoSQLDatabase db;

    @dev.ko.annotations.KoCache(name = "greeting-cache", ttl = 600)
    private dev.ko.runtime.cache.KoCache<String, GreetingResponse> cache;

    /** Say hello to someone. */
    @KoAPI(method = "GET", path = "/hello/:name")
    public GreetingResponse hello(@PathParam("name") String name) {
        return new GreetingResponse("Hello, " + name + "!");
    }

    /** Create a personalized greeting. */
    @KoAPI(method = "POST", path = "/greetings", auth = true)
    public GreetingResponse createGreeting(CreateGreetingRequest request) {
        return new GreetingResponse("Hello, " + request.name() + "! " + request.message());
    }

    @KoCron(schedule = "0 3 * * *", name = "cleanup-old-greetings")
    public void cleanupOldGreetings() {
        // cleanup logic
    }
}
