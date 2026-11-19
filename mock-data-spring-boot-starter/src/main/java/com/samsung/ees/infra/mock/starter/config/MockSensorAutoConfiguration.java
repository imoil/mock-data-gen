package com.samsung.ees.infra.mock.starter.config;

import com.samsung.ees.infra.mock.core.config.GeneratorConfig;
import com.samsung.ees.infra.mock.core.engine.DeterministicMockGenerator;
import com.samsung.ees.infra.mock.starter.controller.MockSensorController;
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
        prefix = "app-custom-config.mock.sensor",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
@EnableConfigurationProperties(MockSensorProperties.class)
@Import(MockSensorOpenApiConfiguration.class)
public class MockSensorAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public GeneratorConfig mockSensorGeneratorConfig(MockSensorProperties properties) {
        return properties.toGeneratorConfig();
    }

    @Bean
    @ConditionalOnMissingBean
    public DeterministicMockGenerator deterministicMockGenerator(GeneratorConfig mockSensorGeneratorConfig) {
        return new DeterministicMockGenerator(mockSensorGeneratorConfig);
    }

    @Bean
    @ConditionalOnMissingBean
    public MockSensorController mockSensorController(DeterministicMockGenerator deterministicMockGenerator) {
        return new MockSensorController(deterministicMockGenerator);
    }
}
