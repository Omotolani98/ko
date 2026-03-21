package dev.ko.gradle;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.tasks.compile.JavaCompile;

/**
 * The Kọ́ Gradle plugin. Applying {@code id("io.github.omotolani98.ko")} to a project:
 * <ul>
 *   <li>Applies the {@code java} plugin if not already applied</li>
 *   <li>Adds Ko framework dependencies (ko-annotations, ko-runtime, ko-processor)</li>
 *   <li>Configures compiler args ({@code -parameters})</li>
 *   <li>Exposes a {@code ko { appName = "..." }} extension</li>
 *   <li>Registers a {@code koGenModel} task</li>
 * </ul>
 */
public class KoPlugin implements Plugin<Project> {

    private static final String KO_GROUP = "io.github.omotolani98";

    @Override
    public void apply(Project project) {
        project.getPluginManager().apply(JavaPlugin.class);

        KoExtension extension = project.getExtensions().create("ko", KoExtension.class);
        extension.getAppName().convention(project.getName());
        // Default Ko version to the plugin's own version
        String pluginVersion = KoPlugin.class.getPackage().getImplementationVersion();
        extension.getVersion().convention(pluginVersion != null ? pluginVersion : "0.1.0");

        // Add Ko dependencies after project evaluation so the extension values are resolved
        project.afterEvaluate(p -> {
            String koVersion = extension.getVersion().get();
            var deps = p.getDependencies();

            p.getConfigurations().getByName("implementation").getDependencies().addAll(
                    java.util.List.of(
                            deps.create(KO_GROUP + ":ko-annotations:" + koVersion),
                            deps.create(KO_GROUP + ":ko-runtime:" + koVersion)
                    )
            );
            p.getConfigurations().getByName("annotationProcessor").getDependencies().add(
                    deps.create(KO_GROUP + ":ko-processor:" + koVersion)
            );
        });

        // Configure compiler args
        project.getTasks().withType(JavaCompile.class).configureEach(task -> {
            task.getOptions().getCompilerArgs().add("-parameters");
            task.getOptions().setEncoding("UTF-8");

            // Pass app name to annotation processor (resolve lazily at execution time)
            task.doFirst(t -> {
                String appName = extension.getAppName().get();
                task.getOptions().getCompilerArgs().add("-Ako.app.name=" + appName);
            });
        });

        // Register koGenModel task
        project.getTasks().register("koGenModel", task -> {
            task.setGroup("ko");
            task.setDescription("Generate the Ko application model (ko-app-model.json)");
            task.dependsOn("compileJava");
        });
    }
}
