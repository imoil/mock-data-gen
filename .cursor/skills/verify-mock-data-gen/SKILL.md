---
name: verify-mock-data-gen
description: Verification skill for mock-data-gen (example-app Spring Boot HTTP REST API and OpenAPI surface backed by mock-data-spring-boot-starter and mock-data-core). Reach for this whenever verifying live HTTP routing, deterministic mock waveforms, profile/URI isolation, OpenAPI X-Mock-Data documentation, or controller-scoped error isolation against a real running JVM.
---

# Verify `mock-data-gen` (`example-app`)

`mock-data-gen` is a Gradle multi-module Java 21 project (`mock-data-core`, `mock-data-spring-boot-starter`, `example-app`).

- **Primary user-facing surface:** The HTTP REST API and OpenAPI/Swagger UI served by `example-app` (`ExampleApplication`, Spring Boot 3.5.16 on embedded Tomcat). Users and consumers send `GET` requests to `/api/v1/sensors/current` and `/api/v1/sensors/timeseries` (optionally with header `X-Mock-Data: true` in `local`/`dev` profiles) and inspect `/v3/api-docs` or `/swagger-ui/index.html`.
- **Secondary library surface:** Pure Java `DeterministicMockGenerator` (`mock-data-core`) and Spring Boot auto-configuration/Maven publications (`mock-data-spring-boot-starter`), verified by `.\gradlew.bat clean check` (Windows) / `./gradlew clean check` (POSIX) and `publishToMavenLocal`.
- **Multi-instance isolation:** The mock engine is stateless (no database, no filesystem seed state, no authentication). Multiple verification instances run safely side by side by passing distinct `--run-id` values and distinct `--port` numbers (or omitting `--port` so `verify_app.py` binds a free localhost port). Each run isolates its runtime state and logs under `build/verify-runs/<run-id>/` and stores proof artifacts under `artifacts/verify-mock-data-gen/<run-id>/`.

Before driving individual features, read [features/README.md](./features/README.md) and the matching feature recipe.

## Launch

Build the executable Spring Boot fat JAR once and launch an isolated `example-app` instance via `verify_app.py` (uses `gradlew.bat :example-app:bootJar` on Windows or `./gradlew :example-app:bootJar` on POSIX, then runs `java -jar example-app/build/libs/example-app-0.1.0-SNAPSHOT.jar`):

```powershell
python .cursor/skills/verify-mock-data-gen/scripts/verify_app.py launch --run-id verify-1 --profile local
```

- **Ports & profiles:** `--port 0` (the default) auto-selects an unused `127.0.0.1` TCP port instead of colliding with default `:8080`. `--profile` accepts `local` (default), `dev`, or `prod`. Override properties with `--app-arg key=value` (e.g. `--app-arg ees.mock.sensor.base-path=/api/v2/telemetry`).
- **Seed data & migrations:** None required. Starting from an empty `build/verify-runs/<run-id>/` directory reaches a usable state directly.
- **Readiness criteria (all three must hold before `launch` succeeds):**
  1. `build/verify-runs/<run-id>/stdout.log` contains `Started ExampleApplication in`.
  2. `GET http://127.0.0.1:<port>/v3/api-docs` returns HTTP `200`.
  3. `GET http://127.0.0.1:<port><basePath><currentPath>?sensorIds=READY-PROBE` with `X-Mock-Data: true` returns HTTP `200` with JSON containing `"mock"`.
- **Teardown on failed launch:** If readiness times out or the JVM exits early, `verify_app.py launch` kills only the PID it spawned and prints log tails to stderr.
- **Reuse existing JAR:** Pass `--skip-build` on subsequent launches when source files have not changed.

## Doctor

Run this read-only health check whenever anything looks off or before starting a feature drive:

```powershell
python .cursor/skills/verify-mock-data-gen/scripts/verify_app.py doctor --run-id verify-1
```

`doctor` exits `0` and prints `"healthy": true` only when:

1. The recorded `pid` in `build/verify-runs/<run-id>/state.json` is alive.
2. `<port>` is owned by that exact `pid` (via `Get-NetTCPConnection` on Windows or `lsof` on POSIX, with TCP connect fallback).
3. `example-app/build/libs/example-app-*.jar` still matches the recorded SHA-256 digest.
4. `stdout.log` confirms `Started ExampleApplication in`, `Tomcat started on port <port>`, and the expected active/default Spring profile.
5. `GET /v3/api-docs` returns `200` and includes `X-Mock-Data` header documentation iff `profile` is `local`/`dev` and `ees.mock.sensor.enabled` is `true`.
6. `GET <basePath><currentPath>?sensorIds=DOCTOR-PROBE` with `X-Mock-Data: true` returns `200` with `"mock": true` (`local`/`dev`) or `"mock": false` (`prod`).

## Drive

Read `baseUrl` (`http://127.0.0.1:<port>`) from `launch` output or `build/verify-runs/<run-id>/state.json`. Drive real HTTP requests using `verify_app.py request` (which also records evidence) or `curl.exe` / `curl`:

- **Current sensor mock values (`local`/`dev`):**
  ```powershell
  python .cursor/skills/verify-mock-data-gen/scripts/verify_app.py request --run-id verify-1 --path "/api/v1/sensors/current?sensorIds=TEMP-01,PRESS-02&intervalMs=1000" --header "X-Mock-Data: true" --expected-status 200 --save current-sensor-values/mock-current.json
  ```
- **Operational controller fallback (header omitted or `false`):**
  ```powershell
  python .cursor/skills/verify-mock-data-gen/scripts/verify_app.py request --run-id verify-1 --path "/api/v1/sensors/current?sensorIds=TEMP-01" --expected-status 200 --save current-sensor-values/live-fallback.json
  ```
- **Time-series mock values (ISO-8601 UTC or Epoch ms):**
  ```powershell
  python .cursor/skills/verify-mock-data-gen/scripts/verify_app.py request --run-id verify-1 --path "/api/v1/sensors/timeseries?sensorIds=TEMP-01,PRESS-02&from=2026-11-18T06:40:00Z&to=2026-11-18T06:45:00Z&intervalMs=60000" --header "X-Mock-Data: true" --expected-status 200 --save timeseries-sensor-data/iso-window.json
  ```
- **Direct `curl.exe` equivalent (PowerShell):**
  ```powershell
  curl.exe -sS -i -H "X-Mock-Data: true" "http://127.0.0.1:<port>/api/v1/sensors/current?sensorIds=TEMP-01,PRESS-02"
  ```
- **Gradle library & starter contract check (secondary surface):**
  ```powershell
  .\gradlew.bat clean check --console=plain
  ```

## Evidence

Store all proof artifacts under `artifacts/verify-mock-data-gen/<run-id>/` (outside `build/`, so neither `cleanup` nor `./gradlew clean` deletes them).

Proof standards:

- **Exercise the real user path:** Hit live Tomcat HTTP endpoints on the launched `example-app` process; never substitute `MockMvc` unit tests or internal helper calls for live HTTP verification.
- **Capture action and resulting state:** Every saved evidence JSON via `--save` records `runId`, `profile`, `port`, `capturedAt`, `request` (`method`, `url`, `headers`), and `response` (`status`, `headers`, `body`).
- **Verify routing boundaries alongside happy paths:** Every mock-data feature proof must also verify the negative/fallback boundary (e.g., omitting `X-Mock-Data` or setting `X-Mock-Data: false` yields `mock: false` and `source: "LIVE_STORE"` from `SensorController`, or `prod` profile ignores `X-Mock-Data: true`).
- **Preserve runtime logs when proving startup/profile behavior:** Copy `build/verify-runs/<run-id>/stdout.log` to `artifacts/verify-mock-data-gen/<run-id>/stdout.log` before cleanup if a feature claims log-level evidence.
- **No unverified dry-run assumptions:** `example-app` has no dry-run mode; `MockSensorController` is stateless and reads `Clock.systemUTC()` without mutating external files or databases.

## Cleanup

Tear down only the instance started by this run:

```powershell
python .cursor/skills/verify-mock-data-gen/scripts/verify_app.py cleanup --run-id verify-1
```

To clean up all active verification instances created by this harness:

```powershell
python .cursor/skills/verify-mock-data-gen/scripts/verify_app.py cleanup --all
```

- **Strict PID targeting:** `cleanup` reads `pid` from `build/verify-runs/<run-id>/state.json` and kills only that PID (`taskkill /PID <pid> /T /F` on Windows; `SIGTERM`/`SIGKILL` on POSIX). Never run `taskkill /IM java.exe` or `pkill java`.
- **Evidence preservation:** `cleanup` deletes `build/verify-runs/<run-id>/` (`state.json`, `stdout.log`, `stderr.log`) and leaves `artifacts/verify-mock-data-gen/<run-id>/` completely intact.

## Helpers

Executable script shipped with this skill: `.cursor/skills/verify-mock-data-gen/scripts/verify_app.py` (Python 3 stdlib only, cross-platform Windows/POSIX).

| Subcommand | Purpose | Example |
| :--- | :--- | :--- |
| `launch` | Build `:example-app:bootJar` (unless `--skip-build`), start JVM on isolated port & profile, wait for readiness | `python .cursor/skills/verify-mock-data-gen/scripts/verify_app.py launch --run-id r1 --profile local` |
| `doctor` | Read-only check of PID, port ownership, JAR hash, startup log, OpenAPI docs, and probe response | `python .cursor/skills/verify-mock-data-gen/scripts/verify_app.py doctor --run-id r1` |
| `request` | Send real HTTP GET with optional headers, assert status, and save request+response JSON proof | `python .cursor/skills/verify-mock-data-gen/scripts/verify_app.py request --run-id r1 --path "/api/v1/sensors/current?sensorIds=TEMP-01" --header "X-Mock-Data: true" --expected-status 200 --save proof.json` |
| `cleanup` | Terminate recorded PID, remove `build/verify-runs/<run-id>/`, preserve `artifacts/verify-mock-data-gen/<run-id>/` | `python .cursor/skills/verify-mock-data-gen/scripts/verify_app.py cleanup --run-id r1` |
