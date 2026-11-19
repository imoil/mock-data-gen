package com.samsung.ees.infra.mock.example;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class DevProfileRoutingIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void routesCurrentToMockControllerWhenHeaderIsTrueInDevProfile() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/sensors/current")
                        .header("X-Mock-Data", "true")
                        .param("sensorIds", "TEMP-01,PRESS-02"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timestamp").isString())
                .andExpect(jsonPath("$.epochMillis").isNumber())
                .andExpect(jsonPath("$.mock").value(true))
                .andExpect(jsonPath("$.sensors.length()").value(2))
                .andExpect(jsonPath("$.sensors[0].sensorId").value("TEMP-01"))
                .andExpect(jsonPath("$.sensors[0].pattern").isNotEmpty())
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        Instant parsedTimestamp = Instant.parse(root.get("timestamp").asText());
        assertThat(parsedTimestamp.toEpochMilli()).isEqualTo(root.get("epochMillis").asLong());
    }

    @Test
    void routesCurrentToOperationalControllerWhenHeaderIsAbsentOrFalseInDevProfile() throws Exception {
        mockMvc.perform(get("/api/v1/sensors/current")
                        .param("sensorIds", "TEMP-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mock").value(false))
                .andExpect(jsonPath("$.sensors[0].source").value("LIVE_STORE"));

        mockMvc.perform(get("/api/v1/sensors/current")
                        .header("X-Mock-Data", "false")
                        .param("sensorIds", "TEMP-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mock").value(false))
                .andExpect(jsonPath("$.sensors[0].source").value("LIVE_STORE"));
    }

    @Test
    void activatesMockRoutingInDevProfile() throws Exception {
        mockMvc.perform(get("/api/v1/sensors/timeseries")
                        .header("X-Mock-Data", "true")
                        .param("sensorIds", "TEMP-01")
                        .param("from", "2026-11-18T06:40:00Z")
                        .param("to", "2026-11-18T06:45:00Z")
                        .param("intervalMs", "60000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mock").value(true))
                .andExpect(jsonPath("$.series[0].points.length()").value(6));
    }
}
