package com.samsung.ees.infra.mock.core.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.samsung.ees.infra.mock.core.config.GeneratorConfig;
import com.samsung.ees.infra.mock.core.config.SensorPhysicsPreset;
import com.samsung.ees.infra.mock.core.domain.MockPatternType;
import com.samsung.ees.infra.mock.core.exception.InvalidMockRequestException;
import com.samsung.ees.infra.mock.core.model.CurrentMockResponse;
import com.samsung.ees.infra.mock.core.model.SensorValue;
import com.samsung.ees.infra.mock.core.model.TimeSeriesMockResponse;
import com.samsung.ees.infra.mock.core.model.TimeSeriesPoint;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
        System.out.printf("100x1000 generation elapsed: %d ms%n", elapsedMs);
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

    @Test
    void quantizesCurrentAndAlignsTimeSeriesGridAtExactAndUnalignedBoundaries() {
        long unalignedNow = 1794987600999L;
        long exactNow = 1794987600000L;
        assertThat(generator.generateCurrentValues(List.of("TEMP-01"), 1000L, unalignedNow).epochMillis())
                .isEqualTo(exactNow);
        assertThat(generator.generateCurrentValues(List.of("TEMP-01"), 1000L, exactNow).epochMillis())
                .isEqualTo(exactNow);

        long unalignedFrom = 1794960012000L; // 10:00:12 -> snaps up to 10:01:00 (1794960060000L)
        long expectedGridStart = 1794960060000L;
        TimeSeriesMockResponse unalignedSeries = generator.generateTimeSeries(
                List.of("TEMP-01"), unalignedFrom, expectedGridStart + 60_000L, 60_000L
        );
        assertThat(unalignedSeries.series().get(0).points())
                .extracting(TimeSeriesPoint::timestamp)
                .containsExactly(expectedGridStart, expectedGridStart + 60_000L);

        TimeSeriesMockResponse exactSeries = generator.generateTimeSeries(
                List.of("TEMP-01"), expectedGridStart, expectedGridStart + 60_000L, 60_000L
        );
        assertThat(exactSeries.series().get(0).points())
                .extracting(TimeSeriesPoint::timestamp)
                .containsExactly(expectedGridStart, expectedGridStart + 60_000L);
    }

    @Test
    void guardsAgainst64BitOverflowAndUnderflowAtExtremeEpochBoundaries() {
        assertThatThrownBy(() -> generator.generateTimeSeries(
                List.of("TEMP-01"), -5_000_000_000_000_000_000L, 5_000_000_000_000_000_000L, 1000L))
                .isInstanceOf(InvalidMockRequestException.class)
                .hasMessage("Requested points exceed maximum limit (10000)");

        assertThatThrownBy(() -> generator.generateTimeSeries(List.of("TEMP-01"), Long.MIN_VALUE, 0L, 1000L))
                .isInstanceOf(InvalidMockRequestException.class)
                .hasMessage("Requested points exceed maximum limit (10000)");

        assertThatThrownBy(() -> generator.generateTimeSeries(
                List.of("TEMP-01"), Long.MIN_VALUE, Long.MAX_VALUE, 1000L))
                .isInstanceOf(InvalidMockRequestException.class)
                .hasMessage("Requested points exceed maximum limit (10000)");

        TimeSeriesMockResponse nearMaxEmpty = generator.generateTimeSeries(
                List.of("TEMP-01"), Long.MAX_VALUE - 5L, Long.MAX_VALUE, 1000L);
        assertThat(nearMaxEmpty.series().get(0).points()).isEmpty();

        TimeSeriesMockResponse nearMaxOnePoint = generator.generateTimeSeries(
                List.of("TEMP-01"), Long.MAX_VALUE - 1000L, Long.MAX_VALUE, 1000L);
        assertThat(nearMaxOnePoint.series().get(0).points())
                .extracting(TimeSeriesPoint::timestamp)
                .containsExactly(9223372036854775000L);

        TimeSeriesMockResponse largeIntervalCrossingZero = generator.generateTimeSeries(
                List.of("TEMP-01"), -5_000_000_000_000_000_000L, 5_000_000_000_000_000_000L, 5_000_000_000_000_000_000L);
        assertThat(largeIntervalCrossingZero.series().get(0).points())
                .extracting(TimeSeriesPoint::timestamp)
                .containsExactly(-5_000_000_000_000_000_000L, 0L, 5_000_000_000_000_000_000L);

        assertThatThrownBy(() -> generator.generateCurrentValues(List.of("TEMP-01"), 1000L, Long.MIN_VALUE))
                .isInstanceOf(InvalidMockRequestException.class)
                .hasMessage("Quantized timestamp underflows minimum 64-bit epoch millisecond");

        assertThat(generator.generateCurrentValues(List.of("TEMP-01"), 1024L, Long.MIN_VALUE).epochMillis())
                .isEqualTo(Long.MIN_VALUE);
    }

    @Test
    void assignsAllFivePatternsDeterministicallyAndDifferentiatesSensorsAndTicks() {
        List<String> sensorIds = IntStream.range(0, 100)
                .mapToObj(i -> "SENSOR-" + i)
                .toList();

        CurrentMockResponse first = generator.generateCurrentValues(sensorIds, 1000L, 1794987600000L);
        CurrentMockResponse second = generator.generateCurrentValues(sensorIds, 1000L, 1794987600000L);
        CurrentMockResponse nextTick = generator.generateCurrentValues(sensorIds, 1000L, 1794987601000L);

        assertThat(first).isEqualTo(second);
        assertThat(first.sensors()).extracting(SensorValue::value).allMatch(Double::isFinite);
        assertThat(first.sensors()).isNotEqualTo(nextTick.sensors());

        Set<MockPatternType> observed = EnumSet.noneOf(MockPatternType.class);
        for (SensorValue sensorValue : first.sensors()) {
            observed.add(sensorValue.pattern());
        }
        assertThat(observed).containsExactlyInAnyOrder(MockPatternType.values());

        CurrentMockResponse distinctSensors =
                generator.generateCurrentValues(List.of("TEMP-01", "TEMP-02"), 1000L, 1794987600000L);
        assertThat(distinctSensors.sensors().get(0).value())
                .isNotEqualTo(distinctSensors.sensors().get(1).value());
    }

    @Test
    void steadyPatternRemainsConstantWithoutNoiseAndBoundedWithNoise() {
        String sensorId = findSensorIdForPattern(MockPatternType.STEADY);
        DeterministicMockGenerator noNoiseGenerator = new DeterministicMockGenerator(
                GeneratorConfig.builder()
                        .physicsPreset(SensorPhysicsPreset.builder().noiseRatio(0.0).build())
                        .build()
        );

        double baseline = noNoiseGenerator.generateCurrentValues(List.of(sensorId), 1000L, 0L)
                .sensors().get(0).value();
        assertThat(noNoiseGenerator.generateCurrentValues(List.of(sensorId), 1000L, 60_000L)
                .sensors().get(0).value()).isEqualTo(baseline);
        assertThat(noNoiseGenerator.generateCurrentValues(List.of(sensorId), 1000L, 123_000L)
                .sensors().get(0).value()).isEqualTo(baseline);
        assertThat(noNoiseGenerator.generateCurrentValues(List.of(sensorId), 1000L, 999_000L)
                .sensors().get(0).value()).isEqualTo(baseline);

        TimeSeriesMockResponse noisySeries = generator.generateTimeSeries(List.of(sensorId), 0L, 49_000L, 1000L);
        assertThat(noisySeries.series().get(0).points())
                .allSatisfy(point -> assertThat(point.value()).isBetween(baseline - 5.0, baseline + 5.0));
    }

    @Test
    void sinePatternOscillatesPeriodicallyWithinHarmonicBounds() {
        String sensorId = findSensorIdForPattern(MockPatternType.SINE);
        DeterministicMockGenerator periodicGenerator = generatorWithPreset(
                SensorPhysicsPreset.builder()
                        .baseline(50.0, 0.0)
                        .amplitude(10.0, 0.0)
                        .period(60.0, 1)
                        .noiseRatio(0.0)
                        .build()
        );

        TimeSeriesMockResponse twoPeriods = periodicGenerator.generateTimeSeries(
                List.of(sensorId), 0L, 119_000L, 1000L
        );
        List<TimeSeriesPoint> points = twoPeriods.series().get(0).points();
        Set<Double> distinctValues = new HashSet<>();
        for (int s = 0; s < 60; s++) {
            double firstPeriodValue = points.get(s).value();
            double secondPeriodValue = points.get(s + 60).value();
            distinctValues.add(firstPeriodValue);
            assertThat(firstPeriodValue).isBetween(37.5, 62.5);
            assertThat(secondPeriodValue).isEqualTo(firstPeriodValue);
        }
        assertThat(distinctValues.size()).isGreaterThan(20);
    }

    @Test
    void stepPatternFollowsDutyCycleWithTwoDistinctPlateaus() {
        String sensorId = findSensorIdForPattern(MockPatternType.STEP);
        DeterministicMockGenerator stepGenerator = generatorWithPreset(
                SensorPhysicsPreset.builder()
                        .baseline(50.0, 0.0)
                        .amplitude(10.0, 0.0)
                        .period(100.0, 1)
                        .noiseRatio(0.0)
                        .stepDutyCycle(0.65)
                        .build()
        );

        List<Double> values = stepGenerator.generateTimeSeries(List.of(sensorId), 0L, 99_000L, 1000L)
                .series().get(0).points().stream()
                .map(TimeSeriesPoint::value)
                .toList();

        assertThat(values.stream().distinct().sorted().toList()).containsExactly(45.0, 60.0);
        long highCount = values.stream().filter(v -> v == 60.0).count();
        long lowCount = values.stream().filter(v -> v == 45.0).count();
        assertThat(highCount).isBetween(64L, 66L);
        assertThat(lowCount).isBetween(34L, 36L);
    }

    @Test
    void driftPatternRampsLinearlyOverFourPeriodsAndResets() {
        String sensorId = findSensorIdForPattern(MockPatternType.DRIFT);
        DeterministicMockGenerator driftGenerator = generatorWithPreset(
                SensorPhysicsPreset.builder()
                        .baseline(50.0, 0.0)
                        .amplitude(10.0, 0.0)
                        .period(100.0, 1)
                        .noiseRatio(0.0)
                        .drift(4.0, 1.5)
                        .build()
        );

        List<TimeSeriesPoint> points = driftGenerator.generateTimeSeries(List.of(sensorId), 0L, 799_000L, 1000L)
                .series().get(0).points();

        int resetDrops = 0;
        for (int s = 0; s < 400; s++) {
            double val = points.get(s).value();
            assertThat(val).isBetween(50.0, 65.0);
            assertThat(points.get(s + 400).value()).isEqualTo(val);
            if (s > 0) {
                double prev = points.get(s - 1).value();
                if (val < prev - 1.0) {
                    resetDrops++;
                } else {
                    assertThat(val).isGreaterThanOrEqualTo(prev);
                }
            }
        }
        assertThat(resetDrops).isEqualTo(1);
    }

    @Test
    void anomalyPatternInjectsSpikesInRoughlyFivePercentOfBuckets() {
        String sensorId = findSensorIdForPattern(MockPatternType.ANOMALY);
        DeterministicMockGenerator anomalyGenerator = generatorWithPreset(
                SensorPhysicsPreset.builder()
                        .baseline(50.0, 0.0)
                        .amplitude(10.0, 0.0)
                        .period(60.0, 1)
                        .noiseRatio(0.0)
                        .anomaly(60.0, 5, 2.5)
                        .build()
        );

        double baseValueAtBucketStart = anomalyGenerator.generateCurrentValues(List.of(sensorId), 1000L, 0L)
                .sensors().get(0).value();
        List<Double> bucketValues = anomalyGenerator.generateTimeSeries(
                        List.of(sensorId), 0L, 999L * 60_000L, 60_000L
                )
                .series().get(0).points().stream()
                .map(TimeSeriesPoint::value)
                .toList();

        List<Double> distinct = bucketValues.stream().distinct().sorted().toList();
        assertThat(distinct).hasSize(2);
        double normalVal = distinct.get(0);
        double spikeVal = distinct.get(1);
        assertThat(spikeVal - normalVal).isEqualTo(25.0);
        assertThat(baseValueAtBucketStart).isIn(normalVal, spikeVal);

        long spikeCount = bucketValues.stream().filter(v -> v == spikeVal).count();
        assertThat(spikeCount).isBetween(25L, 80L);
    }

    private DeterministicMockGenerator generatorWithPreset(SensorPhysicsPreset preset) {
        return new DeterministicMockGenerator(
                GeneratorConfig.builder()
                        .minIntervalMs(100L)
                        .maxPointsPerSensor(10000)
                        .physicsPreset(preset)
                        .build()
        );
    }

    private String findSensorIdForPattern(MockPatternType target) {
        for (int i = 0; i < 200; i++) {
            String candidate = "SENSOR-" + i;
            MockPatternType pattern = generator.generateCurrentValues(List.of(candidate), 1000L, 0L)
                    .sensors().get(0).pattern();
            if (pattern == target) {
                return candidate;
            }
        }
        throw new IllegalStateException("No sensorId found for pattern " + target);
    }
}
