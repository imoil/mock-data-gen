package com.samsung.ees.infra.mock.starter.controller;

import java.time.Instant;

/**
 * Isolated error response body returned by {@link MockSensorController}.
 */
public record MockErrorResponse(
        int status,
        String message,
        Instant timestamp
) {
}
