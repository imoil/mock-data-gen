package com.samsung.ees.infra.mock.starter.openapi;

import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import java.util.Arrays;
import java.util.Optional;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.method.HandlerMethod;

/**
 * Injects the optional X-Mock-Data header parameter into OpenAPI docs for operations routed on X-Mock-Data.
 */
public class MockHeaderOperationCustomizer implements OperationCustomizer {

    public static final String MOCK_HEADER_NAME = "X-Mock-Data";
    private static final String HEADER_DESCRIPTION =
            "Set to 'true' to route the request to deterministic mock data (local/dev only).";

    @Override
    public Operation customize(Operation operation, HandlerMethod handlerMethod) {
        if (!isMockRoutedOperation(handlerMethod)) {
            return operation;
        }
        Parameter parameter = findMockHeaderParameter(operation).orElseGet(() -> {
            Parameter created = new Parameter().name(MOCK_HEADER_NAME);
            operation.addParametersItem(created);
            return created;
        });
        applyDefaultMetadata(parameter);
        return operation;
    }

    private boolean isMockRoutedOperation(HandlerMethod handlerMethod) {
        RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(
                handlerMethod.getMethod(),
                RequestMapping.class
        );
        if (mapping == null) {
            return false;
        }
        return Arrays.stream(mapping.headers())
                .anyMatch(MockHeaderOperationCustomizer::targetsMockHeader);
    }

    private static boolean targetsMockHeader(String headerExpression) {
        if (headerExpression == null || headerExpression.isBlank()) {
            return false;
        }
        int eqIdx = headerExpression.indexOf('=');
        String headerName = (eqIdx >= 0 ? headerExpression.substring(0, eqIdx) : headerExpression).trim();
        return MOCK_HEADER_NAME.equalsIgnoreCase(headerName);
    }

    private Optional<Parameter> findMockHeaderParameter(Operation operation) {
        if (operation.getParameters() == null) {
            return Optional.empty();
        }
        return operation.getParameters().stream()
                .filter(p -> MOCK_HEADER_NAME.equalsIgnoreCase(p.getName()))
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
