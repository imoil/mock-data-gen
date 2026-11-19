package com.samsung.ees.infra.mock.core.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class DeterministicHashUtilTest {

    @Test
    void fnv1a64IsDeterministicAndSensitiveToInput() {
        long h1 = DeterministicHashUtil.fnv1a64("TEMP-01");
        long h2 = DeterministicHashUtil.fnv1a64("TEMP-01");
        long h3 = DeterministicHashUtil.fnv1a64("TEMP-02");

        assertThat(h1).isEqualTo(h2);
        assertThat(h1).isNotEqualTo(h3);
    }

    @Test
    void fnv1a64RejectsNull() {
        assertThatThrownBy(() -> DeterministicHashUtil.fnv1a64(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void gaussianNoiseIsStatelessAndDeterministic() {
        long seed = DeterministicHashUtil.fnv1a64("PRESS-02");
        long timestampMs = 1794987600000L;

        double first = DeterministicHashUtil.gaussianNoise(seed, timestampMs);
        double second = DeterministicHashUtil.gaussianNoise(seed, timestampMs);
        double nextTick = DeterministicHashUtil.gaussianNoise(seed, timestampMs + 1000L);

        assertThat(first).isEqualTo(second);
        assertThat(first).isFinite();
        assertThat(first).isNotEqualTo(nextTick);
    }
}
