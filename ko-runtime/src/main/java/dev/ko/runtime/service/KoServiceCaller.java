package dev.ko.runtime.service;

/**
 * Routes service-to-service calls based on deployment mode.
 * InProcessCaller for local dev / monolith, HttpServiceCaller for production.
 */
public interface KoServiceCaller {

    /**
     * Call a method on another service.
     *
     * @param service the target service name (kebab-case)
     * @param method  the HTTP method (GET, POST, etc.)
     * @param path    the endpoint path (e.g. "/hello/:name")
     * @param args    the method arguments (path params, request body)
     * @return the response object
     */
    Object call(String service, String method, String path, Object[] args);
}
