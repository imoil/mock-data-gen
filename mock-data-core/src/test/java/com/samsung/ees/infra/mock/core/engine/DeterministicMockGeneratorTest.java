package com.samsung.ees.infra.mock.core.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.samsung.ees.infra.mock.core.exception.InvalidMockRequestException;
import com.samsung.ees.infra.mock.core.model.CurrentMockResponse;
import com.samsung.ees.infra.mock.core.model.TimeSeriesMockResponse;
import com.samsung.ees.infra.mock.core.model.TimeSeriesPoint;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class DeterministicMockGeneratorTest {

    private final DeterministicMockGenerator generator = new DeterministicMockGenerator();

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
        assertThatThrownBy(() -> generator.generateCurrentValues(List.of(), 1000L, 1000L))
                .isInstanceOf(InvalidMockRequestException.class)
                .hasMessage("sensorIds must not be empty");

        assertThatThrownBy(() -> generator.generateCurrentValues(List.of(" "), 1000L, 1000L))
                .isInstanceOf(InvalidMockRequestException.class)
                .hasMessage("sensorIds must not be empty");

        assertThatThrownBy(() -> generator.generateCurrentValues(List.of("S1"), 50L, 1000L))
                .isInstanceOf(InvalidMockRequestException.class)
                .hasMessage("intervalMs must be at least 100ms");

        assertThatThrownBy(() -> generator.generateTimeSeries(List.of("S1"), 2000L, 2000L, 1000L))
                .isInstanceOf(InvalidMockRequestException.class)
                .hasMessage("'from' timestamp must be before 'to'");

        assertThatThrownBy(() -> generator.generateTimeSeries(List.of("S1"), 0L, 10_001_000L, 1000L))
                .isInstanceOf(InvalidMockRequestException.class)
                .hasMessage("Requested points exceed maximum limit (10000)");
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
}
