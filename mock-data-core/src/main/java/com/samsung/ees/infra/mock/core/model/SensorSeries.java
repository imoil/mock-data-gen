package com.samsung.ees.infra.mock.core.model;

import com.samsung.ees.infra.mock.core.domain.MockPatternType;
import java.util.List;

/**
 * Time-series data for a single sensor.
 */
public record SensorSeries(
        String sensorId,
        MockPatternType pattern,
        List<TimeSeriesPoint> points
) {
}
