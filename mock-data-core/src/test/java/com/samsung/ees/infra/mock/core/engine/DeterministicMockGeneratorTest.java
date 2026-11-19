package com.samsung.ees.infra.mock.core.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.samsung.ees.infra.mock.core.config.GeneratorConfig;
import com.samsung.ees.infra.mock.core.config.SensorPhysicsPreset;
import com.samsung.ees.infra.mock.core.exception.InvalidMockRequestException;
import com.samsung.ees.infra.mock.core.model.CurrentMockResponse;
import com.samsung.ees.infra.mock.core.model.TimeSeriesMockResponse;
import com.samsung.ees.infra.mock.core.model.TimeSeriesPoint;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class DeterministicMockGeneratorTest {

    private final DeterministicMockGenerator generator = new DeterministicMockGenerator();

    @Test
    void usesInjectedClockAndDefaultIntervalWhenGeneratingCurrentValues() {
        Instant fixedInstant = Instant.parse("2026-11-19T05:11:52.789Z");
        Clock fixedClock = Clock.fixed(fixedInstant, ZoneOffset.UTC);
        DeterministicMockGenerator clockGenerator =
                new DeterministicMockGenerator(GeneratorConfig.defaults(), fixedClock);

        CurrentMockResponse first = clockGenerator.generateCurrentValues(List.of("TEMP-01", "PRESS-02"), null);
        CurrentMockResponse second = clockGenerator.generateCurrentValues(List.of("TEMP-01", "PRESS-02"), null);
        CurrentMockResponse explicit = clockGenerator.generateCurrentValues(
                List.of("TEMP-01", "PRESS-02"),
                1000L,
                fixedInstant.toEpochMilli()
        );

        assertThat(first).isEqualTo(second);
        assertThat(first).isEqualTo(explicit);
        assertThat(first.mock()).isTrue();
        assertThat(first.timestamp()).isEqualTo(Instant.parse("2026-11-19T05:11:52Z"));
        assertThat(first.epochMillis()).isEqualTo(Instant.parse("2026-11-19T05:11:52Z").toEpochMilli());
        assertThat(first.sensors()).hasSize(2);

        CurrentMockResponse fiveSecAligned =
                clockGenerator.generateCurrentValues(List.of("TEMP-01"), 5000L);
        assertThat(fiveSecAligned.timestamp()).isEqualTo(Instant.parse("2026-11-19T05:11:50Z"));
        assertThat(fiveSecAligned.epochMillis()).isEqualTo(Instant.parse("2026-11-19T05:11:50Z").toEpochMilli());

        assertThatThrownBy(() -> new DeterministicMockGenerator(null, fixedClock))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("config must not be null");
        assertThatThrownBy(() -> new DeterministicMockGenerator(GeneratorConfig.defaults(), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("clock must not be null");
    }

    @Test
    void currentAndTimeSeriesMatchBitForBitAtSameQuantizedTimestamp() {
        long nowMs = 1794987600450L; // Quantizes to 1794987600000L with interval=1000
        CurrentMockResponse current = generator.generateCurrentValues(
                List.of("TEMP-01", "PRESS-02"), 1000L, nowMs
        );

        TimeSeriesMockResponse series = generator.generateTimeSeries(
                List.of("TEMP-01", "PRESS-02"),
                current.epochMillis() - 5000L,
                current.epochMillis() + 5000L,
                1000L
        );

        assertThat(current.mock()).isTrue();
        assertThat(current.epochMillis()).isEqualTo(1794987600000L);

        for (int i = 0; i < 2; i++) {
            double currentVal = current.sensors().get(i).value();
            double seriesValAtSameTimestamp = series.series().get(i).points().stream()
                    .filter(p -> p.timestamp() == current.epochMillis())
                    .findFirst()
                    .orElseThrow()
                    .value();
            assertThat(currentVal).isEqualTo(seriesValAtSameTimestamp);
        }
    }

    @Test
    void slidingWindowQueriesProduceIdenticalValuesOnOverlappingGridPoints() {
        long intervalMs = 60_000L;
        long from1 = Instant.parse("2026-11-18T10:00:12Z").toEpochMilli();
        long to1 = Instant.parse("2026-11-18T11:00:12Z").toEpochMilli();
        long from2 = Instant.parse("2026-11-18T10:01:12Z").toEpochMilli();
        long to2 = Instant.parse("2026-11-18T11:01:12Z").toEpochMilli();

        TimeSeriesMockResponse first = generator.generateTimeSeries(List.of("TEMP-01"), from1, to1, intervalMs);
        TimeSeriesMockResponse second = generator.generateTimeSeries(List.of("TEMP-01"), from2, to2, intervalMs);

        Map<Long, Double> firstMap = first.series().get(0).points().stream()
                .collect(Collectors.toMap(TimeSeriesPoint::timestamp, TimeSeriesPoint::value));
        Map<Long, Double> secondMap = second.series().get(0).points().stream()
                .collect(Collectors.toMap(TimeSeriesPoint::timestamp, TimeSeriesPoint::value));

        List<Long> overlap = firstMap.keySet().stream()
                .filter(secondMap::containsKey)
                .toList();

        assertThat(overlap).hasSize(59);
        for (Long ts : overlap) {
            assertThat(firstMap.get(ts)).isEqualTo(secondMap.get(ts));
        }
    }

    @Test
    void validatesEmptySensorsTimeRangeMinIntervalAndMaxPoints() {
        assertThatThrownBy(() -> generator.generateCurrentValues(null, 1000L, 1000L))
                .isInstanceOf(InvalidMockRequestException.class)
                .hasMessage("sensorIds must not be empty");

        assertThatThrownBy(() -> generator.generateCurrentValues(List.of(), 1000L, 1000L))
                .isInstanceOf(InvalidMockRequestException.class)
                .hasMessage("sensorIds must not be empty");

        assertThatThrownBy(() -> generator.generateCurrentValues(List.of(" "), 1000L, 1000L))
                .isInstanceOf(InvalidMockRequestException.class)
                .hasMessage("sensorIds must not be empty");

        assertThatThrownBy(() -> generator.generateCurrentValues(List.of("TEMP-01", " "), 1000L, 1000L))
                .isInstanceOf(InvalidMockRequestException.class)
                .hasMessage("sensorIds must not be empty");

        assertThatThrownBy(() -> generator.generateTimeSeries(List.of("TEMP-01", ""), 0L, 10_000L, 1000L))
                .isInstanceOf(InvalidMockRequestException.class)
                .hasMessage("sensorIds must not be empty");

        assertThatThrownBy(() -> generator.generateCurrentValues(List.of("S1"), 50L, 1000L))
                .isInstanceOf(InvalidMockRequestException.class)
                .hasMessage("intervalMs must be at least 100ms");

        assertThatThrownBy(() -> generator.generateTimeSeries(List.of("S1"), 0L, 10_000L, 99L))
                .isInstanceOf(InvalidMockRequestException.class)
                .hasMessage("intervalMs must be at least 100ms");

        assertThatThrownBy(() -> generator.generateTimeSeries(List.of("S1"), 2000L, 2000L, 1000L))
                .isInstanceOf(InvalidMockRequestException.class)
                .hasMessage("'from' timestamp must be before 'to'");

        assertThatThrownBy(() -> generator.generateTimeSeries(List.of("S1"), 3000L, 2000L, 1000L))
                .isInstanceOf(InvalidMockRequestException.class)
                .hasMessage("'from' timestamp must be before 'to'");

        assertThatThrownBy(() -> generator.generateTimeSeries(List.of("S1"), 0L, 10_001_000L, 1000L))
                .isInstanceOf(InvalidMockRequestException.class)
                .hasMessage("Requested points exceed maximum limit (10000)");
    }

    @Test
    void allowsExactMaxPointsRatioBoundaryWithInclusiveGridEndpoints() {
        // (to - from) / intervalMs == 10_000 == maxPointsPerSensor is allowed per Ticket #4 AC1
        TimeSeriesMockResponse atBoundary = generator.generateTimeSeries(List.of("S1"), 0L, 10_000_000L, 1000L);

        assertThat(atBoundary.series()).hasSize(1);
        List<TimeSeriesPoint> points = atBoundary.series().get(0).points();
        assertThat(points).hasSize(10_001);
        assertThat(points.get(0).timestamp()).isZero();
        assertThat(points.get(points.size() - 1).timestamp()).isEqualTo(10_000_000L);
    }

    @Test
    void generates100SensorsBy1000PointsWithinReasonableLatency() {
        List<String> sensorIds = IntStream.range(0, 100)
                .mapToObj(i -> "SENSOR-" + i)
                .toList();

        // Warm-up JIT
        generator.generateTimeSeries(sensorIds.subList(0, 10), 0L, 100_000L, 1000L);

        long startNs = System.nanoTime();
        TimeSeriesMockResponse response = generator.generateTimeSeries(sensorIds, 0L, 999_000L, 1000L);
        long elapsedMs = (System.nanoTime() - startNs) / 1_000_000L;

        assertThat(response.series()).hasSize(100);
        assertThat(response.series().get(0).points()).hasSize(1000);
        assertThat(elapsedMs).isLessThan(1000L);
    }

    @Test
    void appliesCustomGeneratorConfigAndPhysicsPresetForPureJavaConsumers() {
        SensorPhysicsPreset customPreset = SensorPhysicsPreset.builder()
                .baseline(500.0, 0.0)
                .amplitude(10.0, 0.0)
                .period(60.0, 1)
                .noiseRatio(0.05)
                .stepDutyCycle(0.5)
                .drift(2.0, 1.0)
                .anomaly(30.0, 0, 2.0)
                .build();
        GeneratorConfig customConfig = GeneratorConfig.builder()
                .defaultCurrentIntervalMs(5000L)
                .defaultSeriesIntervalMs(15000L)
                .minIntervalMs(500L)
                .maxPointsPerSensor(4)
                .decimalScale(4)
                .physicsPreset(customPreset)
                .build();

        Clock fixedClock = Clock.fixed(Instant.parse("2026-11-19T05:11:53.456Z"), ZoneOffset.UTC);
        DeterministicMockGenerator customGenerator = new DeterministicMockGenerator(customConfig, fixedClock);

        CurrentMockResponse current = customGenerator.generateCurrentValues(List.of("TEMP-01", "PRESS-02"), null);
        assertThat(current.timestamp()).isEqualTo(Instant.parse("2026-11-19T05:11:50Z"));
        assertThat(current.sensors()).allSatisfy(sensor ->
                assertThat(sensor.value()).isBetween(480.0, 530.0));

        TimeSeriesMockResponse series = customGenerator.generateTimeSeries(
                List.of("TEMP-01"),
                0L,
                45_000L,
                null
        );
        assertThat(series.intervalMs()).isEqualTo(15000L);
        assertThat(series.series().get(0).points()).hasSize(4);
        assertThat(series.series().get(0).points())
                .allSatisfy(point -> assertThat(point.value()).isBetween(480.0, 530.0));

        boolean hasMoreThanTwoDecimals = series.series().get(0).points().stream()
                .anyMatch(p -> Math.round(p.value() * 100.0) != p.value() * 100.0);
        assertThat(hasMoreThanTwoDecimals).isTrue();

        assertThatThrownBy(() -> customGenerator.generateCurrentValues(List.of("TEMP-01"), 400L))
                .isInstanceOf(InvalidMockRequestException.class)
                .hasMessage("intervalMs must be at least 500ms");

        assertThatThrownBy(() -> customGenerator.generateTimeSeries(List.of("TEMP-01"), 0L, 2500L, 500L))
                .isInstanceOf(InvalidMockRequestException.class)
                .hasMessage("Requested points exceed maximum limit (4)");
    }
}
