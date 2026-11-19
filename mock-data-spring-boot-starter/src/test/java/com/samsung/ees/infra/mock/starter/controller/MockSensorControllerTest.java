package com.samsung.ees.infra.mock.starter.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = MockSensorControllerTest.TestApp.class)
@AutoConfigureMockMvc
@ActiveProfiles("local")
class MockSensorControllerTest {

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApp {
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void acceptsIso8601AndEpochMillisTimestamps() throws Exception {
        mockMvc.perform(get("/api/v1/sensors/timeseries")
                        .header("X-Mock-Data", "true")
                        .param("sensorIds", "TEMP-01,PRESS-02")
                        .param("from", "2026-11-18T06:40:00Z")
                        .param("to", "2026-11-18T07:40:00Z")
                        .param("intervalMs", "60000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mock").value(true))
                .andExpect(jsonPath("$.series.length()").value(2));

        mockMvc.perform(get("/api/v1/sensors/timeseries")
                        .header("X-Mock-Data", "true")
                        .param("sensorIds", "TEMP-01")
                        .param("from", "1794984000000")
                        .param("to", "1794987600000")
                        .param("intervalMs", "60000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mock").value(true))
                .andExpect(jsonPath("$.series[0].sensorId").value("TEMP-01"))
                .andExpect(jsonPath("$.series[0].points[0].timestamp").value(1794984000000L));
    }

    @Test
    void returns400MockErrorResponseOnInvalidInputs() throws Exception {
        mockMvc.perform(get("/api/v1/sensors/current")
                        .header("X-Mock-Data", "true"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("sensorIds must not be empty"));

        mockMvc.perform(get("/api/v1/sensors/timeseries")
                        .header("X-Mock-Data", "true")
                        .param("sensorIds", "TEMP-01")
                        .param("from", "2026-11-18T07:40:00Z")
                        .param("to", "2026-11-18T06:40:00Z"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("'from' timestamp must be before 'to'"));

        mockMvc.perform(get("/api/v1/sensors/timeseries")
                        .header("X-Mock-Data", "true")
                        .param("sensorIds", "TEMP-01")
                        .param("from", "1794984000000")
                        .param("to", "1794987600000")
                        .param("intervalMs", "50"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("intervalMs must be at least 100ms"));
    }
}
