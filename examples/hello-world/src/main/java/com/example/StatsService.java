package com.example;

import dev.ko.annotations.KoAPI;
import dev.ko.annotations.KoService;
import dev.ko.annotations.KoServiceClient;
import dev.ko.annotations.PathParam;

import java.util.List;
import java.util.Map;

@KoService("stats-service")
public class StatsService {

    @KoServiceClient
    private GreetingServiceClient greetingClient;

    /** Get total greeting count by calling the greeting service. */
    @KoAPI(method = "GET", path = "/stats/greeting-count")
    public Map<String, Object> greetingCount() {
        List<Map<String, Object>> greetings = greetingClient.listGreetings();
        return Map.of("count", greetings.size());
    }

    /** Proxy a hello call through to the greeting service. */
    @KoAPI(method = "GET", path = "/stats/hello/:name")
    public GreetingResponse proxyHello(@PathParam("name") String name) {
        return greetingClient.hello(name);
    }
}
