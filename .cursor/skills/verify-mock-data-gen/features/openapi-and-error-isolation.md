# OpenAPI and error isolation

OpenAPI and error isolation exposes the optional `X-Mock-Data` header documentation in `/v3/api-docs` when mock endpoints are active and isolates invalid mock request errors inside `MockSensorController` without altering host error handling.

## Sub-features

- `openapi-header-present` documents optional header `X-Mock-Data` (`in: "header"`, `required: false`, `schema: { "type": "string", "enum": ["true"] }`, `example: "true"`, plus `description`) on `/api/v1/sensors/current` and `/api/v1/sensors/timeseries` in `local`/`dev` (including custom overridden URIs).
- `openapi-header-absent-prod` omits `X-Mock-Data` from `/v3/api-docs` in `prod` (or when `ees.mock.sensor.enabled=false`).
- `error-missing-sensor-ids` returns HTTP `400` `MockErrorResponse` (`status: 400`, `message`, `timestamp`) when `sensorIds` is empty, blank, or omitted on a mock request.
- `error-missing-or-mismatched-param` returns HTTP `400` `MockErrorResponse` when required `/timeseries` parameters (`from`/`to`) are missing (`MissingServletRequestParameterException`) or `intervalMs` is non-numeric (`MethodArgumentTypeMismatchException`, e.g., `"Invalid parameter 'intervalMs': abc"`).
- `error-invalid-range-or-timestamp` returns HTTP `400` `MockErrorResponse` when `from`/`to` is blank, cannot be parsed, or `from >= to`.
- `error-interval-or-limit` returns HTTP `400` `MockErrorResponse` when `intervalMs < minIntervalMs` (`100`) or `floor((to - from) / intervalMs) > maxPointsPerSensor` (`10000`, including 64-bit extreme ranges).

## How to get to it (user POV)

- Fetch `GET /v3/api-docs` or open `/swagger-ui/index.html` in a browser.
- Send malformed mock requests (`X-Mock-Data: true`) to `/api/v1/sensors/current` or `/api/v1/sensors/timeseries` and inspect the `400` JSON body.

## Driving it with verify_app.py

Preconditions:

- `example-app` is running under `--profile local` and `doctor --run-id <run-id>` reports `"healthy": true`.

- **Inspect OpenAPI JSON.** Fetch `/v3/api-docs`. Run `python .cursor/skills/verify-mock-data-gen/scripts/verify_app.py request --run-id <run-id> --path "/v3/api-docs" --expected-status 200 --save openapi-and-error-isolation/api-docs-local.json`. Both `/api/v1/sensors/current` and `/api/v1/sensors/timeseries` `get.parameters` include `{ "name": "X-Mock-Data", "in": "header", "required": false, "example": "true" }` with `schema.type == "string"`, `schema.enum == ["true"]`, and non-blank `description`.
- **Inspect Swagger UI HTML.** Fetch `/swagger-ui/index.html`. Run `python .cursor/skills/verify-mock-data-gen/scripts/verify_app.py request --run-id <run-id> --path "/swagger-ui/index.html" --expected-status 200 --save openapi-and-error-isolation/swagger-ui.html.json`. Response returns `200` with Swagger UI markup.
- **Empty `sensorIds` error.** Send `X-Mock-Data: true` without `sensorIds`. Run `python .cursor/skills/verify-mock-data-gen/scripts/verify_app.py request --run-id <run-id> --path "/api/v1/sensors/current" --header "X-Mock-Data: true" --expected-status 400 --save openapi-and-error-isolation/err-empty-sensors.json`. Body has `status: 400`, `message: "sensorIds must not be empty"`, and UTC `timestamp`.
- **Missing required / type-mismatched query params.** Request `/api/v1/sensors/timeseries?sensorIds=TEMP-01&to=1794987600000` (`err-missing-from.json`) and `/api/v1/sensors/current?sensorIds=TEMP-01&intervalMs=abc` (`err-type-mismatch.json`) with `X-Mock-Data: true` and `--expected-status 400`. Both return `MockErrorResponse` (`status: 400`, mentioning `'from'` and `"Invalid parameter 'intervalMs': abc"`, respectively).
- **Invalid timestamp / reversed range.** Send `from=not-a-timestamp` (`err-bad-ts.json`) and `from=2026-11-18T10:00:00Z&to=2026-11-18T09:00:00Z` (`err-reversed-range.json`) with `X-Mock-Data: true` and `--expected-status 400`. Both return `MockErrorResponse` (`status: 400`, descriptive `message`, `timestamp`).
- **Minimum interval & max-points guard.** Send `intervalMs=50` (`err-min-interval.json`), a 2-hour range at `intervalMs=100` (`err-max-points.json`), and an extreme 64-bit span `from=-5000000000000000000&to=5000000000000000000&intervalMs=1000` (`err-64bit-overflow.json`) to `/api/v1/sensors/timeseries` with `X-Mock-Data: true` and `--expected-status 400`. All return `400` with `"intervalMs must be at least 100ms"` or `"Requested points exceed maximum limit (10000)"`.

## Gotchas

- `MockErrorResponse` is produced only by `MockSensorController`'s local `@ExceptionHandler` methods (when `X-Mock-Data: true` routes to it); requests without `X-Mock-Data: true` hit `SensorController` and Spring Boot's default error handler instead.
- Springdoc initializes `DispatcherServlet` and `AbstractOpenApiResource` lazily on the first `/v3/api-docs` request (~200ms); `verify_app.py launch` warms this once during readiness.
- If `--expected-status 400` is omitted, `verify_app.py request` still prints and saves the `400` JSON body cleanly without failing.
