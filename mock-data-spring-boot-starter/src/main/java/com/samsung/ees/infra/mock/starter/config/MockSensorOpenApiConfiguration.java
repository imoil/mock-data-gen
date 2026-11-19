package com.samsung.ees.infra.mock.starter.config;

import com.samsung.ees.infra.mock.starter.openapi.MockHeaderOperationCustomizer;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Optional Springdoc OpenAPI configuration loaded only when springdoc is on the classpath.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(OperationCustomizer.class)
public class MockSensorOpenApiConfiguration {

    @Bean
    @ConditionalOnMissingBean(name = "mockHeaderOperationCustomizer")
    public OperationCustomizer mockHeaderOperationCustomizer() {
        return new MockHeaderOperationCustomizer();
    }
}
