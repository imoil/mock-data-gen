package com.samsung.ees.infra.mock.starter.controller;

import com.samsung.ees.infra.mock.core.exception.InvalidMockRequestException;
import java.time.Instant;
import java.time.format.DateTimeParseException;

/**
 * Parses timestamps supplied either as Epoch milliseconds or ISO-8601 strings.
 */
final class FlexibleTimestampParser {

    private FlexibleTimestampParser() {
    }

    static long parseEpochMillis(String rawValue, String parameterName) {
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
