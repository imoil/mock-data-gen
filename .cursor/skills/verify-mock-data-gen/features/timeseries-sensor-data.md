# Time-series sensor data

Time-series sensor data generates deterministic, grid-aligned historical sensor points over a `[from, to]` time window when `X-Mock-Data: true` is present, preserving point continuity across shifted windows and matching current quantized readings at shared grid timestamps.

## Sub-features

- `series-iso-utc` parses ISO-8601 UTC or offset timestamps (`from`, `to`) and normalizes them to UTC in the response.
- `series-epoch-ms` parses raw Epoch millisecond timestamps (`from`, `to`).
- `series-sliding-continuity` produces identical `(timestamp, value)` pairs for overlapping timestamps across shifted query windows.
- `series-current-consistency` produces the exact same `pattern` and `value` at a quantized timestamp returned by `/current`.
- `series-fallback` routes requests without `X-Mock-Data: true` to operational `SensorController` (`mock: false`, `source: "LIVE_STORE"`, empty `points`).

## How to get to it (user POV)

- Send `GET /api/v1/sensors/timeseries?sensorIds=<ids>&from=<from>&to=<to>` (with optional `intervalMs`) and header `X-Mock-Data: true`.
- Send the same request without `X-Mock-Data: true` (or with `X-Mock-Data: false`) to reach the operational controller.

## Driving it with verify_app.py

Preconditions:

- `example-app` is running under `--profile local` (or `dev`) and `doctor --run-id <run-id>` reports `"healthy": true`.

- **ISO-8601 offset & UTC window.** Query a 5-minute window with `+09:00` offset (`%2B09:00`) and `intervalMs=60000`. Run `python .cursor/skills/verify-mock-data-gen/scripts/verify_app.py request --run-id <run-id> --path "/api/v1/sensors/timeseries?sensorIds=TEMP-01,PRESS-02&from=2026-11-18T15:40:00%2B09:00&to=2026-11-18T15:45:00%2B09:00&intervalMs=60000" --header "X-Mock-Data: true" --expected-status 200 --save timeseries-sensor-data/iso-window.json`. Response returns `from: "2026-11-18T06:40:00Z"`, `to: "2026-11-18T06:45:00Z"`, `intervalMs: 60000`, `mock: true`, and `6` points per sensor.
- **Sliding-window continuity.** Query two overlapping 1-hour windows shifted by 1 minute (`2026-11-18T10:00:12Z`..`11:00:12Z` and `10:01:12Z`..`11:01:12Z`, `intervalMs=60000`). Save both via `verify_app.py request` to `timeseries-sensor-data/window-1.json` and `timeseries-sensor-data/window-2.json`. Both grids align on `:00` seconds, share `59` overlapping timestamps, and have identical `value` numbers at every shared timestamp.
- **Current-to-series consistency.** Call `/api/v1/sensors/current?sensorIds=TEMP-01&intervalMs=10000` with `X-Mock-Data: true`, read `epochMillis` (`E`), then call `/api/v1/sensors/timeseries?sensorIds=TEMP-01&from=<E-20000>&to=<E+20000>&intervalMs=10000` with `X-Mock-Data: true`. Save both to `timeseries-sensor-data/consistency-current.json` and `timeseries-sensor-data/consistency-series.json`. The point at `timestamp == E` matches `sensors[0].value` and `pattern` exactly.
- **Operational fallback.** Request `/api/v1/sensors/timeseries?sensorIds=TEMP-01&from=2026-11-18T06:40:00Z&to=2026-11-18T06:45:00Z` without `X-Mock-Data` and save to `timeseries-sensor-data/fallback.json`. Response returns `mock: false`, `series[0].source: "LIVE_STORE"`, and `series[0].points: []`.

## Gotchas

- Always URL-encode `+` as `%2B` in timezone offsets such as `+09:00`; an unencoded `+` is decoded as a space in query strings and fails timestamp parsing with `400 Bad Request`.
- Grid start uses ceiling alignment (`ceil(from / intervalMs) * intervalMs`), so if `to - from < intervalMs` and no grid multiple falls inside `[from, to]`, or if `from` is near `Long.MAX_VALUE` with no grid multiple `<= Long.MAX_VALUE`, `points` is legitimately empty (`[]`).
- Default `intervalMs` is `10000` (10s) and `maxPointsPerSensor` is `10000`; the guard checks `floor((to - from) / intervalMs) > maxPointsPerSensor` using unsigned 64-bit arithmetic (so exact aligned boundaries at `maxPointsPerSensor * intervalMs` succeed with `maxPointsPerSensor + 1` inclusive points, while oversized or extreme 64-bit ranges return `400 Bad Request`).
- Unlike `MockSensorController`, operational fallback `SensorController.getTimeSeries` echoes `from`/`to` strings without UTC normalization or range/limit validation.
