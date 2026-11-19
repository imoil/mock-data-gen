package com.samsung.ees.infra.mock.core.domain;

/**
 * Deterministic waveform pattern assigned to each sensor based on its seed hash.
 */
public enum MockPatternType {
    STEADY,
    SINE,
    STEP,
    DRIFT,
    ANOMALY;

    private static final MockPatternType[] VALUES = values();

    /**
     * Maps a 64-bit derived hash value to one of the five canonical waveform patterns.
     *
     * @param patternHash derived 64-bit hash
     * @return deterministic pattern type
     */
    public static MockPatternType fromHash(long patternHash) {
        int index = Math.floorMod(patternHash, VALUES.length);
        return VALUES[index];
    }
}
