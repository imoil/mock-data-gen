package com.samsung.ees.infra.mock.core.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class TimeGridAlignerTest {

    @Test
    void quantizeCurrentFloorsToIntervalMultiple() {
        assertThat(TimeGridAligner.quantizeCurrent(1794987600999L, 1000L)).isEqualTo(1794987600000L);
        assertThat(TimeGridAligner.quantizeCurrent(1794987600000L, 1000L)).isEqualTo(1794987600000L);
    }

    @Test
    void alignGridStartSnapsUpToIntervalMultiple() {
        // 10:00:12 -> 10:01:00 when intervalMs = 60_000
        long from = 1794960012000L;
        long expected = 1794960060000L;
        assertThat(TimeGridAligner.alignGridStart(from, 60_000L)).isEqualTo(expected);

        // Exact boundary remains unchanged
        assertThat(TimeGridAligner.alignGridStart(expected, 60_000L)).isEqualTo(expected);
    }

    @Test
    void rejectsNonPositiveInterval() {
        assertThatThrownBy(() -> TimeGridAligner.quantizeCurrent(1000L, 0L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TimeGridAligner.alignGridStart(1000L, -100L))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
