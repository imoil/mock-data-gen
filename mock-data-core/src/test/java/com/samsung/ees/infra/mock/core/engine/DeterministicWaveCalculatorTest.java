package com.samsung.ees.infra.mock.core.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.samsung.ees.infra.mock.core.config.SensorPhysicsPreset;
import com.samsung.ees.infra.mock.core.domain.MockPatternType;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DeterministicWaveCalculatorTest {

    @Test
    void resolvesAllFivePatternsAcrossSensorIds() {
        DeterministicWaveCalculator calculator = new DeterministicWaveCalculator();
        Set<MockPatternType> observed = EnumSet.noneOf(MockPatternType.class);

        for (int i = 0; i < 100; i++) {
            observed.add(calculator.resolvePattern("SENSOR-" + i));
        }

        assertThat(observed).containsExactlyInAnyOrder(MockPatternType.values());
    }

    @Test
    void calculatesDeterministicValueAndSupportsCustomPhysicsPreset() {
        DeterministicWaveCalculator defaultCalc = new DeterministicWaveCalculator();
        long ts = 1794987600000L;
        assertThat(defaultCalc.calculate("TEMP-01", ts, 2))
                .isEqualTo(defaultCalc.calculate("TEMP-01", ts, 2));

        SensorPhysicsPreset highBaselinePreset = SensorPhysicsPreset.builder()
                .baseline(500.0, 10.0)
                .amplitude(5.0, 1.0)
                .noiseRatio(0.0)
                .build();
        DeterministicWaveCalculator customCalc = new DeterministicWaveCalculator(highBaselinePreset);
        double customValue = customCalc.calculate("TEMP-01", ts, 2);

        assertThat(customValue).isBetween(490.0, 530.0);
    }

    @Test
    void steadyPatternRemainsConstantWithoutNoiseAndDeterministicWithNoise() {
        String sensorId = findSensorIdForPattern(MockPatternType.STEADY);
        DeterministicWaveCalculator noNoiseCalc = new DeterministicWaveCalculator(
                SensorPhysicsPreset.builder().noiseRatio(0.0).build()
        );

        double baseline = noNoiseCalc.calculate(sensorId, 0L, 2);
        assertThat(noNoiseCalc.calculate(sensorId, 60_000L, 2)).isEqualTo(baseline);
        assertThat(noNoiseCalc.calculate(sensorId, 123_456L, 2)).isEqualTo(baseline);
        assertThat(noNoiseCalc.calculate(sensorId, 999_000L, 2)).isEqualTo(baseline);

        DeterministicWaveCalculator defaultCalc = new DeterministicWaveCalculator();
        double noisyAtT = defaultCalc.calculate(sensorId, 60_000L, 2);
        assertThat(defaultCalc.calculate(sensorId, 60_000L, 2)).isEqualTo(noisyAtT);
        for (int i = 0; i < 50; i++) {
            double sample = defaultCalc.calculate(sensorId, i * 1000L, 2);
            assertThat(sample).isBetween(baseline - 5.0, baseline + 5.0);
        }
    }

    @Test
    void sinePatternOscillatesPeriodicallyWithinHarmonicBounds() {
        String sensorId = findSensorIdForPattern(MockPatternType.SINE);
        DeterministicWaveCalculator periodicCalc = new DeterministicWaveCalculator(
                SensorPhysicsPreset.builder()
                        .baseline(50.0, 0.0)
                        .amplitude(10.0, 0.0)
                        .period(60.0, 1)
                        .noiseRatio(0.0)
                        .build()
        );

        Set<Double> distinctValues = new HashSet<>();
        for (int s = 0; s < 60; s++) {
            long ts = s * 1000L;
            double value = periodicCalc.calculate(sensorId, ts, 2);
            distinctValues.add(value);
            assertThat(value).isBetween(37.5, 62.5);
            assertThat(periodicCalc.calculate(sensorId, ts + 60_000L, 2)).isEqualTo(value);
        }

        assertThat(distinctValues.size()).isGreaterThan(20);
    }

    @Test
    void stepPatternFollowsDutyCycleWithTwoDistinctPlateaus() {
        String sensorId = findSensorIdForPattern(MockPatternType.STEP);
        DeterministicWaveCalculator stepCalc = new DeterministicWaveCalculator(
                SensorPhysicsPreset.builder()
                        .baseline(50.0, 0.0)
                        .amplitude(10.0, 0.0)
                        .period(100.0, 1)
                        .noiseRatio(0.0)
                        .stepDutyCycle(0.65)
                        .build()
        );

        List<Double> values = new ArrayList<>();
        for (int s = 0; s < 100; s++) {
            values.add(stepCalc.calculate(sensorId, s * 1000L, 2));
        }

        List<Double> sortedDistinct = values.stream().distinct().sorted().toList();
        assertThat(sortedDistinct).containsExactly(45.0, 60.0);

        long highCount = values.stream().filter(v -> v == 60.0).count();
        long lowCount = values.stream().filter(v -> v == 45.0).count();
        assertThat(highCount).isBetween(64L, 66L);
        assertThat(lowCount).isBetween(34L, 36L);
    }

    @Test
    void driftPatternRampsLinearlyOverFourPeriodsAndResets() {
        String sensorId = findSensorIdForPattern(MockPatternType.DRIFT);
        DeterministicWaveCalculator driftCalc = new DeterministicWaveCalculator(
                SensorPhysicsPreset.builder()
                        .baseline(50.0, 0.0)
                        .amplitude(10.0, 0.0)
                        .period(100.0, 1)
                        .noiseRatio(0.0)
                        .drift(4.0, 1.5)
                        .build()
        );

        List<Double> values = new ArrayList<>();
        for (int s = 0; s < 400; s++) {
            long ts = s * 1000L;
            double val = driftCalc.calculate(sensorId, ts, 2);
            values.add(val);
            assertThat(val).isBetween(50.0, 65.0);
            assertThat(driftCalc.calculate(sensorId, ts + 400_000L, 2)).isEqualTo(val);
        }

        int resetDrops = 0;
        for (int i = 1; i < values.size(); i++) {
            double prev = values.get(i - 1);
            double curr = values.get(i);
            if (curr < prev - 1.0) {
                resetDrops++;
            } else {
                assertThat(curr).isGreaterThanOrEqualTo(prev);
            }
        }
        assertThat(resetDrops).isEqualTo(1);
    }

    @Test
    void anomalyPatternInjectsSpikesInRoughlyFivePercentOfBuckets() {
        String sensorId = findSensorIdForPattern(MockPatternType.ANOMALY);
        DeterministicWaveCalculator anomalyCalc = new DeterministicWaveCalculator(
                SensorPhysicsPreset.builder()
                        .baseline(50.0, 0.0)
                        .amplitude(10.0, 0.0)
                        .period(60.0, 1)
                        .noiseRatio(0.0)
                        .anomaly(60.0, 5, 2.5)
                        .build()
        );

        double baseValueAtBucketStart = anomalyCalc.calculate(sensorId, 0L, 2);
        List<Double> bucketValues = new ArrayList<>();
        for (int bucket = 0; bucket < 1000; bucket++) {
            bucketValues.add(anomalyCalc.calculate(sensorId, bucket * 60_000L, 2));
        }

        List<Double> distinct = bucketValues.stream().distinct().sorted().toList();
        assertThat(distinct).hasSize(2);
        double normalVal = distinct.get(0);
        double spikeVal = distinct.get(1);
        assertThat(spikeVal - normalVal).isEqualTo(25.0);
        assertThat(baseValueAtBucketStart).isIn(normalVal, spikeVal);

        long spikeCount = bucketValues.stream().filter(v -> v == spikeVal).count();
        assertThat(spikeCount).isBetween(25L, 80L);
    }

    private String findSensorIdForPattern(MockPatternType target) {
        DeterministicWaveCalculator calculator = new DeterministicWaveCalculator();
        for (int i = 0; i < 200; i++) {
            String candidate = "SENSOR-" + i;
            if (calculator.resolvePattern(candidate) == target) {
                return candidate;
            }
        }
        throw new IllegalStateException("No sensorId found for pattern " + target);
    }
}
