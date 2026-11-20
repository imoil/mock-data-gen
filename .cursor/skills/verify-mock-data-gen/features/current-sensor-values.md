# Current sensor values

Current sensor values lets a consumer fetch deterministic instantaneous mock readings for one or more sensor IDs when `X-Mock-Data: true` is sent in `local` or `dev` profiles, while transparently routing requests without that header to the operational controller.

## Sub-features

- `current-mock-single` returns a deterministic quantized reading for one sensor ID with `mock: true`.
- `current-mock-multi` returns ordered readings and waveform patterns for comma-separated `sensorIds`.
- `current-quantize` aligns `epochMillis` and `timestamp` to `intervalMs` multiples (default `1000`, custom e.g. `10000`).
- `current-fallback-absent` routes to operational `SensorController` (`mock: false`, `source: "LIVE_STORE"`) when `X-Mock-Data` is omitted.
- `current-fallback-false` routes to operational `SensorController` (`mock: false`, `source: "LIVE_STORE"`) when `X-Mock-Data: false`.

## How to get to it (user POV)

- Send `GET /api/v1/sensors/current?sensorIds=<ids>` with header `X-Mock-Data: true` (and optional `intervalMs`) to get mock data.
- Send `GET /api/v1/sensors/current?sensorIds=<ids>` without `X-Mock-Data` or with `X-Mock-Data: false` to reach the live operational controller.
- Invoke the same operation from Swagger UI (`/swagger-ui/index.html`) using the optional `X-Mock-Data` header input.

## Driving it with verify_app.py

Preconditions:

- `example-app` is running under `--profile local` (or `dev`) and `doctor --run-id <run-id>` reports `"healthy": true`.
- `ees.mock.sensor.enabled` is `true` (default) on `/api/v1/sensors/current`.

- **Single sensor mock.** Request one sensor with `X-Mock-Data: true`. Run `python .cursor/skills/verify-mock-data-gen/scripts/verify_app.py request --run-id <run-id> --path "/api/v1/sensors/current?sensorIds=TEMP-01" --header "X-Mock-Data: true" --expected-status 200 --save current-sensor-values/mock-single.json`. Response returns `200`, `mock: true`, `sensors` length `1`, `sensorId: "TEMP-01"`, numeric `value`, and non-empty `pattern`.
- **Multiple sensors mock.** Request two sensors together. Run `python .cursor/skills/verify-mock-data-gen/scripts/verify_app.py request --run-id <run-id> --path "/api/v1/sensors/current?sensorIds=TEMP-01,PRESS-02" --header "X-Mock-Data: true" --expected-status 200 --save current-sensor-values/mock-multi.json`. Response returns `sensors` in `[TEMP-01, PRESS-02]` order with `timestamp` matching `epochMillis` in UTC ISO-8601 format.
- **Interval quantization.** Request with `intervalMs=60000` twice within the same minute. Run `python .cursor/skills/verify-mock-data-gen/scripts/verify_app.py request --run-id <run-id> --path "/api/v1/sensors/current?sensorIds=TEMP-01,PRESS-02&intervalMs=60000" --header "X-Mock-Data: true" --expected-status 200 --save current-sensor-values/mock-quantized-1.json` and repeat once to `current-sensor-values/mock-quantized-2.json`. In both responses `epochMillis % 60000 == 0`, and when both fall in the same 60-second bucket their `value` fields are identical.
- **Fallback without header.** Omit `X-Mock-Data`. Run `python .cursor/skills/verify-mock-data-gen/scripts/verify_app.py request --run-id <run-id> --path "/api/v1/sensors/current?sensorIds=TEMP-01" --expected-status 200 --save current-sensor-values/fallback-absent.json`. Response returns `mock: false` and `sensors[0].source: "LIVE_STORE"` with `value: 25.0`.
- **Fallback with `X-Mock-Data: false`.** Send `X-Mock-Data: false`. Run `python .cursor/skills/verify-mock-data-gen/scripts/verify_app.py request --run-id <run-id> --path "/api/v1/sensors/current?sensorIds=TEMP-01" --header "X-Mock-Data: false" --expected-status 200 --save current-sensor-values/fallback-false.json`. Response returns `mock: false` and `sensors[0].source: "LIVE_STORE"`.

## Gotchas

- If the two `intervalMs=60000` requests cross a `:00` UTC minute boundary, `epochMillis` advances by `60000` and `value` changes deterministically for the new bucket; check `epochMillis` equality before comparing values.
- `X-Mock-Data` header matching in Spring MVC is exact (`X-Mock-Data=true`); `X-Mock-Data: TRUE` does not match `MockSensorController` and falls through to `SensorController`.
- `SensorController` requires `sensorIds` (`@RequestParam List<String> sensorIds`) and ignores `intervalMs < 100`, while `MockSensorController` accepts `required = false` and validates empty/blank `sensorIds` and `minIntervalMs` (`100`) inside `DeterministicMockGenerator` to return `MockErrorResponse` (`400`).
