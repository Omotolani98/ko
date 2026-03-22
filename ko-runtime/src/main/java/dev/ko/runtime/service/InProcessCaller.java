package dev.ko.runtime.service;

import dev.ko.runtime.model.ApiEndpointModel;
import dev.ko.runtime.model.AppModel;
import dev.ko.runtime.model.ServiceModel;
import dev.ko.runtime.tracing.KoSpan;
import dev.ko.runtime.tracing.KoSpanCollector;
import dev.ko.runtime.tracing.TracingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * In-process service caller for local dev and monolith deployments.
 * Looks up the target service bean from Spring context and invokes the method directly.
 * Zero network overhead, full type safety.
 *
 * Handle cache is built lazily on first call to avoid bean lifecycle issues.
 */
public class InProcessCaller implements KoServiceCaller {

    private static final Logger log = LoggerFactory.getLogger(InProcessCaller.class);

    private final ApplicationContext context;
    private final AppModel appModel;
    private volatile KoSpanCollector spanCollector;

    // Cache: "service:METHOD:/path" -> resolved MethodHandle
    private volatile Map<String, MethodHandle> handleCache;

    public InProcessCaller(ApplicationContext context, AppModel appModel) {
        this.context = context;
        this.appModel = appModel;
    }

    /** Set the span collector for tracing. */
    public void setSpanCollector(KoSpanCollector collector) {
        this.spanCollector = collector;
    }

    @Override
    public Object call(String service, String method, String path, Object[] args) {
        ensureCacheBuilt();

        String key = service + ":" + method + ":" + path;
        MethodHandle handle = handleCache.get(key);
        if (handle == null) {
            throw new IllegalArgumentException(
                    "No endpoint found for " + method + " " + path + " on service '" + service + "'");
        }

        TracingContext ctx = null;
        long start = 0;
        if (spanCollector != null && TracingContext.current() != null) {
            ctx = TracingContext.childSpan();
            start = System.currentTimeMillis();
        }

        String status = "OK";
        try {
            return handle.method().invoke(handle.bean(), args);
        } catch (Exception e) {
            status = "ERROR";
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            throw new RuntimeException(
                    "Failed to call " + service + " " + method + " " + path, cause);
        } finally {
            if (ctx != null) {
                long duration = System.currentTimeMillis() - start;
                Map<String, String> attrs = new LinkedHashMap<>();
                attrs.put("service.target", service);
                attrs.put("http.method", method);
                attrs.put("http.path", path);
                spanCollector.submit(new KoSpan(
                        ctx.traceId(), ctx.spanId(), ctx.parentSpanId(),
                        service, "call " + service + " " + method + " " + path, "SERVICE_CALL",
                        start, duration, status, attrs
                ));
                ctx.restore();
            }
        }
    }

    private void ensureCacheBuilt() {
        if (handleCache == null) {
            synchronized (this) {
                if (handleCache == null) {
                    handleCache = buildHandleCache();
                }
            }
        }
    }

    private Map<String, MethodHandle> buildHandleCache() {
        Map<String, MethodHandle> cache = new HashMap<>();
        for (ServiceModel service : appModel.services()) {
            Object bean = context.getBean(service.name());
            Class<?> beanClass = bean.getClass();

            for (ApiEndpointModel api : service.apis()) {
                String key = service.name() + ":" + api.method() + ":" + api.path();
                Method javaMethod = findMethod(beanClass, api.name());
                cache.put(key, new MethodHandle(bean, javaMethod));
                log.debug("Ko: Cached service call handler {} -> {}.{}()",
                        key, service.name(), api.name());
            }
        }
        return cache;
    }

    private Method findMethod(Class<?> clazz, String name) {
        for (Method m : clazz.getDeclaredMethods()) {
            if (m.getName().equals(name)) {
                m.setAccessible(true);
                return m;
            }
        }
        throw new IllegalStateException("Method not found: " + clazz.getName() + "." + name);
    }

    private record MethodHandle(Object bean, Method method) {}
}
