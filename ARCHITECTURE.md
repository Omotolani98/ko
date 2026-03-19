# Kọ́ Framework — Architecture & Development Specification

> A Java-native framework for building type-safe distributed systems with declarative infrastructure.
> Inspired by [Encore.dev](https://encore.dev), built for the JVM ecosystem.
> Organization: **Mediawind** | License: **MPL-2.0**

---

## Table of Contents

1. [Project Overview](#1-project-overview)
2. [Architecture](#2-architecture)
3. [Repository Structure](#3-repository-structure)
4. [Module Breakdown](#4-module-breakdown)
5. [Infrastructure Primitives](#5-infrastructure-primitives)
6. [The Application Model](#6-the-application-model)
7. [Runtime Engine](#7-runtime-engine)
8. [CLI Tooling](#8-cli-tooling)
9. [Dev Dashboard](#9-dev-dashboard)
10. [Code Generation](#10-code-generation)
11. [Testing Strategy](#11-testing-strategy)
12. [Git Workflow](#12-git-workflow)
13. [Feature Development Guide](#13-feature-development-guide)
14. [Documentation Standards](#14-documentation-standards)
15. [Build & Release](#15-build--release)
16. [Implementation Phases](#16-implementation-phases)
17. [ADR Log](#17-adr-log)
18. [Glossary](#18-glossary)

---

## 1. Project Overview

### 1.1 What Is Kọ́?

Kọ́ (Yoruba: "to build / to learn") is a Java framework that lets developers define services and infrastructure declaratively using annotations. The framework:

- **Parses** your annotated code at compile time to build an Application Model (a JSON graph of your entire system).
- **Provisions** local infrastructure automatically (Postgres, Kafka, Redis) — no Docker Compose.
- **Wires** infrastructure at runtime using environment-specific config — same code runs locally, in Docker, and in the cloud.
- **Generates** service clients, OpenAPI specs, TypeScript API clients, and architecture diagrams from the model.
- **Traces** every API call, database query, and Pub/Sub event automatically — no instrumentation code.

### 1.2 Design Principles

1. **Code is the source of truth.** No YAML, no Terraform, no docker-compose for infrastructure your app needs. Annotations declare intent; the framework handles the rest.
2. **Semantic infrastructure.** You declare *what* you need (`@KoDatabase("users")`), not *how* to provision it. The runtime resolves the binding per environment.
3. **Compile-time safety.** The annotation processor catches misconfigurations (invalid cron expressions, missing migrations, circular dependencies) as build errors — not runtime surprises.
4. **Zero boilerplate service-to-service calls.** Generated client classes let you call other services like local methods. Locally they route in-process; in production they make HTTP calls. No code changes.
5. **Progressive complexity.** Start as a modular monolith (single JVM, multiple services). Deploy as microservices later by changing only the infra config — no code changes.

### 1.3 Tech Stack

| Layer | Technology | Rationale |
|-------|-----------|-----------|
| Language | Java 21+ (LTS) | Virtual threads, records, sealed interfaces, pattern matching |
| Build | Gradle (Kotlin DSL) | Plugin ecosystem, incremental builds, composite builds |
| DI / Web | Spring Boot 3.x | Industry standard, auto-configuration, massive ecosystem |
| Annotation Processing | `javax.annotation.processing` + JavaPoet | Compile-time code generation, no reflection overhead |
| Database Pools | HikariCP | Fastest JVM connection pool |
| Migrations | Flyway | Declarative SQL migrations, version tracking |
| Pub/Sub (local) | In-memory `BlockingQueue` | Zero dependencies for local dev |
| Pub/Sub (prod) | Kafka / SQS+SNS / GCP Pub/Sub | Provider interface pattern, pluggable |
| Cache | Redis (Lettuce) / ConcurrentHashMap (local) | Async-first Redis client |
| Object Storage | S3 / GCS / MinIO (local) | Provider interface pattern |
| Tracing | OpenTelemetry SDK | Vendor-neutral, auto-instrumentation |
| CLI | Go + Bubble Tea | Fast native binary, TUI for interactive flows |
| CLI (alt) | GraalVM native-image (Java) | Single-language stack option |
| Dev Dashboard | React + Vite + D3.js | Fast builds, interactive service graphs |
| Testing | JUnit 5 + Testcontainers + AssertJ | Container-based integration tests |
| Native Build | GraalVM native-image | AOT compilation for CLI and optionally services |

---

## 2. Architecture

### 2.1 High-Level Data Flow

```
Developer Code (annotations)
        │
        ▼
┌──────────────────────┐
│  Annotation Processor │  ← compile-time (javac plugin)
│  (Static Analyzer)    │
└──────────┬───────────┘
           │
           ▼
┌──────────────────────┐
│   Application Model   │  ← ko-app-model.json (single source of truth)
│   (JSON graph)        │
└──────────┬───────────┘
           │
     ┌─────┼─────────┬──────────────┬──────────────┐
     ▼     ▼         ▼              ▼              ▼
  Runtime  CLI    Dev Dashboard  Code Generator  Docker Builder
  Engine   Tool   (React SPA)   (service clients) (ko build)
     │     │         │              │
     ▼     ▼         ▼              ▼
  Spring   Testcontainers   D3.js graphs    JavaPoet / Mustache
  Boot     + local infra    + trace viewer  + OpenAPI + TS client
```

### 2.2 Component Interaction

```
┌─────────────────────────────────────────────────────────┐
│                      Developer Machine                   │
│                                                          │
│  ┌──────────┐    ┌─────────────────┐    ┌────────────┐  │
│  │  IDE      │───▶│  Gradle Build   │───▶│ Annotation │  │
│  │  (IntelliJ)│   │  (continuous)   │    │ Processor  │  │
│  └──────────┘    └────────┬────────┘    └─────┬──────┘  │
│                           │                    │         │
│                           ▼                    ▼         │
│                  ┌─────────────────┐  ┌──────────────┐  │
│                  │ ko CLI daemon   │  │ ko-app-model │  │
│                  │ (background)    │◀─│    .json     │  │
│                  └───────┬─────────┘  └──────────────┘  │
│                          │                               │
│            ┌─────────────┼─────────────┐                │
│            ▼             ▼             ▼                │
│   ┌──────────────┐ ┌──────────┐ ┌──────────────┐      │
│   │ Testcontainers│ │ Spring   │ │ Dev Dashboard │      │
│   │ (Postgres,   │ │ Boot App │ │ :9400         │      │
│   │  Kafka,Redis)│ │ :8080    │ │ (React SPA)   │      │
│   └──────────────┘ └──────────┘ └──────────────┘      │
│                                                          │
└─────────────────────────────────────────────────────────┘
```

### 2.3 Production Deployment

```
┌────────────────────────────────────────────────┐
│              Production Environment              │
│                                                  │
│  ┌─────────────┐     infra-config.json          │
│  │ Docker Image │◀─── (baked at build time)      │
│  │ (ko build)  │                                 │
│  └──────┬──────┘                                 │
│         │                                        │
│         ▼                                        │
│  ┌─────────────┐  ┌──────────┐  ┌───────────┐  │
│  │ Runtime     │──▶│ Postgres │  │ Kafka     │  │
│  │ Engine      │──▶│ (RDS/    │  │ (MSK/     │  │
│  │ (resolves   │  │  CloudSQL)│  │  Confluent)│  │
│  │  providers) │  └──────────┘  └───────────┘  │
│  │             │──▶ Redis  ──▶ S3/GCS  ──▶ ... │
│  └─────────────┘                                 │
└────────────────────────────────────────────────┘
```

---

## 3. Repository Structure

```
ko/
├── .github/
│   ├── workflows/
│   │   ├── ci.yml                    # Main CI pipeline
│   │   ├── release.yml               # Release pipeline (tags)
│   │   ├── nightly.yml               # Nightly E2E tests
│   │   └── docs.yml                  # Documentation site deploy
│   ├── ISSUE_TEMPLATE/
│   │   ├── bug_report.yml
│   │   ├── feature_request.yml
│   │   └── primitive_request.yml     # New infrastructure primitive
│   ├── PULL_REQUEST_TEMPLATE.md
│   └── CODEOWNERS
│
├── docs/
│   ├── architecture/
│   │   ├── ARCHITECTURE.md           # This file
│   │   ├── adrs/                     # Architecture Decision Records
│   │   │   ├── 001-annotation-processing-over-reflection.md
│   │   │   ├── 002-spring-boot-as-runtime-base.md
│   │   │   ├── 003-go-cli-over-graalvm.md
│   │   │   └── template.md
│   │   └── diagrams/                 # Mermaid/PlantUML source files
│   ├── guides/
│   │   ├── getting-started.md
│   │   ├── adding-a-primitive.md     # How to add @KoBucket, @KoQueue, etc.
│   │   ├── adding-a-provider.md      # How to add AWS SQS, GCP Pub/Sub, etc.
│   │   ├── writing-tests.md
│   │   └── cli-development.md
│   ├── reference/
│   │   ├── annotations.md            # All @Ko* annotations reference
│   │   ├── infra-config-schema.md    # infra-config.json schema docs
│   │   ├── app-model-schema.md       # ko-app-model.json schema docs
│   │   └── cli-commands.md           # ko run, ko build, ko gen, etc.
│   └── rfcs/                         # Request for Comments (pre-ADR)
│       └── template.md
│
├── ko-annotations/                   # Module: annotation definitions
│   ├── build.gradle.kts
│   └── src/main/java/dev/ko/annotations/
│       ├── KoService.java
│       ├── KoAPI.java
│       ├── KoDatabase.java
│       ├── KoPubSub.java
│       ├── KoSubscribe.java
│       ├── KoCache.java
│       ├── KoCron.java
│       ├── KoBucket.java
│       ├── KoSecret.java
│       ├── KoServiceClient.java
│       ├── PathParam.java
│       └── DeliveryGuarantee.java
│
├── ko-processor/                     # Module: annotation processor
│   ├── build.gradle.kts
│   └── src/
│       ├── main/java/dev/ko/processor/
│       │   ├── KoAnnotationProcessor.java       # Entry point
│       │   ├── model/                            # Application Model types
│       │   │   ├── AppModel.java                 # Root model (record)
│       │   │   ├── ServiceModel.java
│       │   │   ├── ApiEndpointModel.java
│       │   │   ├── DatabaseModel.java
│       │   │   ├── PubSubTopicModel.java
│       │   │   ├── CacheModel.java
│       │   │   ├── CronJobModel.java
│       │   │   ├── BucketModel.java
│       │   │   └── SecretModel.java
│       │   ├── scanner/                          # Annotation scanners
│       │   │   ├── ServiceScanner.java
│       │   │   ├── ApiScanner.java
│       │   │   ├── DatabaseScanner.java
│       │   │   ├── PubSubScanner.java
│       │   │   └── CronScanner.java
│       │   ├── validation/                       # Compile-time validators
│       │   │   ├── CronExpressionValidator.java
│       │   │   ├── PathValidator.java
│       │   │   ├── CircularDependencyValidator.java
│       │   │   ├── MigrationValidator.java
│       │   │   └── NamingConventionValidator.java
│       │   ├── emitter/                          # Output writers
│       │   │   ├── AppModelEmitter.java          # Writes ko-app-model.json
│       │   │   ├── ServiceClientEmitter.java     # Generates client classes
│       │   │   └── OpenApiEmitter.java           # Generates openapi.json
│       │   └── util/
│       │       ├── TypeMirrorUtils.java
│       │       └── JavadocExtractor.java
│       └── test/java/dev/ko/processor/
│           ├── KoAnnotationProcessorTest.java    # Compile-testing
│           ├── validation/
│           │   ├── CronExpressionValidatorTest.java
│           │   └── PathValidatorTest.java
│           └── fixtures/                          # Test source files
│               ├── ValidService.java
│               ├── InvalidCronService.java
│               └── CircularDependencyService.java
│
├── ko-runtime/                        # Module: runtime engine
│   ├── build.gradle.kts
│   └── src/
│       ├── main/java/dev/ko/runtime/
│       │   ├── KoApplication.java                # Bootstrap entry point
│       │   ├── config/
│       │   │   ├── KoAutoConfiguration.java      # Spring Boot auto-config
│       │   │   ├── InfraConfig.java              # infra-config.json mapping
│       │   │   ├── InfraConfigLoader.java        # Loads + resolves $env refs
│       │   │   └── AppModelLoader.java           # Loads ko-app-model.json
│       │   ├── database/
│       │   │   ├── KoSQLDatabase.java            # Developer-facing API
│       │   │   ├── KoDatabaseProvider.java       # Provider interface
│       │   │   ├── HikariDatabaseProvider.java   # HikariCP implementation
│       │   │   └── KoDatabaseAutoConfig.java     # Wires pools from model
│       │   ├── pubsub/
│       │   │   ├── KoTopic.java                  # Developer-facing publish API
│       │   │   ├── KoPubSubProvider.java          # Provider interface
│       │   │   ├── InMemoryPubSubProvider.java   # Local dev
│       │   │   ├── KafkaPubSubProvider.java      # Kafka (production)
│       │   │   ├── AwsPubSubProvider.java         # SNS+SQS
│       │   │   ├── GcpPubSubProvider.java         # GCP Pub/Sub
│       │   │   └── KoPubSubAutoConfig.java
│       │   ├── cache/
│       │   │   ├── KoCache.java                  # Developer-facing API
│       │   │   ├── KoCacheProvider.java           # Provider interface
│       │   │   ├── InMemoryCacheProvider.java    # Local dev
│       │   │   ├── RedisCacheProvider.java       # Redis (Lettuce)
│       │   │   └── KoCacheAutoConfig.java
│       │   ├── storage/
│       │   │   ├── KoBucket.java                 # Developer-facing API
│       │   │   ├── KoStorageProvider.java         # Provider interface
│       │   │   ├── LocalFileStorageProvider.java # Local dev
│       │   │   ├── S3StorageProvider.java        # AWS S3
│       │   │   ├── GcsStorageProvider.java       # GCP GCS
│       │   │   └── KoStorageAutoConfig.java
│       │   ├── cron/
│       │   │   ├── KoCronScheduler.java          # Cron job executor
│       │   │   └── KoCronAutoConfig.java
│       │   ├── secrets/
│       │   │   ├── KoSecretProvider.java          # Provider interface
│       │   │   ├── EnvVarSecretProvider.java     # Environment variables
│       │   │   ├── AwsSecretProvider.java         # AWS Secrets Manager
│       │   │   └── KoSecretAutoConfig.java
│       │   ├── service/
│       │   │   ├── KoServiceCaller.java          # Service-to-service routing
│       │   │   ├── InProcessCaller.java          # Local: direct method call
│       │   │   ├── HttpServiceCaller.java        # Production: HTTP client
│       │   │   ├── ServiceRegistry.java          # Tracks all active services
│       │   │   └── KoServiceAutoConfig.java
│       │   ├── tracing/
│       │   │   ├── KoTracingInterceptor.java     # AOP interceptor
│       │   │   ├── KoSpanProcessor.java          # Custom span processor
│       │   │   ├── TracingContext.java            # Request-scoped context
│       │   │   └── KoTracingAutoConfig.java
│       │   ├── errors/
│       │   │   ├── KoError.java                  # Framework error type
│       │   │   ├── KoErrorCode.java              # Error code enum
│       │   │   └── KoExceptionHandler.java       # Global exception handler
│       │   └── metadata/
│       │       ├── KoMetadata.java               # Runtime metadata API
│       │       └── EnvironmentType.java          # production/staging/dev/test
│       └── test/java/dev/ko/runtime/
│           ├── database/
│           │   └── HikariDatabaseProviderTest.java
│           ├── pubsub/
│           │   ├── InMemoryPubSubProviderTest.java
│           │   └── KafkaPubSubProviderTest.java
│           ├── cache/
│           │   └── RedisCacheProviderTest.java
│           ├── service/
│           │   ├── InProcessCallerTest.java
│           │   └── HttpServiceCallerTest.java
│           └── tracing/
│               └── KoTracingInterceptorTest.java
│
├── ko-gradle-plugin/                  # Module: Gradle plugin
│   ├── build.gradle.kts
│   └── src/main/java/dev/ko/gradle/
│       ├── KoPlugin.java             # Registers tasks
│       ├── tasks/
│       │   ├── GenerateAppModelTask.java
│       │   ├── GenerateServiceClientsTask.java
│       │   ├── GenerateOpenApiTask.java
│       │   └── GenerateTypeScriptClientTask.java
│       └── extensions/
│           └── KoExtension.java       # ko { appName = "bawo-core" }
│
├── ko-test/                           # Module: test utilities
│   ├── build.gradle.kts
│   └── src/main/java/dev/ko/test/
│       ├── KoTestExtension.java       # JUnit 5 extension
│       ├── KoTestApp.java             # Bootstraps isolated test app
│       ├── TestInfraManager.java      # Testcontainers lifecycle
│       ├── MockServiceClient.java     # Mock inter-service calls
│       ├── TestPubSub.java            # Inspect published messages
│       └── TestDatabase.java          # Helpers for test DB state
│
├── ko-cli/                            # Module: CLI (Go)
│   ├── go.mod
│   ├── go.sum
│   ├── main.go
│   ├── cmd/
│   │   ├── root.go
│   │   ├── run.go                     # ko run
│   │   ├── build.go                   # ko build docker
│   │   ├── gen.go                     # ko gen client
│   │   ├── test.go                    # ko test
│   │   └── dashboard.go              # ko dashboard (open browser)
│   ├── daemon/
│   │   ├── daemon.go                  # Background process manager
│   │   ├── infra.go                   # Testcontainers orchestration
│   │   ├── watcher.go                 # File change watcher (hot reload)
│   │   └── dashboard_server.go        # Embedded HTTP server for dashboard
│   ├── model/
│   │   ├── app_model.go               # Parses ko-app-model.json
│   │   └── infra_config.go            # Generates local infra-config.json
│   └── internal/
│       ├── docker.go                  # Docker image builder
│       ├── gradle.go                  # Gradle invocation wrapper
│       └── ports.go                   # Port allocation for containers
│
├── ko-dashboard/                      # Module: Dev Dashboard (React)
│   ├── package.json
│   ├── vite.config.ts
│   ├── tsconfig.json
│   └── src/
│       ├── App.tsx
│       ├── pages/
│       │   ├── ArchitecturePage.tsx    # D3.js service graph
│       │   ├── ApiExplorerPage.tsx     # Interactive API testing
│       │   ├── TracesPage.tsx          # Distributed trace viewer
│       │   ├── DatabasePage.tsx        # SQL query runner
│       │   └── ServiceCatalogPage.tsx  # Service docs
│       ├── components/
│       │   ├── ServiceGraph.tsx        # D3 force-directed graph
│       │   ├── TraceWaterfall.tsx      # Trace span visualization
│       │   ├── ApiRequestBuilder.tsx   # Form-based API tester
│       │   └── SchemaViewer.tsx        # JSON schema renderer
│       └── api/
│           └── dashboard-api.ts       # Talks to CLI daemon
│
├── ko-e2e-tests/                      # Module: end-to-end tests
│   ├── build.gradle.kts
│   ├── src/test/java/dev/ko/e2e/
│   │   ├── FullLifecycleE2ETest.java  # Build → run → call API → verify
│   │   ├── PubSubFlowE2ETest.java     # Publish → subscribe → verify
│   │   ├── CronExecutionE2ETest.java
│   │   ├── MultiServiceE2ETest.java   # Cross-service calls
│   │   ├── MigrationE2ETest.java      # DB migration lifecycle
│   │   └── DockerBuildE2ETest.java    # ko build docker → run image
│   └── testapps/                      # Sample apps for E2E
│       ├── hello-world/
│       ├── url-shortener/
│       └── event-driven/
│
├── examples/                          # Example applications
│   ├── hello-world/
│   ├── url-shortener/
│   ├── event-driven-system/
│   └── saas-starter/
│
├── build.gradle.kts                   # Root build file
├── settings.gradle.kts                # Module includes
├── gradle.properties                  # Shared properties
├── gradle/
│   ├── libs.versions.toml             # Version catalog
│   └── wrapper/
├── .editorconfig
├── .gitignore
├── LICENSE                            # MPL-2.0
├── README.md
├── CONTRIBUTING.md
├── CHANGELOG.md
└── CLAUDE.md                          # Claude Code project instructions
```

---

## 4. Module Breakdown

### 4.1 Module Dependency Graph

```
ko-annotations          (zero dependencies — just annotation definitions)
       ▲
       │
ko-processor            (depends on: ko-annotations, JavaPoet, Jackson)
       ▲
       │
ko-runtime              (depends on: ko-annotations, Spring Boot, HikariCP, etc.)
       ▲
       │
ko-gradle-plugin        (depends on: ko-processor)
       ▲
       │
ko-test                 (depends on: ko-runtime, JUnit 5, Testcontainers)
       ▲
       │
ko-e2e-tests            (depends on: ko-test, all provider modules)
```

### 4.2 Module Details

| Module | Type | Published | Description |
|--------|------|-----------|-------------|
| `ko-annotations` | Java library | Maven Central | Pure annotation definitions. Zero deps. Any Java project can depend on this. |
| `ko-processor` | Annotation processor | Maven Central | Compile-time scanner, validator, and emitter. Generates `ko-app-model.json`, service clients, OpenAPI spec. |
| `ko-runtime` | Spring Boot starter | Maven Central | Auto-configuration that reads the app model and infra config, wires providers, registers tracing. |
| `ko-gradle-plugin` | Gradle plugin | Gradle Plugin Portal | Registers the annotation processor, adds custom tasks (`koGenModel`, `koGenClients`, `koGenOpenApi`, `koGenTsClient`). |
| `ko-test` | Test library | Maven Central | JUnit 5 extension, test infra manager, mock service clients, assertion helpers. |
| `ko-cli` | Go binary | GitHub Releases / Homebrew | `ko run`, `ko build`, `ko gen`, `ko test`, `ko dashboard`. |
| `ko-dashboard` | React SPA | Bundled into ko-cli | Architecture graph, API explorer, trace viewer, DB explorer. |
| `ko-e2e-tests` | Test suite | Not published | Full end-to-end tests against sample apps. |

### 4.3 Version Catalog (`gradle/libs.versions.toml`)

```toml
[versions]
java = "21"
spring-boot = "3.4.1"
hikaricp = "6.2.1"
flyway = "10.22.0"
kafka = "3.9.0"
lettuce = "6.5.2"
aws-sdk = "2.29.0"
gcp-sdk = "26.52.0"
jackson = "2.18.2"
javapoet = "1.13.0"
opentelemetry = "1.45.0"
junit = "5.11.4"
testcontainers = "1.20.4"
assertj = "3.27.0"
archunit = "1.3.0"
compile-testing = "0.21.0"

[libraries]
spring-boot-starter-web = { module = "org.springframework.boot:spring-boot-starter-web", version.ref = "spring-boot" }
spring-boot-starter-aop = { module = "org.springframework.boot:spring-boot-starter-aop", version.ref = "spring-boot" }
spring-boot-starter-test = { module = "org.springframework.boot:spring-boot-starter-test", version.ref = "spring-boot" }
hikaricp = { module = "com.zaxxer:HikariCP", version.ref = "hikaricp" }
flyway-core = { module = "org.flywaydb:flyway-core", version.ref = "flyway" }
kafka-clients = { module = "org.apache.kafka:kafka-clients", version.ref = "kafka" }
lettuce-core = { module = "io.lettuce:lettuce-core", version.ref = "lettuce" }
jackson-databind = { module = "com.fasterxml.jackson.core:jackson-databind", version.ref = "jackson" }
javapoet = { module = "com.squareup:javapoet", version.ref = "javapoet" }
opentelemetry-api = { module = "io.opentelemetry:opentelemetry-api", version.ref = "opentelemetry" }
opentelemetry-sdk = { module = "io.opentelemetry:opentelemetry-sdk", version.ref = "opentelemetry" }
junit-jupiter = { module = "org.junit.jupiter:junit-jupiter", version.ref = "junit" }
testcontainers-postgresql = { module = "org.testcontainers:postgresql", version.ref = "testcontainers" }
testcontainers-kafka = { module = "org.testcontainers:kafka", version.ref = "testcontainers" }
assertj-core = { module = "org.assertj:assertj-core", version.ref = "assertj" }
archunit-junit5 = { module = "com.tngtech.archunit:archunit-junit5", version.ref = "archunit" }
compile-testing = { module = "com.google.testing.compile:compile-testing", version.ref = "compile-testing" }

[plugins]
spring-boot = { id = "org.springframework.boot", version.ref = "spring-boot" }
graalvm-native = { id = "org.graalvm.buildtools.native", version = "0.10.4" }
```

---

## 5. Infrastructure Primitives

### 5.1 Annotation Definitions

Every primitive follows the same pattern: a marker annotation that declares semantic intent, a runtime interface the developer uses, and a provider interface that the runtime resolves per environment.

```java
// --- @KoService: Declares a service boundary ---
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface KoService {
    String value();  // service name (kebab-case)
}

// --- @KoAPI: Declares an HTTP endpoint ---
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface KoAPI {
    String method();              // GET, POST, PUT, DELETE, PATCH
    String path();                // e.g., "/lessons/:id"
    boolean auth() default false; // requires authentication
    String[] permissions() default {};  // e.g., {"CREATE:LESSONS"}
    boolean expose() default true;     // publicly accessible
}

// --- @KoDatabase: Declares a SQL database dependency ---
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface KoDatabase {
    String name();                     // logical database name
    String migrations() default "";    // path to migrations dir
}

// --- @KoPubSub: Declares a Pub/Sub topic ---
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface KoPubSub {
    String topic();                            // topic name
    DeliveryGuarantee delivery() default DeliveryGuarantee.AT_LEAST_ONCE;
}

// --- @KoSubscribe: Subscribes to a topic ---
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface KoSubscribe {
    String topic();          // topic to subscribe to
    String name();           // subscription name
    int maxRetries() default 3;
    String deadLetter() default "";
}

// --- @KoCache: Declares a cache cluster ---
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface KoCache {
    String name();
    Class<?> keyType() default String.class;
    String ttl() default "5m";  // default TTL
}

// --- @KoCron: Declares a cron job ---
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface KoCron {
    String schedule();   // cron expression (5-field)
    String name();       // unique job name
}

// --- @KoBucket: Declares an object storage bucket ---
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface KoBucket {
    String name();
    boolean publicRead() default false;
}

// --- @KoSecret: Declares an application secret ---
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface KoSecret {
    String value();  // secret name
}
```

### 5.2 Developer-Facing APIs

```java
// KoSQLDatabase — wraps a connection pool
public class KoSQLDatabase {
    public <T> T exec(ConnectionCallback<T> callback);
    public <T> Optional<T> queryOne(String sql, Object... params);
    public <T> List<T> queryMany(String sql, Class<T> type, Object... params);
    public int execute(String sql, Object... params);
}

// KoTopic<T> — publish to a topic
public class KoTopic<T> {
    public void publish(T message);
    public CompletableFuture<Void> publishAsync(T message);
}

// KoCache<K, V> — read-through cache
public class KoCache<K, V> {
    public Optional<V> get(K key);
    public V getOrSet(K key, Supplier<V> loader);
    public void set(K key, V value);
    public void set(K key, V value, Duration ttl);
    public void invalidate(K key);
}

// KoBucket — object storage
public class KoBucketClient {
    public void upload(String key, byte[] data, String contentType);
    public byte[] download(String key);
    public String publicUrl(String key);
    public void delete(String key);
    public List<String> list(String prefix);
}

// KoError — structured errors
public class KoError extends RuntimeException {
    public static KoError notFound(String message);
    public static KoError badRequest(String message);
    public static KoError internal(String message);
    public static KoError permissionDenied(String message);
    public static KoError of(KoErrorCode code, String message);
}
```

---

## 6. The Application Model

### 6.1 Schema (`ko-app-model.json`)

The Application Model is a JSON file generated at compile time by the annotation processor. It is the **single source of truth** that every other component reads from.

```json
{
  "$schema": "https://ko.dev/schemas/app-model.schema.json",
  "version": "1.0",
  "app": "bawo-core",
  "generated_at": "2026-03-19T14:30:00Z",
  "services": [
    {
      "name": "lesson-service",
      "class": "com.mediawind.bawo.lesson.LessonService",
      "package": "com.mediawind.bawo.lesson",
      "apis": [
        {
          "name": "createLesson",
          "method": "POST",
          "path": "/lessons",
          "auth": true,
          "permissions": ["CREATE:LESSONS"],
          "request_type": {
            "class": "CreateLessonRequest",
            "fields": [
              { "name": "title", "type": "String", "required": true },
              { "name": "language", "type": "String", "required": true },
              { "name": "difficulty", "type": "Difficulty", "required": false }
            ]
          },
          "response_type": {
            "class": "LessonResponse",
            "fields": [
              { "name": "id", "type": "String" },
              { "name": "title", "type": "String" },
              { "name": "language", "type": "String" }
            ]
          },
          "javadoc": "Creates a new lesson for the specified language."
        }
      ],
      "databases": [
        { "name": "lessons", "migrations": "./migrations/lessons" }
      ],
      "publishes": ["lesson-created"],
      "subscribes": [],
      "caches": [
        { "name": "lesson-cache", "key_type": "String", "ttl": "15m" }
      ],
      "cron_jobs": [
        { "name": "archive-old-lessons", "schedule": "0 3 * * *", "method": "archiveOldLessons" }
      ],
      "secrets": [],
      "buckets": []
    }
  ],
  "pubsub_topics": [
    {
      "name": "lesson-created",
      "delivery": "at-least-once",
      "message_type": {
        "class": "LessonCreatedEvent",
        "fields": [
          { "name": "lessonId", "type": "String" },
          { "name": "creatorId", "type": "String" }
        ]
      },
      "publishers": ["lesson-service"],
      "subscribers": [
        { "service": "notification-service", "subscription": "notify-on-lesson" }
      ]
    }
  ],
  "databases": [
    { "name": "lessons", "type": "postgresql", "services": ["lesson-service"] },
    { "name": "notifications", "type": "postgresql", "services": ["notification-service"] }
  ],
  "service_dependencies": [
    { "from": "notification-service", "to": "lesson-service", "type": "api_call" },
    { "from": "lesson-service", "to": "notification-service", "type": "pubsub", "topic": "lesson-created" }
  ]
}
```

### 6.2 What the Model Enables

| Consumer | What it reads | What it produces |
|----------|--------------|-----------------|
| CLI (`ko run`) | `databases[]`, `pubsub_topics[]`, `caches[]` | Testcontainers for each resource, local `infra-config.json` |
| Runtime Engine | `services[]`, `service_dependencies[]` | Spring bean wiring, service caller routing |
| Code Generator | `services[].apis[]`, `request_type`, `response_type` | Service client classes, OpenAPI spec, TS client |
| Dev Dashboard | Full model | Architecture graph, API explorer, service catalog |
| Docker Builder | Full model | Multi-stage Dockerfile, health checks |

---

## 7. Runtime Engine

### 7.1 Startup Sequence

```
1. JVM starts → Spring Boot initializes
2. KoAutoConfiguration loads:
   a. ko-app-model.json from classpath (generated at compile time)
   b. infra-config.json from KO_INFRA_CONFIG env var or default path
3. For each declared database → instantiate KoDatabaseProvider (HikariCP pool)
4. For each declared pubsub topic → instantiate KoPubSubProvider (Kafka/InMemory/SQS)
5. For each declared cache → instantiate KoCacheProvider (Redis/InMemory)
6. For each declared bucket → instantiate KoStorageProvider (S3/local)
7. For each declared secret → resolve via KoSecretProvider
8. Register KoServiceCaller (InProcess or HTTP based on env_type)
9. Register KoTracingInterceptor (AOP around all @KoAPI methods)
10. Run Flyway migrations for all declared databases
11. Start cron scheduler for all @KoCron methods
12. Register all @KoSubscribe handlers with the pubsub provider
13. Start HTTP server → Ready
```

### 7.2 Provider Interface Pattern

Every infrastructure type follows this contract:

```java
// 1. Provider interface (in ko-runtime)
public interface KoPubSubProvider {
    <T> void publish(String topic, T message);
    <T> void subscribe(String topic, String subscription,
                       Class<T> type, Consumer<T> handler);
    void close();
}

// 2. Local implementation (in ko-runtime, zero external deps)
public class InMemoryPubSubProvider implements KoPubSubProvider {
    private final Map<String, List<Consumer<?>>> subscribers = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    // ...
}

// 3. Production implementation (separate module: ko-provider-kafka)
public class KafkaPubSubProvider implements KoPubSubProvider {
    private final KafkaProducer<String, byte[]> producer;
    private final Map<String, KafkaConsumer<String, byte[]>> consumers;
    // ...
}

// 4. Auto-configuration selects based on infra-config.json
@Configuration
public class KoPubSubAutoConfig {
    @Bean
    public KoPubSubProvider pubSubProvider(InfraConfig config) {
        return switch (config.pubsubType()) {
            case "kafka"      -> new KafkaPubSubProvider(config.kafka());
            case "aws_sns_sqs" -> new AwsPubSubProvider(config.aws());
            case "gcp_pubsub" -> new GcpPubSubProvider(config.gcp());
            default           -> new InMemoryPubSubProvider();
        };
    }
}
```

### 7.3 Service-to-Service Calls

```java
// KoServiceCaller routes calls based on deployment mode
public interface KoServiceCaller {
    <Req, Res> Res call(String service, String method, String path,
                        Req request, Class<Res> responseType);
}

// In-process (monolith / local dev): direct method invocation
public class InProcessCaller implements KoServiceCaller {
    private final ServiceRegistry registry;

    @Override
    public <Req, Res> Res call(String service, String method, String path,
                               Req request, Class<Res> responseType) {
        // Look up the registered service bean and invoke the method directly
        // Zero network overhead, full type safety
        Object serviceBean = registry.getService(service);
        Method handler = registry.resolveHandler(service, method, path);
        return responseType.cast(handler.invoke(serviceBean, request));
    }
}

// HTTP (microservices / production): HTTP call via service discovery
public class HttpServiceCaller implements KoServiceCaller {
    private final HttpClient client;
    private final Map<String, String> serviceBaseUrls;  // from infra-config.json

    @Override
    public <Req, Res> Res call(String service, String method, String path,
                               Req request, Class<Res> responseType) {
        String baseUrl = serviceBaseUrls.get(service);
        // Build HTTP request, serialize, call, deserialize
        // Includes retry logic, circuit breaker, tracing context propagation
    }
}
```

### 7.4 Infra Config Schema (`infra-config.json`)

```json
{
  "$schema": "https://ko.dev/schemas/infra-config.schema.json",
  "metadata": {
    "app_id": "string",
    "env_name": "string",
    "env_type": "production | staging | development | test",
    "cloud": "aws | gcp | hetzner | local",
    "base_url": "string"
  },
  "sql_servers": [{
    "host": "string",
    "tls_config": { "disabled": false, "ca": "PEM string" },
    "databases": {
      "<db-name>": {
        "username": "string",
        "password": "string | { $env: ENV_VAR }",
        "max_connections": 20,
        "min_connections": 5
      }
    }
  }],
  "pubsub": [{
    "type": "kafka | aws_sns_sqs | gcp_pubsub | in_memory",
    "bootstrap_servers": "string (kafka)",
    "topics": {
      "<topic-name>": {
        "name": "string (actual topic name on broker)",
        "partitions": 6,
        "subscriptions": {
          "<sub-name>": { "name": "string (actual subscription name)" }
        }
      }
    }
  }],
  "redis": {
    "<cache-name>": {
      "host": "string",
      "database_index": 0,
      "auth": { "type": "acl", "username": "string", "password": "{ $env: REDIS_PASS }" }
    }
  },
  "object_storage": [{
    "type": "s3 | gcs | local",
    "region": "string",
    "buckets": {
      "<bucket-name>": { "name": "string", "key_prefix": "string" }
    }
  }],
  "service_discovery": {
    "<service-name>": { "base_url": "string" }
  },
  "secrets": {
    "<secret-name>": "string | { $env: ENV_VAR }"
  },
  "metrics": {
    "type": "prometheus | datadog | otlp",
    "endpoint": "string"
  },
  "graceful_shutdown": {
    "total": 30,
    "handlers": 20
  }
}
```

---

## 8. CLI Tooling

### 8.1 Commands

| Command | Description |
|---------|-------------|
| `ko run` | Build app model, provision local infra (Testcontainers), start Spring Boot app, serve dev dashboard |
| `ko run --watch` | Same as `ko run` but watches for file changes and triggers hot reload via Gradle |
| `ko build docker --config <path> <image:tag>` | Build production Docker image with infra config baked in |
| `ko gen model` | Regenerate `ko-app-model.json` without starting the app |
| `ko gen client --lang typescript --output <dir>` | Generate TypeScript API client from the model |
| `ko gen openapi --output <path>` | Generate OpenAPI 3.1 spec |
| `ko test` | Run tests with isolated Testcontainers infra per test class |
| `ko test --e2e` | Run end-to-end tests against sample apps |
| `ko dashboard` | Open the dev dashboard in browser (requires `ko run` to be active) |
| `ko version` | Print CLI and framework versions |

### 8.2 `ko run` Internal Flow

```
1. Parse ko-app-model.json from build/ko/
2. Determine required infrastructure:
   - databases[] → Postgres Testcontainer per unique DB
   - pubsub_topics[] → Kafka Testcontainer (KRaft mode, single broker)
   - caches[] → Redis Testcontainer
   - buckets[] → MinIO Testcontainer
3. Start containers, wait for health checks
4. Run Flyway migrations against each database
5. Generate local infra-config.json with container host:port mappings
6. Set KO_INFRA_CONFIG env var pointing to generated config
7. Run `./gradlew bootRun` with the env vars
8. Start dev dashboard HTTP server on :9400
9. Watch for file changes → trigger `./gradlew classes` → Spring DevTools picks up reload
```

### 8.3 CLI Architecture (Go)

```
ko-cli/
├── cmd/                  # Cobra commands
├── daemon/               # Long-running background process
│   ├── daemon.go         # Manages Testcontainer lifecycle + dashboard
│   ├── infra.go          # Container provisioning logic
│   └── watcher.go        # fsnotify file watcher
├── model/                # JSON parsers for app model + infra config
└── internal/             # Docker, Gradle, port helpers
```

The CLI daemon communicates with the main `ko` command over a Unix domain socket at `~/.ko/daemon.sock`.

---

## 9. Dev Dashboard

### 9.1 Pages

| Page | Data Source | Visualization |
|------|-----------|---------------|
| Architecture | `ko-app-model.json` | D3.js force-directed graph: services as nodes, API calls / PubSub / DB deps as edges |
| API Explorer | `openapi.json` | Interactive form: select endpoint → fill params → send request → see response + timing |
| Traces | OpenTelemetry spans (via CLI daemon API) | Waterfall chart: request → DB query → PubSub publish → response |
| Database | Direct SQL connection to Testcontainer Postgres | Table browser, query runner, migration history |
| Service Catalog | `ko-app-model.json` | Per-service docs: endpoints, dependencies, schemas, Javadoc |

### 9.2 Dashboard API (served by CLI daemon)

```
GET  /api/model           → returns ko-app-model.json
GET  /api/openapi         → returns openapi.json
GET  /api/traces          → returns recent traces (from in-memory buffer)
GET  /api/traces/:id      → returns single trace detail
POST /api/query/:database → executes SQL query against local DB
GET  /api/health          → service health status
```

---

## 10. Code Generation

### 10.1 What Gets Generated

| Artifact | Generator | Trigger | Output Location |
|----------|-----------|---------|-----------------|
| `ko-app-model.json` | `AppModelEmitter` | Every compilation | `build/ko/ko-app-model.json` |
| Service client classes | `ServiceClientEmitter` | Every compilation | `build/generated/sources/ko/` |
| OpenAPI 3.1 spec | `OpenApiEmitter` | Every compilation | `build/ko/openapi.json` |
| TypeScript API client | `GenerateTypeScriptClientTask` | `ko gen client` | User-specified output dir |

### 10.2 Service Client Generation Rules

For each `@KoService` class, the processor generates a `{ServiceName}Client.java`:

1. One method per `@KoAPI` method in the service.
2. Method signature matches the original: same parameter types, same return type.
3. Body delegates to `KoServiceCaller.call(service, method, path, request, responseType)`.
4. Class is annotated `@Generated("ko-annotation-processor")`.
5. A `@KoServiceClient` field injection auto-wires the generated client.

### 10.3 TypeScript Client Generation

The `ko gen client --lang typescript` command reads the app model and generates:

```typescript
// Generated by ko gen — DO NOT EDIT

export interface CreateLessonRequest {
  title: string;
  language: string;
  difficulty?: Difficulty;
}

export interface LessonResponse {
  id: string;
  title: string;
  language: string;
}

export const lessonService = {
  createLesson: (req: CreateLessonRequest): Promise<LessonResponse> =>
    fetch('/lessons', { method: 'POST', body: JSON.stringify(req), headers: { 'Content-Type': 'application/json' } })
      .then(r => r.json()),

  getLesson: (id: string): Promise<LessonResponse> =>
    fetch(`/lessons/${id}`).then(r => r.json()),
};
```

---

## 11. Testing Strategy

### 11.1 Test Pyramid

```
         ┌──────────┐
         │   E2E    │  ← ko-e2e-tests: full lifecycle against sample apps
         │  Tests   │     (slow, nightly CI)
         ├──────────┤
         │Integration│  ← ko-runtime tests: real Testcontainers
         │  Tests   │     (per-module, per-PR CI)
         ├──────────┤
         │  Unit    │  ← All modules: fast, no I/O
         │  Tests   │     (per-module, per-PR CI)
         └──────────┘
```

### 11.2 Unit Tests

**Location:** `src/test/java/` in each module.

**Scope:** Pure logic — validators, model builders, serializers, in-memory providers.

**Framework:** JUnit 5 + AssertJ.

**Rules:**
- No network, no filesystem, no containers.
- Use `@ParameterizedTest` for validation edge cases.
- Mock external dependencies with Mockito.
- Target: < 2 seconds total per module.

```java
// Example: CronExpressionValidatorTest.java
@ParameterizedTest
@ValueSource(strings = {"0 * * * *", "30 2 * * MON-FRI", "0 0 1 1 *"})
void validCronExpressions(String expression) {
    assertThat(CronExpressionValidator.isValid(expression)).isTrue();
}

@ParameterizedTest
@ValueSource(strings = {"invalid", "* * * *", "60 * * * *", ""})
void invalidCronExpressions(String expression) {
    assertThat(CronExpressionValidator.isValid(expression)).isFalse();
}
```

### 11.3 Annotation Processor Tests

**Location:** `ko-processor/src/test/`

**Framework:** [google/compile-testing](https://github.com/google/compile-testing).

**Approach:** Feed the processor source files and assert on compilation success/failure and generated output.

```java
@Test
void validServiceGeneratesAppModel() {
    Compilation compilation = javac()
        .withProcessors(new KoAnnotationProcessor())
        .compile(JavaFileObjects.forSourceString("TestService", """
            @KoService("test-service")
            public class TestService {
                @KoAPI(method = "GET", path = "/hello")
                public String hello() { return "world"; }
            }
        """));

    assertThat(compilation).succeeded();
    assertThat(compilation).generatedFile(StandardLocation.CLASS_OUTPUT, "ko-app-model.json")
        .contentsAsString(UTF_8)
        .contains("\"name\": \"test-service\"");
}

@Test
void invalidCronFailsCompilation() {
    Compilation compilation = javac()
        .withProcessors(new KoAnnotationProcessor())
        .compile(JavaFileObjects.forSourceString("BadService", """
            @KoService("bad-service")
            public class BadService {
                @KoCron(schedule = "not-a-cron", name = "bad-job")
                public void run() {}
            }
        """));

    assertThat(compilation).failed();
    assertThat(compilation).hadErrorContaining("Invalid cron expression");
}
```

### 11.4 Integration Tests

**Location:** `ko-runtime/src/test/` and per-provider modules.

**Framework:** JUnit 5 + Testcontainers + `@KoTest` extension.

**Scope:** Real infrastructure — Postgres, Kafka, Redis in containers.

```java
@ExtendWith(KoTestExtension.class)
class KafkaPubSubProviderTest {

    @KoTestInfra
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.7.1"));

    @Test
    void publishAndSubscribe() {
        var provider = new KafkaPubSubProvider(kafka.getBootstrapServers());
        var received = new CompletableFuture<String>();

        provider.subscribe("test-topic", "test-sub", String.class, received::complete);
        provider.publish("test-topic", "hello");

        assertThat(received).succeedsWithin(Duration.ofSeconds(5)).isEqualTo("hello");
    }
}
```

### 11.5 End-to-End Tests

**Location:** `ko-e2e-tests/`

**Scope:** Full lifecycle — compile a sample app → build model → start with real infra → make HTTP requests → verify responses, DB state, published events.

**Execution:** Nightly CI pipeline (slower, requires Docker-in-Docker).

```java
@E2ETest
class FullLifecycleE2ETest {

    @Test
    void urlShortenerApp() {
        // 1. Compile the sample app
        var result = KoE2ERunner.compile("testapps/url-shortener");
        assertThat(result.appModel().services()).hasSize(1);
        assertThat(result.appModel().databases()).hasSize(1);

        // 2. Start with real infra
        try (var app = KoE2ERunner.start(result)) {
            // 3. Call the API
            var response = app.post("/url", Map.of("url", "https://example.com"));
            assertThat(response.statusCode()).isEqualTo(200);

            var body = response.bodyAs(ShortenResponse.class);
            assertThat(body.id()).hasSize(8);

            // 4. Verify the redirect works
            var getResponse = app.get("/url/" + body.id());
            assertThat(getResponse.bodyAs(UrlResponse.class).url()).isEqualTo("https://example.com");

            // 5. Verify DB state directly
            var dbRow = app.query("lessons", "SELECT * FROM urls WHERE id = ?", body.id());
            assertThat(dbRow).isNotNull();
        }
    }
}
```

### 11.6 Architecture Tests (ArchUnit)

**Location:** `ko-runtime/src/test/java/dev/ko/runtime/ArchitectureTest.java`

**Purpose:** Enforce module boundaries and dependency rules.

```java
@AnalyzeClasses(packages = "dev.ko")
class ArchitectureTest {

    @ArchTest
    static final ArchRule annotationsHaveNoDeps = noClasses()
        .that().resideInAPackage("dev.ko.annotations..")
        .should().dependOnClassesThat().resideInAPackage("dev.ko.runtime..");

    @ArchTest
    static final ArchRule providersDontLeakImpl = noClasses()
        .that().resideInAPackage("dev.ko.runtime.pubsub..")
        .and().haveSimpleNameEndingWith("Provider")
        .and().doNotHaveSimpleName("KoPubSubProvider")
        .should().bePublic();

    @ArchTest
    static final ArchRule noSpringInAnnotations = noClasses()
        .that().resideInAPackage("dev.ko.annotations..")
        .should().dependOnClassesThat().resideInAPackage("org.springframework..");
}
```

### 11.7 Test Naming Convention

```
{MethodUnderTest}_{StateOrInput}_{ExpectedBehavior}

Examples:
  publish_validMessage_deliveredToSubscriber
  isValid_emptyExpression_returnsFalse
  resolveHandler_unknownService_throwsKoError
  call_serviceDown_retriesThreeTimes
```

---

## 12. Git Workflow

### 12.1 Branch Strategy

```
main                    ← always deployable, protected
  ├── develop           ← integration branch (optional, for larger teams)
  ├── feat/ko-42-pubsub-provider    ← feature branches
  ├── fix/ko-67-cron-validation     ← bug fixes
  ├── refactor/ko-80-runtime-loader ← refactors
  ├── docs/ko-90-migration-guide    ← documentation
  └── release/v0.2.0               ← release preparation
```

### 12.2 Branch Naming

```
{type}/ko-{issue-number}-{short-description}

Types: feat, fix, refactor, docs, test, chore, ci
Examples:
  feat/ko-12-kafka-pubsub-provider
  fix/ko-34-hikari-pool-leak
  docs/ko-56-getting-started-guide
  test/ko-78-e2e-multi-service
```

### 12.3 Commit Convention

Follow [Conventional Commits](https://www.conventionalcommits.org/):

```
{type}({scope}): {description}

[optional body]

[optional footer(s)]
```

**Types:** `feat`, `fix`, `refactor`, `docs`, `test`, `chore`, `ci`, `perf`, `build`

**Scopes:** `annotations`, `processor`, `runtime`, `cli`, `dashboard`, `gradle`, `test`, `e2e`, `deps`

**Examples:**

```
feat(runtime): add Kafka PubSub provider with KRaft support

Implements KafkaPubSubProvider with:
- Producer with idempotent writes
- Consumer group management per subscription
- Graceful shutdown with wakeup()
- Configurable batch size and poll interval

Closes #42

fix(processor): validate cron expressions at compile time

The processor now fails the build if a @KoCron annotation
contains an invalid 5-field cron expression.

Fixes #67

docs(guides): add "Adding a New Primitive" guide

Covers the full workflow: annotation → scanner → validator →
emitter → provider interface → local impl → test.
```

### 12.4 Pull Request Process

1. **Create branch** from `main` following naming convention.
2. **Develop** with small, focused commits.
3. **Push** and open PR using the PR template.
4. **CI checks** must pass: lint, unit tests, integration tests, build.
5. **Review** — at least 1 approval required.
6. **Squash merge** into `main` with a clean conventional commit message.
7. **Delete** the feature branch after merge.

### 12.5 PR Template

```markdown
## Summary

<!-- What does this PR do? Link to issue. -->

Closes #<issue-number>

## Type of Change

- [ ] feat: new feature
- [ ] fix: bug fix
- [ ] refactor: code change (no new feature, no bug fix)
- [ ] docs: documentation only
- [ ] test: adding or fixing tests
- [ ] chore: tooling, deps, CI

## Module(s) Changed

- [ ] ko-annotations
- [ ] ko-processor
- [ ] ko-runtime
- [ ] ko-gradle-plugin
- [ ] ko-test
- [ ] ko-cli
- [ ] ko-dashboard
- [ ] ko-e2e-tests

## Checklist

- [ ] Tests added/updated for the change
- [ ] Documentation updated (if applicable)
- [ ] No breaking changes (or migration guide added)
- [ ] `./gradlew check` passes locally
- [ ] ADR created (if architectural decision)

## How to Test

<!-- Steps to manually verify this change -->
```

### 12.6 CI Pipeline (`.github/workflows/ci.yml`)

```yaml
name: CI

on:
  push:
    branches: [main]
  pull_request:
    branches: [main]

jobs:
  lint:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'graalvm'
      - run: ./gradlew spotlessCheck

  unit-tests:
    runs-on: ubuntu-latest
    needs: lint
    strategy:
      matrix:
        module: [ko-annotations, ko-processor, ko-runtime, ko-gradle-plugin, ko-test]
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'graalvm'
      - run: ./gradlew :${{ matrix.module }}:test
      - uses: actions/upload-artifact@v4
        if: failure()
        with:
          name: test-reports-${{ matrix.module }}
          path: ${{ matrix.module }}/build/reports/tests/

  integration-tests:
    runs-on: ubuntu-latest
    needs: unit-tests
    services:
      docker:
        image: docker:dind
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'graalvm'
      - run: ./gradlew integrationTest

  build-cli:
    runs-on: ubuntu-latest
    needs: unit-tests
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-go@v5
        with:
          go-version: '1.23'
      - run: cd ko-cli && go build -o ko ./cmd/ko
      - uses: actions/upload-artifact@v4
        with:
          name: ko-cli-linux
          path: ko-cli/ko

  build-dashboard:
    runs-on: ubuntu-latest
    needs: lint
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with:
          node-version: '22'
      - run: cd ko-dashboard && npm ci && npm run build

  all-checks:
    runs-on: ubuntu-latest
    needs: [integration-tests, build-cli, build-dashboard]
    steps:
      - run: echo "All checks passed"
```

### 12.7 Nightly E2E (`.github/workflows/nightly.yml`)

```yaml
name: Nightly E2E

on:
  schedule:
    - cron: '0 3 * * *'  # 3 AM UTC
  workflow_dispatch:

jobs:
  e2e:
    runs-on: ubuntu-latest
    timeout-minutes: 30
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'graalvm'
      - run: ./gradlew :ko-e2e-tests:test
```

---

## 13. Feature Development Guide

### 13.1 Adding a New Infrastructure Primitive

Example: Adding `@KoQueue` for task queues.

**Step 1 — Annotation** (`ko-annotations`)

```java
// ko-annotations/src/main/java/dev/ko/annotations/KoQueue.java
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface KoQueue {
    String name();
    int maxRetries() default 3;
    String deadLetter() default "";
}
```

**Step 2 — Model** (`ko-processor`)

```java
// ko-processor/src/main/java/dev/ko/processor/model/QueueModel.java
public record QueueModel(
    String name,
    int maxRetries,
    String deadLetter
) {}
```

Add `List<QueueModel> queues` field to `ServiceModel` and `AppModel`.

**Step 3 — Scanner** (`ko-processor`)

```java
// ko-processor/src/main/java/dev/ko/processor/scanner/QueueScanner.java
public class QueueScanner {
    public List<QueueModel> scan(TypeElement serviceClass) {
        return serviceClass.getEnclosedElements().stream()
            .filter(e -> e.getAnnotation(KoQueue.class) != null)
            .map(e -> {
                KoQueue ann = e.getAnnotation(KoQueue.class);
                return new QueueModel(ann.name(), ann.maxRetries(), ann.deadLetter());
            })
            .toList();
    }
}
```

**Step 4 — Validator** (`ko-processor`)

```java
// Validate queue names are kebab-case, dead letter references exist, etc.
```

**Step 5 — Provider Interface** (`ko-runtime`)

```java
public interface KoQueueProvider {
    <T> void enqueue(String queue, T task);
    <T> void process(String queue, Class<T> type, Consumer<T> handler);
}
```

**Step 6 — Local Implementation** (`ko-runtime`)

```java
public class InMemoryQueueProvider implements KoQueueProvider {
    private final Map<String, BlockingQueue<Object>> queues = new ConcurrentHashMap<>();
    // ...
}
```

**Step 7 — Developer-Facing API** (`ko-runtime`)

```java
public class KoQueue<T> {
    public void enqueue(T task);
    public CompletableFuture<Void> enqueueAsync(T task);
}
```

**Step 8 — Auto-Configuration** (`ko-runtime`)

Wire the provider based on `infra-config.json`.

**Step 9 — CLI Support** (`ko-cli`)

Update `ko run` to provision a queue backend container if needed.

**Step 10 — Tests**

- Unit: validator tests, in-memory provider tests
- Integration: real provider tests (e.g., SQS Testcontainer)
- E2E: sample app with queue usage

**Step 11 — Documentation**

- Update `docs/reference/annotations.md`
- Update `docs/reference/infra-config-schema.md`
- Update `docs/reference/app-model-schema.md`
- Add entry to CHANGELOG.md

### 13.2 Adding a New Provider (e.g., AWS SQS for Pub/Sub)

1. Create a new module `ko-provider-aws-sqs/` or add to `ko-runtime` if core.
2. Implement `KoPubSubProvider` interface.
3. Add `"aws_sns_sqs"` case to `KoPubSubAutoConfig`.
4. Add `aws_sns_sqs` section to `infra-config.json` schema.
5. Write integration tests with LocalStack Testcontainer.
6. Update docs.

### 13.3 Feature Branch Workflow

```bash
# 1. Create issue on GitHub with the primitive_request template
# 2. Create branch
git checkout -b feat/ko-42-task-queue-primitive

# 3. Implement following the steps above
# 4. Commit incrementally
git commit -m "feat(annotations): add @KoQueue annotation"
git commit -m "feat(processor): add QueueScanner and QueueModel"
git commit -m "feat(runtime): add KoQueueProvider and InMemoryQueueProvider"
git commit -m "test(processor): add @KoQueue compile-testing tests"
git commit -m "test(runtime): add InMemoryQueueProvider unit tests"
git commit -m "docs(reference): document @KoQueue annotation and config"

# 5. Push and create PR
git push -u origin feat/ko-42-task-queue-primitive
```

---

## 14. Documentation Standards

### 14.1 Code Documentation

**Public API classes and methods** — Always add Javadoc:

```java
/**
 * Publishes a message to the configured topic.
 *
 * <p>The message is serialized to JSON and delivered according to the
 * topic's {@link DeliveryGuarantee}. For {@code AT_LEAST_ONCE}, the
 * method blocks until the broker acknowledges receipt.</p>
 *
 * @param message the message payload (must be serializable to JSON)
 * @throws KoError if publishing fails after retries
 */
public void publish(T message) { ... }
```

**Internal classes** — Brief doc comment explaining purpose:

```java
/** Scans a @KoService class for @KoAPI method annotations. */
class ApiScanner { ... }
```

### 14.2 Architecture Decision Records (ADRs)

Every significant architectural decision gets an ADR in `docs/architecture/adrs/`.

**Template: `docs/architecture/adrs/template.md`**

```markdown
# ADR-{number}: {Title}

## Status

{Proposed | Accepted | Deprecated | Superseded by ADR-xxx}

## Date

{YYYY-MM-DD}

## Context

{What is the issue or decision we need to make?}

## Decision

{What did we decide and why?}

## Alternatives Considered

{What other options were evaluated?}

### Option A: {Name}
- Pros: ...
- Cons: ...

### Option B: {Name}
- Pros: ...
- Cons: ...

## Consequences

{What are the positive and negative consequences of this decision?}

## References

{Links to relevant issues, PRs, docs, or external resources.}
```

### 14.3 Guide Structure

Guides in `docs/guides/` follow this structure:

```markdown
# {Guide Title}

## Prerequisites
{What you need before starting}

## Overview
{What you'll learn / accomplish}

## Step 1: {First Step}
{Instructions with code examples}

## Step 2: {Second Step}
...

## Verification
{How to verify your work}

## Troubleshooting
{Common problems and solutions}

## Next Steps
{What to do after completing this guide}
```

### 14.4 Changelog Convention

Follow [Keep a Changelog](https://keepachangelog.com/):

```markdown
# Changelog

## [Unreleased]

### Added
- `@KoQueue` annotation for task queue primitives (#42)

### Changed
- KoPubSubProvider now supports batch publishing (#55)

### Fixed
- HikariCP pool leak when database connection times out (#67)

### Deprecated
- `KoCache.put()` — use `KoCache.set()` instead (#71)

## [0.2.0] - 2026-06-15

### Added
- Kafka PubSub provider (#42)
- Redis cache provider (#48)
...
```

---

## 15. Build & Release

### 15.1 Build Commands

```bash
# Full build (all modules)
./gradlew build

# Single module
./gradlew :ko-runtime:build

# Run all tests
./gradlew check

# Run unit tests only
./gradlew test

# Run integration tests only (requires Docker)
./gradlew integrationTest

# Run E2E tests (requires Docker)
./gradlew :ko-e2e-tests:test

# Generate app model from a sample app
./gradlew :examples:hello-world:koGenModel

# Build CLI (Go)
cd ko-cli && go build -o ko .

# Build CLI native image (GraalVM alternative)
./gradlew :ko-cli-java:nativeCompile

# Build dashboard
cd ko-dashboard && npm run build

# Code formatting
./gradlew spotlessApply

# Lint check
./gradlew spotlessCheck
```

### 15.2 Release Process

Releases follow semver (`MAJOR.MINOR.PATCH`):

```bash
# 1. Create release branch
git checkout -b release/v0.2.0

# 2. Update version in gradle.properties
echo "version=0.2.0" > gradle.properties

# 3. Update CHANGELOG.md (move Unreleased to versioned section)

# 4. Commit
git commit -am "chore: prepare release v0.2.0"

# 5. Merge to main
git checkout main && git merge release/v0.2.0

# 6. Tag
git tag -a v0.2.0 -m "Release v0.2.0"
git push origin main --tags

# 7. CI release pipeline publishes:
#    - Java modules → Maven Central
#    - Gradle plugin → Gradle Plugin Portal
#    - CLI binaries → GitHub Releases + Homebrew
#    - Dashboard → bundled into CLI binary
```

### 15.3 GraalVM Native Image (Optional)

For services that need fast startup (serverless, CLI):

```kotlin
// build.gradle.kts
plugins {
    id("org.graalvm.buildtools.native") version "0.10.4"
}

graalvmNative {
    binaries {
        named("main") {
            imageName.set("ko-service")
            mainClass.set("dev.ko.runtime.KoApplication")
            buildArgs.add("--enable-preview")
            buildArgs.add("-H:+ReportExceptionStackTraces")
        }
    }
}
```

---

## 16. Implementation Phases

### Phase 1: Foundation (Weeks 1-6)

**Goal:** Annotations + Annotation Processor + Application Model generation.

**Deliverables:**
- [ ] `ko-annotations` module with all annotation definitions
- [ ] `ko-processor` module with scanners, validators, and `AppModelEmitter`
- [ ] Compile-testing test suite for the processor
- [ ] `ko-gradle-plugin` that registers the processor and `koGenModel` task
- [ ] Working `ko-app-model.json` generation from annotated code
- [ ] ADR-001: Annotation Processing over Reflection

**Exit criteria:** Annotate a Bawo service class → run `./gradlew build` → valid `ko-app-model.json` appears in build output.

### Phase 2: Runtime Engine (Weeks 7-12)

**Goal:** Spring Boot auto-configuration that reads the model and wires infrastructure.

**Deliverables:**
- [ ] `ko-runtime` module with all provider interfaces
- [ ] Local providers: `InMemoryPubSubProvider`, `HikariDatabaseProvider` (embedded Postgres), `InMemoryCacheProvider`
- [ ] `InfraConfigLoader` and `AppModelLoader`
- [ ] `KoServiceCaller` with `InProcessCaller`
- [ ] `KoAutoConfiguration` that wires everything
- [ ] `ko-test` module with `KoTestExtension`
- [ ] Integration tests for each provider

**Exit criteria:** Annotated service starts with `./gradlew bootRun`, connects to local Postgres, publishes/subscribes to in-memory Pub/Sub.

### Phase 3: CLI MVP (Weeks 13-16)

**Goal:** `ko run` with Testcontainers orchestration and hot reload.

**Deliverables:**
- [ ] `ko-cli` Go module with `ko run` and `ko build docker`
- [ ] Daemon process managing Testcontainer lifecycle
- [ ] Local `infra-config.json` generation from container ports
- [ ] File watcher with Gradle continuous build integration
- [ ] `ko test` command

**Exit criteria:** Run `ko run` → Postgres, Kafka, Redis containers start → app starts → APIs callable at localhost.

### Phase 4: Observability (Weeks 17-22)

**Goal:** Tracing + Dev Dashboard.

**Deliverables:**
- [ ] `KoTracingInterceptor` with OpenTelemetry spans
- [ ] CLI daemon API for traces, model, and DB queries
- [ ] `ko-dashboard` React SPA with architecture graph, API explorer, trace viewer
- [ ] `ko dashboard` command to open browser

**Exit criteria:** Make API call → see trace in dashboard with DB queries and Pub/Sub events → view architecture diagram.

### Phase 5: Code Generation (Weeks 23-25)

**Goal:** Service clients + TypeScript client + OpenAPI.

**Deliverables:**
- [ ] `ServiceClientEmitter` generating Java client classes
- [ ] `OpenApiEmitter` generating OpenAPI 3.1 spec
- [ ] `ko gen client --lang typescript` command
- [ ] `@KoServiceClient` injection support

**Exit criteria:** Call another service via generated client → works in-process locally and over HTTP when deployed separately.

### Phase 6: Production Providers (Weeks 26-31)

**Goal:** Kafka, Redis, S3, Cloud SQL providers.

**Deliverables:**
- [ ] `KafkaPubSubProvider` with KRaft support
- [ ] `RedisCacheProvider` with Lettuce
- [ ] `S3StorageProvider` and `GcsStorageProvider`
- [ ] `HttpServiceCaller` with retry, circuit breaker, tracing propagation
- [ ] Production infra-config.json examples for AWS, GCP, Hetzner
- [ ] E2E test suite against sample apps

**Exit criteria:** Bawo deploys to Hetzner Kubernetes cluster using `ko build docker` with a production infra config, connects to real Postgres, Kafka, Redis.

---

## 17. ADR Log

| # | Title | Status | Date |
|---|-------|--------|------|
| 001 | Annotation Processing over Reflection | Accepted | TBD |
| 002 | Spring Boot as Runtime Base | Accepted | TBD |
| 003 | Go CLI over GraalVM native-image | Proposed | TBD |
| 004 | In-memory Pub/Sub for Local Dev | Accepted | TBD |
| 005 | JSON Application Model over Binary Format | Accepted | TBD |
| 006 | Testcontainers over Embedded DBs | Proposed | TBD |
| 007 | Provider Interface per Infrastructure Type | Accepted | TBD |
| 008 | InProcess vs HTTP Service Caller Selection | Proposed | TBD |

---

## 18. Glossary

| Term | Definition |
|------|-----------|
| **Application Model** | The JSON graph (`ko-app-model.json`) generated at compile time that describes your entire system: services, APIs, databases, topics, caches, cron jobs, and their relationships. |
| **Infrastructure Primitive** | A declarative annotation (`@KoDatabase`, `@KoPubSub`, etc.) that declares what infrastructure a service needs without specifying how to provision it. |
| **Provider** | An implementation of a provider interface (e.g., `KafkaPubSubProvider`) that connects a logical resource to a physical backend. |
| **Provider Interface** | A Java interface (e.g., `KoPubSubProvider`) that abstracts the specific backend behind a logical infrastructure primitive. |
| **Infra Config** | A JSON file (`infra-config.json`) that maps logical resources to physical infrastructure for a specific environment. |
| **Service Caller** | The routing layer (`KoServiceCaller`) that handles service-to-service communication — in-process for monolith, HTTP for microservices. |
| **Semantic Infrastructure** | Infrastructure declared by what it does (a database, a topic), not how it's provisioned (RDS, Cloud SQL, local Postgres). |
| **Testcontainers** | Docker containers automatically provisioned by the CLI for local development — Postgres, Kafka, Redis, MinIO. |

---

## CLAUDE.md Instructions

When working on this codebase with Claude Code, the following instructions apply:

```
# Kọ́ Framework — Claude Code Instructions

## Project Context
This is a Java framework (inspired by Encore.dev) for building distributed systems
with declarative infrastructure. See docs/architecture/ARCHITECTURE.md for full details.

## Build Commands
- `./gradlew build` — full build
- `./gradlew :ko-runtime:test` — test single module
- `./gradlew spotlessApply` — format code
- `cd ko-cli && go build -o ko .` — build CLI

## Code Style
- Java 21+ features: records, sealed interfaces, pattern matching, virtual threads
- Conventional Commits for all commit messages
- All public APIs must have Javadoc
- Tests use JUnit 5 + AssertJ naming: {method}_{input}_{expected}

## Module Rules
- ko-annotations: ZERO external dependencies
- ko-processor: no Spring dependency, only javax.annotation.processing + JavaPoet
- ko-runtime: Spring Boot allowed, all providers must implement their interface
- ko-test: depends on ko-runtime, provides JUnit 5 extensions

## When Adding Features
1. Read docs/guides/adding-a-primitive.md for new @Ko* annotations
2. Read docs/guides/adding-a-provider.md for new infrastructure backends
3. Always create tests (unit + integration) before marking complete
4. Update docs/reference/ and CHANGELOG.md

## Architecture Decisions
- Create an ADR in docs/architecture/adrs/ for any significant decision
- Use the template at docs/architecture/adrs/template.md
```
