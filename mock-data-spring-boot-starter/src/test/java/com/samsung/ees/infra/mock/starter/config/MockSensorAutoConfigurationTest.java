package com.samsung.ees.infra.mock.starter.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.samsung.ees.infra.mock.core.config.GeneratorConfig;
import com.samsung.ees.infra.mock.core.engine.DeterministicMockGenerator;
import com.samsung.ees.infra.mock.starter.controller.MockSensorController;
import com.samsung.ees.infra.mock.starter.openapi.MockHeaderOperationCustomizer;
import org.junit.jupiter.api.Test;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
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
    void bindsCustomConfigurationProperties() {
        contextRunner
                .withPropertyValues(
                        "spring.profiles.active=local",
                        "app-custom-config.mock.sensor.base-path=/api/v2/telemetry",
                        "app-custom-config.mock.sensor.current-path=/latest",
                        "app-custom-config.mock.sensor.timeseries-path=/history",
                        "app-custom-config.mock.sensor.min-interval-ms=250",
                        "app-custom-config.mock.sensor.decimal-scale=4"
                )
                .run(context -> {
                    MockSensorProperties props = context.getBean(MockSensorProperties.class);
                    assertThat(props.getBasePath()).isEqualTo("/api/v2/telemetry");
                    assertThat(props.getCurrentPath()).isEqualTo("/latest");
                    assertThat(props.getTimeseriesPath()).isEqualTo("/history");
                    assertThat(props.getMinIntervalMs()).isEqualTo(250L);
                    assertThat(props.getDecimalScale()).isEqualTo(4);
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
}
