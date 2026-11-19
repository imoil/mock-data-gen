package com.samsung.ees.infra.mock.starter.openapi;

import com.samsung.ees.infra.mock.starter.controller.MockSensorController;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.web.method.HandlerMethod;

/**
 * Injects the optional X-Mock-Data header parameter into OpenAPI docs for mock endpoints only.
 */
public class MockHeaderOperationCustomizer implements OperationCustomizer {

    @Override
    public Operation customize(Operation operation, HandlerMethod handlerMethod) {
        if (!MockSensorController.class.isAssignableFrom(handlerMethod.getBeanType())) {
            return operation;
        }
        boolean alreadyPresent = operation.getParameters() != null
                && operation.getParameters().stream()
                        .anyMatch(p -> MockSensorController.MOCK_HEADER_NAME.equalsIgnoreCase(p.getName()));
        if (!alreadyPresent) {
            operation.addParametersItem(new Parameter()
                    .in("header")
                    .name(MockSensorController.MOCK_HEADER_NAME)
                    .description("Set to 'true' to route the request to deterministic mock data (local/dev only).")
                    .required(false)
                    .schema(new StringSchema()._default("true"))
                    .example("true"));
        }
        return operation;
    }
}
