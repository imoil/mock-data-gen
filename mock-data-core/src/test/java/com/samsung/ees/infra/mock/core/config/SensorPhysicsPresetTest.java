package com.samsung.ees.infra.mock.core.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SensorPhysicsPresetTest {

    @Test
    void defaultsMatchSpecification() {
        SensorPhysicsPreset defaults = SensorPhysicsPreset.defaults();

        assertThat(defaults.baselineMin()).isEqualTo(20.0);
        assertThat(defaults.baselineSpan()).isEqualTo(80.0);
        assertThat(defaults.amplitudeMin()).isEqualTo(2.0);
        assertThat(defaults.amplitudeSpan()).isEqualTo(18.0);
        assertThat(defaults.periodMinSec()).isEqualTo(300.0);
        assertThat(defaults.periodModSec()).isEqualTo(3300);
        assertThat(defaults.noiseRatio()).isEqualTo(0.05);
        assertThat(defaults.stepDutyCycle()).isEqualTo(0.65);
        assertThat(defaults.driftPeriodMultiplier()).isEqualTo(4.0);
        assertThat(defaults.driftAmplitudeMultiplier()).isEqualTo(1.5);
        assertThat(defaults.anomalyBucketSec()).isEqualTo(60.0);
        assertThat(defaults.anomalyThresholdPercent()).isEqualTo(5);
        assertThat(defaults.anomalySpikeMultiplier()).isEqualTo(2.5);
    }

    @Test
    void builderOverridesAllPhysicsFields() {
        SensorPhysicsPreset preset = SensorPhysicsPreset.builder()
                .baseline(100.0, 20.0)
                .amplitude(5.0, 15.0)
                .period(120.0, 600)
                .noiseRatio(0.1)
                .stepDutyCycle(0.75)
                .drift(6.0, 2.0)
                .anomaly(30.0, 10, 3.0)
                .build();

        assertThat(preset.baselineMin()).isEqualTo(100.0);
        assertThat(preset.baselineSpan()).isEqualTo(20.0);
        assertThat(preset.amplitudeMin()).isEqualTo(5.0);
        assertThat(preset.amplitudeSpan()).isEqualTo(15.0);
        assertThat(preset.periodMinSec()).isEqualTo(120.0);
        assertThat(preset.periodModSec()).isEqualTo(600);
        assertThat(preset.noiseRatio()).isEqualTo(0.1);
        assertThat(preset.stepDutyCycle()).isEqualTo(0.75);
        assertThat(preset.driftPeriodMultiplier()).isEqualTo(6.0);
        assertThat(preset.driftAmplitudeMultiplier()).isEqualTo(2.0);
        assertThat(preset.anomalyBucketSec()).isEqualTo(30.0);
        assertThat(preset.anomalyThresholdPercent()).isEqualTo(10);
        assertThat(preset.anomalySpikeMultiplier()).isEqualTo(3.0);
    }

    @Test
    void validatesConstructorArgumentsAndAcceptsBoundaryValues() {
        SensorPhysicsPreset boundary = SensorPhysicsPreset.builder()
                .baseline(0.0, 0.0)
                .amplitude(0.1, 0.0)
                .period(1.0, 1)
                .noiseRatio(0.0)
                .stepDutyCycle(0.5)
                .drift(1.0, 0.0)
                .anomaly(1.0, 0, 0.0)
                .build();
        assertThat(boundary.baselineSpan()).isZero();
        assertThat(boundary.anomalyThresholdPercent()).isZero();

        SensorPhysicsPreset maxThreshold = SensorPhysicsPreset.builder()
                .anomaly(1.0, 100, 1.0)
                .build();
        assertThat(maxThreshold.anomalyThresholdPercent()).isEqualTo(100);

        assertThatThrownBy(() -> SensorPhysicsPreset.builder().baseline(10.0, -0.1).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("baselineSpan must be non-negative");

        assertThatThrownBy(() -> SensorPhysicsPreset.builder().amplitude(0.0, 5.0).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("amplitude bounds must be positive");
        assertThatThrownBy(() -> SensorPhysicsPreset.builder().amplitude(1.0, -0.1).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("amplitude bounds must be positive");

        assertThatThrownBy(() -> SensorPhysicsPreset.builder().period(0.0, 10).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("period bounds must be positive");
        assertThatThrownBy(() -> SensorPhysicsPreset.builder().period(10.0, 0).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("period bounds must be positive");

        assertThatThrownBy(() -> SensorPhysicsPreset.builder().noiseRatio(-0.01).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("noiseRatio must be non-negative");

        assertThatThrownBy(() -> SensorPhysicsPreset.builder().stepDutyCycle(0.0).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("stepDutyCycle must be between 0 and 1");
        assertThatThrownBy(() -> SensorPhysicsPreset.builder().stepDutyCycle(1.0).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("stepDutyCycle must be between 0 and 1");

        assertThatThrownBy(() -> SensorPhysicsPreset.builder().drift(0.0, 1.0).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("drift multipliers must be valid");
        assertThatThrownBy(() -> SensorPhysicsPreset.builder().drift(1.0, -0.1).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("drift multipliers must be valid");

        assertThatThrownBy(() -> SensorPhysicsPreset.builder().anomaly(0.0, 5, 2.0).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("anomaly parameters out of range");
        assertThatThrownBy(() -> SensorPhysicsPreset.builder().anomaly(10.0, -1, 2.0).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("anomaly parameters out of range");
        assertThatThrownBy(() -> SensorPhysicsPreset.builder().anomaly(10.0, 101, 2.0).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("anomaly parameters out of range");
    }
}
