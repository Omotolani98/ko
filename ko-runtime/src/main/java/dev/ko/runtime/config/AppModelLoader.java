package dev.ko.runtime.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import dev.ko.runtime.model.AppModel;

import java.io.IOException;
import java.io.InputStream;

public class AppModelLoader {

    private static final String MODEL_PATH = "ko-app-model.json";

    private static volatile AppModel cached;

    public static AppModel load() {
        if (cached != null) {
            return cached;
        }
        synchronized (AppModelLoader.class) {
            if (cached != null) {
                return cached;
            }
            cached = doLoad();
            return cached;
        }
    }

    private static AppModel doLoad() {
        try (InputStream is = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream(MODEL_PATH)) {
            if (is == null) {
                throw new IllegalStateException(
                        "ko-app-model.json not found on classpath. Did you run the annotation processor?");
            }
            ObjectMapper mapper = new ObjectMapper()
                    .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            return mapper.readValue(is, AppModel.class);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load ko-app-model.json", e);
        }
    }
}
