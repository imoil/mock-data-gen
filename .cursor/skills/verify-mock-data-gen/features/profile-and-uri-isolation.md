# Profile and URI isolation

Profile and URI isolation guarantees that mock endpoints are active only in `local` and `dev` profiles when enabled, completely absent in `prod`, and configurable under custom URI paths without colliding with default routes.

## Sub-features

- `profile-dev-active` enables `MockSensorController` and `X-Mock-Data: true` routing under `--profile dev`.
- `profile-prod-isolated` omits all mock beans under `--profile prod` even when `ees.mock.sensor.enabled=true` is explicitly passed, routing `X-Mock-Data: true` to `SensorController`.
- `property-disabled` disables `MockSensorAutoConfiguration` in `local` when `ees.mock.sensor.enabled=false`.
- `uri-custom-override` serves both mock and operational traffic on custom base/sub-paths and returns `404 Not Found` on default `/api/v1/sensors/*` paths.

## How to get to it (user POV)

- Launch `example-app` with `--profile local`, `--profile dev`, or `--profile prod`.
- Launch `example-app` with `--app-arg ees.mock.sensor.enabled=false` or custom `--app-arg` path properties (`ees.mock.sensor.*` and `app-custom-config.sensor.*`).

## Driving it with verify_app.py

Preconditions:

- `:example-app:bootJar` is built (subsequent launches can use `--skip-build`).
- Each profile/URI variant uses its own `--run-id` and is cleaned up individually afterward.

- **Verify `dev` profile.** Launch `python .cursor/skills/verify-mock-data-gen/scripts/verify_app.py launch --run-id verify-dev --profile dev --skip-build` and run `doctor --run-id verify-dev`. Request `/api/v1/sensors/current?sensorIds=TEMP-01` with `X-Mock-Data: true` and save to `profile-and-uri-isolation/dev-mock.json`. Response returns `mock: true`. Clean up `verify-dev`.
- **Verify `prod` isolation.** Launch `python .cursor/skills/verify-mock-data-gen/scripts/verify_app.py launch --run-id verify-prod --profile prod --skip-build --app-arg ees.mock.sensor.enabled=true` and run `doctor --run-id verify-prod`. Request `/api/v1/sensors/current?sensorIds=TEMP-01` with `X-Mock-Data: true` and save to `profile-and-uri-isolation/prod-ignored.json`. Response returns `mock: false` and `source: "LIVE_STORE"`; `/v3/api-docs` contains no `X-Mock-Data` parameter. Clean up `verify-prod`.
- **Verify `enabled=false` in `local`.** Launch with `--profile local --skip-build --app-arg ees.mock.sensor.enabled=false` under `--run-id verify-disabled`, run `doctor --run-id verify-disabled`, and request `/api/v1/sensors/current?sensorIds=TEMP-01` with `X-Mock-Data: true`. Response returns `mock: false` and `source: "LIVE_STORE"`. Clean up `verify-disabled`.
- **Verify custom URI overrides.** Launch with `--run-id verify-uri --profile local --skip-build` and six `--app-arg` flags setting `app-custom-config.sensor.*` and `ees.mock.sensor.*` (`base-path=/api/v2/telemetry`, `current-path=/latest`, `timeseries-path=/history`). Request `/api/v2/telemetry/latest?sensorIds=TEMP-01` with `X-Mock-Data: true` (`200`, `mock: true`) and without the header (`200`, `mock: false`), then request `/api/v1/sensors/current?sensorIds=TEMP-01` with `--expected-status 404`. Save all proofs under `profile-and-uri-isolation/` and clean up `verify-uri`.

## Gotchas

- `SensorController` reads `app-custom-config.sensor.*`, while `MockSensorController` reads `ees.mock.sensor.*`. Overriding only `ees.mock.sensor.*` leaves the operational controller on `/api/v1/sensors/*` while moving only mock routes.
- Do not reuse the same `--run-id` across profile runs; each `launch` writes `build/verify-runs/<run-id>/state.json` and refuses to overwrite a live instance.
- Passing `ees.mock.sensor.enabled=true` in `prod` intentionally has no effect because `MockSensorAutoConfiguration` is annotated with `@Profile({"local", "dev"})`.
