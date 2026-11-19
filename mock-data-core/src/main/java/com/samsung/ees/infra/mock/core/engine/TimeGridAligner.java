package com.samsung.ees.infra.mock.core.engine;

/**
 * Timestamp alignment utilities for Current Quantization and Time-Series Grid Alignment.
 */
public final class TimeGridAligner {

    private TimeGridAligner() {
    }

    /**
     * Floors the current epoch timestamp to a multiple of {@code intervalMs} (Current Quantization).
     *
     * @param nowEpochMs current epoch timestamp in milliseconds
     * @param intervalMs quantization step in milliseconds (must be positive)
     * @return quantized timestamp in milliseconds
     */
    public static long quantizeCurrent(long nowEpochMs, long intervalMs) {
        validatePositiveInterval(intervalMs);
        return Math.floorDiv(nowEpochMs, intervalMs) * intervalMs;
    }

    /**
     * Snaps the start timestamp up to the next multiple of {@code intervalMs} (Grid Alignment).
     *
     * @param fromEpochMs query start timestamp in milliseconds
     * @param intervalMs sampling grid step in milliseconds (must be positive)
     * @return grid-aligned start timestamp in milliseconds
     */
    public static long alignGridStart(long fromEpochMs, long intervalMs) {
        validatePositiveInterval(intervalMs);
        return Math.ceilDiv(fromEpochMs, intervalMs) * intervalMs;
    }

    private static void validatePositiveInterval(long intervalMs) {
        if (intervalMs <= 0) {
            throw new IllegalArgumentException("intervalMs must be positive");
        }
    }
}
