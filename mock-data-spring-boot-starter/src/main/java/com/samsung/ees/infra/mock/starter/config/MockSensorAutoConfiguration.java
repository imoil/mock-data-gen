package com.samsung.ees.infra.mock.starter.config;

import com.samsung.ees.infra.mock.core.config.GeneratorConfig;
import com.samsung.ees.infra.mock.core.config.SensorPhysicsPreset;
import com.samsung.ees.infra.mock.core.engine.DeterministicMockGenerator;
import com.samsung.ees.infra.mock.starter.controller.MockSensorController;
import java.time.Clock;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;

/**
 * Auto-configuration for the deterministic sensor mock endpoints, strictly isolated to local and dev profiles.
 */
@AutoConfiguration
@Profile({"local", "dev"})
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(
        prefix = "ees.mock.sensor",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
@EnableConfigurationProperties
@Import(MockSensorOpenApiConfiguration.class)
public class MockSensorAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public MockSensorProperties mockSensorProperties() {
        return new MockSensorProperties();
    }

    @Bean
    @ConditionalOnMissingBean
    public SensorPhysicsPreset mockSensorPhysicsPreset(MockSensorProperties properties) {
        return properties.toPhysicsPreset();
    }

    @Bean
    @ConditionalOnMissingBean
    public GeneratorConfig mockSensorGeneratorConfig(
            MockSensorProperties properties,
            SensorPhysicsPreset mockSensorPhysicsPreset
    ) {
        return properties.toGeneratorConfig(mockSensorPhysicsPreset);
    }

    @Bean
    @ConditionalOnMissingBean
    public DeterministicMockGenerator deterministicMockGenerator(
            GeneratorConfig mockSensorGeneratorConfig,
            ObjectProvider<Clock> clockProvider
    ) {
        return new DeterministicMockGenerator(mockSensorGeneratorConfig, resolveClock(clockProvider));
    }

    @Bean
    @ConditionalOnMissingBean
    public MockSensorController mockSensorController(
            DeterministicMockGenerator deterministicMockGenerator,
            ObjectProvider<Clock> clockProvider
    ) {
        return new MockSensorController(deterministicMockGenerator, resolveClock(clockProvider));
    }

    private static Clock resolveClock(ObjectProvider<Clock> clockProvider) {
        return clockProvider.getIfAvailable(Clock::systemUTC);
    }
}
