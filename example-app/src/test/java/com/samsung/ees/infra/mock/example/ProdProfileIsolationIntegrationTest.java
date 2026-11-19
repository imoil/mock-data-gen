package com.samsung.ees.infra.mock.example;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.samsung.ees.infra.mock.starter.controller.MockSensorController;
import com.samsung.ees.infra.mock.starter.openapi.MockHeaderOperationCustomizer;
import org.junit.jupiter.api.Test;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("prod")
@TestPropertySource(properties = "ees.mock.sensor.enabled=true")
class ProdProfileIsolationIntegrationTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void doesNotRegisterMockControllerOrCustomizerAndIgnoresMockHeaderInProdEvenWhenEnabled() throws Exception {
        assertThat(applicationContext.getBeansOfType(MockSensorController.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(OperationCustomizer.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(MockHeaderOperationCustomizer.class)).isEmpty();

        mockMvc.perform(get("/api/v1/sensors/current")
                        .header("X-Mock-Data", "true")
                        .param("sensorIds", "TEMP-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mock").value(false))
                .andExpect(jsonPath("$.sensors[0].source").value("LIVE_STORE"));

        mockMvc.perform(get("/api/v1/sensors/timeseries")
                        .header("X-Mock-Data", "true")
                        .param("sensorIds", "TEMP-01")
                        .param("from", "2026-11-18T06:40:00Z")
                        .param("to", "2026-11-18T06:45:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mock").value(false))
                .andExpect(jsonPath("$.series[0].source").value("LIVE_STORE"));

        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("X-Mock-Data"))))
                .andExpect(jsonPath("$.paths['/api/v1/sensors/current'].get.parameters[?(@.name=='X-Mock-Data')]")
                        .isEmpty())
                .andExpect(jsonPath("$.paths['/api/v1/sensors/timeseries'].get.parameters[?(@.name=='X-Mock-Data')]")
                        .isEmpty());
    }
}
