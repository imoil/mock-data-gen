package com.samsung.ees.infra.mock.example;

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
        "app-custom-config.mock.sensor.base-path=/api/v2/telemetry",
        "app-custom-config.mock.sensor.current-path=/latest",
        "app-custom-config.mock.sensor.timeseries-path=/history"
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
                .andExpect(jsonPath("$.mock").value(false));

        mockMvc.perform(get("/api/v2/telemetry/history")
                        .header("X-Mock-Data", "true")
                        .param("sensorIds", "TEMP-01")
                        .param("from", "1794984000000")
                        .param("to", "1794984300000")
                        .param("intervalMs", "60000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mock").value(true))
                .andExpect(jsonPath("$.series[0].points.length()").value(6));
    }
}
