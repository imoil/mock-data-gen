package com.samsung.ees.infra.mock.core.engine;

import com.samsung.ees.infra.mock.core.config.SensorPhysicsPreset;
import com.samsung.ees.infra.mock.core.domain.MockPatternType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;

/**
 * Package-private stateless waveform and hashing implementation behind {@link DeterministicMockGenerator}.
 */
final class DeterministicWaveCalculator {

    private static final long FNV_64_OFFSET_BASIS = 0xcbf29ce484222325L;
    private static final long FNV_64_PRIME = 0x100000001b3L;
    private static final long GOLDEN_GAMMA = 0x9e3779b97f4a7c15L;
    private static final double UNIFORM_DENOMINATOR = (1L << 53) + 1.0;

    private static final long SALT_BASELINE = 0x1111L;
    private static final long SALT_AMPLITUDE = 0x2222L;
    private static final long SALT_PERIOD = 0x3333L;
    private static final long SALT_PHASE = 0x4444L;
    private static final long SALT_PATTERN = 0x5555L;

    private final SensorPhysicsPreset preset;

    DeterministicWaveCalculator(SensorPhysicsPreset preset) {
        if (preset == null) {
            throw new IllegalArgumentException("preset must not be null");
        }
        this.preset = preset;
    }

    /**
     * Derives the deterministic Sensor Seed, Pattern, and physical parameters once per normalized SensorId.
     *
     * @param sensorId normalized non-blank sensor identifier
     * @return precomputed per-sensor waveform parameters
     */
    PreparedSensor prepare(String sensorId) {
        long seed = fnv1a64(sensorId);
        MockPatternType pattern = MockPatternType.fromHash(mix64(seed ^ SALT_PATTERN));

        long s0 = mix64(seed ^ SALT_BASELINE);
        long s1 = mix64(seed ^ SALT_AMPLITUDE);
        long s2 = mix64(seed ^ SALT_PERIOD);
        long s3 = mix64(seed ^ SALT_PHASE);

        double baseline = preset.baselineMin() + (Math.floorMod(s0, 1000) / 1000.0) * preset.baselineSpan();
        double amplitude = preset.amplitudeMin() + (Math.floorMod(s1, 1000) / 1000.0) * preset.amplitudeSpan();
        double periodSec = preset.periodMinSec() + Math.floorMod(s2, preset.periodModSec());
        double phaseRad = 2.0 * Math.PI * (Math.floorMod(s3, 10000) / 10000.0);
        double sigma = preset.noiseRatio() * amplitude;

        return new PreparedSensor(seed, pattern, baseline, amplitude, periodSec, phaseRad, sigma);
    }

    /**
     * Evaluates a rounded deterministic sensor value at the given epoch timestamp using precomputed parameters.
     *
     * @param params precomputed sensor parameters
     * @param timestampMs epoch timestamp in milliseconds
     * @param scale decimal rounding scale
     * @return rounded measurement value
     */
    double calculate(PreparedSensor params, long timestampMs, int scale) {
        double tSec = timestampMs / 1000.0;
        double noise = params.sigma() * gaussianNoise(params.seed(), timestampMs);

        double rawValue = switch (params.pattern()) {
            case STEADY -> params.baseline() + noise;
            case SINE -> computeSine(params, tSec, noise);
            case STEP -> computeStep(params, tSec, noise);
            case DRIFT -> computeDrift(params, tSec, noise);
            case ANOMALY -> computeAnomaly(params, tSec, noise);
        };

        return BigDecimal.valueOf(rawValue)
                .setScale(scale, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private double computeSine(PreparedSensor params, double t, double noise) {
        double primary = params.amplitude() * Math.sin((2.0 * Math.PI * t / params.periodSec()) + params.phaseRad());
        double harmonic = 0.25 * params.amplitude()
                * Math.sin((6.0 * Math.PI * t / params.periodSec()) + (2.0 * params.phaseRad()));
        return params.baseline() + primary + harmonic + noise;
    }

    private double computeStep(PreparedSensor params, double t, double noise) {
        double cyclePos = ((t / params.periodSec()) + (params.phaseRad() / (2.0 * Math.PI))) % 1.0;
        if (cyclePos < 0) {
            cyclePos += 1.0;
        }
        return (cyclePos < preset.stepDutyCycle())
                ? (params.baseline() + params.amplitude()) + noise
                : (params.baseline() - 0.5 * params.amplitude()) + (0.5 * noise);
    }

    private double computeDrift(PreparedSensor params, double t, double noise) {
        double driftPeriod = params.periodSec() * preset.driftPeriodMultiplier();
        double shifted = (t + (params.phaseRad() / (2.0 * Math.PI)) * driftPeriod) % driftPeriod;
        if (shifted < 0) {
            shifted += driftPeriod;
        }
        double progress = shifted / driftPeriod;
        return params.baseline() + (preset.driftAmplitudeMultiplier() * params.amplitude() * progress) + noise;
    }

    private double computeAnomaly(PreparedSensor params, double t, double noise) {
        double sineVal = computeSine(params, t, noise);
        long bucket = (long) Math.floor(t / preset.anomalyBucketSec());
        long bucketHash = mix64(params.seed() ^ bucket);
        double spike = (Math.floorMod(bucketHash, 100) < preset.anomalyThresholdPercent())
                ? (preset.anomalySpikeMultiplier() * params.amplitude())
                : 0.0;
        return sineVal + spike;
    }

    private static long fnv1a64(String input) {
        long hash = FNV_64_OFFSET_BASIS;
        byte[] bytes = input.getBytes(StandardCharsets.UTF_8);
        for (byte b : bytes) {
            hash ^= (b & 0xffL);
            hash *= FNV_64_PRIME;
        }
        return hash;
    }

    private static long mix64(long z) {
        z = (z ^ (z >>> 33)) * 0xff51afd7ed558ccdL;
        z = (z ^ (z >>> 33)) * 0xc4ceb9fe1a85ec53L;
        return z ^ (z >>> 33);
    }

    private static double gaussianNoise(long sensorSeed, long timestampMs) {
        long h1 = mix64(sensorSeed ^ timestampMs);
        long h2 = mix64(h1 ^ GOLDEN_GAMMA);

        // Map top 53 bits to (0.0, 1.0] so Math.log(u1) never receives 0.0.
        double u1 = ((h1 >>> 11) + 1.0) / UNIFORM_DENOMINATOR;
        double u2 = ((h2 >>> 11) + 1.0) / UNIFORM_DENOMINATOR;

        return Math.sqrt(-2.0 * Math.log(u1)) * Math.cos(2.0 * Math.PI * u2);
    }

    record PreparedSensor(
            long seed,
            MockPatternType pattern,
            double baseline,
            double amplitude,
            double periodSec,
            double phaseRad,
            double sigma
    ) {
    }
}
