package com.samsung.ees.infra.mock.starter.controller;

import com.samsung.ees.infra.mock.core.engine.DeterministicMockGenerator;
import com.samsung.ees.infra.mock.core.exception.InvalidMockRequestException;
import com.samsung.ees.infra.mock.core.model.CurrentMockResponse;
import com.samsung.ees.infra.mock.core.model.TimeSeriesMockResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Profile-isolated REST controller serving deterministic mock sensor data when X-Mock-Data=true.
 */
@RestController
@RequestMapping("#{@mockSensorProperties.basePath}")
public class MockSensorController {

    public static final String MOCK_HEADER_NAME = "X-Mock-Data";
    public static final String MOCK_HEADER_CONDITION = "X-Mock-Data=true";

    private final DeterministicMockGenerator mockGenerator;
    private final Clock clock;

    public MockSensorController(DeterministicMockGenerator mockGenerator) {
        this(mockGenerator, Clock.systemUTC());
    }

    public MockSensorController(DeterministicMockGenerator mockGenerator, Clock clock) {
        if (mockGenerator == null) {
            throw new IllegalArgumentException("mockGenerator must not be null");
        }
        if (clock == null) {
            throw new IllegalArgumentException("clock must not be null");
        }
        this.mockGenerator = mockGenerator;
        this.clock = clock;
    }

    @GetMapping(value = "#{@mockSensorProperties.currentPath}", headers = MOCK_HEADER_CONDITION)
    public ResponseEntity<CurrentMockResponse> getCurrentMockValues(
            @RequestParam(name = "sensorIds", required = false) List<String> sensorIds,
            @RequestParam(name = "intervalMs", required = false) Long intervalMs
    ) {
        return ResponseEntity.ok(mockGenerator.generateCurrentValues(sensorIds, intervalMs));
    }

    @GetMapping(value = "#{@mockSensorProperties.timeseriesPath}", headers = MOCK_HEADER_CONDITION)
    public ResponseEntity<TimeSeriesMockResponse> getTimeSeriesMockValues(
            @RequestParam(name = "sensorIds", required = false) List<String> sensorIds,
            @RequestParam(name = "from") String from,
            @RequestParam(name = "to") String to,
            @RequestParam(name = "intervalMs", required = false) Long intervalMs
    ) {
        long fromEpochMs = parseEpochMillis(from, "from");
        long toEpochMs = parseEpochMillis(to, "to");
        return ResponseEntity.ok(
                mockGenerator.generateTimeSeries(sensorIds, fromEpochMs, toEpochMs, intervalMs)
        );
    }

    @ExceptionHandler(InvalidMockRequestException.class)
    public ResponseEntity<MockErrorResponse> handleInvalidMockRequest(InvalidMockRequestException ex) {
        return badRequest(ex.getMessage());
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<MockErrorResponse> handleMissingParam(MissingServletRequestParameterException ex) {
        return badRequest(ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<MockErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return badRequest("Invalid parameter '" + ex.getName() + "': " + ex.getValue());
    }

    private ResponseEntity<MockErrorResponse> badRequest(String message) {
        return ResponseEntity.badRequest()
                .body(new MockErrorResponse(HttpStatus.BAD_REQUEST.value(), message, clock.instant()));
    }

    private static long parseEpochMillis(String rawValue, String parameterName) {
        if (rawValue == null || rawValue.isBlank()) {
            throw new InvalidMockRequestException("'" + parameterName + "' timestamp must not be empty");
        }
        String trimmed = rawValue.trim();
        try {
            return Long.parseLong(trimmed);
        } catch (NumberFormatException ignored) {
            try {
                return Instant.parse(trimmed).toEpochMilli();
            } catch (DateTimeParseException ex) {
                throw new InvalidMockRequestException(
                        "Invalid '" + parameterName + "' timestamp format: " + rawValue
                );
            }
        }
    }
}
