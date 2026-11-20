# mock-data-gen verification map

This directory is the maintained source for verifying the user-facing behavior of `mock-data-gen` through `example-app`. Read this index before driving the application, then follow the matching feature recipe.

## Baseline preconditions

- Build `:example-app:bootJar` and launch an isolated JVM instance on `127.0.0.1` via `python .cursor/skills/verify-mock-data-gen/scripts/verify_app.py launch --run-id <run-id> --profile local`.
- Use a unique `--run-id` and let `launch` pick an unused port (or pass a dedicated `--port`) so concurrent runs do not collide.
- No database, seed files, or auth credentials are required; `example-app` is stateless.
- Run `python .cursor/skills/verify-mock-data-gen/scripts/verify_app.py doctor --run-id <run-id>` and require `"healthy": true` before driving features.
- Never drive an instance that was not started by this verification run.

## Driving conventions

- Start every recipe from the baseline `local` instance unless its preconditions specify `dev`, `prod`, or `--app-arg` overrides.
- Prefer stable HTTP routes (`/api/v1/sensors/current`, `/api/v1/sensors/timeseries`, `/v3/api-docs`), query parameter names, and the `X-Mock-Data` header over internal Spring bean calls.
- Treat every command and URL query string as literal. Keep quoted headers and ISO-8601 timestamps URL-safe (`+` encoded as `%2B` when using offsets).
- Drive HTTP checks with `verify_app.py request` (or `curl.exe` / `curl`).
- Cleanup terminates only the recorded PID and deletes `build/verify-runs/<run-id>/`. Never remove proof artifacts under `artifacts/verify-mock-data-gen/<run-id>/`.

## Proof and skip reporting

- Capture both the request (URL, method, headers) and the resulting HTTP status, headers, and JSON body.
- Every mock feature proof includes the matching operational fallback check (`X-Mock-Data` absent or `false` returning `mock: false` and `source: "LIVE_STORE"`).
- Record the feature ID and entry point used in the evidence filename or summary.
- Report an unreachable path with the attempted HTTP request, status code, and unmet precondition.
- Do not report a skipped entry point or profile as verified through a different profile or unit test.

## Feature entry contract

Each feature file starts with an H1 title and one paragraph describing the user-visible behavior. It then uses exactly four H2 sections in this order.

1. `Sub-features` lists short IDs with one line for each behavior.
2. `How to get to it (user POV)` lists every user entry point.
3. `Driving it with verify_app.py` starts with `Preconditions:` and uses labeled bullets that pair each user action with an exact command and observable result.
4. `Gotchas` lists traps that can waste or invalidate a verification run.

Keep implementation internals out of the map. Name only user routes, headers, query parameters, profile flags, commands, and observable proof.

## Features

- [Current sensor values](./current-sensor-values.md) covers `GET /api/v1/sensors/current` mock generation, quantization, multi-sensor ordering, and operational fallback.
- [Time-series sensor data](./timeseries-sensor-data.md) covers `GET /api/v1/sensors/timeseries` ISO-8601/Epoch-ms parsing, grid alignment, sliding-window continuity, current-to-series consistency, and operational fallback.
- [Profile and URI isolation](./profile-and-uri-isolation.md) covers `local`/`dev` activation vs `prod` isolation, `ees.mock.sensor.enabled=false`, and custom URI overrides.
- [OpenAPI and error isolation](./openapi-and-error-isolation.md) covers `/v3/api-docs` `X-Mock-Data` parameter visibility and controller-scoped `400 Bad Request` `MockErrorResponse` isolation.
