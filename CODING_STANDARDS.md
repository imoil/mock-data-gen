# Coding Standards

Review-time judgement calls for `mock-data-gen`. Automated rules (`-Xlint:all,-processing -Werror`, `verifyMavenPublication`, Java 21 release target) live in `build.gradle` and `.github/workflows/ci.yml`.

## Architecture & Module Boundaries

- **Pure Java Core (`mock-data-core`)**: Keep `mock-data-core` free of Spring and external runtime dependencies. Use plain Java 21 `record` types, builders, and explicit constructors without Lombok.
- **Starter Isolation (`mock-data-spring-boot-starter`)**:
  - Gate auto-configuration behind `@Profile({"local", "dev"})`, `@ConditionalOnWebApplication(type = SERVLET)`, and `app-custom-config.mock.sensor.enabled`.
  - Keep exception handling scoped inside `MockSensorController` (`@ExceptionHandler`); register zero global `@RestControllerAdvice` / `@ControllerAdvice` beans so host error policies remain untouched.
  - Isolate Springdoc OpenAPI customization in its own `@ConditionalOnClass` configuration class and enrich existing or new `X-Mock-Data` parameters through a single metadata helper.
  - Expose `spring-boot-starter-web` as `api` so external consumers referencing only `mock-data-spring-boot-starter` compile cleanly.

## Domain & Decision Alignment

- **Vocabulary (`GLOSSARY.md`)**: Use `SensorId`, `Sensor Seed`, `Pattern`, `Current Quantization`, `Grid Alignment`, and `X-Mock-Data` consistently across code, tests, and docs.
- **ADRs (`docs/adr/`)**: Preserve `timestampMs` deterministic noise input (`sensorSeed ^ timestampMs`) and `app-custom-config.mock.sensor.*` configuration naming unless a new ADR supersedes them.

## Testing & Review Scope

- **Public Seams**: Test `mock-data-core` through `DeterministicMockGenerator` (+ `Clock`) and `mock-data-spring-boot-starter` / `example-app` through `MockMvc` and `WebApplicationContextRunner` without mocking internal math/hash helpers.
- **Shared Test Suites**: Share multi-profile HTTP routing and OpenAPI contract suites via `AbstractProfileRoutingIntegrationTest` instead of duplicating test methods across profile test classes.
- **Log Scope**: `agent-log.md` is appended by the top-level coordinator at user-turn boundaries; ticket/feature subagent diffs should omit `agent-log.md` edits.
