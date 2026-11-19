package com.samsung.ees.infra.mock.starter.config;

import com.samsung.ees.infra.mock.core.config.GeneratorConfig;
import com.samsung.ees.infra.mock.core.config.SensorPhysicsPreset;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the deterministic sensor mock data starter.
 */
@ConfigurationProperties(prefix = "ees.mock.sensor")
public class MockSensorProperties implements InitializingBean {

    private static final GeneratorConfig CORE_DEFAULTS = GeneratorConfig.defaults();
    private static final SensorPhysicsPreset PHYSICS_DEFAULTS = SensorPhysicsPreset.defaults();

    private boolean enabled = true;
    private String basePath = "/api/v1/sensors";
    private String currentPath = "/current";
    private String timeseriesPath = "/timeseries";
    private long defaultCurrentIntervalMs = CORE_DEFAULTS.defaultCurrentIntervalMs();
    private long defaultSeriesIntervalMs = CORE_DEFAULTS.defaultSeriesIntervalMs();
    private long minIntervalMs = CORE_DEFAULTS.minIntervalMs();
    private int maxPointsPerSensor = CORE_DEFAULTS.maxPointsPerSensor();
    private int decimalScale = CORE_DEFAULTS.decimalScale();
    private Physics physics = new Physics();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBasePath() {
        return basePath;
    }

    public void setBasePath(String basePath) {
        this.basePath = basePath;
    }

    public String getCurrentPath() {
        return currentPath;
    }

    public void setCurrentPath(String currentPath) {
        this.currentPath = currentPath;
    }

    public String getTimeseriesPath() {
        return timeseriesPath;
    }

    public void setTimeseriesPath(String timeseriesPath) {
        this.timeseriesPath = timeseriesPath;
    }

    public long getDefaultCurrentIntervalMs() {
        return defaultCurrentIntervalMs;
    }

    public void setDefaultCurrentIntervalMs(long defaultCurrentIntervalMs) {
        this.defaultCurrentIntervalMs = defaultCurrentIntervalMs;
    }

    public long getDefaultSeriesIntervalMs() {
        return defaultSeriesIntervalMs;
    }

    public void setDefaultSeriesIntervalMs(long defaultSeriesIntervalMs) {
        this.defaultSeriesIntervalMs = defaultSeriesIntervalMs;
    }

    public long getMinIntervalMs() {
        return minIntervalMs;
    }

    public void setMinIntervalMs(long minIntervalMs) {
        this.minIntervalMs = minIntervalMs;
    }

    public int getMaxPointsPerSensor() {
        return maxPointsPerSensor;
    }

    public void setMaxPointsPerSensor(int maxPointsPerSensor) {
        this.maxPointsPerSensor = maxPointsPerSensor;
    }

    public int getDecimalScale() {
        return decimalScale;
    }

    public void setDecimalScale(int decimalScale) {
        this.decimalScale = decimalScale;
    }

    public Physics getPhysics() {
        return physics;
    }

    public void setPhysics(Physics physics) {
        this.physics = (physics != null) ? physics : new Physics();
    }

    @Override
    public void afterPropertiesSet() {
        validatePaths();
    }

    /**
     * Converts bound physics properties into a validated {@link SensorPhysicsPreset}.
     *
     * @return core physics preset
     */
    public SensorPhysicsPreset toPhysicsPreset() {
        return physics.toPreset();
    }

    /**
     * Converts bound Spring Boot properties into a pure {@link GeneratorConfig} using bound physics defaults.
     *
     * @return core generator configuration
     */
    public GeneratorConfig toGeneratorConfig() {
        return toGeneratorConfig(toPhysicsPreset());
    }

    /**
     * Converts bound Spring Boot properties into a pure {@link GeneratorConfig} with an explicit physics preset.
     *
     * @param physicsPreset resolved physics preset bean
     * @return core generator configuration
     */
    public GeneratorConfig toGeneratorConfig(SensorPhysicsPreset physicsPreset) {
        return GeneratorConfig.builder()
                .defaultCurrentIntervalMs(defaultCurrentIntervalMs)
                .defaultSeriesIntervalMs(defaultSeriesIntervalMs)
                .minIntervalMs(minIntervalMs)
                .maxPointsPerSensor(maxPointsPerSensor)
                .decimalScale(decimalScale)
                .physicsPreset(physicsPreset)
                .build();
    }

    private void validatePaths() {
        if (basePath == null) {
            throw new IllegalArgumentException("basePath must not be null");
        }
        if (!basePath.isEmpty() && !basePath.startsWith("/")) {
            throw new IllegalArgumentException("basePath must be empty or start with '/'");
        }
        validateEndpointPath(currentPath, "currentPath");
        validateEndpointPath(timeseriesPath, "timeseriesPath");
    }

    private static void validateEndpointPath(String path, String name) {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        if (!path.startsWith("/")) {
            throw new IllegalArgumentException(name + " must start with '/'");
        }
    }

    /**
     * Nested waveform physics properties bound under {@code ees.mock.sensor.physics.*}.
     */
    public static class Physics {

        private double baselineMin = PHYSICS_DEFAULTS.baselineMin();
        private double baselineSpan = PHYSICS_DEFAULTS.baselineSpan();
        private double amplitudeMin = PHYSICS_DEFAULTS.amplitudeMin();
        private double amplitudeSpan = PHYSICS_DEFAULTS.amplitudeSpan();
        private double periodMinSec = PHYSICS_DEFAULTS.periodMinSec();
        private int periodModSec = PHYSICS_DEFAULTS.periodModSec();
        private double noiseRatio = PHYSICS_DEFAULTS.noiseRatio();
        private double stepDutyCycle = PHYSICS_DEFAULTS.stepDutyCycle();
        private double driftPeriodMultiplier = PHYSICS_DEFAULTS.driftPeriodMultiplier();
        private double driftAmplitudeMultiplier = PHYSICS_DEFAULTS.driftAmplitudeMultiplier();
        private double anomalyBucketSec = PHYSICS_DEFAULTS.anomalyBucketSec();
        private int anomalyThresholdPercent = PHYSICS_DEFAULTS.anomalyThresholdPercent();
        private double anomalySpikeMultiplier = PHYSICS_DEFAULTS.anomalySpikeMultiplier();

        public double getBaselineMin() {
            return baselineMin;
        }

        public void setBaselineMin(double baselineMin) {
            this.baselineMin = baselineMin;
        }

        public double getBaselineSpan() {
            return baselineSpan;
        }

        public void setBaselineSpan(double baselineSpan) {
            this.baselineSpan = baselineSpan;
        }

        public double getAmplitudeMin() {
            return amplitudeMin;
        }

        public void setAmplitudeMin(double amplitudeMin) {
            this.amplitudeMin = amplitudeMin;
        }

        public double getAmplitudeSpan() {
            return amplitudeSpan;
        }

        public void setAmplitudeSpan(double amplitudeSpan) {
            this.amplitudeSpan = amplitudeSpan;
        }

        public double getPeriodMinSec() {
            return periodMinSec;
        }

        public void setPeriodMinSec(double periodMinSec) {
            this.periodMinSec = periodMinSec;
        }

        public int getPeriodModSec() {
            return periodModSec;
        }

        public void setPeriodModSec(int periodModSec) {
            this.periodModSec = periodModSec;
        }

        public double getNoiseRatio() {
            return noiseRatio;
        }

        public void setNoiseRatio(double noiseRatio) {
            this.noiseRatio = noiseRatio;
        }

        public double getStepDutyCycle() {
            return stepDutyCycle;
        }

        public void setStepDutyCycle(double stepDutyCycle) {
            this.stepDutyCycle = stepDutyCycle;
        }

        public double getDriftPeriodMultiplier() {
            return driftPeriodMultiplier;
        }

        public void setDriftPeriodMultiplier(double driftPeriodMultiplier) {
            this.driftPeriodMultiplier = driftPeriodMultiplier;
        }

        public double getDriftAmplitudeMultiplier() {
            return driftAmplitudeMultiplier;
        }

        public void setDriftAmplitudeMultiplier(double driftAmplitudeMultiplier) {
            this.driftAmplitudeMultiplier = driftAmplitudeMultiplier;
        }

        public double getAnomalyBucketSec() {
            return anomalyBucketSec;
        }

        public void setAnomalyBucketSec(double anomalyBucketSec) {
            this.anomalyBucketSec = anomalyBucketSec;
        }

        public int getAnomalyThresholdPercent() {
            return anomalyThresholdPercent;
        }

        public void setAnomalyThresholdPercent(int anomalyThresholdPercent) {
            this.anomalyThresholdPercent = anomalyThresholdPercent;
        }

        public double getAnomalySpikeMultiplier() {
            return anomalySpikeMultiplier;
        }

        public void setAnomalySpikeMultiplier(double anomalySpikeMultiplier) {
            this.anomalySpikeMultiplier = anomalySpikeMultiplier;
        }

        SensorPhysicsPreset toPreset() {
            return SensorPhysicsPreset.builder()
                    .baseline(baselineMin, baselineSpan)
                    .amplitude(amplitudeMin, amplitudeSpan)
                    .period(periodMinSec, periodModSec)
                    .noiseRatio(noiseRatio)
                    .stepDutyCycle(stepDutyCycle)
                    .drift(driftPeriodMultiplier, driftAmplitudeMultiplier)
                    .anomaly(anomalyBucketSec, anomalyThresholdPercent, anomalySpikeMultiplier)
                    .build();
        }
    }
}
