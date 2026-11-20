package com.samsung.ees.infra.mock.core.engine;

import com.samsung.ees.infra.mock.core.config.GeneratorConfig;
import com.samsung.ees.infra.mock.core.engine.DeterministicWaveCalculator.PreparedSensor;
import com.samsung.ees.infra.mock.core.exception.InvalidMockRequestException;
import com.samsung.ees.infra.mock.core.model.CurrentMockResponse;
import com.samsung.ees.infra.mock.core.model.SensorSeries;
import com.samsung.ees.infra.mock.core.model.SensorValue;
import com.samsung.ees.infra.mock.core.model.TimeSeriesMockResponse;
import com.samsung.ees.infra.mock.core.model.TimeSeriesPoint;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Stateless entry point for generating deterministic current and time-series sensor mock data.
 */
public class DeterministicMockGenerator {

    private final GeneratorConfig config;
    private final DeterministicWaveCalculator waveCalculator;
    private final Clock clock;

    public DeterministicMockGenerator() {
        this(GeneratorConfig.defaults());
    }

    public DeterministicMockGenerator(GeneratorConfig config) {
        this(config, Clock.systemUTC());
    }

    public DeterministicMockGenerator(GeneratorConfig config, Clock clock) {
        if (config == null) {
            throw new IllegalArgumentException("config must not be null");
        }
        if (clock == null) {
            throw new IllegalArgumentException("clock must not be null");
        }
        this.config = config;
        this.waveCalculator = new DeterministicWaveCalculator(config.physicsPreset());
        this.clock = clock;
    }

    /**
     * Generates deterministic current sensor values using the configured clock.
     *
     * @param sensorIds non-empty list of sensor identifiers
     * @param intervalMs optional quantization interval in milliseconds
     * @return current mock response
     */
    public CurrentMockResponse generateCurrentValues(List<String> sensorIds, Long intervalMs) {
        return generateCurrentValues(sensorIds, intervalMs, clock.millis());
    }

    /**
     * Generates deterministic current sensor values for an explicit reference timestamp.
     *
     * @param sensorIds non-empty list of sensor identifiers
     * @param intervalMs optional quantization interval in milliseconds
     * @param nowEpochMs reference epoch timestamp in milliseconds
     * @return current mock response
     */
    public CurrentMockResponse generateCurrentValues(List<String> sensorIds, Long intervalMs, long nowEpochMs) {
        List<String> normalizedIds = normalizeSensorIds(sensorIds);
        long effectiveInterval = resolveInterval(intervalMs, config.defaultCurrentIntervalMs());
        long alignedEpochMs = quantizeCurrent(nowEpochMs, effectiveInterval);

        List<SensorValue> values = new ArrayList<>(normalizedIds.size());
        for (String sensorId : normalizedIds) {
            PreparedSensor prepared = waveCalculator.prepare(sensorId);
            double value = waveCalculator.calculate(prepared, alignedEpochMs, config.decimalScale());
            values.add(new SensorValue(sensorId, value, prepared.pattern()));
        }

        return new CurrentMockResponse(
                Instant.ofEpochMilli(alignedEpochMs),
                alignedEpochMs,
                true,
                List.copyOf(values)
        );
    }

    /**
     * Generates deterministic grid-aligned time-series data for multiple sensors.
     *
     * @param sensorIds non-empty list of sensor identifiers
     * @param fromEpochMs query start epoch timestamp in milliseconds
     * @param toEpochMs query end epoch timestamp in milliseconds
     * @param intervalMs optional sampling interval in milliseconds
     * @return time-series mock response
     */
    public TimeSeriesMockResponse generateTimeSeries(
            List<String> sensorIds,
            long fromEpochMs,
            long toEpochMs,
            Long intervalMs
    ) {
        List<String> normalizedIds = normalizeSensorIds(sensorIds);
        if (fromEpochMs >= toEpochMs) {
            throw new InvalidMockRequestException("'from' timestamp must be before 'to'");
        }
        long effectiveInterval = resolveInterval(intervalMs, config.defaultSeriesIntervalMs());

        long estimatedIntervals = Long.divideUnsigned(toEpochMs - fromEpochMs, effectiveInterval);
        if (Long.compareUnsigned(estimatedIntervals, config.maxPointsPerSensor()) > 0) {
            throw new InvalidMockRequestException(
                    "Requested points exceed maximum limit (" + config.maxPointsPerSensor() + ")"
            );
        }

        List<Long> timestamps = buildAlignedTimestamps(fromEpochMs, toEpochMs, effectiveInterval);

        List<SensorSeries> seriesList = new ArrayList<>(normalizedIds.size());
        for (String sensorId : normalizedIds) {
            PreparedSensor prepared = waveCalculator.prepare(sensorId);
            List<TimeSeriesPoint> points = new ArrayList<>(timestamps.size());
            for (long ts : timestamps) {
                double value = waveCalculator.calculate(prepared, ts, config.decimalScale());
                points.add(new TimeSeriesPoint(ts, value));
            }
            seriesList.add(new SensorSeries(sensorId, prepared.pattern(), List.copyOf(points)));
        }

        return new TimeSeriesMockResponse(
                Instant.ofEpochMilli(fromEpochMs),
                Instant.ofEpochMilli(toEpochMs),
                effectiveInterval,
                true,
                List.copyOf(seriesList)
        );
    }

    private List<String> normalizeSensorIds(List<String> sensorIds) {
        if (sensorIds == null || sensorIds.isEmpty()) {
            throw new InvalidMockRequestException("sensorIds must not be empty");
        }
        List<String> normalized = new ArrayList<>(sensorIds.size());
        for (String rawId : sensorIds) {
            if (rawId == null || rawId.isBlank()) {
                throw new InvalidMockRequestException("sensorIds must not be empty");
            }
            normalized.add(rawId.trim());
        }
        return normalized;
    }

    private long resolveInterval(Long intervalMs, long defaultIntervalMs) {
        long effective = (intervalMs != null) ? intervalMs : defaultIntervalMs;
        if (effective < config.minIntervalMs()) {
            throw new InvalidMockRequestException(
                    "intervalMs must be at least " + config.minIntervalMs() + "ms"
            );
        }
        return effective;
    }

    private static long quantizeCurrent(long nowEpochMs, long intervalMs) {
        long rem = Math.floorMod(nowEpochMs, intervalMs);
        if (nowEpochMs < Long.MIN_VALUE + rem) {
            throw new InvalidMockRequestException(
                    "Quantized timestamp underflows minimum 64-bit epoch millisecond"
            );
        }
        return nowEpochMs - rem;
    }

    private static List<Long> buildAlignedTimestamps(long fromEpochMs, long toEpochMs, long intervalMs) {
        long rem = Math.floorMod(fromEpochMs, intervalMs);
        long gridStart;
        if (rem == 0L) {
            gridStart = fromEpochMs;
        } else {
            long delta = intervalMs - rem;
            if (fromEpochMs > Long.MAX_VALUE - delta) {
                return List.of();
            }
            gridStart = fromEpochMs + delta;
        }
        if (gridStart > toEpochMs) {
            return List.of();
        }
        long countMinusOne = Long.divideUnsigned(toEpochMs - gridStart, intervalMs);
        int capacity = (int) Math.min(countMinusOne + 1L, Integer.MAX_VALUE);
        List<Long> timestamps = new ArrayList<>(capacity);
        for (long t = gridStart; t <= toEpochMs; t += intervalMs) {
            timestamps.add(t);
            if (Long.MAX_VALUE - intervalMs < t) {
                break;
            }
        }
        return timestamps;
    }
}
