package com.samsung.ees.infra.mock.example;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
@ActiveProfiles("local")
class LocalProfileRoutingIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void routesToMockControllerWhenHeaderIsTrue() throws Exception {
        mockMvc.perform(get("/api/v1/sensors/current")
                        .header("X-Mock-Data", "true")
                        .param("sensorIds", "TEMP-01,PRESS-02"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mock").value(true))
                .andExpect(jsonPath("$.sensors.length()").value(2))
                .andExpect(jsonPath("$.sensors[0].pattern").isNotEmpty());
    }

    @Test
    void routesToOperationalControllerWhenHeaderIsAbsentOrFalse() throws Exception {
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
    void exposesMockHeaderInOpenApiDocsForLocalProfile() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("X-Mock-Data")));
    }
}
