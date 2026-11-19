package com.samsung.ees.infra.mock.core.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.samsung.ees.infra.mock.core.config.SensorPhysicsPreset;
import com.samsung.ees.infra.mock.core.domain.MockPatternType;
import java.util.EnumSet;
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
}
