package com.samsung.ees.infra.mock.example.controller;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Operational production sensor controller (simulated real backend).
 * Requests with X-Mock-Data=true in local/dev profiles are routed to MockSensorController instead.
 */
@RestController
@RequestMapping("${app-custom-config.sensor.base-path:/api/v1/sensors}")
public class SensorController {

    @GetMapping("${app-custom-config.sensor.current-path:/current}")
    public ResponseEntity<Map<String, Object>> getCurrentValues(
            @RequestParam List<String> sensorIds,
            @RequestParam(required = false, defaultValue = "1000") Long intervalMs
    ) {
        Instant now = Instant.now();
        List<Map<String, Object>> sensors = sensorIds.stream()
                .map(id -> Map.<String, Object>of(
                        "sensorId", id,
                        "value", 25.0,
                        "source", "LIVE_STORE"
                ))
                .toList();

        return ResponseEntity.ok(Map.of(
                "timestamp", now.toString(),
                "epochMillis", now.toEpochMilli(),
                "mock", false,
                "sensors", sensors
        ));
    }

    @GetMapping("${app-custom-config.sensor.timeseries-path:/timeseries}")
    public ResponseEntity<Map<String, Object>> getTimeSeries(
            @RequestParam List<String> sensorIds,
            @RequestParam String from,
            @RequestParam String to,
            @RequestParam(required = false, defaultValue = "10000") Long intervalMs
    ) {
        List<Map<String, Object>> series = sensorIds.stream()
                .map(id -> Map.<String, Object>of(
                        "sensorId", id,
                        "source", "LIVE_STORE",
                        "points", List.of()
                ))
                .toList();

        return ResponseEntity.ok(Map.of(
                "from", from,
                "to", to,
                "intervalMs", intervalMs,
                "mock", false,
                "series", series
        ));
    }
}
