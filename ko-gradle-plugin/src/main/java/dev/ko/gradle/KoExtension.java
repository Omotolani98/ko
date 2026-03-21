package dev.ko.gradle;

import org.gradle.api.provider.Property;

/**
 * Extension for the Ko Gradle plugin.
 *
 * <pre>
 * ko {
 *     appName = "my-app"
 *     version = "0.1.0-SNAPSHOT"
 * }
 * </pre>
 */
public abstract class KoExtension {

    /**
     * The application name used in the generated ko-app-model.json.
     * Defaults to the Gradle project name.
     */
    public abstract Property<String> getAppName();

    /**
     * The Ko framework version for dependency resolution.
     * Defaults to the plugin's own version.
     */
    public abstract Property<String> getVersion();
}
