package com.samsung.ees.infra.mock.core.engine;

import com.samsung.ees.infra.mock.core.config.SensorPhysicsPreset;
import com.samsung.ees.infra.mock.core.domain.MockPatternType;
import com.samsung.ees.infra.mock.core.util.DeterministicHashUtil;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Pure stateless waveform engine deriving per-sensor pattern and physics parameters from sensorId hashes.
 */
public class DeterministicWaveCalculator {

    private static final long SALT_BASELINE = 0x1111L;
    private static final long SALT_AMPLITUDE = 0x2222L;
    private static final long SALT_PERIOD = 0x3333L;
    private static final long SALT_PHASE = 0x4444L;
    private static final long SALT_PATTERN = 0x5555L;

    private final SensorPhysicsPreset preset;

    public DeterministicWaveCalculator() {
        this(SensorPhysicsPreset.defaults());
    }

    public DeterministicWaveCalculator(SensorPhysicsPreset preset) {
        if (preset == null) {
            throw new IllegalArgumentException("preset must not be null");
        }
        this.preset = preset;
    }

    /**
     * Resolves the deterministic waveform pattern assigned to a sensorId.
     *
     * @param sensorId sensor identifier
     * @return assigned pattern type
     */
    public MockPatternType resolvePattern(String sensorId) {
        long seed = DeterministicHashUtil.fnv1a64(sensorId);
        long patternHash = DeterministicHashUtil.mix64(seed ^ SALT_PATTERN);
        return MockPatternType.fromHash(patternHash);
    }

    /**
     * Evaluates a rounded deterministic sensor value at the given epoch timestamp.
     *
     * @param sensorId sensor identifier
     * @param timestampMs epoch timestamp in milliseconds
     * @param scale decimal rounding scale
     * @return rounded measurement value
     */
    public double calculate(String sensorId, long timestampMs, int scale) {
        SensorParameters params = deriveParameters(sensorId);
        double tSec = timestampMs / 1000.0;
        double noise = params.sigma() * DeterministicHashUtil.gaussianNoise(params.seed(), timestampMs);

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

    private SensorParameters deriveParameters(String sensorId) {
        long seed = DeterministicHashUtil.fnv1a64(sensorId);
        MockPatternType pattern = MockPatternType.fromHash(DeterministicHashUtil.mix64(seed ^ SALT_PATTERN));

        long s0 = DeterministicHashUtil.mix64(seed ^ SALT_BASELINE);
        long s1 = DeterministicHashUtil.mix64(seed ^ SALT_AMPLITUDE);
        long s2 = DeterministicHashUtil.mix64(seed ^ SALT_PERIOD);
        long s3 = DeterministicHashUtil.mix64(seed ^ SALT_PHASE);

        double baseline = preset.baselineMin() + (Math.floorMod(s0, 1000) / 1000.0) * preset.baselineSpan();
        double amplitude = preset.amplitudeMin() + (Math.floorMod(s1, 1000) / 1000.0) * preset.amplitudeSpan();
        double periodSec = preset.periodMinSec() + Math.floorMod(s2, preset.periodModSec());
        double phaseRad = 2.0 * Math.PI * (Math.floorMod(s3, 10000) / 10000.0);
        double sigma = preset.noiseRatio() * amplitude;

        return new SensorParameters(seed, pattern, baseline, amplitude, periodSec, phaseRad, sigma);
    }

    private double computeSine(SensorParameters params, double t, double noise) {
        double primary = params.amplitude() * Math.sin((2.0 * Math.PI * t / params.periodSec()) + params.phaseRad());
        double harmonic = 0.25 * params.amplitude()
                * Math.sin((6.0 * Math.PI * t / params.periodSec()) + (2.0 * params.phaseRad()));
        return params.baseline() + primary + harmonic + noise;
    }

    private double computeStep(SensorParameters params, double t, double noise) {
        double cyclePos = ((t / params.periodSec()) + (params.phaseRad() / (2.0 * Math.PI))) % 1.0;
        if (cyclePos < 0) {
            cyclePos += 1.0;
        }
        return (cyclePos < preset.stepDutyCycle())
                ? (params.baseline() + params.amplitude()) + noise
                : (params.baseline() - 0.5 * params.amplitude()) + (0.5 * noise);
    }

    private double computeDrift(SensorParameters params, double t, double noise) {
        double driftPeriod = params.periodSec() * preset.driftPeriodMultiplier();
        double shifted = (t + (params.phaseRad() / (2.0 * Math.PI)) * driftPeriod) % driftPeriod;
        if (shifted < 0) {
            shifted += driftPeriod;
        }
        double progress = shifted / driftPeriod;
        return params.baseline() + (preset.driftAmplitudeMultiplier() * params.amplitude() * progress) + noise;
    }

    private double computeAnomaly(SensorParameters params, double t, double noise) {
        double sineVal = computeSine(params, t, noise);
        long bucket = (long) Math.floor(t / preset.anomalyBucketSec());
        long bucketHash = DeterministicHashUtil.mix64(params.seed() ^ bucket);
        double spike = (Math.floorMod(bucketHash, 100) < preset.anomalyThresholdPercent())
                ? (preset.anomalySpikeMultiplier() * params.amplitude())
                : 0.0;
        return sineVal + spike;
    }

    private record SensorParameters(
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
