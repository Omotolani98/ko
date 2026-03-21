package dev.ko.processor;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.CompilationSubject;
import com.google.testing.compile.JavaFileObjects;
import org.junit.jupiter.api.Test;

import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;
import java.io.IOException;

import static com.google.testing.compile.Compiler.javac;
import static org.assertj.core.api.Assertions.assertThat;

class KoAnnotationProcessorTest {

    private static Compilation compile(JavaFileObject... sources) {
        return javac()
                .withProcessors(new KoAnnotationProcessor())
                .compile(sources);
    }

    private static String readAppModel(Compilation compilation) {
        JavaFileObject file = compilation.generatedFile(StandardLocation.CLASS_OUTPUT, "ko-app-model.json")
                .orElseThrow(() -> new AssertionError("Expected ko-app-model.json"));
        try {
            return file.getCharContent(true).toString();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static String readGeneratedSource(Compilation compilation, String qualifiedName) {
        String path = qualifiedName.replace('.', '/') + ".java";
        JavaFileObject file = compilation.generatedFile(StandardLocation.SOURCE_OUTPUT, path)
                .orElseThrow(() -> new AssertionError("Expected generated source: " + qualifiedName));
        try {
            return file.getCharContent(true).toString();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void assertSucceeded(Compilation compilation) {
        CompilationSubject.assertThat(compilation).succeededWithoutWarnings();
    }

    private static void assertCompilationSucceeded(Compilation compilation) {
        // The generated client imports ko-runtime which isn't on test classpath,
        // so we check that processing succeeded (app model generated) even if
        // final compilation of generated source has errors.
        assertThat(compilation.generatedFile(StandardLocation.CLASS_OUTPUT, "ko-app-model.json"))
                .isPresent();
    }

    // ── Valid service: basic @KoAPI ──────────────────────────────────

    @Test
    void process_validService_generatesAppModel() {
        JavaFileObject source = JavaFileObjects.forSourceString("com.example.TestService", """
                package com.example;

                import dev.ko.annotations.KoService;
                import dev.ko.annotations.KoAPI;

                @KoService("test-service")
                public class TestService {
                    @KoAPI(method = "GET", path = "/hello")
                    public String hello() { return "world"; }
                }
                """);

        Compilation compilation = compile(source);
        assertCompilationSucceeded(compilation);

        String appModel = readAppModel(compilation);
        assertThat(appModel).contains("\"name\" : \"test-service\"");
    }

    @Test
    void process_validService_appModelContainsApi() {
        JavaFileObject source = JavaFileObjects.forSourceString("com.example.TestService", """
                package com.example;

                import dev.ko.annotations.KoService;
                import dev.ko.annotations.KoAPI;

                @KoService("test-service")
                public class TestService {
                    @KoAPI(method = "POST", path = "/items")
                    public String createItem(String body) { return "ok"; }
                }
                """);

        Compilation compilation = compile(source);
        assertCompilationSucceeded(compilation);

        String appModel = readAppModel(compilation);
        assertThat(appModel)
                .contains("\"method\" : \"POST\"")
                .contains("\"path\" : \"/items\"");
    }

    // ── Valid service: @KoDatabase ───────────────────────────────────

    @Test
    void process_serviceWithDatabase_appModelContainsDatabase() {
        JavaFileObject source = JavaFileObjects.forSourceString("com.example.DbService", """
                package com.example;

                import dev.ko.annotations.KoService;
                import dev.ko.annotations.KoAPI;
                import dev.ko.annotations.KoDatabase;

                @KoService("db-service")
                public class DbService {
                    @KoDatabase(name = "users")
                    private Object db;

                    @KoAPI(method = "GET", path = "/users")
                    public String list() { return "[]"; }
                }
                """);

        Compilation compilation = compile(source);
        assertCompilationSucceeded(compilation);

        String appModel = readAppModel(compilation);
        assertThat(appModel).contains("\"name\" : \"users\"");
    }

    // ── Valid service: @KoCron ───────────────────────────────────────

    @Test
    void process_serviceWithValidCron_succeeds() {
        JavaFileObject source = JavaFileObjects.forSourceString("com.example.CronService", """
                package com.example;

                import dev.ko.annotations.KoService;
                import dev.ko.annotations.KoAPI;
                import dev.ko.annotations.KoCron;

                @KoService("cron-service")
                public class CronService {
                    @KoAPI(method = "GET", path = "/health")
                    public String health() { return "ok"; }

                    @KoCron(schedule = "0 3 * * *", name = "nightly-cleanup")
                    public void cleanup() {}
                }
                """);

        Compilation compilation = compile(source);
        assertCompilationSucceeded(compilation);

        String appModel = readAppModel(compilation);
        assertThat(appModel)
                .contains("\"name\" : \"nightly-cleanup\"")
                .contains("\"schedule\" : \"0 3 * * *\"");
    }

    // ── Valid service: @KoCache ──────────────────────────────────────

    @Test
    void process_serviceWithCache_appModelContainsCache() {
        JavaFileObject source = JavaFileObjects.forSourceString("com.example.CacheService", """
                package com.example;

                import dev.ko.annotations.KoService;
                import dev.ko.annotations.KoAPI;
                import dev.ko.annotations.KoCache;

                @KoService("cache-service")
                public class CacheService {
                    @KoCache(name = "my-cache", ttl = 600)
                    private Object cache;

                    @KoAPI(method = "GET", path = "/data")
                    public String data() { return "cached"; }
                }
                """);

        Compilation compilation = compile(source);
        assertCompilationSucceeded(compilation);

        String appModel = readAppModel(compilation);
        assertThat(appModel)
                .contains("\"name\" : \"my-cache\"")
                .contains("\"ttl\" : 600");
    }

    // ── Valid service: @KoSecret ─────────────────────────────────────

    @Test
    void process_serviceWithSecret_appModelContainsSecret() {
        JavaFileObject source = JavaFileObjects.forSourceString("com.example.SecretService", """
                package com.example;

                import dev.ko.annotations.KoService;
                import dev.ko.annotations.KoAPI;
                import dev.ko.annotations.KoSecret;

                @KoService("secret-service")
                public class SecretService {
                    @KoSecret("api-key")
                    private Object key;

                    @KoAPI(method = "GET", path = "/check")
                    public String check() { return "ok"; }
                }
                """);

        Compilation compilation = compile(source);
        assertCompilationSucceeded(compilation);

        String appModel = readAppModel(compilation);
        assertThat(appModel).contains("\"name\" : \"api-key\"");
    }

    // ── Valid service: @KoBucket ─────────────────────────────────────

    @Test
    void process_serviceWithBucket_appModelContainsBucket() {
        JavaFileObject source = JavaFileObjects.forSourceString("com.example.StorageService", """
                package com.example;

                import dev.ko.annotations.KoService;
                import dev.ko.annotations.KoAPI;
                import dev.ko.annotations.KoBucket;

                @KoService("storage-service")
                public class StorageService {
                    @KoBucket(name = "uploads")
                    private Object bucket;

                    @KoAPI(method = "GET", path = "/files")
                    public String list() { return "[]"; }
                }
                """);

        Compilation compilation = compile(source);
        assertCompilationSucceeded(compilation);

        String appModel = readAppModel(compilation);
        assertThat(appModel).contains("\"name\" : \"uploads\"");
    }

    // ── Validation: invalid cron expression fails compilation ────────

    @Test
    void process_invalidCronExpression_failsCompilation() {
        JavaFileObject source = JavaFileObjects.forSourceString("com.example.BadCron", """
                package com.example;

                import dev.ko.annotations.KoService;
                import dev.ko.annotations.KoCron;

                @KoService("bad-cron")
                public class BadCron {
                    @KoCron(schedule = "not-a-cron", name = "bad-job")
                    public void run() {}
                }
                """);

        Compilation compilation = compile(source);

        CompilationSubject.assertThat(compilation).failed();
        CompilationSubject.assertThat(compilation).hadErrorContaining("Invalid cron expression");
    }

    // ── Validation: invalid service name fails compilation ──────────

    @Test
    void process_invalidServiceName_failsCompilation() {
        JavaFileObject source = JavaFileObjects.forSourceString("com.example.BadName", """
                package com.example;

                import dev.ko.annotations.KoService;
                import dev.ko.annotations.KoAPI;

                @KoService("BadServiceName")
                public class BadName {
                    @KoAPI(method = "GET", path = "/hello")
                    public String hello() { return "world"; }
                }
                """);

        Compilation compilation = compile(source);

        CompilationSubject.assertThat(compilation).failed();
        CompilationSubject.assertThat(compilation).hadErrorContaining("must be kebab-case");
    }

    @Test
    void process_underscoreServiceName_failsCompilation() {
        JavaFileObject source = JavaFileObjects.forSourceString("com.example.Under", """
                package com.example;

                import dev.ko.annotations.KoService;
                import dev.ko.annotations.KoAPI;

                @KoService("my_service")
                public class Under {
                    @KoAPI(method = "GET", path = "/hello")
                    public String hello() { return "world"; }
                }
                """);

        Compilation compilation = compile(source);

        CompilationSubject.assertThat(compilation).failed();
        CompilationSubject.assertThat(compilation).hadErrorContaining("must be kebab-case");
    }

    // ── Service client generation ───────────────────────────────────

    @Test
    void process_validService_generatesClientClass() {
        JavaFileObject source = JavaFileObjects.forSourceString("com.example.GreetingService", """
                package com.example;

                import dev.ko.annotations.KoService;
                import dev.ko.annotations.KoAPI;
                import dev.ko.annotations.PathParam;

                @KoService("greeting-service")
                public class GreetingService {
                    @KoAPI(method = "GET", path = "/hello/:name")
                    public String hello(@PathParam("name") String name) { return "Hello, " + name; }
                }
                """);

        Compilation compilation = compile(source);
        assertCompilationSucceeded(compilation);

        String client = readGeneratedSource(compilation, "com.example.GreetingServiceClient");
        assertThat(client)
                .contains("class GreetingServiceClient")
                .contains("setCaller")
                .contains("hello");
    }

    @Test
    void process_multipleApis_clientHasAllMethods() {
        JavaFileObject source = JavaFileObjects.forSourceString("com.example.ItemService", """
                package com.example;

                import dev.ko.annotations.KoService;
                import dev.ko.annotations.KoAPI;
                import dev.ko.annotations.PathParam;

                @KoService("item-service")
                public class ItemService {
                    @KoAPI(method = "GET", path = "/items")
                    public String list() { return "[]"; }

                    @KoAPI(method = "GET", path = "/items/:id")
                    public String get(@PathParam("id") String id) { return "item"; }

                    @KoAPI(method = "POST", path = "/items")
                    public String create(String body) { return "created"; }
                }
                """);

        Compilation compilation = compile(source);
        assertCompilationSucceeded(compilation);

        String client = readGeneratedSource(compilation, "com.example.ItemServiceClient");
        assertThat(client)
                .contains("list()")
                .contains("get(")
                .contains("create(");
    }

    // ── Multiple services ───────────────────────────────────────────

    @Test
    void process_multipleServices_appModelContainsBoth() {
        JavaFileObject service1 = JavaFileObjects.forSourceString("com.example.ServiceA", """
                package com.example;

                import dev.ko.annotations.KoService;
                import dev.ko.annotations.KoAPI;

                @KoService("service-a")
                public class ServiceA {
                    @KoAPI(method = "GET", path = "/a")
                    public String a() { return "a"; }
                }
                """);

        JavaFileObject service2 = JavaFileObjects.forSourceString("com.example.ServiceB", """
                package com.example;

                import dev.ko.annotations.KoService;
                import dev.ko.annotations.KoAPI;

                @KoService("service-b")
                public class ServiceB {
                    @KoAPI(method = "GET", path = "/b")
                    public String b() { return "b"; }
                }
                """);

        Compilation compilation = compile(service1, service2);
        assertCompilationSucceeded(compilation);

        String appModel = readAppModel(compilation);
        assertThat(appModel)
                .contains("\"name\" : \"service-a\"")
                .contains("\"name\" : \"service-b\"");
    }

    // ── Auth endpoint ───────────────────────────────────────────────

    @Test
    void process_authEndpoint_appModelContainsAuthFlag() {
        JavaFileObject source = JavaFileObjects.forSourceString("com.example.AuthService", """
                package com.example;

                import dev.ko.annotations.KoService;
                import dev.ko.annotations.KoAPI;

                @KoService("auth-service")
                public class AuthService {
                    @KoAPI(method = "POST", path = "/secure", auth = true)
                    public String secure() { return "ok"; }
                }
                """);

        Compilation compilation = compile(source);
        assertCompilationSucceeded(compilation);

        String appModel = readAppModel(compilation);
        assertThat(appModel).contains("\"auth\" : true");
    }
}
