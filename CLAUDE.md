# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Kọ́ (Yoruba: "to build / to learn") is a Java 21+ framework for building type-safe distributed systems with declarative infrastructure annotations. Inspired by Encore.dev, built on Spring Boot 3.x. Organization: Mediawind. License: MPL-2.0.

Developers annotate code with `@KoService`, `@KoAPI`, `@KoDatabase`, `@KoPubSub`, etc. At compile time, an annotation processor builds an Application Model (`ko-app-model.json`) — a JSON graph of the entire system. The runtime engine reads this model and wires infrastructure providers per environment. The CLI orchestrates local dev with Testcontainers (no Docker Compose needed).

## Build Commands

```bash
./gradlew build                          # Full build (all modules)
./gradlew :ko-runtime:build             # Single module build
./gradlew :ko-runtime:test              # Single module tests
./gradlew check                          # All tests + lint
./gradlew test                           # Unit tests only
./gradlew integrationTest               # Integration tests (requires Docker)
./gradlew :ko-e2e-tests:test            # E2E tests (requires Docker)
./gradlew spotlessApply                  # Format code
./gradlew spotlessCheck                  # Lint check
./gradlew :examples:hello-world:koGenModel  # Generate app model from example

cd ko-cli && go build -o ko .            # Build CLI (Go)
cd ko-dashboard && npm ci && npm run build  # Build dashboard (React)
```

## Architecture

### Data Flow
Annotated Java code → Annotation Processor (compile-time) → `ko-app-model.json` → consumed by Runtime Engine, CLI, Dev Dashboard, Code Generators, Docker Builder.

### Module Dependency Chain
```
ko-annotations  (zero deps — pure annotation definitions)
      ↑
ko-processor    (ko-annotations, JavaPoet, Jackson)
      ↑
ko-runtime      (ko-annotations, Spring Boot, HikariCP, providers...)
      ↑
ko-gradle-plugin (ko-processor)
      ↑
ko-test         (ko-runtime, JUnit 5, Testcontainers)
      ↑
ko-e2e-tests    (ko-test, all provider modules)
```

### Key Modules
- **ko-annotations**: Annotation definitions only. Must have ZERO external dependencies. No Spring imports.
- **ko-processor**: Compile-time annotation processor. No Spring dependency — only `javax.annotation.processing` + JavaPoet. Contains scanners, validators, and emitters. Outputs `ko-app-model.json`, service client classes, and OpenAPI spec.
- **ko-runtime**: Spring Boot auto-configuration. Reads app model + `infra-config.json`, wires providers. Every infrastructure type follows the Provider Interface Pattern: interface → local in-memory impl → production impl (Kafka, Redis, S3, etc.) → auto-config selects based on infra config.
- **ko-cli**: Go binary (Cobra + Bubble Tea). `ko run` provisions Testcontainers, generates local infra-config, runs Gradle bootRun, serves dev dashboard. Daemon communicates over Unix socket at `~/.ko/daemon.sock`.
- **ko-dashboard**: React + Vite + D3.js SPA. Architecture graph, API explorer, trace viewer, DB explorer. Served by CLI daemon on :9400.
- **ko-test**: JUnit 5 extension (`KoTestExtension`) + Testcontainers lifecycle management + mock service clients.

### Provider Interface Pattern
Every infrastructure primitive (database, pubsub, cache, storage, secrets) follows: Provider interface → InMemory local impl → Production impl(s) → AutoConfig bean that selects based on `infra-config.json`.

### Service-to-Service Calls
`KoServiceCaller` routes calls: `InProcessCaller` (direct method invocation in monolith/local) or `HttpServiceCaller` (HTTP in production). Generated `{ServiceName}Client.java` classes delegate to this — same code works both ways.

## Code Conventions

- **Java 21+ features**: records, sealed interfaces, pattern matching, virtual threads
- **Commit messages**: Conventional Commits — `{type}({scope}): {description}`. Types: feat, fix, refactor, docs, test, chore, ci, perf, build. Scopes: annotations, processor, runtime, cli, dashboard, gradle, test, e2e, deps.
- **Branch naming**: `{type}/ko-{issue-number}-{short-description}`
- **Test naming**: `{MethodUnderTest}_{StateOrInput}_{ExpectedBehavior}` (e.g., `publish_validMessage_deliveredToSubscriber`)
- **Testing frameworks**: JUnit 5 + AssertJ for assertions, compile-testing for processor tests, Testcontainers for integration tests, ArchUnit for architecture enforcement
- **Annotation processor tests**: Use google/compile-testing — feed source files to processor, assert on compilation success/failure and generated output
- **Version catalog**: `gradle/libs.versions.toml` for all dependency versions

## Adding New Features

### New Infrastructure Primitive (`@Ko*` annotation)
Follow the 11-step process: annotation def (ko-annotations) → model record (ko-processor) → scanner → validator → provider interface (ko-runtime) → local in-memory impl → developer-facing API class → auto-configuration → CLI support → tests (unit + integration + E2E) → docs update. See `docs/guides/adding-a-primitive.md`.

### New Provider (e.g., AWS SQS)
Implement the provider interface → add case to auto-config → add infra-config schema → integration tests with Testcontainer (e.g., LocalStack) → docs. See `docs/guides/adding-a-provider.md`.

### Architecture Decisions
Create an ADR in `docs/architecture/adrs/` using `docs/architecture/adrs/template.md` for any significant decision.
