package com.samsung.ees.infra.mock.starter.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

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

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void returnsCurrentMockValuesWithIsoTimestampAndEpochMillis() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/sensors/current")
                        .header("X-Mock-Data", "true")
                        .param("sensorIds", "TEMP-01,PRESS-02")
                        .param("intervalMs", "1000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timestamp").isString())
                .andExpect(jsonPath("$.epochMillis").isNumber())
                .andExpect(jsonPath("$.mock").value(true))
                .andExpect(jsonPath("$.sensors.length()").value(2))
                .andExpect(jsonPath("$.sensors[0].sensorId").value("TEMP-01"))
                .andExpect(jsonPath("$.sensors[0].value").isNumber())
                .andExpect(jsonPath("$.sensors[0].pattern").isNotEmpty())
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        Instant parsedTimestamp = Instant.parse(root.get("timestamp").asText());
        long epochMillis = root.get("epochMillis").asLong();
        assertThat(parsedTimestamp.toEpochMilli()).isEqualTo(epochMillis);
        assertThat(epochMillis % 1000L).isZero();
    }

    @Test
    void acceptsIso8601AndEpochMillisTimestamps() throws Exception {
        MvcResult isoUtcResult = mockMvc.perform(get("/api/v1/sensors/timeseries")
                        .header("X-Mock-Data", "true")
                        .param("sensorIds", "TEMP-01,PRESS-02")
                        .param("from", "2026-11-18T06:40:00Z")
                        .param("to", "2026-11-18T07:40:00Z")
                        .param("intervalMs", "60000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from").value("2026-11-18T06:40:00Z"))
                .andExpect(jsonPath("$.to").value("2026-11-18T07:40:00Z"))
                .andExpect(jsonPath("$.intervalMs").value(60000))
                .andExpect(jsonPath("$.mock").value(true))
                .andExpect(jsonPath("$.series.length()").value(2))
                .andExpect(jsonPath("$.series[0].sensorId").value("TEMP-01"))
                .andExpect(jsonPath("$.series[0].pattern").isNotEmpty())
                .andExpect(jsonPath("$.series[0].points.length()").value(61))
                .andExpect(jsonPath("$.series[0].points[0].timestamp").value(1794984000000L))
                .andExpect(jsonPath("$.series[0].points[0].value").isNumber())
                .andReturn();

        MvcResult epochResult = mockMvc.perform(get("/api/v1/sensors/timeseries")
                        .header("X-Mock-Data", "true")
                        .param("sensorIds", "TEMP-01,PRESS-02")
                        .param("from", "1794984000000")
                        .param("to", "1794987600000")
                        .param("intervalMs", "60000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from").value("2026-11-18T06:40:00Z"))
                .andExpect(jsonPath("$.to").value("2026-11-18T07:40:00Z"))
                .andExpect(jsonPath("$.intervalMs").value(60000))
                .andExpect(jsonPath("$.mock").value(true))
                .andExpect(jsonPath("$.series[0].sensorId").value("TEMP-01"))
                .andExpect(jsonPath("$.series[0].points[0].timestamp").value(1794984000000L))
                .andReturn();

        assertThat(epochResult.getResponse().getContentAsString())
                .isEqualTo(isoUtcResult.getResponse().getContentAsString());
    }

    @Test
    void acceptsIso8601OffsetTimestampsAndMatchesUtcEquivalent() throws Exception {
        MvcResult utcResult = mockMvc.perform(get("/api/v1/sensors/timeseries")
                        .header("X-Mock-Data", "true")
                        .param("sensorIds", "TEMP-01,PRESS-02")
                        .param("from", "2026-11-18T06:40:00.500Z")
                        .param("to", "2026-11-18T06:45:00Z")
                        .param("intervalMs", "60000"))
                .andExpect(status().isOk())
                .andReturn();

        MvcResult offsetResult = mockMvc.perform(get("/api/v1/sensors/timeseries")
                        .header("X-Mock-Data", "true")
                        .param("sensorIds", "TEMP-01,PRESS-02")
                        .param("from", "2026-11-18T15:40:00.500+09:00")
                        .param("to", "2026-11-18T15:45:00+09:00")
                        .param("intervalMs", "60000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from").value("2026-11-18T06:40:00.500Z"))
                .andExpect(jsonPath("$.to").value("2026-11-18T06:45:00Z"))
                .andExpect(jsonPath("$.intervalMs").value(60000))
                .andExpect(jsonPath("$.mock").value(true))
                .andExpect(jsonPath("$.series.length()").value(2))
                .andExpect(jsonPath("$.series[0].points.length()").value(5))
                .andReturn();

        assertThat(offsetResult.getResponse().getContentAsString())
                .isEqualTo(utcResult.getResponse().getContentAsString());
    }

    @Test
    void returns400MockErrorResponseOnInvalidInputs() throws Exception {
        // Missing / empty / blank sensorIds on /current and /timeseries
        assertBadRequest(
                get("/api/v1/sensors/current").header("X-Mock-Data", "true"),
                "sensorIds must not be empty"
        );
        assertBadRequest(
                get("/api/v1/sensors/current")
                        .header("X-Mock-Data", "true")
                        .param("sensorIds", ""),
                "sensorIds must not be empty"
        );
        assertBadRequest(
                get("/api/v1/sensors/current")
                        .header("X-Mock-Data", "true")
                        .param("sensorIds", "TEMP-01, "),
                "sensorIds must not be empty"
        );
        assertBadRequest(
                get("/api/v1/sensors/timeseries")
                        .header("X-Mock-Data", "true")
                        .param("sensorIds", "")
                        .param("from", "1794984000000")
                        .param("to", "1794987600000"),
                "sensorIds must not be empty"
        );
        assertBadRequest(
                get("/api/v1/sensors/timeseries")
                        .header("X-Mock-Data", "true")
                        .param("sensorIds", "TEMP-01, ")
                        .param("from", "1794984000000")
                        .param("to", "1794987600000"),
                "sensorIds must not be empty"
        );

        // Invalid time range: from > to and from == to
        assertBadRequest(
                get("/api/v1/sensors/timeseries")
                        .header("X-Mock-Data", "true")
                        .param("sensorIds", "TEMP-01")
                        .param("from", "2026-11-18T07:40:00Z")
                        .param("to", "2026-11-18T06:40:00Z"),
                "'from' timestamp must be before 'to'"
        );
        assertBadRequest(
                get("/api/v1/sensors/timeseries")
                        .header("X-Mock-Data", "true")
                        .param("sensorIds", "TEMP-01")
                        .param("from", "2026-11-18T06:40:00Z")
                        .param("to", "2026-11-18T06:40:00Z"),
                "'from' timestamp must be before 'to'"
        );

        // intervalMs < minIntervalMs
        assertBadRequest(
                get("/api/v1/sensors/current")
                        .header("X-Mock-Data", "true")
                        .param("sensorIds", "TEMP-01")
                        .param("intervalMs", "50"),
                "intervalMs must be at least 100ms"
        );
        assertBadRequest(
                get("/api/v1/sensors/timeseries")
                        .header("X-Mock-Data", "true")
                        .param("sensorIds", "TEMP-01")
                        .param("from", "1794984000000")
                        .param("to", "1794987600000")
                        .param("intervalMs", "50"),
                "intervalMs must be at least 100ms"
        );

        // (to - from) / intervalMs > maxPointsPerSensor (10000)
        assertBadRequest(
                get("/api/v1/sensors/timeseries")
                        .header("X-Mock-Data", "true")
                        .param("sensorIds", "TEMP-01")
                        .param("from", "0")
                        .param("to", "10001000")
                        .param("intervalMs", "1000"),
                "Requested points exceed maximum limit (10000)"
        );

        // MissingServletRequestParameterException (missing from / missing to)
        assertBadRequestContaining(
                get("/api/v1/sensors/timeseries")
                        .header("X-Mock-Data", "true")
                        .param("sensorIds", "TEMP-01")
                        .param("to", "1794987600000"),
                "from"
        );
        assertBadRequestContaining(
                get("/api/v1/sensors/timeseries")
                        .header("X-Mock-Data", "true")
                        .param("sensorIds", "TEMP-01")
                        .param("from", "1794984000000"),
                "to"
        );

        // Invalid / blank timestamp format
        assertBadRequest(
                get("/api/v1/sensors/timeseries")
                        .header("X-Mock-Data", "true")
                        .param("sensorIds", "TEMP-01")
                        .param("from", "not-a-timestamp")
                        .param("to", "1794987600000"),
                "Invalid 'from' timestamp format: not-a-timestamp"
        );
        assertBadRequest(
                get("/api/v1/sensors/timeseries")
                        .header("X-Mock-Data", "true")
                        .param("sensorIds", "TEMP-01")
                        .param("from", " ")
                        .param("to", "1794987600000"),
                "'from' timestamp must not be empty"
        );

        // MethodArgumentTypeMismatchException (intervalMs=abc)
        assertBadRequest(
                get("/api/v1/sensors/current")
                        .header("X-Mock-Data", "true")
                        .param("sensorIds", "TEMP-01")
                        .param("intervalMs", "abc"),
                "Invalid parameter 'intervalMs': abc"
        );
        assertBadRequest(
                get("/api/v1/sensors/timeseries")
                        .header("X-Mock-Data", "true")
                        .param("sensorIds", "TEMP-01")
                        .param("from", "1794984000000")
                        .param("to", "1794987600000")
                        .param("intervalMs", "abc"),
                "Invalid parameter 'intervalMs': abc"
        );
    }

    @Test
    void allowsExactMaxPointsRatioBoundaryWithInclusiveGridEndpoints() throws Exception {
        mockMvc.perform(get("/api/v1/sensors/timeseries")
                        .header("X-Mock-Data", "true")
                        .param("sensorIds", "TEMP-01")
                        .param("from", "0")
                        .param("to", "10000000")
                        .param("intervalMs", "1000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mock").value(true))
                .andExpect(jsonPath("$.series[0].points.length()").value(10001))
                .andExpect(jsonPath("$.series[0].points[0].timestamp").value(0L))
                .andExpect(jsonPath("$.series[0].points[10000].timestamp").value(10000000L));
    }

    private void assertBadRequest(MockHttpServletRequestBuilder request, String expectedMessage) throws Exception {
        MvcResult result = mockMvc.perform(request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(expectedMessage))
                .andExpect(jsonPath("$.timestamp").isString())
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(Instant.parse(root.get("timestamp").asText())).isNotNull();
    }

    private void assertBadRequestContaining(MockHttpServletRequestBuilder request, String expectedSubstring)
            throws Exception {
        MvcResult result = mockMvc.perform(request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").isString())
                .andExpect(jsonPath("$.timestamp").isString())
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(root.get("message").asText()).contains(expectedSubstring);
        assertThat(Instant.parse(root.get("timestamp").asText())).isNotNull();
    }
}
