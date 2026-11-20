import com.samsung.ees.infra.mock.core.config.GeneratorConfig;
import com.samsung.ees.infra.mock.core.config.SensorPhysicsPreset;
import com.samsung.ees.infra.mock.core.engine.DeterministicMockGenerator;
import com.samsung.ees.infra.mock.core.exception.InvalidMockRequestException;
import com.samsung.ees.infra.mock.core.model.CurrentMockResponse;
import com.samsung.ees.infra.mock.core.model.TimeSeriesMockResponse;
import com.samsung.ees.infra.mock.core.model.TimeSeriesPoint;
import java.math.BigInteger;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Property-based and boundary-exhaustive harness exercising the real compiled
 * {@link DeterministicMockGenerator} for grid alignment, continuity, and 64-bit bound safety.
 */
public final class GridBoundsVerifier {

    private static final long PRNG_SEED = 0x5EED_2026_1119L;
    private static final List<String> SENSORS = List.of("TEMP-01", "PRESS-02");

    private record Finding(String property, String summary, String counterexample) {
    }

    public static void main(String[] args) {
        if (args.length >= 1 && "--mode=single-case".equals(args[0])) {
            long from = Long.parseLong(args[1]);
            long to = Long.parseLong(args[2]);
            long interval = Long.parseLong(args[3]);
            int maxPoints = Integer.parseInt(args[4]);
            runSingleExtremeCase(from, to, interval, maxPoints);
            return;
        }
        runSuite();
    }

    private static void runSingleExtremeCase(long from, long to, long interval, int maxPoints) {
        GeneratorConfig config = GeneratorConfig.builder()
                .minIntervalMs(1L)
                .defaultCurrentIntervalMs(1000L)
                .defaultSeriesIntervalMs(10000L)
                .maxPointsPerSensor(maxPoints)
                .physicsPreset(SensorPhysicsPreset.defaults())
                .build();
        DeterministicMockGenerator generator = new DeterministicMockGenerator(config, Clock.systemUTC());
        try {
            TimeSeriesMockResponse resp = generator.generateTimeSeries(List.of("TEMP-01"), from, to, interval);
            int actualSize = resp.series().get(0).points().size();
            long maxAllowedPoints = (long) maxPoints + 1L;
            if (actualSize > maxAllowedPoints) {
                System.out.printf(
                        "FAIL|P1|Returned %d points exceeding inclusive limit maxPointsPerSensor+1 (%d)|from=%d, to=%d, intervalMs=%d%n",
                        actualSize, maxAllowedPoints, from, to, interval
                );
                System.exit(2);
            }
            // Also verify bounds & alignment on any accepted result
            BigInteger iv = BigInteger.valueOf(interval);
            BigInteger biFrom = BigInteger.valueOf(from);
            BigInteger biTo = BigInteger.valueOf(to);
            for (TimeSeriesPoint pt : resp.series().get(0).points()) {
                BigInteger bt = BigInteger.valueOf(pt.timestamp());
                if (bt.compareTo(biFrom) < 0 || bt.compareTo(biTo) > 0 || bt.mod(iv).signum() != 0) {
                    System.out.printf(
                            "FAIL|P2|Point timestamp %d out of range or misaligned|from=%d, to=%d, intervalMs=%d%n",
                            pt.timestamp(), from, to, interval
                    );
                    System.exit(2);
                }
            }
            System.out.println("PASS|Accepted safely within bounds (" + actualSize + " points)");
        } catch (InvalidMockRequestException expectedRejection) {
            System.out.println("PASS|Rejected cleanly with InvalidMockRequestException: " + expectedRejection.getMessage());
        } catch (Throwable unexpected) {
            System.out.printf(
                    "FAIL|P1|Unexpected %s: %s|from=%d, to=%d, intervalMs=%d, maxPointsPerSensor=%d%n",
                    unexpected.getClass().getName(), unexpected.getMessage(), from, to, interval, maxPoints
            );
            System.exit(2);
        }
    }

    private static void runSuite() {
        List<Finding> findings = new ArrayList<>();
        Random rng = new Random(PRNG_SEED);

        checkPointCountBoundary(findings);
        checkGridAlignmentAndMaximality(findings, rng, 2000);
        checkSlidingWindowContinuity(findings, rng, 1000);
        checkCurrentQuantizationAndConsistency(findings, rng, 1000);

        System.out.println("SUITE_FINDINGS=" + findings.size());
        for (Finding f : findings) {
            System.out.printf("FINDING|%s|%s|%s%n", f.property(), f.summary(), f.counterexample());
        }
        System.exit(findings.isEmpty() ? 0 : 2);
    }

    private static void checkPointCountBoundary(List<Finding> findings) {
        int[][] cases = {
                {1000, 1},
                {1000, 5},
                {100, 100},
                {10000, 10000}
        };
        for (int[] c : cases) {
            long interval = c[0];
            int maxPoints = c[1];
            long from = 0L;
            long toAllowed = interval * maxPoints;
            long toExceeded = interval * (maxPoints + 1L);
            GeneratorConfig config = GeneratorConfig.builder()
                    .minIntervalMs(1L)
                    .defaultCurrentIntervalMs(interval)
                    .defaultSeriesIntervalMs(interval)
                    .maxPointsPerSensor(maxPoints)
                    .build();
            DeterministicMockGenerator gen = new DeterministicMockGenerator(config, Clock.systemUTC());
            TimeSeriesMockResponse resp = gen.generateTimeSeries(List.of("TEMP-01"), from, toAllowed, interval);
            int count = resp.series().get(0).points().size();
            if (count > maxPoints + 1) {
                findings.add(new Finding(
                        "P1",
                        "Generated " + count + " points exceeding maxPointsPerSensor+1 (" + (maxPoints + 1) + ")",
                        "fromEpochMs=" + from + ", toEpochMs=" + toAllowed + ", intervalMs=" + interval + ", maxPointsPerSensor=" + maxPoints
                ));
                return;
            }
            try {
                gen.generateTimeSeries(List.of("TEMP-01"), from, toExceeded, interval);
                findings.add(new Finding(
                        "P1",
                        "Accepted request exceeding maxPointsPerSensor ratio limit",
                        "fromEpochMs=" + from + ", toEpochMs=" + toExceeded + ", intervalMs=" + interval + ", maxPointsPerSensor=" + maxPoints
                ));
                return;
            } catch (InvalidMockRequestException expected) {
                // Expected rejection above maxPointsPerSensor ratio.
            }
        }
    }

    private static void checkGridAlignmentAndMaximality(List<Finding> findings, Random rng, int trials) {
        GeneratorConfig config = GeneratorConfig.builder()
                .minIntervalMs(1L)
                .defaultCurrentIntervalMs(1000L)
                .defaultSeriesIntervalMs(10000L)
                .maxPointsPerSensor(10000)
                .build();
        DeterministicMockGenerator gen = new DeterministicMockGenerator(config, Clock.systemUTC());

        long[] fixedFroms = {-10_005L, -1000L, -999L, -1L, 0L, 1L, 999L, 1000L, 1_795_000_000_123L};
        for (int i = 0; i < trials; i++) {
            long interval = (i < fixedFroms.length) ? 1000L : (1L + rng.nextInt(120_000));
            long from = (i < fixedFroms.length)
                    ? fixedFroms[i]
                    : (rng.nextLong(200_000_000_000L) - 100_000_000_000L);
            long span = 1L + rng.nextLong(interval * 50L);
            long to = from + span;

            TimeSeriesMockResponse resp = gen.generateTimeSeries(SENSORS, from, to, interval);
            List<TimeSeriesPoint> pts = resp.series().get(0).points();
            BigInteger biFrom = BigInteger.valueOf(from);
            BigInteger biTo = BigInteger.valueOf(to);
            BigInteger biIv = BigInteger.valueOf(interval);

            for (int k = 0; k < pts.size(); k++) {
                long t = pts.get(k).timestamp();
                BigInteger bt = BigInteger.valueOf(t);
                if (bt.compareTo(biFrom) < 0 || bt.compareTo(biTo) > 0) {
                    findings.add(new Finding(
                            "P2",
                            "Emitted timestamp " + t + " outside [from, to]",
                            "fromEpochMs=" + from + ", toEpochMs=" + to + ", intervalMs=" + interval
                    ));
                    return;
                }
                if (bt.mod(biIv).signum() != 0) {
                    findings.add(new Finding(
                            "P2",
                            "Emitted timestamp " + t + " is not aligned to intervalMs=" + interval,
                            "fromEpochMs=" + from + ", toEpochMs=" + to + ", intervalMs=" + interval
                    ));
                    return;
                }
                if (k > 0) {
                    long prev = pts.get(k - 1).timestamp();
                    if (t - prev != interval) {
                        findings.add(new Finding(
                                "P2",
                                "Non-uniform step between " + prev + " and " + t + " (expected " + interval + ")",
                                "fromEpochMs=" + from + ", toEpochMs=" + to + ", intervalMs=" + interval
                        ));
                        return;
                    }
                }
            }

            if (pts.isEmpty()) {
                // Verify no multiple of interval exists in [from, to]
                BigInteger firstMultiple = ceilMultiple(biFrom, biIv);
                if (firstMultiple.compareTo(biTo) <= 0) {
                    findings.add(new Finding(
                            "P2",
                            "Returned empty points even though grid point " + firstMultiple + " lies in [from, to]",
                            "fromEpochMs=" + from + ", toEpochMs=" + to + ", intervalMs=" + interval
                    ));
                    return;
                }
            } else {
                BigInteger first = BigInteger.valueOf(pts.get(0).timestamp());
                BigInteger last = BigInteger.valueOf(pts.get(pts.size() - 1).timestamp());
                if (first.subtract(biIv).compareTo(biFrom) >= 0) {
                    findings.add(new Finding(
                            "P2",
                            "Skipped leading grid point " + first.subtract(biIv),
                            "fromEpochMs=" + from + ", toEpochMs=" + to + ", intervalMs=" + interval
                    ));
                    return;
                }
                if (last.add(biIv).compareTo(biTo) <= 0) {
                    findings.add(new Finding(
                            "P2",
                            "Skipped trailing grid point " + last.add(biIv),
                            "fromEpochMs=" + from + ", toEpochMs=" + to + ", intervalMs=" + interval
                    ));
                    return;
                }
            }
        }
    }

    private static void checkSlidingWindowContinuity(List<Finding> findings, Random rng, int trials) {
        GeneratorConfig config = GeneratorConfig.builder()
                .minIntervalMs(1L)
                .defaultCurrentIntervalMs(1000L)
                .defaultSeriesIntervalMs(10000L)
                .maxPointsPerSensor(10000)
                .build();
        DeterministicMockGenerator gen = new DeterministicMockGenerator(config, Clock.systemUTC());

        for (int i = 0; i < trials; i++) {
            long interval = 100L + rng.nextInt(60_000);
            long base = rng.nextLong(2_000_000_000_000L) - 1_000_000_000_000L;
            long from1 = base;
            long to1 = base + interval * (5 + rng.nextInt(40)) + rng.nextInt((int) interval);
            long shift = rng.nextLong(interval * 10L);
            long from2 = from1 + shift;
            long to2 = to1 + shift;

            TimeSeriesMockResponse r1 = gen.generateTimeSeries(List.of("TEMP-01"), from1, to1, interval);
            TimeSeriesMockResponse r2 = gen.generateTimeSeries(List.of("TEMP-01"), from2, to2, interval);

            Map<Long, Double> map1 = new LinkedHashMap<>();
            for (TimeSeriesPoint p : r1.series().get(0).points()) {
                map1.put(p.timestamp(), p.value());
            }
            Map<Long, Double> map2 = new LinkedHashMap<>();
            for (TimeSeriesPoint p : r2.series().get(0).points()) {
                map2.put(p.timestamp(), p.value());
            }

            long overlapStart = Math.max(from1, from2);
            long overlapEnd = Math.min(to1, to2);
            for (Map.Entry<Long, Double> e : map1.entrySet()) {
                long t = e.getKey();
                if (t >= overlapStart && t <= overlapEnd) {
                    if (!map2.containsKey(t) || Double.compare(e.getValue(), map2.get(t)) != 0) {
                        findings.add(new Finding(
                                "P3",
                                "Sliding-window discontinuity at timestamp " + t,
                                "window1=[" + from1 + "," + to1 + "], window2=[" + from2 + "," + to2 + "], intervalMs=" + interval
                        ));
                        return;
                    }
                }
            }
        }
    }

    private static void checkCurrentQuantizationAndConsistency(List<Finding> findings, Random rng, int trials) {
        GeneratorConfig config = GeneratorConfig.builder()
                .minIntervalMs(1L)
                .defaultCurrentIntervalMs(1000L)
                .defaultSeriesIntervalMs(10000L)
                .maxPointsPerSensor(10000)
                .build();
        DeterministicMockGenerator gen = new DeterministicMockGenerator(config, Clock.systemUTC());

        long[] boundaryNows = {
                Long.MIN_VALUE, Long.MIN_VALUE + 1, -1001L, -1000L, -999L, -1L,
                0L, 1L, 999L, 1000L, 1001L, Long.MAX_VALUE - 1, Long.MAX_VALUE
        };

        for (int i = 0; i < boundaryNows.length + trials; i++) {
            long interval = (i < boundaryNows.length) ? 1000L : (1L + rng.nextInt(120_000));
            long now = (i < boundaryNows.length) ? boundaryNows[i] : rng.nextLong();

            BigInteger biNow = BigInteger.valueOf(now);
            BigInteger biIv = BigInteger.valueOf(interval);
            BigInteger mathFloor = floorMultiple(biNow, biIv);
            boolean mathUnderflowsLong = mathFloor.compareTo(BigInteger.valueOf(Long.MIN_VALUE)) < 0;

            CurrentMockResponse cur;
            try {
                cur = gen.generateCurrentValues(List.of("TEMP-01"), interval, now);
            } catch (InvalidMockRequestException ex) {
                if (!mathUnderflowsLong) {
                    findings.add(new Finding(
                            "P4",
                            "Unexpected InvalidMockRequestException for representable nowEpochMs=" + now + ": " + ex.getMessage(),
                            "nowEpochMs=" + now + ", intervalMs=" + interval
                    ));
                    return;
                }
                continue;
            }
            if (mathUnderflowsLong) {
                findings.add(new Finding(
                        "P4",
                        "Accepted unrepresentable quantization underflow without rejecting",
                        "nowEpochMs=" + now + ", intervalMs=" + interval + ", returnedEpochMillis=" + cur.epochMillis()
                ));
                return;
            }
            long q = cur.epochMillis();
            BigInteger biQ = BigInteger.valueOf(q);

            if (biQ.mod(biIv).signum() != 0 || biQ.compareTo(biNow) > 0 || biNow.subtract(biQ).compareTo(biIv) >= 0) {
                findings.add(new Finding(
                        "P4",
                        "quantizeCurrent overflow/misalignment: epochMillis=" + q + " for nowEpochMs=" + now,
                        "nowEpochMs=" + now + ", intervalMs=" + interval + ", returnedEpochMillis=" + q
                ));
                return;
            }

            if (q <= Long.MAX_VALUE - interval) {
                TimeSeriesMockResponse ts = gen.generateTimeSeries(List.of("TEMP-01"), q, q + interval, interval);
                List<TimeSeriesPoint> pts = ts.series().get(0).points();
                if (pts.isEmpty() || pts.get(0).timestamp() != q
                        || Double.compare(pts.get(0).value(), cur.sensors().get(0).value()) != 0
                        || ts.series().get(0).pattern() != cur.sensors().get(0).pattern()) {
                    findings.add(new Finding(
                            "P4",
                            "Current vs TimeSeries value/pattern mismatch at quantized timestamp " + q,
                            "nowEpochMs=" + now + ", intervalMs=" + interval + ", q=" + q
                    ));
                    return;
                }
            }
        }
    }

    private static BigInteger ceilMultiple(BigInteger x, BigInteger m) {
        BigInteger[] divRem = x.divideAndRemainder(m);
        BigInteger q = divRem[0];
        BigInteger r = divRem[1];
        if (r.signum() > 0) {
            q = q.add(BigInteger.ONE);
        }
        return q.multiply(m);
    }

    private static BigInteger floorMultiple(BigInteger x, BigInteger m) {
        BigInteger[] divRem = x.divideAndRemainder(m);
        BigInteger q = divRem[0];
        BigInteger r = divRem[1];
        if (r.signum() < 0) {
            q = q.subtract(BigInteger.ONE);
        }
        return q.multiply(m);
    }
}
