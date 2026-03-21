package dev.ko.test;

import dev.ko.runtime.service.KoServiceCaller;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Mock service caller for testing inter-service calls.
 * Allows stubbing responses and inspecting call history.
 *
 * <pre>{@code
 * @Autowired MockServiceClient mockServiceClient;
 *
 * @Test
 * void proxyHello_callsGreetingService() {
 *     mockServiceClient.stub("greeting-service", "GET", "/hello/world",
 *         args -> Map.of("message", "Hello, world!"));
 *
 *     var response = restTemplate.getForObject("/stats/hello/world", Map.class);
 *     assertThat(response.get("message")).isEqualTo("Hello, world!");
 *
 *     assertThat(mockServiceClient.calls("greeting-service")).hasSize(1);
 * }
 * }</pre>
 */
public class MockServiceClient implements KoServiceCaller {

    private final Map<String, Function<Object[], Object>> stubs = new ConcurrentHashMap<>();
    private final List<ServiceCall> callHistory = Collections.synchronizedList(new ArrayList<>());
    private KoServiceCaller fallback;

    /**
     * Set a fallback caller (e.g., InProcessCaller) for unstubbed calls.
     */
    public void setFallback(KoServiceCaller fallback) {
        this.fallback = fallback;
    }

    /** {@inheritDoc} */
    @Override
    public Object call(String service, String method, String path, Object[] args) {
        callHistory.add(new ServiceCall(service, method, path, args));

        String key = stubKey(service, method, path);
        Function<Object[], Object> stub = stubs.get(key);

        // Try service-level wildcard
        if (stub == null) {
            stub = stubs.get(service + ":*:*");
        }

        if (stub != null) {
            return stub.apply(args);
        }

        if (fallback != null) {
            return fallback.call(service, method, path, args);
        }

        throw new IllegalStateException(
                "No stub registered for " + method + " " + service + path
                        + ". Use mockServiceClient.stub() or setFallback().");
    }

    /**
     * Stub a specific service endpoint.
     */
    public void stub(String service, String method, String path, Function<Object[], Object> handler) {
        stubs.put(stubKey(service, method, path), handler);
    }

    /**
     * Stub a specific endpoint with a fixed return value.
     */
    public void stub(String service, String method, String path, Object fixedResponse) {
        stubs.put(stubKey(service, method, path), args -> fixedResponse);
    }

    /**
     * Stub all calls to a service with a handler.
     */
    public void stubAll(String service, Function<Object[], Object> handler) {
        stubs.put(service + ":*:*", handler);
    }

    /**
     * Returns all calls made to a specific service.
     */
    public List<ServiceCall> calls(String service) {
        return callHistory.stream()
                .filter(c -> c.service().equals(service))
                .toList();
    }

    /**
     * Returns all recorded calls.
     */
    public List<ServiceCall> allCalls() {
        return Collections.unmodifiableList(new ArrayList<>(callHistory));
    }

    /**
     * Returns the total number of calls.
     */
    public int callCount() {
        return callHistory.size();
    }

    /**
     * Clear all stubs and call history.
     */
    public void reset() {
        stubs.clear();
        callHistory.clear();
    }

    private static String stubKey(String service, String method, String path) {
        return service + ":" + method + ":" + path;
    }

    /**
     * A recorded service-to-service call.
     */
    public record ServiceCall(
            String service,
            String method,
            String path,
            Object[] args
    ) {}
}
