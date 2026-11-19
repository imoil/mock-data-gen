package com.samsung.ees.infra.mock.core.util;

import java.nio.charset.StandardCharsets;

/**
 * Stateless 64-bit hashing and deterministic Box-Muller Gaussian noise utilities.
 */
public final class DeterministicHashUtil {

    private static final long FNV_64_OFFSET_BASIS = 0xcbf29ce484222325L;
    private static final long FNV_64_PRIME = 0x100000001b3L;
    private static final long GOLDEN_GAMMA = 0x9e3779b97f4a7c15L;
    private static final double UNIFORM_DENOMINATOR = (1L << 53) + 1.0;

    private DeterministicHashUtil() {
    }

    /**
     * Computes an unsigned 64-bit FNV-1a hash from the UTF-8 bytes of the input string.
     *
     * @param input non-null input string
     * @return 64-bit hash value
     */
    public static long fnv1a64(String input) {
        if (input == null) {
            throw new IllegalArgumentException("input must not be null");
        }
        long hash = FNV_64_OFFSET_BASIS;
        byte[] bytes = input.getBytes(StandardCharsets.UTF_8);
        for (byte b : bytes) {
            hash ^= (b & 0xffL);
            hash *= FNV_64_PRIME;
        }
        return hash;
    }

    /**
     * Applies SplitMix64 mixing to decorrelate derived seeds.
     *
     * @param z 64-bit input seed
     * @return mixed 64-bit hash
     */
    public static long mix64(long z) {
        z = (z ^ (z >>> 33)) * 0xff51afd7ed558ccdL;
        z = (z ^ (z >>> 33)) * 0xc4ceb9fe1a85ec53L;
        return z ^ (z >>> 33);
    }

    /**
     * Generates deterministic standard normal N(0, 1) noise for a (sensorSeed, timestampMs) pair.
     *
     * @param sensorSeed 64-bit sensor seed
     * @param timestampMs epoch timestamp in milliseconds
     * @return standard normal noise sample
     */
    public static double gaussianNoise(long sensorSeed, long timestampMs) {
        long h1 = mix64(sensorSeed ^ timestampMs);
        long h2 = mix64(h1 ^ GOLDEN_GAMMA);

        // Map top 53 bits to (0.0, 1.0] so Math.log(u1) never receives 0.0.
        double u1 = ((h1 >>> 11) + 1.0) / UNIFORM_DENOMINATOR;
        double u2 = ((h2 >>> 11) + 1.0) / UNIFORM_DENOMINATOR;

        return Math.sqrt(-2.0 * Math.log(u1)) * Math.cos(2.0 * Math.PI * u2);
    }
}
