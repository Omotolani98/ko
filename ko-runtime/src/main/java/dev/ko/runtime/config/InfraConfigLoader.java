package dev.ko.runtime.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Loads and resolves {@code infra-config.json}.
 *
 * <p>Resolution order for the config file path:</p>
 * <ol>
 *   <li>{@code KO_INFRA_CONFIG} environment variable</li>
 *   <li>{@code ko.infra.config} system property</li>
 *   <li>{@code ./infra-config.json} (working directory)</li>
 * </ol>
 *
 * <p>Supports {@code {"$env": "VAR_NAME"}} references in string values,
 * which are resolved to environment variables at load time.</p>
 */
public class InfraConfigLoader {

    private static final Logger log = LoggerFactory.getLogger(InfraConfigLoader.class);

    public static final String ENV_VAR = "KO_INFRA_CONFIG";
    public static final String SYSTEM_PROPERTY = "ko.infra.config";
    public static final String DEFAULT_PATH = "infra-config.json";

    /**
     * Pattern to match {@code {"$env": "VAR_NAME"}} in raw JSON.
     * Captures the variable name so it can be resolved before deserialization.
     */
    private static final Pattern ENV_REF = Pattern.compile(
            "\\{\\s*\"\\$env\"\\s*:\\s*\"([^\"]+)\"\\s*}"
    );

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    /**
     * Load the infra config, or return null if no config file is found.
     * When no config exists, the framework falls back to local/in-memory providers.
     */
    public static InfraConfig load() {
        String path = resolveConfigPath();
        if (path == null) {
            log.info("Ko: No infra-config.json found — using local development defaults");
            return InfraConfig.local();
        }
        return loadFrom(Path.of(path));
    }

    /**
     * Load infra config from a specific path.
     */
    public static InfraConfig loadFrom(Path configPath) {
        try {
            String raw = Files.readString(configPath);
            String resolved = resolveEnvRefs(raw);
            InfraConfig config = MAPPER.readValue(resolved, InfraConfig.class);

            String envType = config.metadata() != null ? config.metadata().envType() : "unknown";
            String cloud = config.metadata() != null ? config.metadata().cloud() : "local";
            log.info("Ko: Loaded infra config from {} (env={}, cloud={})",
                    configPath, envType, cloud);

            return config;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load infra-config.json from " + configPath, e);
        }
    }

    /**
     * Resolve {@code {"$env": "VAR_NAME"}} references in raw JSON.
     * Replaces the entire JSON object with the resolved environment variable value (quoted).
     * If the env var is not set, throws an error.
     */
    static String resolveEnvRefs(String json) {
        Matcher matcher = ENV_REF.matcher(json);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String varName = matcher.group(1);
            String value = System.getenv(varName);
            if (value == null) {
                log.warn("Ko: Environment variable '{}' referenced in infra-config.json is not set, using empty string", varName);
                value = "";
            }
            // Escape the value for JSON and quote it
            String escaped = escapeJson(value);
            matcher.appendReplacement(result, "\"" + Matcher.quoteReplacement(escaped) + "\"");
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static String resolveConfigPath() {
        // 1. Environment variable
        String envPath = System.getenv(ENV_VAR);
        if (envPath != null && !envPath.isBlank()) {
            if (Files.exists(Path.of(envPath))) {
                return envPath;
            }
            log.warn("Ko: KO_INFRA_CONFIG points to '{}' but file does not exist", envPath);
        }

        // 2. System property
        String sysProp = System.getProperty(SYSTEM_PROPERTY);
        if (sysProp != null && !sysProp.isBlank()) {
            if (Files.exists(Path.of(sysProp))) {
                return sysProp;
            }
            log.warn("Ko: ko.infra.config points to '{}' but file does not exist", sysProp);
        }

        // 3. Default path in working directory
        if (Files.exists(Path.of(DEFAULT_PATH))) {
            return DEFAULT_PATH;
        }

        return null;
    }

    private static String escapeJson(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
