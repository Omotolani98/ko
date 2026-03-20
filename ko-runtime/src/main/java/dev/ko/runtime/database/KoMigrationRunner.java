package dev.ko.runtime.database;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import javax.sql.DataSource;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Statement;
import java.util.Arrays;
import java.util.Comparator;
import java.util.stream.Collectors;

/**
 * Runs SQL migration files from the classpath on startup.
 * Migrations are discovered at: classpath:{migrationsPath}/{databaseName}/*.sql
 * Files are executed in alphabetical order (use numbered prefixes: 001_create_users.sql).
 */
public class KoMigrationRunner {

    private static final Logger log = LoggerFactory.getLogger(KoMigrationRunner.class);

    public static void run(DataSource dataSource, String databaseName, String migrationsPath) {
        String pattern = "classpath:" + migrationsPath + "/" + databaseName + "/*.sql";

        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources(pattern);

            if (resources.length == 0) {
                log.debug("Ko: No migrations found for database '{}' at {}", databaseName, pattern);
                return;
            }

            Arrays.sort(resources, Comparator.comparing(Resource::getFilename));

            try (Connection conn = dataSource.getConnection()) {
                for (Resource resource : resources) {
                    String sql = readResource(resource);
                    try (Statement stmt = conn.createStatement()) {
                        stmt.execute(sql);
                        log.info("Ko: Applied migration {}/{}", databaseName, resource.getFilename());
                    }
                }
            }

            log.info("Ko: Applied {} migration(s) for database '{}'", resources.length, databaseName);
        } catch (Exception e) {
            throw new KoDatabaseException(
                    "Failed to run migrations for database '" + databaseName + "'", e);
        }
    }

    private static String readResource(Resource resource) throws Exception {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            return reader.lines().collect(Collectors.joining("\n"));
        }
    }
}
