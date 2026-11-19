package com.samsung.ees.infra.mock.example;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@TestPropertySource(properties = {
        "app-custom-config.sensor.base-path=/api/v2/telemetry",
        "app-custom-config.sensor.current-path=/latest",
        "app-custom-config.sensor.timeseries-path=/history",
        "ees.mock.sensor.base-path=/api/v2/telemetry",
        "ees.mock.sensor.current-path=/latest",
        "ees.mock.sensor.timeseries-path=/history"
})
class CustomEndpointUriIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void servesMockAndOperationalRoutesOnCustomizedUris() throws Exception {
        mockMvc.perform(get("/api/v2/telemetry/latest")
                        .header("X-Mock-Data", "true")
                        .param("sensorIds", "TEMP-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mock").value(true));

        mockMvc.perform(get("/api/v2/telemetry/latest")
                        .param("sensorIds", "TEMP-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mock").value(false))
                .andExpect(jsonPath("$.sensors[0].source").value("LIVE_STORE"));

        mockMvc.perform(get("/api/v2/telemetry/history")
                        .header("X-Mock-Data", "true")
                        .param("sensorIds", "TEMP-01")
                        .param("from", "1794984000000")
                        .param("to", "1794984300000")
                        .param("intervalMs", "60000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mock").value(true))
                .andExpect(jsonPath("$.series[0].points.length()").value(6));

        mockMvc.perform(get("/api/v2/telemetry/history")
                        .param("sensorIds", "TEMP-01")
                        .param("from", "1794984000000")
                        .param("to", "1794984300000")
                        .param("intervalMs", "60000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mock").value(false))
                .andExpect(jsonPath("$.series[0].sensorId").value("TEMP-01"))
                .andExpect(jsonPath("$.series[0].source").value("LIVE_STORE"))
                .andExpect(jsonPath("$.series[0].points").isEmpty());

        mockMvc.perform(get("/api/v2/telemetry/history")
                        .header("X-Mock-Data", "false")
                        .param("sensorIds", "TEMP-01")
                        .param("from", "1794984000000")
                        .param("to", "1794984300000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mock").value(false))
                .andExpect(jsonPath("$.series[0].source").value("LIVE_STORE"));
    }

    @Test
    void returnsNotFoundOnDefaultEndpointsWhenUrisAreOverridden() throws Exception {
        mockMvc.perform(get("/api/v1/sensors/current")
                        .header("X-Mock-Data", "true")
                        .param("sensorIds", "TEMP-01"))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/sensors/current")
                        .param("sensorIds", "TEMP-01"))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/sensors/timeseries")
                        .header("X-Mock-Data", "true")
                        .param("sensorIds", "TEMP-01")
                        .param("from", "1794984000000")
                        .param("to", "1794984300000"))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/sensors/timeseries")
                        .param("sensorIds", "TEMP-01")
                        .param("from", "1794984000000")
                        .param("to", "1794984300000"))
                .andExpect(status().isNotFound());
    }

    @Test
    void exposesMockHeaderOnCustomizedEndpointsInOpenApiDocs() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v2/telemetry/latest'].get.parameters[?(@.name=='X-Mock-Data')].in")
                        .value(hasItem("header")))
                .andExpect(jsonPath("$.paths['/api/v2/telemetry/latest'].get.parameters[?(@.name=='X-Mock-Data')].required")
                        .value(hasItem(false)))
                .andExpect(jsonPath("$.paths['/api/v2/telemetry/history'].get.parameters[?(@.name=='X-Mock-Data')].in")
                        .value(hasItem("header")))
                .andExpect(jsonPath("$.paths['/api/v2/telemetry/history'].get.parameters[?(@.name=='X-Mock-Data')].required")
                        .value(hasItem(false)))
                .andExpect(jsonPath("$.paths['/api/v1/sensors/current']").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/sensors/timeseries']").doesNotExist());
    }
}
