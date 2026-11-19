package com.samsung.ees.infra.mock.core.model;

import com.samsung.ees.infra.mock.core.domain.MockPatternType;

/**
 * Single sensor measurement at a quantized instant.
 */
public record SensorValue(
        String sensorId,
        double value,
        MockPatternType pattern
) {
}
