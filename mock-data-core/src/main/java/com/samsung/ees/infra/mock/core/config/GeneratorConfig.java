package com.samsung.ees.infra.mock.core.config;

/**
 * Configuration for deterministic mock generation intervals, limits, precision, and physics preset.
 */
public record GeneratorConfig(
        long defaultCurrentIntervalMs,
        long defaultSeriesIntervalMs,
        long minIntervalMs,
        int maxPointsPerSensor,
        int decimalScale,
        SensorPhysicsPreset physicsPreset
) {

    public GeneratorConfig {
        if (minIntervalMs < 1) {
            throw new IllegalArgumentException("minIntervalMs must be at least 1");
        }
        if (defaultCurrentIntervalMs < minIntervalMs) {
            throw new IllegalArgumentException("defaultCurrentIntervalMs must be >= minIntervalMs");
        }
        if (defaultSeriesIntervalMs < minIntervalMs) {
            throw new IllegalArgumentException("defaultSeriesIntervalMs must be >= minIntervalMs");
        }
        if (maxPointsPerSensor < 1) {
            throw new IllegalArgumentException("maxPointsPerSensor must be at least 1");
        }
        if (decimalScale < 0) {
            throw new IllegalArgumentException("decimalScale must be non-negative");
        }
        if (physicsPreset == null) {
            throw new IllegalArgumentException("physicsPreset must not be null");
        }
    }

    /**
     * Returns the default configuration matching the project specification.
     *
     * @return default generator configuration
     */
    public static GeneratorConfig defaults() {
        return new GeneratorConfig(1000L, 10000L, 100L, 10000, 2, SensorPhysicsPreset.defaults());
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private long defaultCurrentIntervalMs = 1000L;
        private long defaultSeriesIntervalMs = 10000L;
        private long minIntervalMs = 100L;
        private int maxPointsPerSensor = 10000;
        private int decimalScale = 2;
        private SensorPhysicsPreset physicsPreset = SensorPhysicsPreset.defaults();

        public Builder defaultCurrentIntervalMs(long defaultCurrentIntervalMs) {
            this.defaultCurrentIntervalMs = defaultCurrentIntervalMs;
            return this;
        }

        public Builder defaultSeriesIntervalMs(long defaultSeriesIntervalMs) {
            this.defaultSeriesIntervalMs = defaultSeriesIntervalMs;
            return this;
        }

        public Builder minIntervalMs(long minIntervalMs) {
            this.minIntervalMs = minIntervalMs;
            return this;
        }

        public Builder maxPointsPerSensor(int maxPointsPerSensor) {
            this.maxPointsPerSensor = maxPointsPerSensor;
            return this;
        }

        public Builder decimalScale(int decimalScale) {
            this.decimalScale = decimalScale;
            return this;
        }

        public Builder physicsPreset(SensorPhysicsPreset physicsPreset) {
            this.physicsPreset = physicsPreset;
            return this;
        }

        public GeneratorConfig build() {
            return new GeneratorConfig(
                    defaultCurrentIntervalMs,
                    defaultSeriesIntervalMs,
                    minIntervalMs,
                    maxPointsPerSensor,
                    decimalScale,
                    physicsPreset
            );
        }
    }
}
