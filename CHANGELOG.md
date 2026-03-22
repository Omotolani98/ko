# Changelog

All notable changes to the Kọ́ framework will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.5.0] - 2026-03-22

### Added

- **`ko upgrade` command** — upgrades the Ko framework version in the current project. Updates plugin version, all `ko-*` dependency versions, and `gradle/libs.versions.toml` version catalog in one pass. Supports `ko upgrade` (latest from Maven Central) or `ko upgrade <version>` (specific version).
- **Automatic migration from old IDs** — `ko upgrade` detects and migrates the old `dev.ko` plugin ID to `io.github.omotolani98.ko` and old `dev.ko:ko-*` dependency group to `io.github.omotolani98:ko-*`.

### Fixed

- **Gradle plugin version** — `ko-gradle-plugin` now uses the root project version instead of a hardcoded snapshot, ensuring `publishToMavenLocal` publishes the correct version.
- **Build artifacts cleaned from repo** — removed 95 accidentally tracked `bin/` class files and `.factorypath`; added `bin/` and `.factorypath` to `.gitignore`.

## [0.4.0] - 2026-03-22

### Added

- **Distributed tracing** — lightweight OTel-compatible tracing across the entire request lifecycle. `KoTracingInterceptor` automatically creates root spans for every API request. `KoSQLDatabase`, `KoTopic`, and `InProcessCaller` create child spans for database queries, pubsub publishes, and service-to-service calls respectively.
- **Tracing context propagation** — `TracingContext` uses ThreadLocal to propagate trace/span IDs across the call chain within a request. Child spans are automatically linked to their parent.
- **Async span collector** — `KoSpanCollector` batches spans in a `ConcurrentLinkedQueue` and flushes them every 500ms via HTTP POST to the dashboard server. Zero overhead on the hot path.
- **Tracing auto-configuration** — `KoTracingAutoConfiguration` registers the tracing interceptor and span collector as Spring beans. Reads dashboard port from `KO_DASHBOARD_PORT` env var (default: 9400). Excludes `/actuator/**` and `/error` paths.
- **Go trace store** — in-memory ring buffer (max 1000 traces) in the CLI dashboard server. Ingests spans from the Java runtime, groups by trace ID, computes trace duration and error status.
- **Trace API endpoints** — `POST /api/traces/ingest` (receives spans from runtime), `GET /api/traces?limit=N` (lists recent trace summaries), `GET /api/traces/{id}` (returns full trace with all spans).
- **Trace viewer UI** — full `TracesPage` replacing the previous stub. Auto-refreshes every 2 seconds. Shows trace list with status indicator, root operation, service name, span count, and color-coded duration (green < 100ms, amber < 500ms, red > 500ms). Click to expand inline waterfall view.
- **Waterfall visualization** — `TraceWaterfall` component renders spans as a hierarchical tree with positioned timing bars. Color-coded by span kind: violet (API), emerald (Database), orange (PubSub), purple (Service Call). Click any span to view detailed attributes.
- **Span detail panel** — `SpanDetail` component shows span ID, kind, service, duration, parent span, and all attributes. SQL queries are syntax-highlighted in a code block.

## [0.3.0] - 2026-03-22

### Added

- **File watcher with auto-reload** — `ko run` now watches Java source files, resources, and Gradle build configs for changes. On save, the project is automatically rebuilt and the application model is regenerated. Spring DevTools picks up class changes for instant restart. Debounced (500ms) to avoid redundant builds on rapid saves.
- **Daemon coordinator** — `ko run` is now managed by a central daemon that orchestrates the full lifecycle: build, provision infrastructure, generate config, start dashboard, start file watcher, and run the app. Supports graceful shutdown of all components.
- **Docker infrastructure manager** — when Docker is available, `ko run --containers` (default: true) provisions real Postgres, Redis, Kafka (KRaft), and MinIO containers with random port mapping. Health checks ensure containers are ready before the app starts. Falls back to in-memory providers when Docker is unavailable.
- **Unix socket daemon API** — a JSON-line protocol server at `~/.ko/daemon.sock` exposes `status`, `model`, `containers`, and `rebuild` methods for inter-process communication.
- **`ko status` command** — queries the running daemon over the Unix socket and displays state, uptime, app/dashboard ports, watch status, and running containers.
- **`ko run --watch` flag** — enables/disables file watching (default: true).
- **`ko run --containers` flag** — enables/disables Docker container provisioning (default: true).
- **`ko run --dashboard-port` flag** — configures the dev dashboard port (default: 9400).
- **Thread-safe dashboard model updates** — the dashboard server now supports live model updates on rebuild via `sync.RWMutex`.

## [0.2.0] - 2026-03-22

### Added

- **Dev dashboard** — React + Vite + TypeScript + Tailwind CSS + D3.js single-page application served by `ko run` on port 9400. Includes architecture graph (force-directed D3 visualization), API explorer with request builder and response viewer, service catalog, and stub pages for traces and database explorer.
- **`ko dashboard` command** — opens the dev dashboard in the default browser.
- **Dashboard HTTP server** — Go HTTP server embedded in CLI via `//go:embed`, serving `/api/model`, `/api/health`, `/api/proxy/*` (reverse proxy to Spring Boot), and SPA fallback routing.
- **App model enhancements** — added `LoadAppModelRaw()` returning raw JSON bytes, and new fields: `expose`, `request_type`, `response_type`, `javadoc` on API endpoints; `TypeInfo`/`FieldInfo` structs; `message_type` on pubsub topics; `migrations` on database refs.

## [0.1.2] - 2026-03-22

### Fixed

- `ko run --port` now correctly passes the port to Spring Boot via `--args=--server.port=N`.

### Added

- Documentation for `ko app` command in CLI reference and site docs.

## [0.1.1] - 2026-03-22

### Added

- Auto-resolve latest Ko version from Maven Central in `ko-gradle-plugin`.

## [0.1.0] - 2026-03-22

### Added

- Initial release with Maven Central publishing.
- `ko-annotations` — 12 annotation definitions (`@KoService`, `@KoAPI`, `@KoDatabase`, `@KoPubSub`, `@KoCache`, `@KoBucket`, `@KoCron`, `@KoSubscribe`, `@KoServiceClient`, etc.).
- `ko-processor` — compile-time annotation processor with scanners, validators, and emitters. Generates `ko-app-model.json` and service client classes.
- `ko-runtime` — Spring Boot auto-configuration with provider interfaces and local in-memory implementations for pubsub, cache, database, storage, and secrets.
- `ko-gradle-plugin` — registers annotation processor and `koGenModel` task.
- `ko-test` — JUnit 5 extension for test isolation with mock service clients.
- `ko-cli` — Go CLI with `ko run`, `ko build`, `ko test`, `ko gen`, `ko app`, `ko version` commands.
