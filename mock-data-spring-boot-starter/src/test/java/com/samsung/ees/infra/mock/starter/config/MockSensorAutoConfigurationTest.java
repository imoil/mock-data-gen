package com.samsung.ees.infra.mock.starter.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.samsung.ees.infra.mock.core.config.GeneratorConfig;
import com.samsung.ees.infra.mock.core.config.SensorPhysicsPreset;
import com.samsung.ees.infra.mock.core.engine.DeterministicMockGenerator;
import com.samsung.ees.infra.mock.core.exception.InvalidMockRequestException;
import com.samsung.ees.infra.mock.core.model.CurrentMockResponse;
import com.samsung.ees.infra.mock.core.model.TimeSeriesMockResponse;
import com.samsung.ees.infra.mock.starter.controller.MockSensorController;
import com.samsung.ees.infra.mock.starter.openapi.MockHeaderOperationCustomizer;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.RestControllerAdvice;

class MockSensorAutoConfigurationTest {

    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(MockSensorAutoConfiguration.class));

    @Test
    void registersMockBeansWhenLocalProfileActive() {
        contextRunner
                .withPropertyValues("spring.profiles.active=local")
                .run(context -> {
                    assertThat(context).hasSingleBean(MockSensorProperties.class);
                    assertThat(context).hasSingleBean(GeneratorConfig.class);
                    assertThat(context).hasSingleBean(DeterministicMockGenerator.class);
                    assertThat(context).hasSingleBean(MockSensorController.class);
                    assertThat(context).hasSingleBean(MockHeaderOperationCustomizer.class);
                });
    }

    @Test
    void registersMockBeansWhenDevProfileActive() {
        contextRunner
                .withPropertyValues("spring.profiles.active=dev")
                .run(context -> {
                    assertThat(context).hasSingleBean(MockSensorController.class);
                    assertThat(context).hasSingleBean(DeterministicMockGenerator.class);
                });
    }

    @Test
    void doesNotRegisterAnyMockBeanWhenProdProfileActive() {
        contextRunner
                .withPropertyValues(
                        "spring.profiles.active=prod",
                        "app-custom-config.mock.sensor.enabled=true"
                )
                .run(context -> {
                    assertThat(context).doesNotHaveBean(MockSensorController.class);
                    assertThat(context).doesNotHaveBean(DeterministicMockGenerator.class);
                    assertThat(context).doesNotHaveBean(MockHeaderOperationCustomizer.class);
                });
    }

    @Test
    void doesNotRegisterWhenExplicitlyDisabledInLocalProfile() {
        contextRunner
                .withPropertyValues(
                        "spring.profiles.active=local",
                        "app-custom-config.mock.sensor.enabled=false"
                )
                .run(context -> {
                    assertThat(context).doesNotHaveBean(MockSensorController.class);
                    assertThat(context).doesNotHaveBean(DeterministicMockGenerator.class);
                    assertThat(context).doesNotHaveBean(GeneratorConfig.class);
                    assertThat(context).doesNotHaveBean(MockHeaderOperationCustomizer.class);
                    assertThat(context).doesNotHaveBean(OperationCustomizer.class);
                });
    }

    @Test
    void doesNotRegisterWhenExplicitlyDisabledInDevProfile() {
        contextRunner
                .withPropertyValues(
                        "spring.profiles.active=dev",
                        "app-custom-config.mock.sensor.enabled=false"
                )
                .run(context -> {
                    assertThat(context).doesNotHaveBean(MockSensorController.class);
                    assertThat(context).doesNotHaveBean(DeterministicMockGenerator.class);
                    assertThat(context).doesNotHaveBean(GeneratorConfig.class);
                    assertThat(context).doesNotHaveBean(MockHeaderOperationCustomizer.class);
                    assertThat(context).doesNotHaveBean(OperationCustomizer.class);
                });
    }

    @Test
    void skipsOpenApiCustomizerWhenSpringdocIsAbsent() {
        contextRunner
                .withPropertyValues("spring.profiles.active=local")
                .withClassLoader(new FilteredClassLoader(OperationCustomizer.class))
                .run(context -> {
                    assertThat(context).hasSingleBean(MockSensorController.class);
                    assertThat(context).doesNotHaveBean(OperationCustomizer.class);
                });
    }

    @Test
    void bindsCustomConfigurationPropertiesAndPropagatesToGeneratorConfigBean() {
        contextRunner
                .withPropertyValues(
                        "spring.profiles.active=local",
                        "app-custom-config.mock.sensor.base-path=/api/v2/telemetry",
                        "app-custom-config.mock.sensor.current-path=/latest",
                        "app-custom-config.mock.sensor.timeseries-path=/history",
                        "app-custom-config.mock.sensor.default-current-interval-ms=2000",
                        "app-custom-config.mock.sensor.default-series-interval-ms=30000",
                        "app-custom-config.mock.sensor.min-interval-ms=250",
                        "app-custom-config.mock.sensor.max-points-per-sensor=500",
                        "app-custom-config.mock.sensor.decimal-scale=4"
                )
                .run(context -> {
                    MockSensorProperties props = context.getBean(MockSensorProperties.class);
                    assertThat(props.getBasePath()).isEqualTo("/api/v2/telemetry");
                    assertThat(props.getCurrentPath()).isEqualTo("/latest");
                    assertThat(props.getTimeseriesPath()).isEqualTo("/history");
                    assertThat(props.getDefaultCurrentIntervalMs()).isEqualTo(2000L);
                    assertThat(props.getDefaultSeriesIntervalMs()).isEqualTo(30000L);
                    assertThat(props.getMinIntervalMs()).isEqualTo(250L);
                    assertThat(props.getMaxPointsPerSensor()).isEqualTo(500);
                    assertThat(props.getDecimalScale()).isEqualTo(4);

                    GeneratorConfig config = context.getBean(GeneratorConfig.class);
                    assertThat(config.defaultCurrentIntervalMs()).isEqualTo(2000L);
                    assertThat(config.defaultSeriesIntervalMs()).isEqualTo(30000L);
                    assertThat(config.minIntervalMs()).isEqualTo(250L);
                    assertThat(config.maxPointsPerSensor()).isEqualTo(500);
                    assertThat(config.decimalScale()).isEqualTo(4);
                    assertThat(config.physicsPreset()).isEqualTo(SensorPhysicsPreset.defaults());

                    DeterministicMockGenerator generator = context.getBean(DeterministicMockGenerator.class);
                    TimeSeriesMockResponse series = generator.generateTimeSeries(
                            List.of("TEMP-01"),
                            0L,
                            60_000L,
                            null
                    );
                    assertThat(series.intervalMs()).isEqualTo(30000L);

                    assertThatThrownBy(() -> generator.generateCurrentValues(List.of("TEMP-01"), 200L))
                            .isInstanceOf(InvalidMockRequestException.class)
                            .hasMessage("intervalMs must be at least 250ms");
                });
    }

    @Test
    void doesNotRegisterAnyGlobalControllerAdviceBeans() {
        for (String profile : new String[]{"local", "dev"}) {
            contextRunner
                    .withPropertyValues("spring.profiles.active=" + profile)
                    .run(context -> {
                        assertThat(context.getBeansWithAnnotation(ControllerAdvice.class)).isEmpty();
                        assertThat(context.getBeansWithAnnotation(RestControllerAdvice.class)).isEmpty();
                    });
        }
    }

    @Test
    void allowsOverridingGeneratorConfigBeanWithCustomPhysicsPreset() {
        contextRunner
                .withPropertyValues("spring.profiles.active=local")
                .withUserConfiguration(CustomGeneratorConfigConfiguration.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(GeneratorConfig.class);
                    assertThat(context).hasSingleBean(DeterministicMockGenerator.class);

                    GeneratorConfig config = context.getBean(GeneratorConfig.class);
                    assertThat(config.physicsPreset().baselineMin()).isEqualTo(500.0);
                    assertThat(config.minIntervalMs()).isEqualTo(500L);

                    DeterministicMockGenerator generator = context.getBean(DeterministicMockGenerator.class);
                    CurrentMockResponse current = generator.generateCurrentValues(
                            List.of("TEMP-01"),
                            null,
                            1794987600000L
                    );
                    assertThat(current.sensors().get(0).value()).isBetween(490.0, 515.0);

                    assertThatThrownBy(() -> generator.generateCurrentValues(List.of("TEMP-01"), 400L))
                            .isInstanceOf(InvalidMockRequestException.class)
                            .hasMessage("intervalMs must be at least 500ms");
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomGeneratorConfigConfiguration {

        @Bean
        GeneratorConfig customGeneratorConfig() {
            SensorPhysicsPreset preset = SensorPhysicsPreset.builder()
                    .baseline(500.0, 0.0)
                    .amplitude(5.0, 0.0)
                    .noiseRatio(0.0)
                    .build();
            return GeneratorConfig.builder()
                    .defaultCurrentIntervalMs(2000L)
                    .defaultSeriesIntervalMs(10000L)
                    .minIntervalMs(500L)
                    .decimalScale(3)
                    .physicsPreset(preset)
                    .build();
        }
    }
}
