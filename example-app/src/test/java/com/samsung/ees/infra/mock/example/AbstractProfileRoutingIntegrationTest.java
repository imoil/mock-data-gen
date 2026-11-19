package com.samsung.ees.infra.mock.example;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
abstract class AbstractProfileRoutingIntegrationTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Test
    void routesCurrentToMockControllerWhenHeaderIsTrue() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/sensors/current")
                        .header("X-Mock-Data", "true")
                        .param("sensorIds", "TEMP-01,PRESS-02"))
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
        assertThat(parsedTimestamp.toEpochMilli()).isEqualTo(root.get("epochMillis").asLong());
    }

    @Test
    void routesCurrentToOperationalControllerWhenHeaderIsAbsentOrFalse() throws Exception {
        for (String headerValue : new String[]{null, "false"}) {
            var request = get("/api/v1/sensors/current")
                    .param("sensorIds", "TEMP-01");
            if (headerValue != null) {
                request.header("X-Mock-Data", headerValue);
            }
            mockMvc.perform(request)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.mock").value(false))
                    .andExpect(jsonPath("$.sensors[0].source").value("LIVE_STORE"));
        }
    }

    @Test
    void exposesMockHeaderInOpenApiDocs() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("X-Mock-Data")))
                .andExpect(jsonPath("$.paths['/api/v1/sensors/current'].get.parameters[?(@.name=='X-Mock-Data')].in")
                        .value(hasItem("header")))
                .andExpect(jsonPath("$.paths['/api/v1/sensors/current'].get.parameters[?(@.name=='X-Mock-Data')].required")
                        .value(hasItem(false)))
                .andExpect(jsonPath("$.paths['/api/v1/sensors/timeseries'].get.parameters[?(@.name=='X-Mock-Data')].in")
                        .value(hasItem("header")))
                .andExpect(jsonPath("$.paths['/api/v1/sensors/timeseries'].get.parameters[?(@.name=='X-Mock-Data')].required")
                        .value(hasItem(false)));
    }

    @Test
    void routesTimeSeriesToMockControllerWhenHeaderIsTrue() throws Exception {
        mockMvc.perform(get("/api/v1/sensors/timeseries")
                        .header("X-Mock-Data", "true")
                        .param("sensorIds", "TEMP-01,PRESS-02")
                        .param("from", "2026-11-18T15:40:00+09:00")
                        .param("to", "2026-11-18T15:45:00+09:00")
                        .param("intervalMs", "60000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from").value("2026-11-18T06:40:00Z"))
                .andExpect(jsonPath("$.to").value("2026-11-18T06:45:00Z"))
                .andExpect(jsonPath("$.intervalMs").value(60000))
                .andExpect(jsonPath("$.mock").value(true))
                .andExpect(jsonPath("$.series.length()").value(2))
                .andExpect(jsonPath("$.series[0].sensorId").value("TEMP-01"))
                .andExpect(jsonPath("$.series[0].pattern").isNotEmpty())
                .andExpect(jsonPath("$.series[0].points.length()").value(6))
                .andExpect(jsonPath("$.series[0].points[0].timestamp").isNumber())
                .andExpect(jsonPath("$.series[0].points[0].value").isNumber());
    }

    @Test
    void routesTimeSeriesToOperationalControllerWhenHeaderIsAbsentOrFalse() throws Exception {
        for (String headerValue : new String[]{null, "false"}) {
            var request = get("/api/v1/sensors/timeseries")
                    .param("sensorIds", "TEMP-01")
                    .param("from", "2026-11-18T06:40:00Z")
                    .param("to", "2026-11-18T06:45:00Z");
            if (headerValue != null) {
                request.header("X-Mock-Data", headerValue);
            }
            mockMvc.perform(request)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.mock").value(false))
                    .andExpect(jsonPath("$.series[0].source").value("LIVE_STORE"));
        }
    }

    @Test
    void maintainsSlidingWindowContinuityOverHttp() throws Exception {
        JsonNode window1 = fetchTimeSeries("TEMP-01,PRESS-02", "2026-11-18T10:00:12Z", "2026-11-18T11:00:12Z", "60000");
        JsonNode window2 = fetchTimeSeries("TEMP-01,PRESS-02", "2026-11-18T10:01:12Z", "2026-11-18T11:01:12Z", "60000");

        long expectedGridStart1 = Instant.parse("2026-11-18T10:01:00Z").toEpochMilli();
        long expectedGridStart2 = Instant.parse("2026-11-18T10:02:00Z").toEpochMilli();

        for (int i = 0; i < 2; i++) {
            Map<Long, Double> map1 = extractPointMap(window1.get("series").get(i).get("points"));
            Map<Long, Double> map2 = extractPointMap(window2.get("series").get(i).get("points"));
            assertThat(window1.get("series").get(i).get("points").get(0).get("timestamp").asLong())
                    .isEqualTo(expectedGridStart1);
            assertThat(window2.get("series").get(i).get("points").get(0).get("timestamp").asLong())
                    .isEqualTo(expectedGridStart2);

            List<Long> overlap = map1.keySet().stream()
                    .filter(map2::containsKey)
                    .toList();
            assertThat(overlap).hasSize(59);
            for (Long ts : overlap) {
                assertThat(map2.get(ts)).isEqualTo(map1.get(ts));
            }
        }
    }

    @Test
    void matchesCurrentAndTimeSeriesExactValueAtQuantizedTimestamp() throws Exception {
        MvcResult currentResult = mockMvc.perform(get("/api/v1/sensors/current")
                        .header("X-Mock-Data", "true")
                        .param("sensorIds", "TEMP-01,PRESS-02")
                        .param("intervalMs", "10000"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode currentRoot = objectMapper.readTree(currentResult.getResponse().getContentAsString());
        long quantizedEpochMs = currentRoot.get("epochMillis").asLong();

        JsonNode seriesRoot = fetchTimeSeries(
                "TEMP-01,PRESS-02",
                Long.toString(quantizedEpochMs - 20_000L),
                Long.toString(quantizedEpochMs + 20_000L),
                "10000"
        );

        for (int i = 0; i < 2; i++) {
            JsonNode currentSensor = currentRoot.get("sensors").get(i);
            JsonNode seriesSensor = seriesRoot.get("series").get(i);
            assertThat(seriesSensor.get("pattern").asText()).isEqualTo(currentSensor.get("pattern").asText());
            Map<Long, Double> pointMap = extractPointMap(seriesSensor.get("points"));
            assertThat(pointMap).containsKey(quantizedEpochMs);
            assertThat(pointMap.get(quantizedEpochMs)).isEqualTo(currentSensor.get("value").asDouble());
        }
    }

    protected JsonNode fetchTimeSeries(String sensorIds, String from, String to, String intervalMs) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/sensors/timeseries")
                        .header("X-Mock-Data", "true")
                        .param("sensorIds", sensorIds)
                        .param("from", from)
                        .param("to", to)
                        .param("intervalMs", intervalMs))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mock").value(true))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    protected Map<Long, Double> extractPointMap(JsonNode pointsNode) {
        Map<Long, Double> points = new LinkedHashMap<>();
        for (JsonNode point : pointsNode) {
            points.put(point.get("timestamp").asLong(), point.get("value").asDouble());
        }
        return points;
    }
}
