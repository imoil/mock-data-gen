package com.samsung.ees.infra.mock.core.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class GeneratorConfigTest {

    @Test
    void defaultsMatchSpecification() {
        GeneratorConfig defaults = GeneratorConfig.defaults();

        assertThat(defaults.defaultCurrentIntervalMs()).isEqualTo(1000L);
        assertThat(defaults.defaultSeriesIntervalMs()).isEqualTo(10000L);
        assertThat(defaults.minIntervalMs()).isEqualTo(100L);
        assertThat(defaults.maxPointsPerSensor()).isEqualTo(10000);
        assertThat(defaults.decimalScale()).isEqualTo(2);
        assertThat(defaults.physicsPreset()).isEqualTo(SensorPhysicsPreset.defaults());
    }

    @Test
    void builderOverridesAllFields() {
        SensorPhysicsPreset customPreset = SensorPhysicsPreset.builder()
                .baseline(200.0, 20.0)
                .noiseRatio(0.02)
                .build();

        GeneratorConfig config = GeneratorConfig.builder()
                .defaultCurrentIntervalMs(2000L)
                .defaultSeriesIntervalMs(30000L)
                .minIntervalMs(250L)
                .maxPointsPerSensor(500)
                .decimalScale(4)
                .physicsPreset(customPreset)
                .build();

        assertThat(config.defaultCurrentIntervalMs()).isEqualTo(2000L);
        assertThat(config.defaultSeriesIntervalMs()).isEqualTo(30000L);
        assertThat(config.minIntervalMs()).isEqualTo(250L);
        assertThat(config.maxPointsPerSensor()).isEqualTo(500);
        assertThat(config.decimalScale()).isEqualTo(4);
        assertThat(config.physicsPreset()).isEqualTo(customPreset);
    }

    @Test
    void validatesConstructorArgumentsAndAcceptsBoundaryValues() {
        GeneratorConfig boundary = GeneratorConfig.builder()
                .minIntervalMs(1L)
                .defaultCurrentIntervalMs(1L)
                .defaultSeriesIntervalMs(1L)
                .maxPointsPerSensor(1)
                .decimalScale(0)
                .build();
        assertThat(boundary.minIntervalMs()).isEqualTo(1L);
        assertThat(boundary.decimalScale()).isEqualTo(0);

        assertThatThrownBy(() -> GeneratorConfig.builder().minIntervalMs(0L).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("minIntervalMs must be at least 1");

        assertThatThrownBy(() -> GeneratorConfig.builder()
                .minIntervalMs(500L)
                .defaultCurrentIntervalMs(499L)
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("defaultCurrentIntervalMs must be >= minIntervalMs");

        assertThatThrownBy(() -> GeneratorConfig.builder()
                .minIntervalMs(500L)
                .defaultSeriesIntervalMs(499L)
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("defaultSeriesIntervalMs must be >= minIntervalMs");

        assertThatThrownBy(() -> GeneratorConfig.builder().maxPointsPerSensor(0).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("maxPointsPerSensor must be at least 1");

        assertThatThrownBy(() -> GeneratorConfig.builder().decimalScale(-1).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("decimalScale must be non-negative");

        assertThatThrownBy(() -> GeneratorConfig.builder().physicsPreset(null).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("physicsPreset must not be null");
    }
}
