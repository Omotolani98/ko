package dev.ko.runtime.tracing;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Spring MVC interceptor that creates root tracing spans for every
 * incoming HTTP request to Ko-registered endpoints.
 *
 * Creates a span on preHandle, completes it on afterCompletion,
 * and submits it to the {@link KoSpanCollector}.
 */
public class KoTracingInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(KoTracingInterceptor.class);
    private static final String START_TIME_ATTR = "ko.trace.startTime";

    private final KoSpanCollector collector;
    private final String serviceName;

    public KoTracingInterceptor(KoSpanCollector collector, String serviceName) {
        this.collector = collector;
        this.serviceName = serviceName;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        TracingContext ctx = TracingContext.newTrace();
        request.setAttribute(START_TIME_ATTR, System.currentTimeMillis());
        log.debug("Ko: Trace started [{}] {} {}", ctx.traceId(), request.getMethod(), request.getRequestURI());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        TracingContext ctx = TracingContext.current();
        if (ctx == null) return;

        Long startTime = (Long) request.getAttribute(START_TIME_ATTR);
        if (startTime == null) return;

        long duration = System.currentTimeMillis() - startTime;
        String method = request.getMethod();
        String path = request.getRequestURI();
        String operation = method + " " + path;
        String status = ex != null || response.getStatus() >= 400 ? "ERROR" : "OK";

        Map<String, String> attributes = new LinkedHashMap<>();
        attributes.put("http.method", method);
        attributes.put("http.path", path);
        attributes.put("http.status_code", String.valueOf(response.getStatus()));
        if (request.getQueryString() != null) {
            attributes.put("http.query", request.getQueryString());
        }

        KoSpan span = new KoSpan(
                ctx.traceId(),
                ctx.spanId(),
                ctx.parentSpanId(),
                serviceName,
                operation,
                "API",
                startTime,
                duration,
                status,
                attributes
        );

        collector.submit(span);
        TracingContext.clear();
        log.debug("Ko: Trace completed [{}] {} {} ({}ms, {})", ctx.traceId(), method, path, duration, status);
    }
}
