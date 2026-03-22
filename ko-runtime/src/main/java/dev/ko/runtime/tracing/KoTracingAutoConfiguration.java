package dev.ko.runtime.tracing;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.ko.runtime.model.AppModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnResource;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Auto-configuration for Ko tracing. Enabled when KO_DASHBOARD_PORT is set
 * (i.e., running under ko run). Registers the tracing interceptor and span collector.
 */
@AutoConfiguration
@ConditionalOnResource(resources = "classpath:ko-app-model.json")
@ConditionalOnProperty(name = "ko.tracing.enabled", matchIfMissing = true)
public class KoTracingAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(KoTracingAutoConfiguration.class);

    @Bean
    public KoSpanCollector koSpanCollector(ObjectMapper objectMapper) {
        int dashboardPort = getDashboardPort();
        log.info("Ko: Tracing enabled, sending spans to dashboard on port {}", dashboardPort);
        return new KoSpanCollector(dashboardPort, objectMapper);
    }

    @Bean
    public KoTracingInterceptor koTracingInterceptor(KoSpanCollector collector, AppModel appModel) {
        // Use the first service name as the default, or app name
        String serviceName = appModel.services().isEmpty()
                ? appModel.app()
                : appModel.services().getFirst().name();
        return new KoTracingInterceptor(collector, serviceName);
    }

    @Bean
    public WebMvcConfigurer koTracingWebMvcConfigurer(KoTracingInterceptor interceptor) {
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                registry.addInterceptor(interceptor)
                        .addPathPatterns("/**")
                        .excludePathPatterns("/actuator/**", "/error");
            }
        };
    }

    private static int getDashboardPort() {
        String port = System.getenv("KO_DASHBOARD_PORT");
        if (port != null && !port.isEmpty()) {
            try {
                return Integer.parseInt(port);
            } catch (NumberFormatException e) {
                // fall through
            }
        }
        return 9400; // default
    }
}
