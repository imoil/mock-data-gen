package com.samsung.ees.infra.mock.starter.openapi;

import com.samsung.ees.infra.mock.starter.controller.MockSensorController;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import java.util.Optional;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.web.method.HandlerMethod;

/**
 * Injects the optional X-Mock-Data header parameter into OpenAPI docs for mock endpoints only.
 */
public class MockHeaderOperationCustomizer implements OperationCustomizer {

    private static final String HEADER_DESCRIPTION =
            "Set to 'true' to route the request to deterministic mock data (local/dev only).";

    @Override
    public Operation customize(Operation operation, HandlerMethod handlerMethod) {
        if (!MockSensorController.class.isAssignableFrom(handlerMethod.getBeanType())) {
            return operation;
        }
        Parameter parameter = findMockHeaderParameter(operation).orElseGet(() -> {
            Parameter created = new Parameter().name(MockSensorController.MOCK_HEADER_NAME);
            operation.addParametersItem(created);
            return created;
        });
        applyDefaultMetadata(parameter);
        return operation;
    }

    private Optional<Parameter> findMockHeaderParameter(Operation operation) {
        if (operation.getParameters() == null) {
            return Optional.empty();
        }
        return operation.getParameters().stream()
                .filter(p -> MockSensorController.MOCK_HEADER_NAME.equalsIgnoreCase(p.getName()))
                .findFirst();
    }

    private void applyDefaultMetadata(Parameter parameter) {
        if (parameter.getIn() == null) {
            parameter.setIn("header");
        }
        if (parameter.getRequired() == null) {
            parameter.setRequired(false);
        }
        if (parameter.getDescription() == null || parameter.getDescription().isBlank()) {
            parameter.setDescription(HEADER_DESCRIPTION);
        }
        if (parameter.getSchema() == null) {
            parameter.setSchema(new StringSchema()._default("true"));
        }
        if (parameter.getExample() == null) {
            parameter.setExample("true");
        }
    }
}
