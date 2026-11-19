package com.samsung.ees.infra.mock.core.model;

import java.time.Instant;
import java.util.List;

/**
 * Response payload for current multi-sensor mock measurements.
 */
public record CurrentMockResponse(
        Instant timestamp,
        long epochMillis,
        boolean mock,
        List<SensorValue> sensors
) {
}
