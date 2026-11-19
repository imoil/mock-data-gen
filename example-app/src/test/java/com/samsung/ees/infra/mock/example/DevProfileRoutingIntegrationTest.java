package com.samsung.ees.infra.mock.example;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class DevProfileRoutingIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

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
