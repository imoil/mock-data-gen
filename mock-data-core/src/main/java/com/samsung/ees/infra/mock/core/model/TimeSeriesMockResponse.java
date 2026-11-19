package com.samsung.ees.infra.mock.core.model;

import java.time.Instant;
import java.util.List;

/**
 * Response payload for multi-sensor time-series mock measurements.
 */
public record TimeSeriesMockResponse(
        Instant from,
        Instant to,
        long intervalMs,
        boolean mock,
        List<SensorSeries> series
) {
}
