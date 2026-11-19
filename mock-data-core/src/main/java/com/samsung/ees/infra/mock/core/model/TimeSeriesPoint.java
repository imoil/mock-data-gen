package com.samsung.ees.infra.mock.core.model;

/**
 * Single timestamped point in a sensor time series.
 */
public record TimeSeriesPoint(
        long timestamp,
        double value
) {
}
