package com.samsung.ees.infra.mock.starter.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.samsung.ees.infra.mock.core.exception.InvalidMockRequestException;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@SpringBootTest(classes = MockSensorControllerAdviceIsolationTest.HostTestApp.class)
@AutoConfigureMockMvc
@ActiveProfiles("local")
class MockSensorControllerAdviceIsolationTest {

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import({HostController.class, HostRestControllerAdvice.class})
    static class HostTestApp {
    }

    @RestController
    static class HostController {

        @GetMapping("/host/boom")
        String boom() {
            throw new IllegalStateException("host failure");
        }

        @GetMapping("/host/invalid-mock-ex")
        String invalidMock() {
            throw new InvalidMockRequestException("host-thrown validation error");
        }
    }

    @RestControllerAdvice
    @Order(Ordered.HIGHEST_PRECEDENCE)
    static class HostRestControllerAdvice {

        @ExceptionHandler(Exception.class)
        ResponseEntity<Map<String, Object>> handleAny(Exception ex) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body(Map.of(
                            "source", "host-advice",
                            "error", ex.getMessage()
                    ));
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void registersHostAdviceBeanAndZeroStarterAdviceBeans() {
        Map<String, Object> controllerAdvices = applicationContext.getBeansWithAnnotation(ControllerAdvice.class);
        Map<String, Object> restControllerAdvices =
                applicationContext.getBeansWithAnnotation(RestControllerAdvice.class);

        assertThat(controllerAdvices.values())
                .anySatisfy(bean -> assertThat(bean).isInstanceOf(HostRestControllerAdvice.class))
                .filteredOn(bean -> !(bean instanceof HostRestControllerAdvice))
                .noneSatisfy(bean -> assertThat(bean.getClass().getPackageName())
                        .startsWith("com.samsung.ees.infra.mock"));
        assertThat(restControllerAdvices.values())
                .anySatisfy(bean -> assertThat(bean).isInstanceOf(HostRestControllerAdvice.class))
                .filteredOn(bean -> !(bean instanceof HostRestControllerAdvice))
                .noneSatisfy(bean -> assertThat(bean.getClass().getPackageName())
                        .startsWith("com.samsung.ees.infra.mock"));
    }

    @Test
    void mockSensorController400ErrorsBypassHostRestControllerAdvice() throws Exception {
        assertIsolatedMockBadRequest(
                "/api/v1/sensors/current",
                "sensorIds must not be empty"
        );
        assertIsolatedMockBadRequest(
                "/api/v1/sensors/timeseries?sensorIds=TEMP-01&from=2026-11-18T07:40:00Z&to=2026-11-18T06:40:00Z",
                "'from' timestamp must be before 'to'"
        );
        assertIsolatedMockBadRequest(
                "/api/v1/sensors/timeseries?sensorIds=TEMP-01&to=1794987600000",
                "Required request parameter 'from'"
        );
        assertIsolatedMockBadRequest(
                "/api/v1/sensors/current?sensorIds=TEMP-01&intervalMs=abc",
                "Invalid parameter 'intervalMs': abc"
        );
    }

    @Test
    void hostControllerExceptionsStillRouteToHostRestControllerAdvice() throws Exception {
        mockMvc.perform(get("/host/boom"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.source").value("host-advice"))
                .andExpect(jsonPath("$.error").value("host failure"));

        mockMvc.perform(get("/host/invalid-mock-ex"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.source").value("host-advice"))
                .andExpect(jsonPath("$.error").value("host-thrown validation error"));
    }

    private void assertIsolatedMockBadRequest(String uri, String expectedMessageSubstring) throws Exception {
        MvcResult result = mockMvc.perform(get(uri).header("X-Mock-Data", "true"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").isString())
                .andExpect(jsonPath("$.timestamp").isString())
                .andExpect(jsonPath("$.source").doesNotExist())
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(root.get("message").asText()).contains(expectedMessageSubstring);
        Instant parsedTimestamp = Instant.parse(root.get("timestamp").asText());
        assertThat(parsedTimestamp).isNotNull();
    }
}
