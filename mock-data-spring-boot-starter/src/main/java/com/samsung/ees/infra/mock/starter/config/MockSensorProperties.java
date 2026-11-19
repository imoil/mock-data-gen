package com.samsung.ees.infra.mock.starter.config;

import com.samsung.ees.infra.mock.core.config.GeneratorConfig;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the deterministic sensor mock data starter.
 */
@ConfigurationProperties(prefix = "app-custom-config.mock.sensor")
public class MockSensorProperties {

    private boolean enabled = true;
    private String basePath = "/api/v1/sensors";
    private String currentPath = "/current";
    private String timeseriesPath = "/timeseries";
    private long defaultCurrentIntervalMs = 1000L;
    private long defaultSeriesIntervalMs = 10000L;
    private long minIntervalMs = 100L;
    private int maxPointsPerSensor = 10000;
    private int decimalScale = 2;

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

    /**
     * Converts bound Spring Boot properties into a pure {@link GeneratorConfig}.
     *
     * @return core generator configuration
     */
    public GeneratorConfig toGeneratorConfig() {
        return GeneratorConfig.builder()
                .defaultCurrentIntervalMs(defaultCurrentIntervalMs)
                .defaultSeriesIntervalMs(defaultSeriesIntervalMs)
                .minIntervalMs(minIntervalMs)
                .maxPointsPerSensor(maxPointsPerSensor)
                .decimalScale(decimalScale)
                .build();
    }
}
