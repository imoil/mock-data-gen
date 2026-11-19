package com.samsung.ees.infra.mock.core.config;

/**
 * Physical range and waveform parameter preset used when deriving sensor characteristics from hashes.
 */
public record SensorPhysicsPreset(
        double baselineMin,
        double baselineSpan,
        double amplitudeMin,
        double amplitudeSpan,
        double periodMinSec,
        int periodModSec,
        double noiseRatio,
        double stepDutyCycle,
        double driftPeriodMultiplier,
        double driftAmplitudeMultiplier,
        double anomalyBucketSec,
        int anomalyThresholdPercent,
        double anomalySpikeMultiplier
) {

    public SensorPhysicsPreset {
        if (baselineSpan < 0) {
            throw new IllegalArgumentException("baselineSpan must be non-negative");
        }
        if (amplitudeMin <= 0 || amplitudeSpan < 0) {
            throw new IllegalArgumentException("amplitude bounds must be positive");
        }
        if (periodMinSec <= 0 || periodModSec <= 0) {
            throw new IllegalArgumentException("period bounds must be positive");
        }
        if (noiseRatio < 0) {
            throw new IllegalArgumentException("noiseRatio must be non-negative");
        }
        if (stepDutyCycle <= 0.0 || stepDutyCycle >= 1.0) {
            throw new IllegalArgumentException("stepDutyCycle must be between 0 and 1");
        }
        if (driftPeriodMultiplier <= 0 || driftAmplitudeMultiplier < 0) {
            throw new IllegalArgumentException("drift multipliers must be valid");
        }
        if (anomalyBucketSec <= 0 || anomalyThresholdPercent < 0 || anomalyThresholdPercent > 100) {
            throw new IllegalArgumentException("anomaly parameters out of range");
        }
    }

    /**
     * Returns the default preset matching the project specification.
     *
     * @return default physics preset
     */
    public static SensorPhysicsPreset defaults() {
        return new SensorPhysicsPreset(
                20.0, 80.0,
                2.0, 18.0,
                300.0, 3300,
                0.05,
                0.65,
                4.0, 1.5,
                60.0, 5, 2.5
        );
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private double baselineMin = 20.0;
        private double baselineSpan = 80.0;
        private double amplitudeMin = 2.0;
        private double amplitudeSpan = 18.0;
        private double periodMinSec = 300.0;
        private int periodModSec = 3300;
        private double noiseRatio = 0.05;
        private double stepDutyCycle = 0.65;
        private double driftPeriodMultiplier = 4.0;
        private double driftAmplitudeMultiplier = 1.5;
        private double anomalyBucketSec = 60.0;
        private int anomalyThresholdPercent = 5;
        private double anomalySpikeMultiplier = 2.5;

        public Builder baseline(double min, double span) {
            this.baselineMin = min;
            this.baselineSpan = span;
            return this;
        }

        public Builder amplitude(double min, double span) {
            this.amplitudeMin = min;
            this.amplitudeSpan = span;
            return this;
        }

        public Builder period(double minSec, int modSec) {
            this.periodMinSec = minSec;
            this.periodModSec = modSec;
            return this;
        }

        public Builder noiseRatio(double noiseRatio) {
            this.noiseRatio = noiseRatio;
            return this;
        }

        public Builder stepDutyCycle(double stepDutyCycle) {
            this.stepDutyCycle = stepDutyCycle;
            return this;
        }

        public Builder drift(double periodMultiplier, double amplitudeMultiplier) {
            this.driftPeriodMultiplier = periodMultiplier;
            this.driftAmplitudeMultiplier = amplitudeMultiplier;
            return this;
        }

        public Builder anomaly(double bucketSec, int thresholdPercent, double spikeMultiplier) {
            this.anomalyBucketSec = bucketSec;
            this.anomalyThresholdPercent = thresholdPercent;
            this.anomalySpikeMultiplier = spikeMultiplier;
            return this;
        }

        public SensorPhysicsPreset build() {
            return new SensorPhysicsPreset(
                    baselineMin,
                    baselineSpan,
                    amplitudeMin,
                    amplitudeSpan,
                    periodMinSec,
                    periodModSec,
                    noiseRatio,
                    stepDutyCycle,
                    driftPeriodMultiplier,
                    driftAmplitudeMultiplier,
                    anomalyBucketSec,
                    anomalyThresholdPercent,
                    anomalySpikeMultiplier
            );
        }
    }
}
